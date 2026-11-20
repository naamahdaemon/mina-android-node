package org.minanode.android

import org.junit.Assert.*
import org.junit.Test

class PowerTimingsTest {
    private val now = 1790000000000L
    private val slots = listOf(ProductionSlot(8, now + 7200000))
    private fun config(key: PowerTiming, seconds: Int) = PowerTimings(mapOf(key to seconds))
    private fun wake(timings: PowerTimings, awake: Long = now - 180000, synced: Long = now - 60000) =
        ProducerPowerPolicy.wakeAt(now, slots, 600, awake, synced, true, timings = timings)

    @Test fun defaultsPreserveExistingGuards() {
        val defaults = PowerTimings()
        assertEquals(listOf(120, 30, 300, 900, 900, 10, 1800), PowerTiming.entries.map { defaults[it] })
        PowerTiming.entries.forEach { assertEquals(it.defaultSeconds * 1000L, defaults.ms(it)) }
    }
    @Test fun awakeHoldIsConfigurableAndForecastUsesSameDeadline() {
        val timings = config(PowerTiming.AWAKE, 300)
        assertNull(wake(timings))
        val forecast = SuspensionForecastPolicy.estimate(now, slots, 600, now - 180000,
            now - 60000, true, true, emptyList(), timings = timings)
        assertEquals(now + 120000, forecast.atMs)
        assertNotNull(ProducerPowerPolicy.wakeAt(now + 120000, slots, 600,
            now - 180000, now - 60000, true, timings = timings))
    }
    @Test fun stableSyncHoldIsConfigurable() {
        assertNull(wake(config(PowerTiming.STABLE, 90)))
        assertNotNull(wake(config(PowerTiming.STABLE, 60)))
    }
    private fun block() = ProducedBlock("", 1, 7, "Committed", 0, 0, null, null, null,
        now - 600000, now - 600000, now - 600000, slotTimeMs = now - 600000, committedMs = now - 600000)
    @Test fun productionGraceControlsEventsAndRetainedSlots() {
        val timings = config(PowerTiming.GRACE, 900)
        assertFalse(ProducerPowerPolicy.productionProtected(now, listOf(block())))
        assertTrue(ProducerPowerPolicy.productionProtected(now, listOf(block()), timings))
        val past = ProductionSlot(7, now - 600000)
        assertTrue(past in ProducerPowerPolicy.retainedSlots(listOf(past), slots, now, 600, timings = timings))
        assertFalse(past in ProducerPowerPolicy.retainedSlots(listOf(past), slots, now, 600))
    }
    @Test fun unfinishedProtectionUsesConfiguredTimeout() {
        val pending = block().copy(status = "ProofCreatePending", committedMs = null, proofEndMs = null)
        assertTrue(ProducerPowerPolicy.productionProtected(now, listOf(pending)))
        assertFalse(ProducerPowerPolicy.productionProtected(now, listOf(pending), config(PowerTiming.UNFINISHED, 300)))
    }
    @Test fun productionGapCanBeTunedIndependentlyFromSync() {
        val close = listOf(ProductionSlot(8, now + 1200000))
        assertNull(ProducerPowerPolicy.wakeAt(now, close, 600, now - 180000, now - 60000, true))
        assertEquals(now + 60000, ProducerPowerPolicy.wakeAt(now, close, 600,
            now - 180000, now - 60000, true, syncSeconds = 60, timings = config(PowerTiming.GAP, 300)))
    }
    @Test fun scheduleFreshnessRejectsExpiredDataAndClockRollback() {
        val timings = config(PowerTiming.SCHEDULE, 30)
        assertTrue(timings.scheduleFresh(now, now - 29999))
        assertFalse(timings.scheduleFresh(now, now - 30000))
        assertFalse(timings.scheduleFresh(now, now + 1))
    }
    @Test fun tipFreshnessIsConfigurableAndRejectsMissingData() {
        val timings = config(PowerTiming.TIP, 120)
        assertTrue(timings.tipFresh(120))
        assertFalse(timings.tipFresh(121))
        assertFalse(timings.tipFresh(null))
        assertFalse(timings.tipFresh(-1))
    }
    @Test fun boundsValidateEverySetting() {
        PowerTiming.entries.forEach { key ->
            assertTrue(runCatching { config(key, key.range.first - 1) }.isFailure)
            assertTrue(runCatching { config(key, key.range.last + 1) }.isFailure)
            assertEquals(key.range.first, config(key, key.range.first)[key])
            assertEquals(key.range.last, config(key, key.range.last)[key])
        }
    }
}
