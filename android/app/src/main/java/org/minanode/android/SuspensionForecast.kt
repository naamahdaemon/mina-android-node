package org.minanode.android

enum class SuspensionReason { OFF, SUSPENDED, SYNC, SCHEDULE, ALARM, SLOT, PRODUCTION, READY, COUNTDOWN }

data class SuspensionForecast(val reason: SuspensionReason = SuspensionReason.OFF, val atMs: Long? = null) {
    fun remainingSeconds(now: Long): Long? = atMs?.let { ((it - now).coerceAtLeast(0) + 999) / 1000 }
}

/** Predict only known time gates, then validate against the real suspension policy. */
object SuspensionForecastPolicy {
    fun estimate(now: Long, slots: List<ProductionSlot>, leadSeconds: Int, awakeSince: Long,
        syncedSince: Long?, freshSchedule: Boolean, alarmsAllowed: Boolean, blocks: List<ProducedBlock>): SuspensionForecast {
        if (!alarmsAllowed) return SuspensionForecast(SuspensionReason.ALARM)
        if (!freshSchedule || slots.none { it.timeMs > now }) return SuspensionForecast(SuspensionReason.SCHEDULE)
        if (syncedSince == null) return SuspensionForecast(SuspensionReason.SYNC)
        // No countdown to completion of a proof whose finish is still unknown.
        if (blocks.any { !ProducerPowerPolicy.discardedBeforeProof(it) && ProducerPowerPolicy.unfinished(it) &&
                ProducerPowerPolicy.latestEvent(it)?.let { time -> now - time in 0..(15 * 60_000L) } == true }) {
            return SuspensionForecast(SuspensionReason.PRODUCTION)
        }
        if (slots.any { it.timeMs > now && now >= it.timeMs - leadSeconds * 1000L }) {
            return SuspensionForecast(SuspensionReason.SLOT)
        }
        var at = maxOf(now, awakeSince + ProducerPowerPolicy.MIN_AWAKE_MS, syncedSince + ProducerPowerPolicy.STABLE_MS)
        blocks.filterNot(ProducerPowerPolicy::discardedBeforeProof).forEach { block ->
            ProducerPowerPolicy.latestEvent(block)?.let { at = maxOf(at, it + ProducerPowerPolicy.AFTER_SLOT_MS + 1) }
        }
        slots.filter { it.timeMs <= now }.forEach { at = maxOf(at, it.timeMs + ProducerPowerPolicy.AFTER_SLOT_MS + 1) }
        // Do not promise a countdown when its end falls too close to the next wake-up.
        if (ProducerPowerPolicy.wakeAt(at, slots, leadSeconds, awakeSince, syncedSince, true, blocks = blocks) == null) {
            return SuspensionForecast(SuspensionReason.SLOT)
        }
        return if (at > now) SuspensionForecast(SuspensionReason.COUNTDOWN, at)
            else SuspensionForecast(SuspensionReason.READY)
    }
}
