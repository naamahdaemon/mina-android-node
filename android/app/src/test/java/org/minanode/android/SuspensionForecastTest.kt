package org.minanode.android

import org.junit.Assert.*
import org.junit.Test

class SuspensionForecastTest {
    private val now = 1790000000000L
    private val future = listOf(ProductionSlot(8, now + 7200000))
    private fun block() = ProducedBlock("", 1, 7, "Committed", 0, 0, null, null, null,
        now - 30000, now - 20000, now - 10000, slotTimeMs = now - 60000, committedMs = now)
    private fun estimate(slots: List<ProductionSlot> = future, awake: Long = now - 180000,
        synced: Long? = now - 60000, fresh: Boolean = true, allowed: Boolean = true,
        blocks: List<ProducedBlock> = emptyList()) =
        SuspensionForecastPolicy.estimate(now, slots, 600, awake, synced, fresh, allowed, blocks)

    @Test fun countdownUsesLatestOfAwakeAndStableSyncGates() {
        val value = estimate(awake = now - 60000, synced = now - 10000)
        assertEquals(SuspensionReason.COUNTDOWN, value.reason)
        assertEquals(now + 60000, value.atMs)
        assertEquals(60L, value.remainingSeconds(now))
    }
    @Test fun postProductionCountdownEndsOnlyWhenTheRealPolicyAllowsSuspension() {
        val blocks = listOf(block())
        val value = estimate(blocks = blocks)
        assertEquals(SuspensionReason.COUNTDOWN, value.reason)
        val at = requireNotNull(value.atMs)
        assertEquals(now + 300001, at)
        assertNull(ProducerPowerPolicy.wakeAt(at - 1, future, 600, now - 180000, now - 60000, true, blocks = blocks))
        assertNotNull(ProducerPowerPolicy.wakeAt(at, future, 600, now - 180000, now - 60000, true, blocks = blocks))
    }
    @Test fun nextSlotInNineteenMinutesShowsReasonNotAFakeCountdown() {
        val value = estimate(slots = listOf(ProductionSlot(8, now + 19 * 60000)))
        assertEquals(SuspensionReason.SLOT, value.reason)
        assertNull(value.atMs)
    }
    @Test fun insufficientSleepAfterPostProductionWaitDoesNotPromiseSuspension() {
        val value = estimate(slots = listOf(ProductionSlot(8, now + 28 * 60000)), blocks = listOf(block()))
        assertEquals(SuspensionReason.SLOT, value.reason)
        assertNull(value.atMs)
    }
    @Test fun unknownWorkCompletionHasNoArtificialDeadline() {
        val pending = block().copy(status = "ProofCreatePending", proofEndMs = null, committedMs = null)
        val value = estimate(blocks = listOf(pending))
        assertEquals(SuspensionReason.PRODUCTION, value.reason)
        assertNull(value.atMs)
    }
    @Test fun alarmSyncAndScheduleBlockersRemainDistinct() {
        assertEquals(SuspensionReason.ALARM, estimate(allowed = false).reason)
        assertEquals(SuspensionReason.SYNC, estimate(synced = null).reason)
        assertEquals(SuspensionReason.SCHEDULE, estimate(fresh = false).reason)
        assertEquals(SuspensionReason.SCHEDULE, estimate(slots = emptyList()).reason)
    }
    @Test fun confirmedDiscardBeforeProofDoesNotShowAWait() {
        val discarded = block().copy(status = "Discarded", proofStartMs = null,
            proofEndMs = null, committedMs = null, discardedMs = now)
        assertEquals(SuspensionReason.READY, estimate(blocks = listOf(discarded)).reason)
    }
    @Test fun readyHasNoDeadlineAndExpiredCounterDoesNotBecomeNegative() {
        assertEquals(SuspensionReason.READY, estimate().reason)
        assertNull(estimate().remainingSeconds(now))
        assertEquals(0L, SuspensionForecast(SuspensionReason.COUNTDOWN, now - 1).remainingSeconds(now))
        assertEquals(1L, SuspensionForecast(SuspensionReason.COUNTDOWN, now + 1).remainingSeconds(now))
    }
    @Test fun compactDurationsStayShortAcrossLongUptimes() {
        assertEquals("0:00", DisplayTime.compactDuration(0))
        assertEquals("4:32", DisplayTime.compactDuration(272))
        assertEquals("15:40", DisplayTime.compactDuration(940))
        assertEquals("1h01", DisplayTime.compactDuration(3660))
        assertEquals("2d03h", DisplayTime.compactDuration(183600))
    }
}
