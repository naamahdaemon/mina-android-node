package org.minanode.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFBBAAFF), background = Color(0xFF101018), surface = Color(0xFF1C1C29))) {
                val state by NodeState.flow.collectAsStateWithLifecycle()
                val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
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
                        .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("MINA / ANDROID", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Votre nœud.\nSur votre téléphone.", style = MaterialTheme.typography.headlineLarge)
                        Text("DEVNET · ARM64 · Prototype ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
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
                                    Button(enabled = !active, onClick = {
                                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        runCatching { ContextCompat.startForegroundService(this@MainActivity,
                                            Intent(this@MainActivity, NodeService::class.java).setAction(NodeService.START)) }
                                            .onFailure { NodeState.message("Démarrage impossible : ${it.message}") }
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
                        if (state.vpn) Card {
                            Text("VPN détecté. Lors des essais, il ralentissait fortement la synchronisation. Essayez sans VPN si le nœud prend du retard.", Modifier.padding(16.dp))
                        }
                        Text("Le nœud consomme du réseau, du stockage et de la batterie. Pendant son exécution, le processeur reste actif même écran éteint. Android peut toutefois arrêter l’application.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                        }) { Text("Réglages Android de l’application") }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Journal", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { export.launch("mina-android-log.txt") }) { Text("Exporter") }
                        }
                        SelectionContainer {
                            Text(state.log.ifBlank { "Les événements du nœud apparaîtront ici." },
                                Modifier.fillMaxWidth().background(Color(0xFF08080E)).padding(12.dp),
                                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Les données Termux restent séparées. Ce nœud utilise son propre stockage privé et effectue sa première synchronisation.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val log = NodeFiles(this@MainActivity).tail()
            NodeState.flow.update { it.copy(log = log) }
        }
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
