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
        fun parse(text: String, nowMs: Long = System.currentTimeMillis(), expectedNetwork: String = "devnet"): ChainStatus {
            val root = JSONObject(text)
            require(root.getString("network") == expectedNetwork) { "L’API ne répond pas sur le réseau attendu" }
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
                persistence = persistenceStatus(root.optJSONObject("frontier_store")),
            )
        }

        private fun persistenceStatus(store: JSONObject?): String {
            if (store == null) return "Désactivée"
            if (store.optBoolean("locked")) return "Verrouillée par une autre instance"
            val unavailable = store.optString("unavailable").takeUnless { it.isBlank() || it == "null" }
            if (unavailable != null) return "Indisponible : $unavailable"
            if (!store.optBoolean("open")) return "Ouverture en cours"
            val saved = store.optJSONObject("saved_chain") ?: return "État de sauvegarde en attente"
            return when (saved.optString("state")) {
                "checking" -> "Vérification de la sauvegarde"
                "not_saved" -> "Aucune sauvegarde pour l’instant"
                "saved" -> "Chaîne sauvegardée · hauteur ${saved.optLong("height")}"
                "not_restorable" -> "Sauvegarde non restaurable : ${saved.optString("reason")}"
                "unknown" -> "État inconnu : ${saved.optString("reason")}"
                else -> "État de sauvegarde inconnu"
            }
        }
    }
}

data class NodeUiState(
    val network: String = "devnet",
    val run: RunPhase = RunPhase.STOPPED,
    val status: ChainStatus = ChainStatus(),
    val message: String = "Prêt à démarrer sur devnet",
    val log: String = "",
    val vpn: Boolean = false,
    val wakeLock: Boolean = false,
    val monitor: MonitorState = MonitorState(),
)

object NodeState {
    val flow = MutableStateFlow(NodeUiState())
    fun message(text: String) = flow.update { it.copy(message = text) }
}
