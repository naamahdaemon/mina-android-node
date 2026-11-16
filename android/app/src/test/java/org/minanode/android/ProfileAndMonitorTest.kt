package org.minanode.android

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileAndMonitorTest {
    private val publicKey = "B62" + "a".repeat(52)

    @Test fun oldProfilesKeepTheirValuesAndAcquireCompatibleDefaults() {
        val loaded = NodeProfile.fromJson(MinaNetwork.MAINNET, JSONObject("""{"producer":true,"checkpointSeconds":600,"slowNetwork":true}"""))
        assertTrue(loaded.producer)
        assertEquals(600, loaded.checkpointSeconds)
        assertEquals("", loaded.coinbaseReceiver)
        assertEquals(8303, loaded.libp2pPort)
        assertTrue(loaded.persistence)
        assertTrue(loaded.restore)
        assertEquals(1000L, loaded.blockInjectDelayMs)
    }

    @Test fun savesAndAppliesProductionNetworkStorageAndLoggingSettings() {
        val profile = NodeProfile(producer = true, coinbaseReceiver = publicKey, blockInjectDelayMs = 1500,
            snarkerBlockFirstLead = 600, maxPeers = 24, libp2pPort = 8400, logLevel = "debug",
            restore = false, restoreTimeoutSeconds = 120, allowWorkerControl = true)
        val loaded = NodeProfile.fromJson(profile.network, JSONObject(profile.toJson().toString()))
        assertEquals(profile, loaded)
        loaded.validate(true)
        val args = loaded.arguments(File("work"), File("key.json"))
        for ((name, value) in listOf("--coinbase-receiver" to publicKey, "--block-inject-delay-ms" to "1500",
            "--max-peers" to "24", "--libp2p-port" to "8400", "--log-level" to "debug",
            "--snarker-block-first-lead" to "600", "--frontier-store-load-timeout" to "120")) {
            assertEquals(name, value, args[args.indexOf(name) + 1])
        }
        assertTrue(args.contains("--frontier-store-no-restore"))
        assertTrue(args.contains("--allow-worker-control"))
    }

    @Test fun omitsCoinbaseOptionsWhenProductionIsOffOrReceiverIsEmpty() {
        assertFalse(NodeProfile(coinbaseReceiver = publicKey).arguments(File("work"), null).contains("--coinbase-receiver"))
        assertFalse(NodeProfile(producer = true).arguments(File("work"), File("key.json")).contains("--coinbase-receiver"))
        val args = NodeProfile(persistence = false, restore = false).arguments(File("work"), null)
        assertTrue(args.contains("--no-frontier-store"))
        assertFalse(args.contains("--frontier-store-no-restore"))
    }

    @Test fun rejectsInvalidReceiverAndConflictingPortsBeforeLaunch() {
        for (profile in listOf(NodeProfile(producer = true, coinbaseReceiver = "invalid"),
            NodeProfile(producer = true, blockInjectDelayMs = 999), NodeProfile(libp2pPort = NodeApi.PORT),
            NodeProfile(maxPeers = 0), NodeProfile(restoreTimeoutSeconds = 301), NodeProfile(snarkerBlockFirstLead = 4201))) {
            assertTrue(runCatching { profile.validate(true) }.isFailure)
        }
    }

    @Test fun separatesNetworksAndKeepsCredentialsOutOfArguments() {
        val args = NodeProfile(MinaNetwork.MAINNET, producer = true)
            .arguments(File("mainnet"), File("encrypted.json"))
        assertEquals("mainnet", args[1])
        assertTrue(args.contains("--android-credentials-stdin"))
        assertTrue(args.contains("--producer-key"))
        assertFalse(args.any { it.contains("password") || it.contains("MINA_PRIVKEY_PASS") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun producerRequiresImportedKey() { NodeProfile(producer = true).validate(false) }

    @Test fun rejectsFeeSyntaxUnsupportedByNode() {
        val profile = NodeProfile(snarker = true, snarkerPublicKey = "B62" + "a".repeat(52))
        profile.validate(false)
        for (fee in listOf("1e2", "-1", "0.0000000001", "1000000000")) {
            assertTrue(runCatching { profile.copy(snarkerFee = fee).validate(false) }.isFailure)
        }
    }

    @Test fun readsNanosecondProductionTimesAndTransactionCounts() {
        val raw = """{"attempts":[{"status":"Canonical","won_slot":{"global_slot":42,"slot_time":1000000000},
          "times":{"staged_ledger_diff_create_start":1000000000,"staged_ledger_diff_create_end":1040000000,
          "proof_create_start":2000000000,"proof_create_end":5000000000,"committed":6000000000},
          "block":{"hash":"example","height":90,"transactions":{"payments":2,"delegations":1,"zkapps":3}}}]}"""
        val block = ProductionParser.parse(raw).first.single()
        assertEquals(6, block.tx)
        assertEquals(3, block.zkapps)
        assertEquals(40.0, block.prepMs!!, 0.001)
        assertEquals(3.0, block.proofSeconds!!, 0.001)
        assertEquals(1.0, block.delaySeconds!!, 0.001)
        assertEquals(2000L, block.proofStartMs)
    }
}
