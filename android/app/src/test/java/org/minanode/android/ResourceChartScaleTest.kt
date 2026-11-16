package org.minanode.android

import org.junit.Assert.*
import org.junit.Test

class ResourceChartScaleTest {
    @Test fun scaleIncludesMulticorePeaksAndMissingValues() {
        assertEquals(500.0, ResourceChartScale.maximum(listOf(null, 327.9, Double.NaN)), 0.001)
        assertEquals(2000.0, ResourceChartScale.maximum(listOf(1600.0)), 0.001)
        assertEquals(1.0, ResourceChartScale.maximum(listOf(null, 0.0)), 0.001)
    }

    @Test fun timeAxisUsesElapsedTimeRatherThanSampleIndex() {
        assertEquals(0f, ResourceChartScale.timeFraction(1000, 1000, 10000), 0.001f)
        assertEquals(1f / 9, ResourceChartScale.timeFraction(2000, 1000, 10000), 0.001f)
        assertEquals(1f, ResourceChartScale.timeFraction(10000, 1000, 10000), 0.001f)
        assertEquals(0.5f, ResourceChartScale.timeFraction(1000, 1000, 1000), 0.001f)
    }
}
