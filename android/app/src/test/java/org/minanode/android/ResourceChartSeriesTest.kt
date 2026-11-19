package org.minanode.android

import org.junit.Assert.*
import org.junit.Test

class ResourceChartSeriesTest {
    private fun series(samples: List<ResourceSample>, bounds: LongRange = 0L..3601000L, buckets: Int = 240) =
        ResourceChartSeries.segments(samples, bounds, buckets) { it.cpu }

    @Test fun suspensionGapNeverBecomesAStraightBridge() {
        val samples = listOf(ResourceSample(0, 10.0, 100.0), ResourceSample(1000, 20.0, 110.0),
            ResourceSample(3600000, 0.0, 120.0), ResourceSample(3601000, 5.0, 130.0))
        val segments = series(samples)
        assertEquals(2, segments.size)
        assertEquals(listOf(0L, 1000L), segments[0].map { it.timeMs })
        assertEquals(listOf(3600000L, 3601000L), segments[1].map { it.timeMs })
    }
    @Test fun missingValueBreakSurvivesEvenWithinOneReductionBucket() {
        val samples = listOf(ResourceSample(0, 1.0, null), ResourceSample(1000, 20.0, null),
            ResourceSample(2000, null, null), ResourceSample(3000, 30.0, null), ResourceSample(4000, 0.0, null))
        val segments = series(samples, 0L..4000L, buckets = 1)
        assertEquals(2, segments.size)
        assertEquals(listOf(1.0, 20.0), segments[0].map { it.value })
        assertEquals(listOf(30.0, 0.0), segments[1].map { it.value })
    }
    @Test fun reductionPreservesValleysPeaksAndBothEndpointsInTimeOrder() {
        val values = listOf(4.0, 10.0, 0.0, 3.0, 5.0)
        val points = series(values.mapIndexed { i, value -> ResourceSample(i * 1000L, value, null) }, 0L..4000L, 1).single()
        assertEquals(listOf(4.0, 10.0, 0.0, 5.0), points.map { it.value })
        assertEquals(listOf(0L, 1000L, 2000L, 4000L), points.map { it.timeMs })
    }
    @Test fun longContinuousRecordIsReducedWithoutInventingLargeGaps() {
        val samples = (0..20000).map { ResourceSample(it * 1000L, (it % 101).toDouble(), null) }
        val points = series(samples, 0L..20000000L).single()
        assertTrue(points.size <= 4 * 240 + 4)
        assertEquals(0L, points.first().timeMs)
        assertEquals(20000000L, points.last().timeMs)
        assertEquals(100.0, points.maxOf { it.value }, 0.001)
        assertEquals(0.0, points.minOf { it.value }, 0.001)
        assertEquals(points.map { it.timeMs }.sorted(), points.map { it.timeMs })
    }
    @Test fun missingCpuDoesNotCreateAMemoryGap() {
        val samples = listOf(ResourceSample(0, 1.0, 100.0), ResourceSample(1000, null, 110.0), ResourceSample(2000, 2.0, 120.0))
        assertEquals(2, series(samples, 0L..2000L).size)
        assertEquals(1, ResourceChartSeries.segments(samples, 0L..2000L) { it.ramMiB }.size)
    }
    @Test fun emptyInvalidAndSinglePointRecordingsAreSafe() {
        assertTrue(series(emptyList()).isEmpty())
        assertTrue(series(listOf(ResourceSample(0, Double.NaN, null), ResourceSample(1, Double.POSITIVE_INFINITY, null),
            ResourceSample(2, -1.0, null))).isEmpty())
        assertEquals(0.0, series(listOf(ResourceSample(0, 0.0, null)), 0L..0L).single().single().value, 0.001)
    }
    @Test fun visibleTimeBoundsAndTimestampOrderAreRespected() {
        val samples = listOf(ResourceSample(3000, 3.0, null), ResourceSample(0, 0.0, null), ResourceSample(1000, 1.0, null))
        assertEquals(listOf(1000L, 3000L), series(samples, 1000L..3000L).single().map { it.timeMs })
    }
    @Test fun horizontalFingerSeparationControlsTimeZoom() {
        assertEquals(2f, ChartGesture.horizontalZoom(listOf(100f, 200f), listOf(50f, 250f), 8f), 0.001f)
        assertEquals(0.5f, ChartGesture.horizontalZoom(listOf(50f, 250f), listOf(100f, 200f), 8f), 0.001f)
        assertEquals(1f, ChartGesture.horizontalZoom(listOf(100f, 200f), listOf(100f, 200f), 8f), 0.001f)
    }
    @Test fun pointerTransitionsAndNearVerticalPinchesCannotJumpTheScale() {
        assertEquals(1f, ChartGesture.horizontalZoom(listOf(100f), listOf(100f, 200f), 8f), 0f)
        assertEquals(1f, ChartGesture.horizontalZoom(listOf(100f, 101f), listOf(90f, 200f), 8f), 0f)
        assertEquals(1f, ChartGesture.horizontalZoom(listOf(Float.NaN, 200f), listOf(100f, 200f), 8f), 0f)
        assertEquals(1f, ChartGesture.horizontalZoom(emptyList(), emptyList(), 8f), 0f)
    }
    @Test fun zoomingBackOutOfAnEmptyGapRecoversMeasuredData() {
        val samples = listOf(ResourceSample(0, 1.0, 100.0), ResourceSample(1000, 2.0, 100.0),
            ResourceSample(900000, 3.0, 100.0), ResourceSample(901000, 4.0, 100.0))
        var window = ChartWindow(5000, 0, 500000)
        assertTrue(ResourceViewport.select(samples, window).samples.isEmpty())
        repeat(4) { window = window.transform(0, 901000, 0.1f, 0f, 0.5f) }
        assertFalse(ResourceViewport.select(samples, window).samples.isEmpty())
    }
    @Test fun normalSamplingCadenceIsContinuousButLongMissingIntervalsAreNot() {
        assertEquals(1, series(listOf(ResourceSample(0, 1.0, null), ResourceSample(5000, 2.0, null),
            ResourceSample(20000, 3.0, null)), 0L..20000L).size)
        assertEquals(2, series(listOf(ResourceSample(0, 1.0, null), ResourceSample(15001, 2.0, null)), 0L..15001L).size)
    }
    @Test fun abruptFingerSpreadsHaveABoundedPerFrameZoom() {
        assertEquals(2f, ChartGesture.horizontalZoom(listOf(0f, 10f), listOf(-1000f, 1000f), 8f), 0f)
        assertEquals(0.5f, ChartGesture.horizontalZoom(listOf(-1000f, 1000f), listOf(0f, 10f), 8f), 0f)
    }

}
