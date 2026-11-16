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
    val coinbaseReceiver: String = "",
    val blockInjectDelayMs: Long = 1000,
    val snarkerBlockFirstLead: Int = 420,
    val maxPeers: Int = 100,
    val libp2pPort: Int = 8303,
    val logLevel: String = "info",
    val persistence: Boolean = true,
    val restore: Boolean = true,
    val restoreTimeoutSeconds: Int = 60,
    val allowWorkerControl: Boolean = false,
) {
    fun validate(hasKey: Boolean) {
        require(!producer || hasKey) { "Importer d’abord un fichier de clé Mina chiffré" }
        if (producer) {
            require(coinbaseReceiver.isBlank() || validPublicKey(coinbaseReceiver)) { "Destinataire de la coinbase invalide" }
            require(blockInjectDelayMs >= 1000) { "Délai de publication : au moins 1000 millisecondes" }
        }
        if (snarker) {
            require(snarkerPublicKey.matches(Regex("B62[1-9A-HJ-NP-Za-km-z]{40,60}"))) { "Clé publique SNARK invalide" }
            val fee = snarkerFee.toBigDecimalOrNull()
            require(snarkerFee.matches(Regex("[0-9]+(\\.[0-9]{1,9})?")) && fee != null && fee >= BigDecimal.ZERO && fee < BigDecimal("1000000000")) { "Frais SNARK invalides (9 décimales maximum)" }
        }
        require(snarkerStrategy in listOf("rand", "seq")) { "Stratégie SNARK invalide" }
        require(checkpointSeconds in 60..86400) { "Intervalle de sauvegarde : 60 à 86400 secondes" }
        require(snarkerBlockFirstLead in 0..4200) { "Priorité production : 0 à 4200 secondes" }
        require(maxPeers in 1..1000) { "Nombre maximal de pairs : 1 à 1000" }
        require(libp2pPort in 1024..65535 && libp2pPort != NodeApi.PORT) { "Port P2P : 1024 à 65535, différent du port API ${NodeApi.PORT}" }
        require(logLevel in listOf("error", "warn", "info", "debug", "trace")) { "Niveau de journal invalide" }
        require(restoreTimeoutSeconds in 1..300) { "Délai de restauration : 1 à 300 secondes" }
    }

    fun arguments(work: File, key: File?): List<String> = buildList {
        addAll(listOf("--network", network.cli, "node", "--work-dir", work.absolutePath,
            "--http-addr", "127.0.0.1", "--port", NodeApi.PORT.toString(), "--libp2p-port", libp2pPort.toString(),
            "--max-peers", maxPeers.toString(), "--checkpoint-interval", checkpointSeconds.toString(),
            "--log-level", logLevel, "--frontier-store-load-timeout", restoreTimeoutSeconds.toString(),
            "--snarker-block-first-lead", snarkerBlockFirstLead.toString(), "--android-credentials-stdin"))
        if (!persistence) add("--no-frontier-store")
        else if (!restore) add("--frontier-store-no-restore")
        if (allowWorkerControl) add("--allow-worker-control")
        if (producer) {
            addAll(listOf("--producer-key", requireNotNull(key).absolutePath, "--block-inject-delay-ms", blockInjectDelayMs.toString()))
            if (coinbaseReceiver.isNotBlank()) addAll(listOf("--coinbase-receiver", coinbaseReceiver))
        }
        if (snarker) addAll(listOf("--run-snarker", snarkerPublicKey, "--snarker-fee", snarkerFee,
            "--snarker-strategy", snarkerStrategy))
    }

    fun toJson(): JSONObject = JSONObject().put("producer", producer).put("snarker", snarker)
        .put("snarkerPublicKey", snarkerPublicKey).put("snarkerFee", snarkerFee)
        .put("snarkerStrategy", snarkerStrategy).put("checkpointSeconds", checkpointSeconds)
        .put("slowNetwork", slowNetwork).put("coinbaseReceiver", coinbaseReceiver)
        .put("blockInjectDelayMs", blockInjectDelayMs).put("snarkerBlockFirstLead", snarkerBlockFirstLead)
        .put("maxPeers", maxPeers).put("libp2pPort", libp2pPort).put("logLevel", logLevel)
        .put("persistence", persistence).put("restore", restore).put("restoreTimeoutSeconds", restoreTimeoutSeconds)
        .put("allowWorkerControl", allowWorkerControl)

    companion object {
        private fun validPublicKey(value: String) = value.matches(Regex("B62[1-9A-HJ-NP-Za-km-z]{40,60}"))
        fun fromJson(network: MinaNetwork, json: JSONObject) = NodeProfile(
            network = network, producer = json.optBoolean("producer"), snarker = json.optBoolean("snarker"),
            snarkerPublicKey = json.optString("snarkerPublicKey"), snarkerFee = json.optString("snarkerFee", "0.1"),
            snarkerStrategy = json.optString("snarkerStrategy", "rand"), checkpointSeconds = json.optInt("checkpointSeconds", 1800),
            slowNetwork = json.optBoolean("slowNetwork"), coinbaseReceiver = json.optString("coinbaseReceiver"),
            blockInjectDelayMs = json.optLong("blockInjectDelayMs", 1000), snarkerBlockFirstLead = json.optInt("snarkerBlockFirstLead", 420),
            maxPeers = json.optInt("maxPeers", 100), libp2pPort = json.optInt("libp2pPort", 8303), logLevel = json.optString("logLevel", "info"),
            persistence = json.optBoolean("persistence", true), restore = json.optBoolean("restore", true),
            restoreTimeoutSeconds = json.optInt("restoreTimeoutSeconds", 60), allowWorkerControl = json.optBoolean("allowWorkerControl"))
    }
}

class ProfileStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("node-profiles", Context.MODE_PRIVATE)
    fun selected(): MinaNetwork = runCatching { MinaNetwork.valueOf(prefs.getString("network", "DEVNET")!!) }.getOrDefault(MinaNetwork.DEVNET)
    fun load(network: MinaNetwork = selected()): NodeProfile = runCatching {
        val json = JSONObject(prefs.getString(network.cli, "{}")!!)
        NodeProfile.fromJson(network, json)
    }.getOrDefault(NodeProfile(network))
    fun save(profile: NodeProfile) {
        val json = profile.toJson()
        check(prefs.edit().putString(profile.network.cli, json.toString()).putString("network", profile.network.name).commit())
    }
}
