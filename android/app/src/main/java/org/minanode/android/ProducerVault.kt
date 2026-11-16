package org.minanode.android

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores only Mina's encrypted key file, additionally wrapped by Android Keystore. */
class ProducerVault(private val context: Context) {
    private val root = File(context.noBackupFilesDir, "producer-keys").apply { mkdirs() }
    private fun file(network: MinaNetwork) = File(root, "${network.cli}.vault")
    fun exists(network: MinaNetwork) = file(network).isFile

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("mina-encrypted-key-wrapper-v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("mina-encrypted-key-wrapper-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    fun import(network: MinaNetwork, uri: Uri) {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readNBytes(65537) }
        try {
            require(bytes.size in 1..65536) { "Fichier chiffré trop volumineux" }
            val json = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }
                .getOrElse { error("Le fichier n’est pas une clé Mina chiffrée JSON") }
            require(json.optString("box_primitive") == "xsalsa20poly1305" && json.optString("pw_primitive") == "argon2i") { "Format de clé chiffrée Mina non pris en charge" }
            for (name in listOf("nonce", "pwsalt", "ciphertext")) require(json.optString(name).isNotBlank()) { "Fichier de clé incomplet" }
            val costs = json.optJSONArray("pwdiff") ?: error("Coût de dérivation absent")
            require(costs.length() == 2 && costs.optLong(0) in 1..268435456 && costs.optLong(1) in 1..16) { "Coût Argon2 trop élevé pour ce profil mobile" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val wrapped = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(bytes)
            val target = AtomicFile(file(network))
            val stream = target.startWrite()
            try { stream.write(wrapped); target.finishWrite(stream) } catch (error: Exception) { target.failWrite(stream); throw error }
        } finally { bytes.fill(0) }
    }

    /** This temporary file is still password-encrypted Mina JSON, never plaintext key material. */
    fun materialize(network: MinaNetwork): File {
        val bytes = file(network).readBytes()
        val size = bytes[0].toInt() and 255
        require(size == 12 && bytes.size > 29) { "Coffre de clé invalide" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 1 + size)))
        }
        val encryptedMinaFile = cipher.doFinal(bytes, 1 + size, bytes.size - 1 - size)
        return try {
            File.createTempFile("producer-", ".json", context.cacheDir).apply {
                setReadable(false, false); setWritable(false, false)
                check(setReadable(true, true) && setWritable(true, true))
                writeBytes(encryptedMinaFile)
            }
        } finally { encryptedMinaFile.fill(0) }
    }

    fun remove(network: MinaNetwork) { AtomicFile(file(network)).delete() }
}

/** No password in Intent extras, preferences, saved UI state or process environment. */
object LaunchSecret {
    private var pending: CharArray? = null
    @Synchronized fun put(value: CharArray) { clear(); pending = value }
    @Synchronized fun take(): CharArray? = pending.also { pending = null }
    @Synchronized fun clear() { pending?.fill('\u0000'); pending = null }
}
