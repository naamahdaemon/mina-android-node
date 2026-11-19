package org.minanode.android

/** Separate CPU baseline: suspended reads must never change the running sampler. */
class SuspendedResourceSampler {
    private data class Cpu(val pid: Int, val start: Long, val ticks: Long, val nanos: Long)
    private var previous: Cpu? = null

    fun sample(pid: Int, nowNs: Long, nowMs: Long, ticksPerSecond: Long, read: (String) -> String): ResourceSample {
        val current = runCatching {
            require(pid > 1)
            val raw = read("/proc/$pid/stat")
            require(raw.lastIndexOf(')') >= 0)
            val fields = raw.substring(raw.lastIndexOf(')') + 2).trim().split(Regex("\\s+"))
            val start = fields[19].toLong()
            val user = fields[11].toLong(); val system = fields[12].toLong()
            require(start >= 0 && user >= 0 && system >= 0)
            Cpu(pid, start, Math.addExact(user, system), nowNs)
        }.getOrNull()
        val old = previous
        val cpu = if (current != null && old != null && current.pid == old.pid && current.start == old.start &&
            current.ticks >= old.ticks && current.nanos > old.nanos && ticksPerSecond > 0) {
            100.0 * (current.ticks - old.ticks) / ticksPerSecond / ((current.nanos - old.nanos) / 1e9)
        } else null
        previous = current
        val ram = runCatching {
            require(pid > 1)
            val line = read("/proc/$pid/status").lineSequence().firstOrNull { it.startsWith("VmRSS:") }
            val fields = line?.trim()?.split(Regex("\\s+"))
            fields?.getOrNull(1)?.toDoubleOrNull()?.takeIf {
                it.isFinite() && it >= 0 && fields.getOrNull(2) == "kB"
            }?.div(1024)
        }.getOrNull()
        return ResourceSample(nowMs, cpu, ram)
    }

    fun reset() { previous = null }

    companion object {
        fun append(samples: List<ResourceSample>, sample: ResourceSample): List<ResourceSample> =
            (samples + sample).filter { it.timeMs >= sample.timeMs - 6 * 3600_000L }.takeLast(21600)
    }
}
