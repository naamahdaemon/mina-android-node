package org.minanode.android

import java.net.HttpURLConnection
import java.net.URL

object NodeApi {
    const val PORT = 4300

    fun status(): ChainStatus {
        val connection = URL("http://127.0.0.1:$PORT/status").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 1500
            connection.readTimeout = 2500
            connection.instanceFollowRedirects = false
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.use { it.readNBytes(4 * 1024 * 1024 + 1) }
            check(bytes.size <= 4 * 1024 * 1024) { "Réponse de statut trop volumineuse" }
            ChainStatus.parse(bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }
}
