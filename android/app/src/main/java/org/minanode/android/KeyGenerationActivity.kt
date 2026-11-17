package org.minanode.android

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.doOnAttach
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.util.UUID

/** No passwords or private keys in Intents, Compose state, saved state, logs or files. */
class KeyGenerationActivity : ComponentActivity() {
    private lateinit var generator: NativeWalletGenerator
    private lateinit var network: MinaNetwork
    private var generated: GeneratedWallet? = null
    private var resultReady by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var exported by mutableStateOf(false)
    private var applied by mutableStateOf(false)
    private var revealed by mutableStateOf(false)
    private var message by mutableStateOf<UiText?>(null)
    private var secretDialog: AlertDialog? = null
    private var passwordInput: List<EditText> = emptyList()
    private var privateText: TextView? = null
    private var pendingPassword: CharArray? = null
    private var generationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val appearance = AppearancePreferences(this)
        setTheme(appearance.activityTheme())
        super.onCreate(savedInstanceState)
        network = runCatching { MinaNetwork.valueOf(intent.getStringExtra(NETWORK).orEmpty()) }.getOrElse { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.doOnAttach { applyFullscreen(it, appearance.fullscreen()) }
        generator = NativeWalletGenerator(this, network)
        generated = generator.pending()
        resultReady = generated != null
        // A resumed encrypted result is never an excuse to decrypt or redisplay its private key.
        revealed = generated != null
        setContent {
            MinaTheme(appearance.dark(), appearance.fullscreen()) {
                val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    if (uri != null) lifecycleScope.launch {
                        val file = generated?.encryptedFile ?: return@launch
                        busy = true
                        val result = withContext(Dispatchers.IO) { runCatching {
                            val output = contentResolver.openOutputStream(uri) ?: error("Export unavailable")
                            output.use { target -> file.inputStream().use { it.copyTo(target) } }
                        } }
                        busy = false
                        if (result.isSuccess) { exported = true; message = uiText(R.string.wallet_exported) }
                        else message = uiText(R.string.wallet_export_failed)
                    }
                }
                BackHandler { if (!busy || generated == null) leave() }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(appContentInsets()).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.wallet_generate_title), style = MaterialTheme.typography.headlineSmall)
                        Text(network.cli, style = MaterialTheme.typography.labelLarge)
                        message?.let { Text(it.resolve(this@KeyGenerationActivity), color = MaterialTheme.colorScheme.primary) }
                        val wallet = if (resultReady) generated else null
                        if (wallet == null) {
                            Text(stringResource(R.string.wallet_password_warning))
                            Text(stringResource(R.string.wallet_activation_warning))
                            Text(stringResource(R.string.wallet_private_warning))
                            Text(stringResource(R.string.wallet_no_automatic_transfer), style = MaterialTheme.typography.bodySmall)
                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            Button(enabled = !busy, onClick = { askPassword() }) { Text(stringResource(R.string.wallet_generate_button)) }
                        } else {
                            Text(stringResource(R.string.wallet_generated), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.wallet_address))
                            Text(wallet.publicKey.ifBlank { getString(R.string.wallet_address_unavailable) })
                            TextButton(enabled = wallet.publicKey.isNotBlank(), onClick = { copyPublic(wallet.publicKey) }) {
                                Text("⧉  " + stringResource(R.string.wallet_copy_address))
                            }
                            if (!revealed && wallet.privateKey() != null) {
                                Text(stringResource(R.string.wallet_reveal_warning))
                                Button(onClick = { revealWarning() }) { Text(stringResource(R.string.wallet_reveal)) }
                            } else Text(stringResource(R.string.wallet_private_gone), style = MaterialTheme.typography.bodySmall)
                            if (revealed || wallet.privateKey() == null) {
                            OutlinedButton(enabled = !busy, onClick = {
                                wallet.erasePrivate(); revealed = true
                                export.launch("mina-${network.cli}-${wallet.publicKey.take(12).ifBlank { "wallet" }}-encrypted.json")
                            }) { Text(stringResource(R.string.wallet_save_encrypted)) }
                            Button(enabled = !busy && !applied, onClick = { confirmApply() }) { Text(stringResource(R.string.wallet_use_producer)) }
                            Text(stringResource(R.string.wallet_backup_help), style = MaterialTheme.typography.bodySmall)
                            TextButton(enabled = !busy, onClick = { leave() }) { Text(stringResource(R.string.close)) }
                            }
                        }
                        TextButton(enabled = !busy, onClick = { leave() }) { Text(stringResource(R.string.back_to_node)) }
                    }
                }
            }
        }
    }

    private fun secure(dialog: AlertDialog): AlertDialog {
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val inputs = passwordInput.toList()
        val displayed = privateText
        dialog.setOnDismissListener {
            inputs.forEach { it.text.clear() }
            displayed?.text = ""
            if (displayed != null) generated?.erasePrivate()
            if (secretDialog === dialog) {
                passwordInput = emptyList(); privateText = null; secretDialog = null
            }
        }
        secretDialog = dialog
        return dialog
    }

    private fun askPassword() {
        val first = passwordField(R.string.wallet_new_password)
        val second = passwordField(R.string.wallet_repeat_password)
        passwordInput = listOf(first, second)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16)
            addView(TextView(this@KeyGenerationActivity).apply { text = getString(R.string.wallet_password_warning) })
            addView(first); addView(second)
        }
        val dialog = secure(AlertDialog.Builder(this).setTitle(R.string.wallet_password_title).setView(body)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.wallet_continue, null).create())
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val a = first.text; val b = second.text
            if (a.length < 12 || a.length > 4096) { first.error = getString(R.string.wallet_password_length); return@setOnClickListener }
            if (a.length != b.length || (0 until a.length).any { a[it] != b[it] }) {
                second.error = getString(R.string.wallet_password_mismatch); return@setOnClickListener
            }
            pendingPassword = CharArray(a.length) { a[it] }
            a.clear(); b.clear(); dialog.dismiss()
            confirmPassword()
        }
        first.requestFocus()
    }
    private fun passwordField(hintId: Int) = EditText(this).apply {
        hint = getString(hintId); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
    }
    private fun confirmPassword() {
        val dialog = secure(AlertDialog.Builder(this).setTitle(R.string.wallet_confirm_title)
            .setMessage(getString(R.string.wallet_password_warning) + "\n\n" + getString(R.string.wallet_activation_warning))
            .setNegativeButton(R.string.cancel) { _, _ -> pendingPassword?.fill('\u0000'); pendingPassword = null }
            .setPositiveButton(R.string.wallet_generate_button) { _, _ -> generate() }.create())
        dialog.setOnCancelListener { pendingPassword?.fill('\u0000'); pendingPassword = null }
        dialog.show()
    }
    private fun generate() {
        val password = pendingPassword ?: return
        pendingPassword = null; busy = true; message = null
        generationJob = lifecycleScope.launch {
            try {
                val wallet = generator.generate(password)
                generated = wallet; resultReady = true
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { wallet.erasePrivate(); revealed = true }
            } catch (_: CancellationException) { message = uiText(R.string.wallet_generation_cancelled) }
            catch (_: Exception) { message = uiText(R.string.wallet_generation_failed) }
            finally { password.fill('\u0000'); busy = false }
        }
    }
    private fun revealWarning() {
        var accepted = false
        val dialog = secure(AlertDialog.Builder(this).setTitle(R.string.wallet_reveal_title).setMessage(R.string.wallet_reveal_warning)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.wallet_reveal) { _, _ -> accepted = true }.create())
        dialog.setOnDismissListener {
            if (secretDialog === dialog) secretDialog = null
            if (accepted) showPrivateOnce()
        }
        dialog.show()
    }
    private fun showPrivateOnce() {
        val wallet = generated ?: return
        val chars = wallet.privateKey() ?: return
        if (revealed || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        revealed = true
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
        body.addView(TextView(this).apply { text = getString(R.string.wallet_address) + "\n" + wallet.publicKey })
        body.addView(android.widget.Button(this).apply { text = "⧉ " + getString(R.string.wallet_copy_address); setOnClickListener { copyPublic(wallet.publicKey) } })
        privateText = TextView(this).apply {
            text = String(chars); setTextIsSelectable(false); isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        }
        body.addView(TextView(this).apply { text = getString(R.string.wallet_private_key) }); body.addView(requireNotNull(privateText))
        body.addView(android.widget.Button(this).apply {
            text = "⧉ " + getString(R.string.wallet_copy_private)
            setOnClickListener {
                val warning = AlertDialog.Builder(this@KeyGenerationActivity).setTitle(R.string.wallet_copy_private)
                    .setMessage(R.string.wallet_clipboard_warning).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.wallet_copy_confirm) { _, _ -> wallet.privateKey()?.let { copyPrivate(it) } }.create()
                warning.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE); warning.show()
            }
        })
        body.addView(TextView(this).apply { text = getString(R.string.wallet_private_warning) })
        val dialog = secure(AlertDialog.Builder(this).setTitle(R.string.wallet_one_time_title).setView(body).setPositiveButton(R.string.close, null).create())
        dialog.show()
    }
    private fun copyPublic(value: String) {
        requireNotNull(getSystemService(ClipboardManager::class.java)).setPrimaryClip(ClipData.newPlainText(getString(R.string.wallet_address), value))
    }
    private fun copyPrivate(chars: CharArray) {
        val clipboard = requireNotNull(applicationContext.getSystemService(ClipboardManager::class.java))
        val token = "mina-private-" + UUID.randomUUID()
        val clip = ClipData.newPlainText(token, String(chars))
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        clipboard.setPrimaryClip(clip)
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { if (clipboard.primaryClipDescription?.label?.toString() == token) clipboard.clearPrimaryClip() }
        }, 60000)
    }
    private fun confirmApply() {
        val replacing = ProducerVault(this).exists(network)
        val dialog = secure(AlertDialog.Builder(this).setTitle(R.string.wallet_use_producer)
            .setMessage(if (replacing) R.string.wallet_replace_warning else R.string.wallet_apply_warning)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.wallet_use_producer) { _, _ ->
                if (NodeState.flow.value.run in listOf(RunPhase.STARTING, RunPhase.RUNNING, RunPhase.STOPPING)) {
                    message = uiText(R.string.stop_to_edit); return@setPositiveButton
                }
                val file = generated?.encryptedFile ?: return@setPositiveButton
                busy = true
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching {
                        val bytes = file.readBytes()
                        try { ProducerVault(this@KeyGenerationActivity).storeEncrypted(network, bytes) } finally { bytes.fill(0) }
                    } }
                    busy = false
                    if (result.isSuccess) { applied = true; setResult(RESULT_OK); message = uiText(R.string.wallet_applied) }
                    else message = uiText(R.string.wallet_apply_failed)
                }
            }.create())
        dialog.show()
    }
    private fun leave() {
        generated?.erasePrivate()
        if (exported) generator.discard()
        finish()
    }
    override fun onStop() {
        secretDialog?.dismiss(); passwordInput.forEach { it.text.clear() }; passwordInput = emptyList()
        privateText?.text = ""; privateText = null
        pendingPassword?.fill('\u0000'); pendingPassword = null
        generated?.erasePrivate(); if (generated != null) revealed = true
        if (busy && generated == null) { generator.cancel(); generationJob?.cancel() }
        super.onStop()
    }
    override fun onDestroy() { generated?.erasePrivate(); if (::generator.isInitialized) generator.cancel(); super.onDestroy() }
    companion object { const val NETWORK = "network" }
}
