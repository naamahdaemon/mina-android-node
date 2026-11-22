package org.minanode.android

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SnarkWorkerStartupTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun disabledProfilePreventsSavedDashboardSettingFromRestartingWorker() {
        val work = temporary.newFolder("devnet")
        val saved = File(work, "snark-worker.json").apply { writeText("saved worker") }
        val history = File(work, "snark-worker-included.json").apply { writeText("proof history") }
        val key = File(work, "producer-key.json").apply { writeText("encrypted key") }
        val chain = File(work, "frontier").apply { mkdir() }
        assertTrue(SnarkWorkerStartup.prepare(work, enabled = false))
        assertFalse(saved.exists())
        assertEquals("proof history", history.readText())
        assertEquals("encrypted key", key.readText())
        assertTrue(chain.isDirectory)
        assertFalse(SnarkWorkerStartup.prepare(work, enabled = false))
    }

    @Test fun enabledProfileKeepsDashboardSettingAndUsesExplicitWorkerArguments() {
        val work = temporary.newFolder()
        val saved = File(work, "snark-worker.json").apply { writeText("saved worker") }
        assertFalse(SnarkWorkerStartup.prepare(work, enabled = true))
        assertEquals("saved worker", saved.readText())
        val key = "B62" + "a".repeat(52)
        val args = NodeProfile(snarker = true, snarkerPublicKey = key).arguments(work, null)
        assertEquals(key, args[args.indexOf("--run-snarker") + 1])
        assertFalse(NodeProfile(snarker = false).arguments(work, null).contains("--run-snarker"))
    }

    @Test fun disablingOneNetworkLeavesTheOtherNetworkUntouched() {
        val devnet = temporary.newFolder("devnet")
        val mainnet = temporary.newFolder("mainnet")
        File(devnet, "snark-worker.json").writeText("devnet")
        val saved = File(mainnet, "snark-worker.json").apply { writeText("mainnet") }
        SnarkWorkerStartup.prepare(devnet, enabled = false)
        assertEquals("mainnet", saved.readText())
    }

    @Test fun refusesToStartIfSavedSettingCannotBeRemovedSafely() {
        val work = temporary.newFolder()
        val directory = File(work, "snark-worker.json").apply { mkdir() }
        val unrelated = File(directory, "keep").apply { writeText("keep") }
        assertTrue(runCatching { SnarkWorkerStartup.prepare(work, enabled = false) }.isFailure)
        assertEquals("keep", unrelated.readText())
    }
}
