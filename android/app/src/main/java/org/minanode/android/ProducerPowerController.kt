package org.minanode.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Pauses only the node's process group. The app and supervisor remain alive. */
class ProducerPowerController(
    private val context: Context, private val files: NodeFiles,
    private val pid: () -> Int?, private val holdCpu: () -> Unit, private val releaseCpu: () -> Unit,
    private val changed: () -> Unit,
) {
    private val lock = Mutex()
    private val settings = ProducerPowerPreferences(context)
    private val alarms = requireNotNull(context.getSystemService(AlarmManager::class.java))
    val token: String = UUID.randomUUID().toString()
    private val alarm = PendingIntent.getService(context, 73,
        Intent(context, NodeService::class.java).setAction(NodeService.POWER_WAKE).putExtra("power-token", token),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @Volatile private var sleeper: CompletableDeferred<Unit>? = null
    private val manualSuspendRequested = AtomicBoolean(false)
    private var awakeSince = System.currentTimeMillis()
    private var syncedSince: Long? = null
    private var slots = emptyList<ProductionSlot>()
    private var savedSlots = emptyList<ProductionSlot>()
    private var lastSchedule = 0L
    private val schedule = AtomicFile(File(files.work, "power-upcoming-slots.json"))

    // Wake only the existing process; never reconstruct a launch or keep its password.
    fun wake() { if (sleeper != null) { holdCpu(); sleeper?.complete(Unit) } }

    fun requestSuspend() {
        if (settings.enabled() && NodeState.flow.value.run == RunPhase.RUNNING && NodeState.flow.value.powerCanSuspend) {
            manualSuspendRequested.set(true)
        }
    }

    private fun suspensionAvailable(available: Boolean) {
        NodeState.flow.update { if (it.powerCanSuspend != available) it.copy(powerCanSuspend = available) else it }
    }

    fun unavailable() { syncedSince = null; manualSuspendRequested.set(false); suspensionAvailable(false) }

    suspend fun consider(status: ChainStatus, measured: MonitorState) {
        // This is intentionally the first check: disabled mode does no scheduling,
        // persistence, signals, or changes to the normal polling/wake-lock path.
        if (!settings.enabled()) { unavailable(); return }
        val manual = manualSuspendRequested.getAndSet(false)
        suspensionAvailable(false)
        val now = System.currentTimeMillis()
        val fresh = measured.producerError == null
        if (fresh) {
            val next = measured.upcomingSlots.filter { it.timeMs > now }.distinct().sortedBy { it.timeMs }.take(10)
            slots = (slots.filter { it.timeMs <= now && it.timeMs + ProducerPowerPolicy.AFTER_SLOT_MS >= now } + next).distinct()
            if (next != savedSlots) {
                val out = schedule.startWrite()
                try {
                    val data = JSONObject().put("updated_ms", now).put("slots", JSONArray(next.map {
                        JSONObject().put("slot", it.slot).put("time_ms", it.timeMs)
                    }))
                    out.write(data.toString().toByteArray(Charsets.UTF_8)); schedule.finishWrite(out)
                    savedSlots = next
                } catch (error: Exception) { schedule.failWrite(out); throw error }
            }
            lastSchedule = now
        }
        if (status.phase.substringBefore(" · ") != "Synced" || status.ageSeconds == null || status.ageSeconds > 1800 || !fresh) {
            syncedSince = null
            return
        }
        if (syncedSince == null) syncedSince = now
        // Recent unfinished attempts also keep the node awake, beyond a slot's fixed grace.
        if (measured.blocks.any { it.slotTimeMs != null && it.slotTimeMs <= now &&
                now - it.slotTimeMs < 15 * 60_000L && it.status !in setOf("Canonical", "Orphaned", "Discarded", "Missed", "Committed") }) return
        val lead = maxOf(settings.wakeSeconds(), AppearancePreferences(context).productionAlertLeadSeconds())
        val freshSchedule = now >= lastSchedule && now - lastSchedule < 10_000
        // Manual control may end the automatic 2-minute awake hold early, but
        // never bypasses stable sync, the production window or alarm safeguards.
        val manualWake = ProducerPowerPolicy.wakeAt(now, slots, lead, awakeSince, syncedSince, freshSchedule, manual = true)
        val allowed = alarms.canScheduleExactAlarms()
        suspensionAvailable(ProducerPowerPolicy.canSuspendManually(settings.enabled(),
            NodeState.flow.value.run == RunPhase.RUNNING, allowed, manualWake))
        if (!allowed) {
            if (manualWake != null) NodeState.message(uiText(R.string.power_alarm_required))
            return
        }
        val wakeAt = ProducerPowerPolicy.wakeAt(now, slots, lead, awakeSince, syncedSince, freshSchedule, manual = manual) ?: return
        val gate = lock.withLock {
            if (!settings.enabled() || NodeState.flow.value.run != RunPhase.RUNNING) return@withLock null
            val processId = pid()?.takeIf { it > 1 } ?: return@withLock null
            // Install the wake-up BEFORE stopping the group or releasing the CPU.
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeAt, alarm)
            val waiting = CompletableDeferred<Unit>()
            sleeper = waiting
            try {
                Os.kill(-processId, OsConstants.SIGSTOP)
                // Confirm the main process is stopped before displaying SUSPENDED.
                var confirmed = false
                repeat(50) {
                    if (!confirmed) {
                        confirmed = File("/proc/$processId/status").readLines().any { it.startsWith("State:") && it.substringAfter(':').trim().startsWith("T") }
                        if (!confirmed) delay(10)
                    }
                }
                check(confirmed)
                files.append("[app] Node suspended (${if (manual) "manual" else "automatic"}); wake scheduled at ${java.time.Instant.ofEpochMilli(wakeAt)}")
                NodeState.flow.update { if (it.run == RunPhase.RUNNING) it.copy(run = RunPhase.SUSPENDED, wakeLock = false,
                    message = uiText(R.string.power_waiting), powerWakeAtMs = wakeAt, powerCanSuspend = false,
                    status = it.status.copy(peers = 0)) else it }
                runCatching { changed() }
                if (NodeState.flow.value.run == RunPhase.SUSPENDED) releaseCpu() else waiting.complete(Unit)
                waiting
            } catch (error: Exception) {
                // Never leave the node stopped if arming/confirmation fails.
                runCatching { Os.kill(-processId, OsConstants.SIGCONT) }
                holdCpu()
                alarms.cancel(alarm); sleeper = null
                NodeState.flow.update { if (it.run == RunPhase.SUSPENDED) it.copy(run = RunPhase.RUNNING,
                    wakeLock = true, powerWakeAtMs = null, powerCanSuspend = false) else it }
                throw error
            }
        } ?: return
        try { gate.await() } finally {
            withContext(NonCancellable) { lock.withLock {
                holdCpu()
                pid()?.takeIf { it > 1 }?.let { runCatching { Os.kill(-it, OsConstants.SIGCONT) }.getOrThrow() }
                alarms.cancel(alarm); sleeper = null
                awakeSince = System.currentTimeMillis(); syncedSince = null
                files.append("[app] Node resumed; refreshing synchronization and production schedule")
                NodeState.flow.update { if (it.run == RunPhase.SUSPENDED) it.copy(run = RunPhase.RUNNING,
                    wakeLock = true, message = uiText(R.string.power_resuming), powerWakeAtMs = null, powerCanSuspend = false) else it }
                runCatching { changed() }
            } }
        }
    }

    fun close() { alarms.cancel(alarm); wake() }
}
