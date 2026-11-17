package org.minanode.android

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class WalletFramesTest {
    private fun frame(bytes: ByteArray) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.size).array() + bytes
    @Test fun readsBoundedFramesWithoutMixingTheTwoValues() {
        val public = ("B62" + "a".repeat(52)).toByteArray()
        val private = ("EK" + "b".repeat(50)).toByteArray()
        val input = ByteArrayInputStream(frame(public) + frame(private))
        assertTrue(WalletFrames.validPublic(WalletFrames.read(input, 128).toString(Charsets.UTF_8)))
        val bytes = WalletFrames.read(input, 128)
        val chars = WalletFrames.privateChars(bytes)
        assertEquals('E', chars[0]); assertEquals('K', chars[1]); assertEquals(52, chars.size)
        chars.fill('\u0000'); bytes.fill(0)
        assertTrue(chars.all { it == '\u0000' }); assertTrue(bytes.all { it == 0.toByte() })
        assertEquals(-1, input.read())
    }
    @Test fun rejectsOversizedTruncatedAndMalformedPrivateReplies() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2), ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(16385).array(),
            byteArrayOf(3, 0, 0, 0, 1))) {
            assertTrue(runCatching { WalletFrames.read(ByteArrayInputStream(bytes), 128) }.isFailure)
        }
        for (value in listOf("not-a-key", "EK" + "0".repeat(50), "EK" + "a".repeat(200))) {
            val failure = runCatching { WalletFrames.privateChars(value.toByteArray()) }.exceptionOrNull()
            assertNotNull(failure)
            assertFalse(failure!!.message.orEmpty().contains(value))
        }
    }
    @Test fun privateBufferCanBeIrreversiblyClearedWithoutDeletingEncryptedBackup() {
        val chars = ("EK" + "b".repeat(50)).toCharArray()
        val result = GeneratedWallet("B62" + "a".repeat(52), chars, java.io.File("encrypted.json"))
        result.erasePrivate()
        assertNull(result.privateKey())
        assertTrue(chars.all { it == '\u0000' })
        assertEquals("encrypted.json", result.encryptedFile.name)
    }
}
