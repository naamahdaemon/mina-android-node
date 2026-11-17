package org.minanode.android

import org.junit.Assert.*
import org.junit.Test

class MonitorInteractionTest {
    @Test fun paginationLimitsEveryPageToFiveAndClampsAfterHistoryShrinks() {
        val blocks = (1..13).toList()
        assertEquals(3, BlockPages.count(blocks.size))
        assertEquals(listOf(1, 2, 3, 4, 5), BlockPages.items(blocks, 0))
        assertEquals(listOf(6, 7, 8, 9, 10), BlockPages.items(blocks, 1))
        assertEquals(listOf(11, 12, 13), BlockPages.items(blocks, 2))
        assertEquals(listOf(1, 2), BlockPages.items(listOf(1, 2), 9))
        assertTrue(BlockPages.items(emptyList<Int>(), 0).isEmpty())
    }
    @Test fun zoomKeepsAnchorAndPanStaysInsideHistory() {
        val initial = ChartWindow()
        assertEquals(10000L..110000L, initial.bounds(10000, 110000))
        val zoom = initial.transform(10000, 110000, 2f, 0f, 0.5f)
        assertEquals(35000L..85000L, zoom.bounds(10000, 110000))
        assertEquals(35000L..85000L, zoom.bounds(10000, 120000))
        assertEquals(10000L..60000L, zoom.transform(10000, 110000, 1f, 100f, 0.5f).bounds(10000, 110000))
        assertEquals(60000L..110000L, zoom.transform(10000, 110000, 1f, -100f, 0.5f).bounds(10000, 110000))
        assertEquals(60000L..120000L, ChartWindow(60000).bounds(10000, 120000))
    }
    @Test fun zoomPreservesMillisecondPrecisionAtRealEpochDates() {
        val first = 1790000000000L
        val zoom = ChartWindow().transform(first, first + 100000, 2f, 0f, 0.5f)
        assertEquals((first + 25000)..(first + 75000), zoom.bounds(first, first + 100000))
        assertEquals((first + 25000)..(first + 75000), zoom.bounds(first, first + 110000))
        assertEquals(first..first, ChartWindow().bounds(first, first))
    }
    @Test fun zoomFitsShortRecordingsAndClampsWhenHistoryExpires() {
        assertEquals(1000L..2000L, ChartWindow(60000, 999999).bounds(1000, 2000))
        val range = ChartWindow(60000, 999999).bounds(100000, 200000)
        assertEquals(100000L..160000L, range)
    }
    @Test fun explorerUsesHashAndNetworkAndRejectsUnsafeTemplates() {
        val hash = "3N" + "a".repeat(50)
        assertEquals("https://minascan.io/mainnet/block/$hash/txs", ExplorerLink.url(ExplorerLink.DEFAULT, "mainnet", hash))
        assertEquals("https://example.org/devnet/$hash", ExplorerLink.url("https://example.org/{network}/{hash}", "devnet", hash))
        for (template in listOf("http://example.org/{hash}", "javascript:{hash}", "https://example.org/blocks", "https://user:password@example.org/{hash}")) assertFalse(ExplorerLink.valid(template))
        assertNull(ExplorerLink.url(ExplorerLink.DEFAULT, "mainnet", ""))
        assertNull(ExplorerLink.url(ExplorerLink.DEFAULT, "unknown", hash))
        assertNull(ExplorerLink.url(ExplorerLink.DEFAULT, "mainnet", "../../etc"))
    }
    @Test fun retainsDiscardedAttemptsWithoutHashAndDoesNotDoubleCountZkapps() {
        val text = """{"attempts":[{"status":"Discarded","discard_reason":"BestTipSuperior","won_slot":{"global_slot":43,"slot_time":43000000000},"times":{"proof_create_start":44000000000,"discarded":45000000000},"block":null}]}"""
        val block = ProductionParser.parse(text).first.single()
        assertEquals("", block.hash)
        assertEquals("BestTipSuperior", block.discardReason)
        assertEquals(45000L, block.discardedMs)
        assertNull(block.proofSeconds)
        assertNull(block.height)
    }
    @Test fun canonicalLookupRequiresExactHeightAndValidStateHash() {
        val hash = "3N" + "a".repeat(50)
        val reply = """{"headers":[{"height":42,"hash":"$hash"}]}"""
        assertEquals(hash, NodeApi.parseBestChainHash(reply, 42))
        assertNull(NodeApi.parseBestChainHash(reply, 43))
        assertNull(NodeApi.parseBestChainHash("""{"headers":[]}""", 42))
        assertNull(NodeApi.parseBestChainHash(reply.replace(hash, "invalid"), 42))
    }
    @Test fun startupZoomCannotFreezeAnIntervalWithoutMeasurements() {
        val first = 1790000000000L
        val samples = listOf(ResourceSample(first, null, null), ResourceSample(first + 1000, null, null),
            ResourceSample(first + 10000, 6.6, 1262.3))
        val view = ResourceViewport.select(samples, ChartWindow())
        assertEquals(listOf(samples.last()), view.samples)
        val zoom = ChartWindow().transform(view.recording.first, view.recording.last, 2f, 0f, 0.5f)
        assertEquals(ChartWindow(), zoom)
        val later = samples + ResourceSample(first + 20000, 10.0, 1300.0)
        assertEquals(later.last(), ResourceViewport.select(later, zoom).samples.last())
    }
    @Test fun restoringAnExpiredViewportKeepsZoomButReturnsToMeasuredData() {
        val first = 1790000000000L
        val samples = (0..10).map { ResourceSample(first + it * 1000, 6.6, 1262.3) }
        val oldWindow = ChartWindow(5000, 100000, first - 60000)
        val view = ResourceViewport.select(samples, oldWindow)
        assertEquals((first + 5000)..(first + 10000), view.bounds)
        assertEquals(samples.last(), view.samples.last())
        assertEquals(6, view.samples.size)
        val future = ResourceViewport.select(samples, oldWindow.copy(endTimeMs = first + 60000))
        assertEquals(view.bounds, future.bounds)
    }
    @Test fun measuredViewportPreservesAValidHistoricalZoomAndRealGaps() {
        val samples = (0..20).map { ResourceSample(it * 1000L, if (it in 7..13) null else 5.0, if (it in 7..13) null else 1000.0) }
        val window = ChartWindow(5000, 0, 13000)
        val view = ResourceViewport.select(samples, window)
        assertEquals(8000L..13000L, view.bounds)
        assertEquals(6, view.samples.size)
        assertTrue(view.samples.all { it.cpu == null && it.ramMiB == null })
        assertEquals(view.bounds, ResourceViewport.select(samples + ResourceSample(21000, 5.0, 1000.0), window).bounds)
    }
    @Test fun navigationOrderIsStable() {
        assertEquals(listOf("NODE", "MONITOR", "DASHBOARD", "SETTINGS", "JOURNAL"), NodePage.entries.map { it.name })
    }
}
