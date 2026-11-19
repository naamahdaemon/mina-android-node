package org.minanode.android

import org.junit.Assert.*
import org.junit.Test

class DisplayTimeTest {
    @Test fun blockAgeAdvancesWithoutMutatingFrozenMeasurementsOrUptime() {
        val status = ChainStatus(ageSeconds = 54, uptimeSeconds = 34, bestTipTimestampMs = 100000)
        assertEquals(54L, status.ageAt(154000))
        assertEquals(114L, status.ageAt(214000))
        assertEquals(54L, status.ageSeconds)
        assertEquals(34L, status.uptimeSeconds)
    }
    @Test fun missingBlockTimeDoesNotInventAnAge() {
        assertNull(ChainStatus().ageAt(214000))
        assertEquals(12L, ChainStatus(ageSeconds = 12).ageAt(214000))
    }
    @Test fun blockAgeDoesNotBecomeNegativeIfClockMovesBack() {
        assertEquals(0L, ChainStatus(bestTipTimestampMs = 100000).ageAt(99000))
    }
    @Test fun countdownAdvancesWhileMonitorSnapshotIsUnchanged() {
        val state = MonitorState(nextSlot = 7, nextSlotTimeMs = 200000)
        val next = requireNotNull(DisplayTime.nextSlot(state, 100000))
        assertEquals(100L, DisplayTime.remainingSeconds(next, 100000))
        assertEquals(40L, DisplayTime.remainingSeconds(next, 160000))
        assertEquals(200000L, state.nextSlotTimeMs)
    }
    @Test fun expiredSlotAdvancesToNextKnownSlot() {
        val state = MonitorState(nextSlot = 7, nextSlotTimeMs = 100000,
            upcomingSlots = listOf(ProductionSlot(9, 300000), ProductionSlot(8, 200000), ProductionSlot(7, 100000)))
        assertEquals(ProductionSlot(8, 200000), DisplayTime.nextSlot(state, 100000))
        assertEquals(ProductionSlot(9, 300000), DisplayTime.nextSlot(state, 200000))
        assertNull(DisplayTime.nextSlot(state, 300000))
    }
    @Test fun unknownOrExpiredSchedulesDoNotShowAFrozenZeroCountdown() {
        assertNull(DisplayTime.nextSlot(MonitorState(), 100000))
        assertNull(DisplayTime.nextSlot(MonitorState(nextSlot = 7, nextSlotTimeMs = 99000), 100000))
        assertNull(DisplayTime.nextSlot(MonitorState(upcomingSlots = listOf(ProductionSlot(-1, 200000))), 100000))
    }
    @Test fun fallbackAndCachedSlotsUseEarliestFutureTime() {
        val state = MonitorState(nextSlot = 7, nextSlotTimeMs = 200000,
            upcomingSlots = listOf(ProductionSlot(8, 300000)))
        assertEquals(ProductionSlot(7, 200000), DisplayTime.nextSlot(state, 100000))
        assertEquals(0L, DisplayTime.remainingSeconds(ProductionSlot(7, 200000), 201000))
    }
}
