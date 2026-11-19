package org.minanode.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

enum class RunPhase { STOPPED, STARTING, RUNNING, SUSPENDED, STOPPING, ERROR }

data class ChainStatus(
    val phase: String = "",
    val height: Long? = null,
    val ageSeconds: Long? = null,
    val peers: Int = 0,
    val uptimeSeconds: Long = 0,
    val persistence: UiText = uiText(R.string.waiting),
) {
    companion object {
        fun parse(text: String, nowMs: Long = System.currentTimeMillis(), expectedNetwork: String = "devnet"): ChainStatus {
            val root = JSONObject(text)
            requireUi(root.getString("network") == expectedNetwork, R.string.error_network_mismatch)
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

        private fun persistenceStatus(store: JSONObject?): UiText {
            if (store == null) return uiText(R.string.persist_disabled)
            if (store.optBoolean("locked")) return uiText(R.string.persist_locked)
            val unavailable = store.optString("unavailable").takeUnless { it.isBlank() || it == "null" }
            if (unavailable != null) return uiText(R.string.persist_unavailable, unavailable)
            if (!store.optBoolean("open")) return uiText(R.string.persist_opening)
            val saved = store.optJSONObject("saved_chain") ?: return uiText(R.string.persist_pending)
            return when (saved.optString("state")) {
                "checking" -> uiText(R.string.persist_checking)
                "not_saved" -> uiText(R.string.persist_absent)
                "saved" -> uiText(R.string.persist_saved, saved.optLong("height"))
                "not_restorable" -> uiText(R.string.persist_not_restorable, saved.optString("reason"))
                "unknown" -> uiText(R.string.persist_unknown_reason, saved.optString("reason"))
                else -> uiText(R.string.persist_unknown)
            }
        }
    }
}

data class NodeUiState(
    val network: String = "devnet",
    val run: RunPhase = RunPhase.STOPPED,
    val status: ChainStatus = ChainStatus(),
    val message: UiText = uiText(R.string.ready),
    val log: String = "",
    val vpn: Boolean = false,
    val wakeLock: Boolean = false,
    val monitor: MonitorState = MonitorState(),
    val powerWakeAtMs: Long? = null,
)

object NodeState {
    val flow = MutableStateFlow(NodeUiState())
    fun message(text: UiText) = flow.update { it.copy(message = text) }
}
