package org.minanode.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
            OutlinedTextField(profile.checkpointSeconds.takeIf { it > 0 }?.toString() ?: "", {
                onChange(profile.copy(checkpointSeconds = it.toIntOrNull() ?: 0))
            }, label = { Text("Intervalle des checkpoints (secondes)") }, enabled = enabled, singleLine = true)
            Row { Checkbox(profile.slowNetwork, { onChange(profile.copy(slowNetwork = it)) }, enabled = enabled); Text("Délais réseau étendus", Modifier.padding(top = 12.dp)) }
            Text("À activer seulement si les transferts volumineux expirent. Un VPN peut ralentir le rattrapage.", style = MaterialTheme.typography.bodySmall)
            Button(enabled = enabled, onClick = onSave) { Text("Enregistrer le profil") }
        }
    }
}
