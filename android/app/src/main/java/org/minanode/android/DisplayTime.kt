package org.minanode.android

import androidx.compose.runtime.*
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

object DisplayTime {
    fun nextSlot(state: MonitorState, nowMs: Long): ProductionSlot? {
        val fallback = state.nextSlot?.let { slot -> state.nextSlotTimeMs?.let { ProductionSlot(slot, it) } }
        return (state.upcomingSlots + listOfNotNull(fallback))
            .filter { it.slot >= 0 && it.timeMs > nowMs }.minByOrNull { it.timeMs }
    }
    fun remainingSeconds(slot: ProductionSlot, nowMs: Long): Long =
        ((slot.timeMs - nowMs) / 1000).coerceAtLeast(0)
}
