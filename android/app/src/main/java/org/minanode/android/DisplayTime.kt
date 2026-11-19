package org.minanode.android

import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** UI-only clock: no node requests, background polling, alarms or wake locks. */
@Composable
fun rememberDisplayTime(enabled: Boolean): Long {
    val owner = LocalLifecycleOwner.current
    val clock = remember(enabled, owner) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(enabled, owner) {
        if (enabled) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { clock.longValue = System.currentTimeMillis(); delay(1000) }
        }
    }
    return clock.longValue
}

@Composable
fun BlockAgeUptimeText(state: NodeUiState) {
    val suspended = state.run == RunPhase.SUSPENDED
    val forecast = state.suspensionForecast
    val now = rememberDisplayTime(suspended || (state.run == RunPhase.RUNNING && forecast.atMs != null))
    val age = if (suspended) state.status.ageAt(now) else state.status.ageSeconds
    val checking = stringResource(R.string.suspend_line_check)
    val pause = when {
        suspended -> DisplayTime.until(state.powerWakeAtMs, now)?.let {
            if (it > 0) DisplayTime.compactDuration(it) else checking
        } ?: "\u2014"
        state.run != RunPhase.RUNNING -> "\u2014"
        forecast.reason == SuspensionReason.COUNTDOWN -> forecast.remainingSeconds(now)?.takeIf { it > 0 }
            ?.let { DisplayTime.compactDuration(it) } ?: stringResource(R.string.suspend_line_check)
        else -> stringResource(when (forecast.reason) {
            SuspensionReason.OFF -> R.string.suspend_line_off
            SuspensionReason.SYNC -> R.string.suspend_line_sync
            SuspensionReason.SCHEDULE -> R.string.suspend_line_plan
            SuspensionReason.ALARM -> R.string.suspend_line_alarm
            SuspensionReason.SLOT -> R.string.suspend_line_slot
            SuspensionReason.PRODUCTION -> R.string.suspend_line_production
            SuspensionReason.READY -> R.string.suspend_line_check
            else -> R.string.suspend_line_check
        })
    }
    val ageText = age?.let { if (it < 60) "${it}s" else DisplayTime.compactDuration(it) } ?: "\u2014"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimingCell(stringResource(R.string.timing_age), ageText, Modifier.weight(1f))
        TimingCell(stringResource(R.string.timing_uptime),
            DisplayTime.compactDuration(state.status.uptimeSeconds), Modifier.weight(1f))
        TimingCell(stringResource(if (suspended) R.string.timing_wake else R.string.timing_sleep),
            pause, Modifier.weight(1f))
    }
}

/** Equal-width cells and fixed-size monospace values never resize as time ticks. */
@Composable
private fun TimingCell(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, modifier = Modifier.fillMaxWidth(), fontFamily = FontFamily.Monospace,
            fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 1,
            softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun NextProductionSlotText(state: MonitorState, running: Boolean) {
    val now = rememberDisplayTime(running)
    val next = DisplayTime.nextSlot(state, now)
    Text(if (next == null) stringResource(R.string.no_next_slot)
        else stringResource(R.string.next_slot, next.slot, DisplayTime.remainingSeconds(next, now)))
}

object DisplayTime {
    fun until(deadlineMs: Long?, nowMs: Long): Long? = deadlineMs?.let { ((it - nowMs).coerceAtLeast(0) + 999) / 1000 }

    fun compactDuration(seconds: Long): String {
        val value = seconds.coerceAtLeast(0)
        return when {
            value < 3600 -> String.format(Locale.ROOT, "%d:%02d", value / 60, value % 60)
            value < 86400 -> String.format(Locale.ROOT, "%dh%02d", value / 3600, value / 60 % 60)
            else -> String.format(Locale.ROOT, "%dd%02dh", value / 86400, value / 3600 % 24)
        }
    }

    fun nextSlot(state: MonitorState, nowMs: Long): ProductionSlot? {
        val fallback = state.nextSlot?.let { slot -> state.nextSlotTimeMs?.let { ProductionSlot(slot, it) } }
        return (state.upcomingSlots + listOfNotNull(fallback))
            .filter { it.slot >= 0 && it.timeMs > nowMs }.minByOrNull { it.timeMs }
    }
    fun remainingSeconds(slot: ProductionSlot, nowMs: Long): Long =
        ((slot.timeMs - nowMs) / 1000).coerceAtLeast(0)
}
