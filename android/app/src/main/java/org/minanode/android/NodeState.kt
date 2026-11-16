package org.minanode.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

enum class RunPhase { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

data class ChainStatus(
    val phase: String = "En attente de l’API",
    val height: Long? = null,
    val ageSeconds: Long? = null,
    val peers: Int = 0,
    val uptimeSeconds: Long = 0,
    val persistence: String = "En attente",
) {
    companion object {
        fun parse(text: String, nowMs: Long = System.currentTimeMillis()): ChainStatus {
            val root = JSONObject(text)
            require(root.getString("network") == "devnet") { "L’API ne répond pas sur devnet" }
            val frontier = root.getJSONObject("transition_frontier")
            val sync = frontier.getJSONObject("sync")
            val tip = frontier.optJSONObject("best_tip")
            val timestamp = tip?.optLong("timestamp_ms", -1) ?: -1
            val peers = root.getJSONArray("peers")
            return ChainStatus(
                phase = "${sync.getString("status")} · ${sync.optString("phase")}",
                height = tip?.optLong("height", -1)?.takeIf { it >= 0 },
                ageSeconds = timestamp.takeIf { it >= 0 }?.let { ((nowMs - it) / 1000).coerceAtLeast(0) },
                peers = (0 until peers.length()).count {
                    peers.getJSONObject(it).optString("connection_status") == "Connected"
                },
                uptimeSeconds = root.optLong("uptime_secs"),
                persistence = if (root.optJSONObject("frontier_store") == null) "Désactivée" else "Activée",
            )
        }
    }
}

data class NodeUiState(
    val run: RunPhase = RunPhase.STOPPED,
    val status: ChainStatus = ChainStatus(),
    val message: String = "Prêt à démarrer sur devnet",
    val log: String = "",
    val vpn: Boolean = false,
    val wakeLock: Boolean = false,
)

object NodeState {
    val flow = MutableStateFlow(NodeUiState())
    fun message(text: String) = flow.update { it.copy(message = text) }
}
