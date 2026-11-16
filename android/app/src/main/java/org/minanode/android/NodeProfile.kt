package org.minanode.android

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal

enum class MinaNetwork(val cli: String) { DEVNET("devnet"), MAINNET("mainnet") }

data class NodeProfile(
    val network: MinaNetwork = MinaNetwork.DEVNET,
    val producer: Boolean = false,
    val snarker: Boolean = false,
    val snarkerPublicKey: String = "",
    val snarkerFee: String = "0.1",
    val snarkerStrategy: String = "rand",
    val checkpointSeconds: Int = 1800,
    val slowNetwork: Boolean = false,
) {
    fun validate(hasKey: Boolean) {
        require(!producer || hasKey) { "Importer d’abord un fichier de clé Mina chiffré" }
        if (snarker) {
            require(snarkerPublicKey.matches(Regex("B62[1-9A-HJ-NP-Za-km-z]{40,60}"))) { "Clé publique SNARK invalide" }
            val fee = snarkerFee.toBigDecimalOrNull()
            require(snarkerFee.matches(Regex("[0-9]+(\\.[0-9]{1,9})?")) && fee != null && fee >= BigDecimal.ZERO && fee < BigDecimal("1000000000")) { "Frais SNARK invalides (9 décimales maximum)" }
        }
        require(snarkerStrategy in listOf("rand", "seq")) { "Stratégie SNARK invalide" }
        require(checkpointSeconds in 60..86400) { "Intervalle de sauvegarde : 60 à 86400 secondes" }
    }

    fun arguments(work: File, key: File?): List<String> = buildList {
        addAll(listOf("--network", network.cli, "node", "--work-dir", work.absolutePath,
            "--http-addr", "127.0.0.1", "--port", NodeApi.PORT.toString(), "--libp2p-port", "8303",
            "--checkpoint-interval", checkpointSeconds.toString(), "--log-level", "info", "--android-credentials-stdin"))
        if (producer) addAll(listOf("--producer-key", requireNotNull(key).absolutePath))
        if (snarker) addAll(listOf("--run-snarker", snarkerPublicKey, "--snarker-fee", snarkerFee,
            "--snarker-strategy", snarkerStrategy))
    }
}

class ProfileStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("node-profiles", Context.MODE_PRIVATE)
    fun selected(): MinaNetwork = runCatching { MinaNetwork.valueOf(prefs.getString("network", "DEVNET")!!) }.getOrDefault(MinaNetwork.DEVNET)
    fun load(network: MinaNetwork = selected()): NodeProfile = runCatching {
        val json = JSONObject(prefs.getString(network.cli, "{}")!!)
        NodeProfile(network, json.optBoolean("producer"), json.optBoolean("snarker"),
            json.optString("snarkerPublicKey"), json.optString("snarkerFee", "0.1"),
            json.optString("snarkerStrategy", "rand"), json.optInt("checkpointSeconds", 1800), json.optBoolean("slowNetwork"))
    }.getOrDefault(NodeProfile(network))
    fun save(profile: NodeProfile) {
        val json = JSONObject().put("producer", profile.producer).put("snarker", profile.snarker)
            .put("snarkerPublicKey", profile.snarkerPublicKey).put("snarkerFee", profile.snarkerFee)
            .put("snarkerStrategy", profile.snarkerStrategy).put("checkpointSeconds", profile.checkpointSeconds)
            .put("slowNetwork", profile.slowNetwork)
        check(prefs.edit().putString(profile.network.cli, json.toString()).putString("network", profile.network.name).commit())
    }
}
