package org.minanode.android

import org.junit.Assert.*
import org.junit.Test

class ProductionAlertPolicyTest {
    @Test fun alertsOnlyForFutureSlotsWithinOneMinute() {
        val now = 1790000000000L
        val slots = listOf(ProductionSlot(1, now - 1), ProductionSlot(2, now), ProductionSlot(3, now + 1),
            ProductionSlot(4, now + 60000), ProductionSlot(5, now + 60001))
        assertEquals(listOf(3L, 4L), ProductionAlertPolicy.due(slots, "mainnet", now, emptySet()).map { it.slot })
    }
    @Test fun handlesOverlappingSlotsAndDeduplicatesAcrossPollsAndRestarts() {
        val now = 1790000000000L
        val first = ProductionSlot(1, now + 30000)
        val second = ProductionSlot(2, now + 50000)
        val due = ProductionAlertPolicy.due(listOf(second, first, first), "mainnet", now, emptySet())
        assertEquals(listOf(first, second), due)
        val restoredSent = due.map { it.key("mainnet") }.toSet()
        assertTrue(ProductionAlertPolicy.due(due, "mainnet", now + 1000, restoredSent).isEmpty())
        assertEquals(2, ProductionAlertPolicy.due(due, "devnet", now + 1000, restoredSent).size)
    }
    @Test fun appliesSecondsAndChangedLeadWithoutRepeatingSentAlerts() {
        val now = 1790000000000L
        val slots = listOf(ProductionSlot(1, now + 20000), ProductionSlot(2, now + 120000), ProductionSlot(3, now + 120001))
        assertEquals(listOf(1L), ProductionAlertPolicy.due(slots, "mainnet", now, emptySet(), 20).map { it.slot })
        assertTrue(ProductionAlertPolicy.due(slots, "mainnet", now, emptySet(), 19).isEmpty())
        val sent = setOf(slots.first().key("mainnet"))
        assertEquals(listOf(2L), ProductionAlertPolicy.due(slots, "mainnet", now, sent, 120).map { it.slot })
        assertEquals(slots, ProductionAlertPolicy.due(slots, "mainnet", now, emptySet(), 3600))
    }
    @Test fun rejectsInvalidLeadTimes() {
        for (seconds in listOf(-1, 0, 3601, Int.MAX_VALUE)) {
            assertTrue(runCatching { ProductionAlertPolicy.due(emptyList(), "mainnet", 0, emptySet(), seconds) }.isFailure)
        }
    }
    @Test fun parsesEveryAnnouncedSlotAndIgnoresOldOrUnscheduledAttempts() {
        val text = """{"attempts":[
          {"status":"Scheduled","won_slot":{"global_slot":1,"slot_time":130000000000}},
          {"status":"Scheduled","won_slot":{"global_slot":2,"slot_time":150000000000}},
          {"status":"Discarded","won_slot":{"global_slot":3,"slot_time":140000000000}},
          {"status":"Scheduled","won_slot":{"global_slot":4,"slot_time":90000000000}}
        ]}"""
        assertEquals(listOf(ProductionSlot(1, 130000), ProductionSlot(2, 150000)), ProductionParser.upcomingSlots(text, 100000))
    }
}
