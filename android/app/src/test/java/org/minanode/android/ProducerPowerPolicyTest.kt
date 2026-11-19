package org.minanode.android

import org.junit.Assert.*
import org.junit.Test

class ProducerPowerPolicyTest {
    private val now = 1790000000000L
    private fun wake(slots: List<ProductionSlot>, lead: Int = 600, awake: Long = now - 180000,
        synced: Long? = now - 60000, fresh: Boolean = true) =
        ProducerPowerPolicy.wakeAt(now, slots, lead, awake, synced, fresh)

    @Test fun sleepsUntilIndependentProductionLead() {
        assertEquals(now + 1200000, wake(listOf(ProductionSlot(1, now + 1800000))))
    }
    @Test fun periodicallyRefreshesDistantSchedules() {
        assertEquals(now + ProducerPowerPolicy.REFRESH_MS, wake(listOf(ProductionSlot(1, now + 7200000))))
    }
    @Test fun unknownOrStaleScheduleNeverSleeps() {
        assertNull(wake(emptyList()))
        assertNull(wake(listOf(ProductionSlot(1, now + 1200000)), fresh = false))
        assertNull(wake(listOf(ProductionSlot(1, now - 3600000))))
    }
    @Test fun bootstrappingAndRecentWakeNeverSleep() {
        val slots = listOf(ProductionSlot(1, now + 1200000))
        assertNull(wake(slots, synced = null))
        assertNull(wake(slots, synced = now - 29000))
        assertNull(wake(slots, awake = now - 119999))
    }
    @Test fun includesProductionWindowBoundaryAndPostSlotGrace() {
        for (offset in listOf(600000L, 1L, 0L, -1L, -300000L)) {
            assertNull(wake(listOf(ProductionSlot(1, now + offset), ProductionSlot(2, now + 3600000))))
        }
    }
    @Test fun nearbySlotsKeepNodeAwakeWithoutMicroSleeps() {
        assertNull(wake(listOf(ProductionSlot(1, now + 1500000))))
        assertEquals(now + 900001, wake(listOf(ProductionSlot(1, now + 1500001))))
    }
    @Test fun scheduleOrderDoesNotChangeEarliestWake() {
        assertEquals(now + 1200000, wake(listOf(ProductionSlot(2, now + 3600000), ProductionSlot(1, now + 1800000))))
    }
    @Test fun wakeLeadIsIndependentAndConfigurable() {
        assertEquals(now + 1140000, wake(listOf(ProductionSlot(1, now + 1200000)), lead = 60))
        assertNull(wake(listOf(ProductionSlot(1, now + 1200000)), lead = 1200))
    }
    @Test fun invalidWakeLeadIsRejected() {
        for (lead in listOf(0, 59, 7201)) assertTrue(runCatching { wake(emptyList(), lead = lead) }.isFailure)
    }
    @Test fun clockRollbackKeepsNodeAwake() {
        assertNull(wake(listOf(ProductionSlot(1, now + 1200000)), awake = now + 1000))
        assertNull(wake(listOf(ProductionSlot(1, now + 1200000)), synced = now + 1000))
    }
}
