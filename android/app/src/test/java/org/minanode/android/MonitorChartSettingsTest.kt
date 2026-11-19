package org.minanode.android

import org.junit.Assert.*
import org.junit.Test

class MonitorChartSettingsTest {
    @Test fun roundTripKeepsAutoscaleZoomAndAbsolutePanWithEpochPrecision() {
        val value = MonitorChartSettings(false, ChartWindow(60000, 12345, 1790000012345))
        assertEquals(value, MonitorChartSettings.decode(value.encode()))
    }
    @Test fun liveResetRemainsLiveAfterRestoreWithoutChangingScaleChoice() {
        val value = MonitorChartSettings(false, ChartWindow())
        val restored = MonitorChartSettings.decode(value.encode())
        assertFalse(restored.autoscale)
        assertNull(restored.window.endTimeMs)
        assertEquals(10000L..30000L, restored.window.bounds(10000, 30000))
    }
    @Test fun missingCorruptAndUnknownVersionUseSafeDefaults() {
        for (text in listOf(null, "broken", "{}", "{\"version\":2}")) {
            assertEquals(MonitorChartSettings(), MonitorChartSettings.decode(text))
        }
    }
    @Test fun invalidWindowValuesAreBoundedAndDoNotBreakRendering() {
        val restored = MonitorChartSettings.decode("""{"version":1,"autoscale":false,"span_ms":999999999,"offset_ms":-1,"end_ms":-1}""")
        assertEquals(21600000L, restored.window.spanMs)
        assertEquals(0L, restored.window.offsetMs)
        assertNull(restored.window.endTimeMs)
        assertEquals(10000L..30000L, restored.window.bounds(10000, 30000))
    }
    @Test fun expiredRecordingRecentersWithoutLosingSavedZoomOrAutoscale() {
        val saved = MonitorChartSettings(false, ChartWindow(5000, 0, 100000))
        val restored = MonitorChartSettings.decode(saved.encode())
        assertFalse(restored.autoscale)
        assertEquals(205000L..210000L, restored.window.bounds(200000, 210000))
        assertEquals(5000L, restored.window.spanMs)
    }
    @Test fun savedHistoricalPanDoesNotMoveWhenNewPointsArrive() {
        val saved = MonitorChartSettings(true, ChartWindow(60000, 10000, 1790000100000))
        val restored = MonitorChartSettings.decode(saved.encode())
        val first = 1790000000000L
        assertEquals((first + 40000)..(first + 100000), restored.window.bounds(first, first + 120000))
        assertEquals(restored.window.bounds(first, first + 120000), restored.window.bounds(first, first + 180000))
    }
}
