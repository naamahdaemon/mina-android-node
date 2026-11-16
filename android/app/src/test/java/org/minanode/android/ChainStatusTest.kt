package org.minanode.android

import org.junit.Assert.*
import org.junit.Test

class ChainStatusTest {
    private val reply = """{
      "network":"devnet","uptime_secs":34,
      "transition_frontier":{"sync":{"status":"Synced","phase":"Synced"},
        "best_tip":{"height":590477,"timestamp_ms":100000}},
      "peers":[{"connection_status":"Connected"},{"connection_status":"Disconnected"}],
      "frontier_store":{}
    }"""

    @Test fun readsStatusAndCountsOnlyConnectedPeers() {
        val parsed = ChainStatus.parse(reply, nowMs = 154000)
        assertEquals(590477L, parsed.height)
        assertEquals(54L, parsed.ageSeconds)
        assertEquals(1, parsed.peers)
        assertEquals("Activée", parsed.persistence)
    }

    @Test fun bootstrapAllowsMissingTip() {
        val parsed = ChainStatus.parse(reply.replace("{\"height\":590477,\"timestamp_ms\":100000}", "null"))
        assertNull(parsed.height)
        assertNull(parsed.ageSeconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesAnotherNetwork() { ChainStatus.parse(reply.replace("devnet", "mainnet")) }
}
