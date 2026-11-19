package org.minanode.android

/** Pure scheduling policy. Unknown schedules and close production windows stay awake. */
object ProducerPowerPolicy {
    const val REFRESH_MS = 30 * 60_000L
    const val MIN_AWAKE_MS = 120_000L
    const val STABLE_MS = 30_000L
    const val AFTER_SLOT_MS = 300_000L
    const val MIN_SLEEP_MS = 15 * 60_000L

    fun canSuspendManually(enabled: Boolean, running: Boolean, alarmsAllowed: Boolean, safeWakeAt: Long?): Boolean =
        enabled && running && alarmsAllowed && safeWakeAt != null

    private fun discardedBeforeProof(block: ProducedBlock): Boolean = block.status == "Discarded" &&
        block.discardedMs != null && block.proofStartMs == null && block.proofEndMs == null && block.committedMs == null

    fun retainedSlots(previous: List<ProductionSlot>, announced: List<ProductionSlot>, now: Long, leadSeconds: Int,
        blocks: List<ProducedBlock> = emptyList()): List<ProductionSlot> {
        val abandoned = blocks.filter(::discardedBeforeProof).map { it.slot }.toSet()
        val protected = previous.filter { it.slot !in abandoned && it.timeMs + AFTER_SLOT_MS >= now && it.timeMs <= now + leadSeconds * 1000L }
        val next = announced.filter { it.timeMs > now }.distinct().sortedBy { it.timeMs }.take(10)
        return (protected + next).distinct().sortedBy { it.timeMs }
    }

    fun productionProtected(now: Long, blocks: List<ProducedBlock>): Boolean = blocks.any { block ->
        if (discardedBeforeProof(block)) return@any false
        val latest = listOfNotNull(block.slotTimeMs, block.producedMs, block.proofStartMs, block.proofEndMs,
            block.committedMs, block.discardedMs).maxOrNull()
        if (latest == null) false
        else now <= latest + AFTER_SLOT_MS ||
            (block.status !in setOf("Canonical", "Orphaned", "Discarded", "Missed", "Committed") && now - latest in 0..(15 * 60_000L))
    }

    fun wakeAt(now: Long, slots: List<ProductionSlot>, leadSeconds: Int,
        awakeSince: Long, syncedSince: Long?, freshSchedule: Boolean, manual: Boolean = false, blocks: List<ProducedBlock> = emptyList()): Long? {
        require(leadSeconds in 60..7200)
        if (!freshSchedule || syncedSince == null || (!manual && now < awakeSince + MIN_AWAKE_MS) ||
            now < syncedSince + STABLE_MS) return null
        if (productionProtected(now, blocks)) return null
        if (slots.any { now >= it.timeMs - leadSeconds * 1000L && now <= it.timeMs + AFTER_SLOT_MS }) return null
        val next = slots.filter { it.timeMs > now }.minOfOrNull { it.timeMs } ?: return null
        val wake = minOf(next - leadSeconds * 1000L, now + REFRESH_MS)
        return wake.takeIf { it > now + MIN_SLEEP_MS }
    }
}
