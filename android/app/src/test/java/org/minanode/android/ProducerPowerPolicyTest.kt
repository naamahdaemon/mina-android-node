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
    @Test fun manualSuspendCanEndAutomaticAwakeHoldOnlyWhenOtherwiseSafe() {
        val slots = listOf(ProductionSlot(1, now + 1800000))
        assertNull(wake(slots, awake = now - 60000))
        assertEquals(now + 1200000, ProducerPowerPolicy.wakeAt(now, slots, 600,
            now - 60000, now - 60000, true, manual = true))
    }
    @Test fun manualSuspendCannotBypassProductionWindowsOrStaleUnknownSchedules() {
        for (offset in listOf(-300000L, -1L, 0L, 600000L, 1500000L)) {
            assertNull(ProducerPowerPolicy.wakeAt(now, listOf(ProductionSlot(1, now + offset),
                ProductionSlot(2, now + 3600000)), 600, now, now - 60000, true, manual = true))
        }
        assertNull(ProducerPowerPolicy.wakeAt(now, emptyList(), 600, now, now - 60000, true, manual = true))
        assertNull(ProducerPowerPolicy.wakeAt(now, listOf(ProductionSlot(1, now + 1800000)), 600,
            now, now - 60000, false, manual = true))
    }
    @Test fun manualSuspendStillRequiresStableSynchronizationAndExplicitPermission() {
        val slots = listOf(ProductionSlot(1, now + 1800000))
        assertNull(ProducerPowerPolicy.wakeAt(now, slots, 600, now, null, true, manual = true))
        assertNull(ProducerPowerPolicy.wakeAt(now, slots, 600, now, now - 29000, true, manual = true))
        assertTrue(ProducerPowerPolicy.canSuspendManually(true, true, true, now + 1200000))
        assertFalse(ProducerPowerPolicy.canSuspendManually(false, true, true, now + 1200000))
        assertFalse(ProducerPowerPolicy.canSuspendManually(true, false, true, now + 1200000))
        assertFalse(ProducerPowerPolicy.canSuspendManually(true, true, false, now + 1200000))
        assertFalse(ProducerPowerPolicy.canSuspendManually(true, true, true, null))
    }
    @Test fun automaticCycleStillWaitsTwoMinutesAfterResume() {
        val slots = listOf(ProductionSlot(1, now + 7200000))
        assertNull(ProducerPowerPolicy.wakeAt(now + 119999, slots, 600, now, now + 1000, true))
        assertNotNull(ProducerPowerPolicy.wakeAt(now + 120000, slots, 600, now, now + 1000, true))
    }

    private fun finished(status: String = "Committed", committed: Long? = now, proofEnd: Long? = now - 1000) =
        ProducedBlock("", 1, 7, status, 0, 0, null, null, null, now - 3000,
            now - 4000, proofEnd, slotTimeMs = now - 1200000, committedMs = committed)

    @Test fun completedProductionKeepsAwakeEvenWhenSlotDisappearedFromSchedule() {
        val future = listOf(ProductionSlot(8, now + 7200000))
        for (manual in listOf(false, true)) {
            assertNull(ProducerPowerPolicy.wakeAt(now, future, 600, now - 180000,
                now - 60000, true, manual = manual, blocks = listOf(finished())))
        }
    }
    @Test fun postProductionGuardLastsFiveMinutesFromActualCompletionNotOldSlot() {
        val blocks = listOf(finished())
        assertTrue(ProducerPowerPolicy.productionProtected(now + 300000, blocks))
        assertFalse(ProducerPowerPolicy.productionProtected(now + 300001, blocks))
    }
    @Test fun proofEndProtectsProductionWhenCommitTimeIsUnavailable() {
        for (status in listOf("Canonical", "Orphaned", "Committed")) {
            assertTrue(ProducerPowerPolicy.productionProtected(now, listOf(finished(status, committed = null, proofEnd = now))))
        }
        val discarded = finished("Discarded", committed = null, proofEnd = now - 600000).copy(discardedMs = now)
        assertTrue(ProducerPowerPolicy.productionProtected(now, listOf(discarded)))
    }
    @Test fun oldProductionDoesNotHoldForeverAndPendingWorkRemainsProtected() {
        assertFalse(ProducerPowerPolicy.productionProtected(now + 600000, listOf(finished())))
        val pending = finished("ProofCreatePending", committed = null, proofEnd = null)
        assertTrue(ProducerPowerPolicy.productionProtected(now + 600000, listOf(pending)))
    }
    @Test fun imminentSlotIsRetainedIfItLeavesScheduledBeforeItsTime() {
        val current = ProductionSlot(7, now + 1000)
        val future = ProductionSlot(8, now + 7200000)
        val retained = ProducerPowerPolicy.retainedSlots(listOf(current, future), listOf(future), now, 600)
        assertTrue(current in retained)
        assertNull(wake(retained))
        assertFalse(current in ProducerPowerPolicy.retainedSlots(retained, listOf(future), now + 301001, 600))
    }
    @Test fun postProductionGuardEventuallyAllowsNormalSuspensionAgain() {
        val next = listOf(ProductionSlot(8, now + 7200000))
        assertNotNull(ProducerPowerPolicy.wakeAt(now + 300001, next, 600, now - 180000,
            now - 60000, true, blocks = listOf(finished())))
    }

    @Test fun confirmedDiscardBeforeProofDoesNotRequirePostProductionWait() {
        val early = finished("Discarded", committed = null, proofEnd = null)
            .copy(slotTimeMs = now + 1000, proofStartMs = null, discardedMs = now)
        val next = ProductionSlot(8, now + 7200000)
        val slots = ProducerPowerPolicy.retainedSlots(listOf(ProductionSlot(7, now + 1000), next),
            listOf(next), now, 600, listOf(early))
        assertEquals(listOf(next), slots)
        assertFalse(ProducerPowerPolicy.productionProtected(now, listOf(early)))
        assertNotNull(ProducerPowerPolicy.wakeAt(now, slots, 600, now - 180000,
            now - 60000, true, blocks = listOf(early)))
    }
    @Test fun discardDuringProofStillRequiresGraceEvenWithoutProofDuration() {
        val during = finished("Discarded", committed = null, proofEnd = null).copy(discardedMs = now)
        assertNull(during.proofSeconds)
        assertTrue(ProducerPowerPolicy.productionProtected(now, listOf(during)))
    }
    @Test fun stillAnnouncedFutureSlotIsProtectedDespiteAnOldDiscardRecord() {
        val slot = ProductionSlot(7, now + 1000)
        val early = finished("Discarded", committed = null, proofEnd = null)
            .copy(slotTimeMs = slot.timeMs, proofStartMs = null, discardedMs = now)
        val slots = ProducerPowerPolicy.retainedSlots(listOf(slot), listOf(slot, ProductionSlot(8, now + 7200000)), now, 600, listOf(early))
        assertTrue(slot in slots)
        assertNull(wake(slots))
    }
    @Test fun incompleteDiscardMetadataCannotBypassGrace() {
        val unknown = finished("Discarded", committed = null, proofEnd = null).copy(proofStartMs = null)
        assertTrue(ProducerPowerPolicy.productionProtected(now, listOf(unknown)))
    }

}
