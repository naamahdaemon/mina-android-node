package org.minanode.android

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ProductionResourceArchiveTest {
    private fun block(slot: Long = 1) = ProducedBlock("3Nexample", 42, slot, "Canonical", 1, 0,
        40.0, 15.0, 1.0, 5000, 5000, 20000, committedMs = 21000)
    private val samples = (0..30).map { ResourceSample(it * 1000L, if (it == 0) null else it.toDouble(), 1200.0 + it) }

    @Test fun restoresProductionSamplesAfterRestartWithoutRecentHistory() {
        val dir = Files.createTempDirectory("production-resources").toFile()
        try {
            val first = ProductionResourceArchive(dir).capture(listOf(block()), samples, 30000)
            assertEquals(27, first.getValue("slot:1").size)
            assertNull(first.getValue("slot:1").first().cpu)
            val restored = ProductionResourceArchive(dir)
            assertEquals(first, restored.snapshot())
            assertEquals(first, restored.capture(listOf(block()), emptyList(), 60000))
            assertEquals(26.0, restored.snapshot().getValue("slot:1").last().cpu!!, 0.001)
        } finally { dir.deleteRecursively() }
    }
    @Test fun keepsNewestAttemptsAndHonorsByteLimit() {
        val dir = Files.createTempDirectory("production-resources").toFile()
        try {
            val archive = ProductionResourceArchive(dir, maxBlocks = 2)
            archive.capture(listOf(block(1), block(3), block(2)), samples, 30000)
            assertEquals(setOf("slot:2", "slot:3"), ProductionResourceArchive(dir).snapshot().keys)
            val limit = dir.listFiles()!!.filter { it.extension == "json" }.maxOf { it.length() }
            val bounded = ProductionResourceArchive(dir, maxBytes = limit)
            assertEquals(setOf("slot:3"), bounded.snapshot().keys)
            assertTrue(dir.listFiles()!!.sumOf { it.length() } <= limit)
        } finally { dir.deleteRecursively() }
    }
    @Test fun ignoresCorruptAndMismatchedFilesWithoutLosingOtherProductions() {
        val dir = Files.createTempDirectory("production-resources").toFile()
        try {
            ProductionResourceArchive(dir).capture(listOf(block()), samples, 30000)
            java.io.File(dir, "slot-2.json").writeText("broken")
            java.io.File(dir, "slot-3.json").writeText("""{"version":1,"slot":4,"samples":[]}""")
            assertEquals(setOf("slot:1"), ProductionResourceArchive(dir).snapshot().keys)
        } finally { dir.deleteRecursively() }
    }
}
