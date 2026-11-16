package org.minanode.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun ProfileEditor(profile: NodeProfile, enabled: Boolean, hasKey: Boolean,
    onChange: (NodeProfile) -> Unit, onNetwork: (MinaNetwork) -> Unit,
    onImport: () -> Unit, onRemove: () -> Unit, onSave: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Profil du nœud", style = MaterialTheme.typography.titleLarge)
            if (!enabled) Text("Arrêter le nœud pour modifier son profil.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MinaNetwork.entries.forEach { network ->
                    FilterChip(selected = profile.network == network, enabled = enabled,
                        onClick = { onNetwork(network) }, label = { Text(network.cli) })
                }
            }
            Text("Chaque réseau possède ses propres données et sa propre configuration.", style = MaterialTheme.typography.bodySmall)
            Row { Checkbox(profile.producer, { onChange(profile.copy(producer = it)) }, enabled = enabled); Text("Produire des blocs", Modifier.padding(top = 12.dp)) }
            Text(if (hasKey) "Fichier Mina chiffré importé · protégé par Android Keystore" else "Aucun fichier de clé de production importé")
            Row {
                TextButton(enabled = enabled, onClick = onImport) { Text(if (hasKey) "Remplacer la clé" else "Importer une clé chiffrée") }
                if (hasKey) TextButton(enabled = enabled, onClick = onRemove) { Text("Retirer") }
            }
            Text("Le mot de passe sera demandé à chaque démarrage. Il n’est pas enregistré.", style = MaterialTheme.typography.bodySmall)
            if (profile.producer) {
                OutlinedTextField(profile.coinbaseReceiver, { onChange(profile.copy(coinbaseReceiver = it.trim())) },
                    label = { Text("Coinbase receiver · clé publique B62…") },
                    supportingText = { Text("Facultatif : vide = clé du producteur. Pour un compte zkApp, la permission receive doit être None.") },
                    enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
                NumberSetting("Délai minimal de publication (ms)", profile.blockInjectDelayMs, enabled) {
                    onChange(profile.copy(blockInjectDelayMs = it))
                }
                Text("Au moins 1000 ms après le début du slot.", style = MaterialTheme.typography.bodySmall)
            }
            Row { Checkbox(profile.snarker, { onChange(profile.copy(snarker = it)) }, enabled = enabled); Text("Exécuter un SNARK worker", Modifier.padding(top = 12.dp)) }
            if (profile.snarker) {
                OutlinedTextField(profile.snarkerPublicKey, { onChange(profile.copy(snarkerPublicKey = it.trim())) },
                    label = { Text("Clé publique destinataire B62…") }, enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(profile.snarkerFee, { onChange(profile.copy(snarkerFee = it)) },
                    label = { Text("Frais par travail (MINA)") }, enabled = enabled, singleLine = true)
                Row {
                    listOf("rand", "seq").forEach { strategy -> FilterChip(profile.snarkerStrategy == strategy,
                        onClick = { onChange(profile.copy(snarkerStrategy = strategy)) }, enabled = enabled, label = { Text(strategy) }) }
                }
                Text("Le worker utilise une clé publique ; aucune clé privée SNARK n’est nécessaire.", style = MaterialTheme.typography.bodySmall)
            }
            if (profile.producer) {
                NumberSetting("Priorité production avant un slot (s)", profile.snarkerBlockFirstLead.toLong(), enabled) {
                    onChange(profile.copy(snarkerBlockFirstLead = it.toIntOrNullSafe()))
                }
                Text("Ne démarre pas de nouveau travail SNARK pendant cette fenêtre : 0 à 4200 s, 0 désactive cette priorité.", style = MaterialTheme.typography.bodySmall)
            }
            Row { Checkbox(profile.allowWorkerControl, { onChange(profile.copy(allowWorkerControl = it)) }, enabled = enabled); Text("Piloter le worker depuis le dashboard", Modifier.padding(top = 12.dp)) }
            Text("Autorise le dashboard local à démarrer, arrêter et modifier les frais du worker. Ses réglages sauvegardés peuvent le relancer au démarrage.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Réseau", style = MaterialTheme.typography.titleMedium)
            NumberSetting("Nombre maximal de pairs", profile.maxPeers.toLong(), enabled) {
                onChange(profile.copy(maxPeers = it.toIntOrNullSafe()))
            }
            NumberSetting("Port TCP libp2p", profile.libp2pPort.toLong(), enabled) {
                onChange(profile.copy(libp2pPort = it.toIntOrNullSafe()))
            }
            Text("API et dashboard : 127.0.0.1:${NodeApi.PORT}.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Stockage", style = MaterialTheme.typography.titleMedium)
            Row { Checkbox(profile.persistence, { onChange(profile.copy(persistence = it)) }, enabled = enabled); Text("Sauvegarder la chaîne sur disque", Modifier.padding(top = 12.dp)) }
            if (!profile.persistence) Text("Le prochain lancement devra synchroniser à nouveau. Les sauvegardes existantes ne sont pas supprimées.", style = MaterialTheme.typography.bodySmall)
            if (profile.persistence) {
            Row { Checkbox(profile.restore, { onChange(profile.copy(restore = it)) }, enabled = enabled); Text("Restaurer la chaîne au démarrage", Modifier.padding(top = 12.dp)) }
            OutlinedTextField(profile.checkpointSeconds.takeIf { it > 0 }?.toString() ?: "", {
                onChange(profile.copy(checkpointSeconds = it.toIntOrNull() ?: 0))
            }, label = { Text("Intervalle des checkpoints (secondes)") }, enabled = enabled, singleLine = true)
            NumberSetting("Délai sans progrès pendant la restauration (s)", profile.restoreTimeoutSeconds.toLong(), enabled) {
                onChange(profile.copy(restoreTimeoutSeconds = it.toIntOrNullSafe()))
            }
            Text("Entre 1 et 300 secondes.", style = MaterialTheme.typography.bodySmall)
            }
            Row { Checkbox(profile.slowNetwork, { onChange(profile.copy(slowNetwork = it)) }, enabled = enabled); Text("Délais réseau étendus", Modifier.padding(top = 12.dp)) }
            Text("À activer seulement si les transferts volumineux expirent. Un VPN peut ralentir le rattrapage.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Niveau du journal", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("error", "warn", "info", "debug", "trace").forEach { level ->
                    FilterChip(profile.logLevel == level, { onChange(profile.copy(logLevel = level)) }, enabled = enabled, label = { Text(level) })
                }
            }
            Text("Debug et trace produisent davantage de messages. Le journal est accessible dans son onglet dédié.", style = MaterialTheme.typography.bodySmall)
            Button(enabled = enabled, onClick = onSave) { Text("Enregistrer le profil") }
        }
    }
}

private fun Long.toIntOrNullSafe() = if (this in 0..Int.MAX_VALUE.toLong()) toInt() else -1

@Composable
private fun NumberSetting(label: String, value: Long, enabled: Boolean, onChange: (Long) -> Unit) {
    OutlinedTextField(value.takeIf { it >= 0 }?.toString() ?: "", { onChange(it.toLongOrNull() ?: -1) },
        label = { Text(label) }, enabled = enabled, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}
