package org.minanode.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.app.AlertDialog
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var preparingStart by mutableStateOf(false)
    private var pendingStart: NodeProfile? = null
    private var passwordDialog: AlertDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFBBAAFF), background = Color(0xFF101018), surface = Color(0xFF1C1C29))) {
                val state by NodeState.flow.collectAsStateWithLifecycle()
                val store = remember { ProfileStore(this@MainActivity) }
                val vault = remember { ProducerVault(this@MainActivity) }
                var profile by remember { mutableStateOf(store.load()) }
                var profileDirty by remember { mutableStateOf(false) }
                var hasKey by remember { mutableStateOf(vault.exists(profile.network)) }
                var page by remember { mutableStateOf("Nœud") }
                val importKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) lifecycleScope.launch {
                        val network = profile.network
                        val result = withContext(Dispatchers.IO) { runCatching { vault.import(network, uri) } }
                        hasKey = vault.exists(profile.network)
                        NodeState.message(result.fold({ "Clé chiffrée importée" }, { "Import impossible : ${it.message}" }))
                    }
                }
                val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { continueStart() }
                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME && !profileDirty && !preparingStart &&
                            NodeState.flow.value.run !in listOf(RunPhase.STARTING, RunPhase.RUNNING, RunPhase.STOPPING)) {
                            profile = store.load()
                            hasKey = vault.exists(profile.network)
                        }
                    }
                    owner.lifecycle.addObserver(observer)
                    onDispose { owner.lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(state.run) {
                    if (state.run != RunPhase.STOPPED) preparingStart = false
                }
                val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                    if (uri != null) lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching {
                            val stream = contentResolver.openOutputStream(uri) ?: error("Fichier inaccessible")
                            NodeFiles(this@MainActivity).export(stream)
                        } }
                        NodeState.message(result.fold({ "Journal exporté" }, { "Export impossible : ${it.message}" }))
                    }
                }
                val active = state.run in listOf(RunPhase.STARTING, RunPhase.RUNNING, RunPhase.STOPPING)
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                        .verticalScroll(remember(page) { ScrollState(0) }).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("MINA / ANDROID", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Votre nœud.\nSur votre téléphone.", style = MaterialTheme.typography.headlineLarge)
                        Text("${if (active) state.network else profile.network.cli} · ARM64 · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Nœud", "Réglages", "Moniteur", "Journal").forEach { name ->
                                FilterChip(page == name, { page = name }, label = { Text(name) })
                            }
                        }
                        if (page == "Réglages") ProfileEditor(profile, !active && !preparingStart, hasKey,
                            onChange = { profile = it; profileDirty = true }, onNetwork = { profile = store.load(it); profileDirty = true; hasKey = vault.exists(it) },
                            onImport = { importKey.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            onRemove = { vault.remove(profile.network); hasKey = false },
                            onSave = { runCatching { profile.validate(hasKey); store.save(profile); profileDirty = false }
                                .onSuccess { NodeState.message("Profil enregistré") }.onFailure { NodeState.message(it.message ?: "Profil invalide") } })
                        if (page == "Réglages") Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        if (page == "Moniteur") MonitorPanel(state.monitor, store.load().producer)
                        if (page == "Journal") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Journal", style = MaterialTheme.typography.titleLarge)
                                TextButton(onClick = { export.launch("mina-android-log.txt") }) { Text("Exporter") }
                            }
                            Text(state.message, style = MaterialTheme.typography.bodyMedium)
                            SelectionContainer {
                                Text(state.log.ifBlank { "Les événements du nœud apparaîtront ici." },
                                    Modifier.fillMaxWidth().background(Color(0xFF08080E)).padding(12.dp),
                                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (page == "Nœud") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(when (state.run) {
                                    RunPhase.STOPPED -> "À l’arrêt"
                                    RunPhase.STARTING -> "Démarrage"
                                    RunPhase.RUNNING -> state.status.phase
                                    RunPhase.STOPPING -> "Arrêt en cours"
                                    RunPhase.ERROR -> "Intervention nécessaire"
                                }, style = MaterialTheme.typography.titleLarge)
                                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                                if (state.run == RunPhase.STARTING || state.run == RunPhase.STOPPING) LinearProgressIndicator(Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(enabled = !active && !preparingStart, onClick = {
                                        runCatching { profile.validate(vault.exists(profile.network)); store.save(profile); profileDirty = false }.onSuccess {
                                            preparingStart = true
                                            pendingStart = profile
                                            // Finish the Android permission dialog before opening the
                                            // password dialog; never stack both windows at launch.
                                            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else continueStart()
                                        }.onFailure { NodeState.message(it.message ?: "Profil invalide") }
                                    }) { Text("Démarrer") }
                                    OutlinedButton(enabled = active && state.run != RunPhase.STOPPING, onClick = {
                                        startService(Intent(this@MainActivity, NodeService::class.java).setAction(NodeService.STOP))
                                    }) { Text("Arrêter") }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Metric("HAUTEUR", state.status.height?.toString() ?: "—", Modifier.weight(1f))
                            Metric("PAIRS", state.status.peers.toString(), Modifier.weight(1f))
                        }
                        Text("Âge du bloc : ${state.status.ageSeconds?.let { "$it s" } ?: "—"}  ·  Uptime : ${state.status.uptimeSeconds} s")
                        Text("Sauvegarde : ${state.status.persistence} · API 127.0.0.1:${NodeApi.PORT}", style = MaterialTheme.typography.bodySmall)
                        Text("Mode sélectionné : ${if (profile.producer) "producteur de blocs · mot de passe requis à chaque démarrage" else "nœud sans production"}", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(enabled = state.run == RunPhase.RUNNING, onClick = {
                            startActivity(Intent(this@MainActivity, DashboardActivity::class.java))
                        }) { Text("Ouvrir le tableau de bord complet") }
                        if (state.vpn) Card {
                            Text("VPN détecté. Lors des essais, il ralentissait fortement la synchronisation. Essayez sans VPN si le nœud prend du retard.", Modifier.padding(16.dp))
                        }
                        Text("Le nœud consomme du réseau, du stockage et de la batterie. Pendant son exécution, le processeur reste actif même écran éteint. Android peut toutefois arrêter l’application.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                        }) { Text("Réglages Android de l’application") }
                        Text("Les données Termux restent séparées. Ce nœud utilise son propre stockage privé et effectue sa première synchronisation.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val log = NodeFiles(this@MainActivity).tail()
            NodeState.flow.update { it.copy(log = log) }
        }
    }

    private fun continueStart() {
        val profile = pendingStart ?: return
        pendingStart = null
        if (profile.producer) passwordPrompt(profile) else launchNode(profile)
    }

    private fun launchNode(profile: NodeProfile, secret: CharArray? = null) {
        var id: String? = null
        runCatching {
            id = LaunchGate.offer(profile, secret)
            ContextCompat.startForegroundService(this,
                Intent(this, NodeService::class.java).setAction(NodeService.START).putExtra(NodeService.LAUNCH_ID, id))
        }.onFailure {
            LaunchGate.revoke(id); secret?.fill('\u0000'); preparingStart = false
            NodeState.message("Démarrage impossible : ${it.message}")
        }
    }

    private fun passwordPrompt(profile: NodeProfile) {
        // Native editable text avoids retaining the password in Compose or saved state.
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled = false
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            setPadding(32, 24, 32, 24)
        }
        var submitted = false
        val dialog = AlertDialog.Builder(this).setTitle("Démarrer le producteur · ${profile.network.cli}")
            .setMessage("Saisir le mot de passe de la clé Mina pour ce lancement. Il ne sera pas enregistré.")
            .setView(input).setNegativeButton("Annuler", null)
            .setPositiveButton("Démarrer") { _, _ ->
                val text = input.text
                val secret = CharArray(text.length) { text[it] }
                text.clear()
                submitted = true
                launchNode(profile, secret)
            }.create()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        passwordDialog = dialog
        dialog.setOnDismissListener {
            input.text.clear(); passwordDialog = null
            if (!submitted) preparingStart = false
        }
        dialog.show()
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    override fun onDestroy() {
        pendingStart = null
        passwordDialog?.dismiss()
        super.onDestroy()
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
