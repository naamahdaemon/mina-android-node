package org.minanode.android

enum class PowerWakeReason { SYNC, BLOCK }

/** Pure scheduling policy. Unknown schedules and close production windows stay awake. */
object ProducerPowerPolicy {
    const val DEFAULT_SYNC_SECONDS = 600

    fun canSuspendManually(enabled: Boolean, running: Boolean, alarmsAllowed: Boolean, safeWakeAt: Long?): Boolean =
        enabled && running && alarmsAllowed && safeWakeAt != null

    internal fun discardedBeforeProof(block: ProducedBlock): Boolean = block.status == "Discarded" &&
        block.discardedMs != null && block.proofStartMs == null && block.proofEndMs == null && block.committedMs == null

    fun retainedSlots(previous: List<ProductionSlot>, announced: List<ProductionSlot>, now: Long, leadSeconds: Int,
        blocks: List<ProducedBlock> = emptyList(), timings: PowerTimings = PowerTimings()): List<ProductionSlot> {
        val abandoned = blocks.filter(::discardedBeforeProof).map { it.slot }.toSet()
        val protected = previous.filter { it.slot !in abandoned && it.timeMs + timings.ms(PowerTiming.GRACE) >= now && it.timeMs <= now + leadSeconds * 1000L }
        val next = announced.filter { it.timeMs > now }.distinct().sortedBy { it.timeMs }.take(10)
        return (protected + next).distinct().sortedBy { it.timeMs }
    }

    internal fun latestEvent(block: ProducedBlock): Long? = listOfNotNull(block.slotTimeMs, block.producedMs,
        block.proofStartMs, block.proofEndMs, block.committedMs, block.discardedMs).maxOrNull()

    internal fun unfinished(block: ProducedBlock): Boolean = block.status !in
        setOf("Canonical", "Orphaned", "Discarded", "Missed", "Committed")

    fun productionProtected(now: Long, blocks: List<ProducedBlock>, timings: PowerTimings = PowerTimings()): Boolean = blocks.any { block ->
        if (discardedBeforeProof(block)) return@any false
        val latest = latestEvent(block)
        if (latest == null) false
        else now <= latest + timings.ms(PowerTiming.GRACE) || (unfinished(block) && now - latest in 0..timings.ms(PowerTiming.UNFINISHED))
    }

    fun wakeReason(now: Long, slots: List<ProductionSlot>, leadSeconds: Int, syncSeconds: Int): PowerWakeReason {
        val productionWake = slots.filter { it.timeMs > now }.minOfOrNull { it.timeMs - leadSeconds * 1000L }
        return if (productionWake != null && productionWake <= now + syncSeconds * 1000L)
            PowerWakeReason.BLOCK else PowerWakeReason.SYNC
    }

    fun wakeAt(now: Long, slots: List<ProductionSlot>, leadSeconds: Int,
        awakeSince: Long, syncedSince: Long?, freshSchedule: Boolean, manual: Boolean = false, blocks: List<ProducedBlock> = emptyList(), syncSeconds: Int = DEFAULT_SYNC_SECONDS, timings: PowerTimings = PowerTimings()): Long? {
        require(leadSeconds in 60..7200)
        require(syncSeconds in 60..7200)
        if (!freshSchedule || syncedSince == null || (!manual && now < awakeSince + timings.ms(PowerTiming.AWAKE)) ||
            now < syncedSince + timings.ms(PowerTiming.STABLE)) return null
        if (productionProtected(now, blocks, timings)) return null
        if (slots.any { now >= it.timeMs - leadSeconds * 1000L && now <= it.timeMs + timings.ms(PowerTiming.GRACE) }) return null
        val next = slots.filter { it.timeMs > now }.minOfOrNull { it.timeMs } ?: return null
        val productionWake = next - leadSeconds * 1000L
        // Avoid short gaps between production windows; periodic sync is independent.
        if (productionWake <= now + timings.ms(PowerTiming.GAP)) return null
        return minOf(productionWake, now + syncSeconds * 1000L)
    }
}
