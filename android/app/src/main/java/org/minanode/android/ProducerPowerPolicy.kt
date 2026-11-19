package org.minanode.android

/** Pure scheduling policy. Unknown schedules and close production windows stay awake. */
object ProducerPowerPolicy {
    const val REFRESH_MS = 30 * 60_000L
    const val MIN_AWAKE_MS = 120_000L
    const val STABLE_MS = 30_000L
    const val AFTER_SLOT_MS = 300_000L
    const val MIN_SLEEP_MS = 15 * 60_000L

    fun wakeAt(now: Long, slots: List<ProductionSlot>, leadSeconds: Int,
        awakeSince: Long, syncedSince: Long?, freshSchedule: Boolean): Long? {
        require(leadSeconds in 60..7200)
        if (!freshSchedule || syncedSince == null || now < awakeSince + MIN_AWAKE_MS ||
            now < syncedSince + STABLE_MS) return null
        if (slots.any { now >= it.timeMs - leadSeconds * 1000L && now <= it.timeMs + AFTER_SLOT_MS }) return null
        val next = slots.filter { it.timeMs > now }.minOfOrNull { it.timeMs } ?: return null
        val wake = minOf(next - leadSeconds * 1000L, now + REFRESH_MS)
        return wake.takeIf { it > now + MIN_SLEEP_MS }
    }
}
