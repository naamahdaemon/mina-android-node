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
    @Test fun navigationOrderIsStable() {
        assertEquals(listOf("NODE", "MONITOR", "DASHBOARD", "SETTINGS", "JOURNAL"), NodePage.entries.map { it.name })
    }
}
