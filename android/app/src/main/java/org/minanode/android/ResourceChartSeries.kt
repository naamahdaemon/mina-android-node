package org.minanode.android

data class ResourceChartPoint(val timeMs: Long, val value: Double, val segment: Int)

/** Split real recording gaps BEFORE reducing points for the screen. */
object ResourceChartSeries {
    const val MAX_SAMPLE_GAP_MS = 15_000L // normal collection is every 1 or 5 seconds

    fun segments(samples: List<ResourceSample>, bounds: LongRange, buckets: Int = 240,
        valueOf: (ResourceSample) -> Double?): List<List<ResourceChartPoint>> {
        require(buckets > 0)
        val points = mutableListOf<ResourceChartPoint>()
        var segment = 0
        var previous: Long? = null
        samples.filter { it.timeMs in bounds }.sortedBy { it.timeMs }.forEach { sample ->
            val value = valueOf(sample)?.takeIf { it.isFinite() && it >= 0 }
            if (value == null) {
                segment++; previous = null
            } else {
                previous?.let { if (sample.timeMs <= it || sample.timeMs - it > MAX_SAMPLE_GAP_MS) segment++ }
                points += ResourceChartPoint(sample.timeMs, value, segment)
                previous = sample.timeMs
            }
        }
        // First/min/max/last retain valleys, peaks and endpoints in timestamp order.
        // Segment identities survive reduction, including gaps hidden inside a bucket.
        fun envelope(bucket: List<ResourceChartPoint>) =
            listOf(bucket.first(), bucket.minBy { it.value }, bucket.maxBy { it.value }, bucket.last())
        val byBucket = points.groupBy {
            (ResourceChartScale.timeFraction(it.timeMs, bounds.first, bounds.last) * buckets)
                .toInt().coerceIn(0, buckets - 1)
        }.values.flatMap(::envelope)
        // Never truncate a segment just because a gap falls inside one screen bucket.
        val reduced = (byBucket + points.groupBy { it.segment }.values.flatMap(::envelope))
            .distinct().sortedWith(compareBy<ResourceChartPoint> { it.timeMs }.thenBy { it.segment })
        val result = mutableListOf<MutableList<ResourceChartPoint>>()
        reduced.forEach { point ->
            if (result.lastOrNull()?.lastOrNull()?.segment != point.segment) result.add(mutableListOf())
            result.last().add(point)
        }
        return result
    }
}

object ChartGesture {
    /** Vertical finger separation and near-zero horizontal spans must not jump the time scale. */
    fun horizontalZoom(previous: List<Float>, current: List<Float>, minimumSpan: Float): Float {
        if (!minimumSpan.isFinite() || previous.size < 2 || previous.size != current.size ||
            previous.any { !it.isFinite() } || current.any { !it.isFinite() }) return 1f
        val before = previous.max() - previous.min()
        val after = current.max() - current.min()
        if (before < minimumSpan.coerceAtLeast(1f) || after < minimumSpan.coerceAtLeast(1f)) return 1f
        val ratio = after / before
        return if (ratio.isFinite()) ratio.coerceIn(0.5f, 2f) else 1f
    }
}
