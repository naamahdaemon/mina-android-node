package org.minanode.android

import org.junit.Assert.*
import org.junit.Test

class ChainStatusTest {
    private val reply = """{
      "network":"devnet","uptime_secs":34,
      "transition_frontier":{"sync":{"status":"Synced","phase":"Synced"},
        "best_tip":{"height":590477,"timestamp_ms":100000}},
      "peers":[{"connection_status":"Connected"},{"connection_status":"Disconnected"}],
      "frontier_store":{"open":true,"locked":false,"unavailable":null,"saved_chain":{"state":"saved","height":590477}}
    }"""

    @Test fun readsStatusAndCountsOnlyConnectedPeers() {
        val parsed = ChainStatus.parse(reply, nowMs = 154000)
        assertEquals(590477L, parsed.height)
        assertEquals(54L, parsed.ageSeconds)
        assertEquals(1, parsed.peers)
        assertEquals("Chaîne sauvegardée · hauteur 590477", parsed.persistence)
    }

    @Test fun bootstrapAllowsMissingTip() {
        val parsed = ChainStatus.parse(reply.replace("{\"height\":590477,\"timestamp_ms\":100000}", "null"))
        assertNull(parsed.height)
        assertNull(parsed.ageSeconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesAnotherNetwork() { ChainStatus.parse(reply.replace("devnet", "mainnet")) }

    @Test fun reportsMissingCheckpoint() {
        val input = reply.replace("\"state\":\"saved\",\"height\":590477", "\"state\":\"not_saved\"")
        assertEquals("Aucune sauvegarde pour l’instant", ChainStatus.parse(input).persistence)
    }

    @Test fun reportsStoreFailureInsteadOfEnabled() {
        val input = reply.replace("\"unavailable\":null", "\"unavailable\":\"I/O error\"")
        assertEquals("Indisponible : I/O error", ChainStatus.parse(input).persistence)
    }

    @Test fun reportsLockContention() {
        val input = reply.replace("\"locked\":false", "\"locked\":true")
        assertEquals("Verrouillée par une autre instance", ChainStatus.parse(input).persistence)
    }
}
