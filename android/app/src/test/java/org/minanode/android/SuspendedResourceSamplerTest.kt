package org.minanode.android

import org.junit.Assert.*
import org.junit.Test

class SuspendedResourceSamplerTest {
    private fun stat(ticks: Long = 100, start: Long = 10): String {
        val fields = MutableList(20) { "0" }
        fields[0] = "T"; fields[11] = ticks.toString(); fields[12] = "0"; fields[19] = start.toString()
        return "42 (mina (worker) node) " + fields.joinToString(" ")
    }
    private fun sample(sampler: SuspendedResourceSampler, nanos: Long, ticks: Long = 100, start: Long = 10,
        rss: String = "VmRSS:\t204800 kB", pid: Int = 42): ResourceSample =
        sampler.sample(pid, nanos, nanos / 1000000, 100) { path -> if (path.endsWith("/stat")) stat(ticks, start) else rss }

    @Test fun firstReadingDoesNotInventCpuAndReadsRealResidentMemory() {
        val reading = sample(SuspendedResourceSampler(), 1000000000)
        assertNull(reading.cpu)
        assertEquals(200.0, reading.ramMiB!!, 0.001)
    }
    @Test fun stoppedProcessProducesMeasuredZeroCpuAndChangingRam() {
        val sampler = SuspendedResourceSampler()
        sample(sampler, 1000000000)
        val reading = sample(sampler, 3000000000, rss = "Name: mina\nVmRSS: 102400 kB\n")
        assertEquals(0.0, reading.cpu!!, 0.001)
        assertEquals(100.0, reading.ramMiB!!, 0.001)
    }
    @Test fun reportsActualTickChangesWithoutForcingZero() {
        val sampler = SuspendedResourceSampler()
        sample(sampler, 1000000000)
        assertEquals(50.0, sample(sampler, 3000000000, ticks = 200).cpu!!, 0.001)
    }
    @Test fun reusedPidOrNewPidCannotUseOldCpuBaseline() {
        val sampler = SuspendedResourceSampler()
        sample(sampler, 1000000000)
        assertNull(sample(sampler, 3000000000, start = 20).cpu)
        assertNull(sample(sampler, 5000000000, start = 20, pid = 43).cpu)
    }
    @Test fun counterOrClockRollbackDoesNotInventNegativeCpu() {
        val sampler = SuspendedResourceSampler()
        sample(sampler, 3000000000)
        assertNull(sample(sampler, 5000000000, ticks = 50).cpu)
        assertNull(sample(sampler, 4000000000, ticks = 50).cpu)
    }
    @Test fun inaccessibleStatClearsCpuBaselineButKeepsIndependentRamMeasurement() {
        val sampler = SuspendedResourceSampler()
        sample(sampler, 1000000000)
        val missing = sampler.sample(42, 3000000000, 3000, 100) {
            if (it.endsWith("/stat")) throw SecurityException() else "VmRSS: 102400 kB"
        }
        assertNull(missing.cpu)
        assertEquals(100.0, missing.ramMiB!!, 0.001)
        assertNull(sample(sampler, 5000000000).cpu)
    }
    @Test fun inaccessibleOrInvalidRssIsMissingNotZero() {
        val sampler = SuspendedResourceSampler()
        for (rss in listOf("", "VmRSS: NaN kB", "VmRSS: -1 kB", "VmRSS: 123 bytes")) {
            assertNull(sample(sampler, 1000000000, rss = rss).ramMiB)
        }
        val value = sampler.sample(42, 3000000000, 3000, 100) {
            if (it.endsWith("/stat")) stat() else throw SecurityException()
        }
        assertEquals(0.0, value.cpu!!, 0.001)
        assertNull(value.ramMiB)
    }
    @Test fun resetStartsFreshBaselineWhenMonitorIsReopened() {
        val sampler = SuspendedResourceSampler()
        sample(sampler, 1000000000)
        sampler.reset()
        assertNull(sample(sampler, 3000000000).cpu)
    }
    @Test fun invalidPidNeverReadsOtherProcessPaths() {
        val reading = SuspendedResourceSampler().sample(0, 1000000000, 1000, 100) { error("Must not read") }
        assertNull(reading.cpu); assertNull(reading.ramMiB)
    }
    @Test fun graphHistoryRetainsNewReadingsWithExistingTimeAndPointLimits() {
        val sample = ResourceSample(7 * 3600000L, 0.0, 200.0)
        val values = SuspendedResourceSampler.append(listOf(ResourceSample(0, 10.0, 100.0)), sample)
        assertEquals(listOf(sample), values)
        val full = (0..22000).map { ResourceSample(sample.timeMs - 22000 + it, 0.0, 200.0) }
        val bounded = SuspendedResourceSampler.append(full, sample)
        assertEquals(21600, bounded.size)
        assertEquals(sample, bounded.last())
    }
}
