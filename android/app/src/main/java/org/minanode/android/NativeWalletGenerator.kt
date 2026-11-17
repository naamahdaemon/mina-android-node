package org.minanode.android

import android.content.Context
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Owns the one-time plaintext buffer. Never put this object in saved state or log it. */
class GeneratedWallet(val publicKey: String, private var secret: CharArray?, val encryptedFile: File) {
    fun privateKey(): CharArray? = secret
    fun erasePrivate() { secret?.fill('\u0000'); secret = null }
}

object WalletFrames {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    fun read(input: InputStream, max: Int): ByteArray {
        val header = input.readNBytes(4)
        require(header.size == 4)
        val size = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int
        require(size in 1..max)
        val bytes = input.readNBytes(size)
        if (bytes.size != size) { bytes.fill(0); throw IllegalArgumentException("Incomplete private reply") }
        return bytes
    }
    fun privateChars(bytes: ByteArray): CharArray {
        require(bytes.size in 40..128 && bytes[0] == 'E'.code.toByte() && bytes[1] == 'K'.code.toByte())
        require(bytes.all { (it.toInt() and 255).toChar() in ALPHABET })
        return CharArray(bytes.size) { (bytes[it].toInt() and 255).toChar() }
    }
    fun validPublic(value: String) = value.startsWith("B62") && value.length in 40..128 && value.all { it in ALPHABET }
}

class NativeWalletGenerator(private val context: Context, val network: MinaNetwork) {
    private val root = File(context.noBackupFilesDir, "generated-wallets/${network.cli}").apply { mkdirs() }
    val encryptedFile = File(root, "wallet.json")
    private val publicFile = File(root, "wallet.json.pub")
    @Volatile private var child: java.lang.Process? = null
    fun pending(): GeneratedWallet? {
        if (!encryptedFile.isFile) return null
        val public = runCatching { publicFile.takeIf { it.length() <= 128 }?.readText()?.trim() }.getOrNull()
            ?.takeIf(WalletFrames::validPublic).orEmpty()
        return GeneratedWallet(public, null, encryptedFile)
    }
    @Volatile private var transientSecret: CharArray? = null
    fun cancel() { transientSecret?.fill('\u0000'); child?.destroyForcibly() }
    fun discard() { encryptedFile.delete(); publicFile.delete() }
    suspend fun generate(password: CharArray): GeneratedWallet = withContext(Dispatchers.IO) {
        var success = false
        var ownsOutput = false
        var privateKey: CharArray? = null
        var process: java.lang.Process? = null
        val timer = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "mina-keygen-timeout").apply { isDaemon = true } }
        try {
            requireUi(!encryptedFile.exists() && !publicFile.exists(), R.string.wallet_pending)
            val binary = File(context.applicationInfo.nativeLibraryDir, "libmina.so")
            requireUi(binary.canExecute(), R.string.error_binary_missing)
            val builder = ProcessBuilder(binary.absolutePath, "--network", network.cli, "wallet", "generate",
                "--android-credentials-stdin", "--output", encryptedFile.absolutePath)
                .directory(root).redirectError(File("/dev/null"))
            builder.environment().apply {
                keys.filter { it.startsWith("MINA_") }.toList().forEach { remove(it) }
                put("HOME", context.noBackupFilesDir.absolutePath)
                put("TMPDIR", context.cacheDir.absolutePath)
                put("RUST_BACKTRACE", "0")
            }
            ownsOutput = true
            val running = builder.start()
            process = running; child = running
            timer.schedule({ running.destroyForcibly() }, 60, TimeUnit.SECONDS)
            val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(password))
            val bytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
            try {
                require(bytes.size in 1..16384)
                running.outputStream.use { stream ->
                    stream.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.size).array())
                    stream.write(bytes); stream.flush()
                }
            } finally { bytes.fill(0); password.fill('\u0000'); if (encoded.hasArray()) encoded.array().fill(0) }
            val publicBytes = WalletFrames.read(running.inputStream, 128)
            val public = publicBytes.toString(Charsets.UTF_8)
            require(WalletFrames.validPublic(public))
            val secretBytes = WalletFrames.read(running.inputStream, 128)
            try { privateKey = WalletFrames.privateChars(secretBytes); transientSecret = privateKey } finally { secretBytes.fill(0) }
            require(running.inputStream.read() == -1 && running.waitFor() == 0)
            require(encryptedFile.length() in 1..65536 && publicFile.readText().trim() == public)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            success = true
            GeneratedWallet(public, privateKey, encryptedFile)
        } catch (_: Exception) {
            privateKey?.fill('\u0000')
            throw UiException(uiText(R.string.wallet_generation_failed))
        } finally {
            password.fill('\u0000'); process?.destroyForcibly(); child = null; timer.shutdownNow()
            if (!success && ownsOutput) discard()
        }
    }
}
