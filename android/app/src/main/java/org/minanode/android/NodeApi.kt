package org.minanode.android

import java.net.HttpURLConnection
import java.net.URL

object NodeApi {
    const val PORT = 4300

    fun status(network: String = "devnet"): ChainStatus = ChainStatus.parse(get("status"), expectedNetwork = network)

    fun bestChainHash(height: Long, network: String): String {
        require(height in 1..4_294_967_295L)
        // Verify the network on both sides of the lookup, including during a restart.
        status(network)
        val hash = parseBestChainHash(get("best-chain/headers?limit=1&max_height=$height"), height)
        status(network)
        return hash ?: throw UiException(uiText(R.string.canonical_unavailable))
    }

    fun parseBestChainHash(text: String, height: Long): String? {
        val header = org.json.JSONObject(text).optJSONArray("headers")?.optJSONObject(0) ?: return null
        // max_height may return an older header: never substitute a different height.
        if (header.optLong("height", -1) != height) return null
        return header.optString("hash").takeIf { ExplorerLink.url(ExplorerLink.DEFAULT, "mainnet", it) != null }
    }

    fun get(path: String): String {
        require(path in listOf("status", "stats/block_producer", "stats/sync/progress") ||
            path.matches(Regex("best-chain/headers\\?limit=1&max_height=[0-9]{1,10}")))
        val connection = URL("http://127.0.0.1:$PORT/$path").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 1500
            connection.readTimeout = 2500
            connection.instanceFollowRedirects = false
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.use { it.readNBytes(4 * 1024 * 1024 + 1) }
            requireUi(bytes.size <= 4 * 1024 * 1024, R.string.error_response_large)
            bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }
}
