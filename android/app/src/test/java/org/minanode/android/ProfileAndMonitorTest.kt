package org.minanode.android

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ProfileAndMonitorTest {
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
