package org.minanode.android

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import org.json.JSONObject
import java.io.File

data class ResourceSample(val timeMs: Long, val cpu: Double?, val ramMiB: Double?)
data class ProducedBlock(val hash: String, val height: Long?, val slot: Long, val status: String,
    val tx: Int, val zkapps: Int, val prepMs: Double?, val proofSeconds: Double?, val delaySeconds: Double?,
    val producedMs: Long?, val proofStartMs: Long?, val proofEndMs: Long?,
    val discardReason: String? = null, val snarks: Int? = null, val slotTimeMs: Long? = null,
    val committedMs: Long? = null, val discardedMs: Long? = null) {
    val id: String get() = "slot:$slot"
}
data class MonitorState(
    val samples: List<ResourceSample> = emptyList(), val blocks: List<ProducedBlock> = emptyList(),
    val nextSlot: Long? = null, val nextSlotTimeMs: Long? = null,
    val temperature: Double? = null, val battery: Int? = null, val charging: Boolean = false,
    val thermal: Int = 0, val freeGiB: Double? = null, val usedMiB: Double? = null,
    val producerError: UiText? = null,
    val cpuCapacity: Double = Runtime.getRuntime().availableProcessors() * 100.0,
    val ramCapacityMiB: Double? = null,
    val productionSamples: Map<String, List<ResourceSample>> = emptyMap(),
    val resourceArchiveFailed: Boolean = false,
)

object ProductionParser {
    fun parse(text: String): Triple<List<ProducedBlock>, Long?, Long?> {
        val attempts = JSONObject(text).optJSONArray("attempts") ?: return Triple(emptyList(), null, null)
        val blocks = mutableListOf<ProducedBlock>()
        val upcoming = mutableListOf<Pair<Long, Long>>()
        for (i in 0 until attempts.length()) {
            val attempt = attempts.optJSONObject(i) ?: continue
            val slot = attempt.optJSONObject("won_slot") ?: continue
            val times = attempt.optJSONObject("times") ?: JSONObject()
            val status = attempt.optString("status")
            val slotMs = slot.optLong("slot_time", 0) / 1_000_000
            if (status == "Scheduled" && slotMs > System.currentTimeMillis()) upcoming += slotMs to slot.optLong("global_slot")
            val block = attempt.optJSONObject("block") ?: JSONObject()
            val hash = block.optString("hash")
            // Future schedules belong in the header, not in the produced-attempt list.
            if (status == "Scheduled") continue
            val tx = block.optJSONObject("transactions") ?: JSONObject()
            fun timestamp(name: String): Long? = times.optLong(name, -1).takeIf { it >= 0 }?.div(1_000_000)
            fun duration(start: String, end: String, divisor: Double): Double? {
                val a = times.optLong(start, -1); val b = times.optLong(end, -1)
                return if (a >= 0 && b >= a) (b - a) / divisor else null
            }
            blocks += ProducedBlock(hash, block.optLong("height", -1).takeIf { it >= 0 }, slot.optLong("global_slot"), status,
                tx.optInt("payments") + tx.optInt("delegations") + tx.optInt("zkapps"), tx.optInt("zkapps"),
                duration("staged_ledger_diff_create_start", "staged_ledger_diff_create_end", 1e6),
                duration("proof_create_start", "proof_create_end", 1e9),
                duration("proof_create_end", "committed", 1e9), timestamp("produced"),
                timestamp("proof_create_start"), timestamp("proof_create_end"),
                attempt.optString("discard_reason").takeIf { it.isNotBlank() && it != "null" },
                block.optInt("completed_works_count", -1).takeIf { it >= 0 }, slotMs.takeIf { it > 0 },
                timestamp("committed"), timestamp("discarded"))
        }
        val next = upcoming.minByOrNull { it.first }
        return Triple(blocks.distinctBy { it.id }.sortedByDescending { it.slot }, next?.second, next?.first)
    }
}

/** Measurements are missing, rather than zero, when /proc access is denied. */
class ProductionMonitor(private val context: Context, private val files: NodeFiles) {
    private var lastTicks: Long? = null
    private var lastNs = 0L
    private var identity: Long? = null
    private var used: Double? = null
    private var lastDisk = 0L
    private var state = MonitorState()
    private var lastResponse: String? = null
    private val history = File(files.work, "production-history.json")
    private val resources = ProductionResourceArchive(File(files.work, "production-resources"))
    init {
        runCatching {
            if (history.isFile && history.length() <= 4 * 1024 * 1024) {
                val json = JSONObject(history.readText())
                state = state.copy(blocks = ProductionParser.parse(json.toString()).first, productionSamples = resources.snapshot())
            }
        }
    }

    fun sample(pid: Int?, producer: Boolean): MonitorState {
        val now = SystemClock.elapsedRealtimeNanos()
        var cpu: Double? = null; var ram: Double? = null
        if (pid != null) runCatching {
            val raw = File("/proc/$pid/stat").readText()
            val fields = raw.substring(raw.lastIndexOf(')') + 2).trim().split(Regex("\\s+"))
            val start = fields[19].toLong()
            val ticks = fields[11].toLong() + fields[12].toLong()
            if (identity == start && lastTicks != null && now > lastNs) {
                cpu = (100.0 * (ticks - lastTicks!!) / Os.sysconf(OsConstants._SC_CLK_TCK) / ((now - lastNs) / 1e9)).coerceAtLeast(0.0)
            }
            identity = start; lastTicks = ticks; lastNs = now
            val line = File("/proc/$pid/status").readLines().firstOrNull { it.startsWith("VmRSS:") }
            ram = line?.split(Regex("\\s+"))?.getOrNull(1)?.toDoubleOrNull()?.div(1024)
        }
        val clock = System.currentTimeMillis()
        val samples = (state.samples + ResourceSample(clock, cpu, ram)).filter { it.timeMs >= clock - 6 * 3600_000L }.takeLast(21600)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        var blocks = state.blocks; var nextSlot: Long? = null; var nextTime: Long? = null; var error: UiText? = null
        if (producer) {
            runCatching { NodeApi.get("stats/block_producer") }.onSuccess { text ->
                runCatching { ProductionParser.parse(text) }.onSuccess { parsed ->
                    blocks = (parsed.first + blocks).distinctBy { it.id }.sortedByDescending { it.slot }.take(500)
                    nextSlot = parsed.second; nextTime = parsed.third
                    // Retain raw public production events, never keys or credentials.
                    if (text != lastResponse) runCatching {
                        val current = JSONObject(text).optJSONArray("attempts") ?: org.json.JSONArray()
                        val old = if (history.isFile) JSONObject(history.readText()).optJSONArray("attempts") else null
                        val merged = linkedMapOf<String, JSONObject>()
                        for (array in listOfNotNull(current, old)) for (i in 0 until array.length()) {
                            val item = array.optJSONObject(i) ?: continue
                            val slot = item.optJSONObject("won_slot")?.optLong("global_slot", -1)?.takeIf { it >= 0 } ?: continue
                            if (item.optString("status") != "Scheduled") merged.putIfAbsent("slot:$slot", item)
                        }
                        val target = android.util.AtomicFile(history); val out = target.startWrite()
                        try { out.write(JSONObject().put("attempts", org.json.JSONArray(merged.values.sortedByDescending { it.optJSONObject("won_slot")?.optLong("global_slot") ?: -1 }.take(500))).toString().toByteArray()); target.finishWrite(out) }
                        catch (failure: Exception) { target.failWrite(out); throw failure }
                        lastResponse = text
                    }
                }.onFailure { error = uiText(R.string.error_production_reply) }
            }.onFailure { error = uiText(R.string.error_production_unavailable) }
        }
        if (now - lastDisk > 60_000_000_000L) {
            used = runCatching { files.work.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1048576.0 }.getOrNull()
            lastDisk = now
        }
        state = MonitorState(samples, blocks, nextSlot, nextTime,
            battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.takeIf { it >= 0 }?.div(10.0),
            battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 },
            (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0,
            runCatching { StatFs(files.work.absolutePath).availableBytes / 1073741824.0 }.getOrNull(), used, error, ramCapacityMiB = runCatching {
                val info = android.app.ActivityManager.MemoryInfo()
                context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
                info.totalMem / 1048576.0
            }.getOrNull(), productionSamples = if (producer) resources.capture(blocks, samples, clock) else resources.snapshot(),
            resourceArchiveFailed = resources.writeFailed)
        return state
    }
}
