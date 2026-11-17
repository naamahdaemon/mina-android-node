package org.minanode.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun ProfileEditor(profile: NodeProfile, enabled: Boolean, hasKey: Boolean,
    onChange: (NodeProfile) -> Unit, onNetwork: (MinaNetwork) -> Unit,
    onImport: () -> Unit, onGenerate: () -> Unit, onRemove: () -> Unit, onSave: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.node_profile), style = MaterialTheme.typography.titleLarge)
            if (!enabled) Text(stringResource(R.string.stop_to_edit))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MinaNetwork.entries.forEach { network ->
                    FilterChip(selected = profile.network == network, enabled = enabled,
                        onClick = { onNetwork(network) }, label = { Text(network.cli) })
                }
            }
            Text(stringResource(R.string.networks_separate), style = MaterialTheme.typography.bodySmall)
            Row { Checkbox(profile.producer, { onChange(profile.copy(producer = it)) }, enabled = enabled); Text(stringResource(R.string.produce_blocks), Modifier.padding(top = 12.dp)) }
            Text(if (hasKey) stringResource(R.string.key_present) else stringResource(R.string.key_absent))
            Row {
                TextButton(enabled = enabled, onClick = onImport) { Text(if (hasKey) stringResource(R.string.replace_key) else stringResource(R.string.import_key)) }
                if (hasKey) TextButton(enabled = enabled, onClick = onRemove) { Text(stringResource(R.string.remove_key)) }
            }
            if (profile.producer) OutlinedButton(enabled = enabled, onClick = onGenerate) { Text(stringResource(R.string.wallet_generate_button)) }
            Text(stringResource(R.string.password_each_start), style = MaterialTheme.typography.bodySmall)
            if (profile.producer) {
                OutlinedTextField(profile.coinbaseReceiver, { onChange(profile.copy(coinbaseReceiver = it.trim())) },
                    label = { Text(stringResource(R.string.coinbase_receiver)) },
                    supportingText = { Text(stringResource(R.string.coinbase_help)) },
                    enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
                NumberSetting(stringResource(R.string.publish_delay), profile.blockInjectDelayMs, enabled) {
                    onChange(profile.copy(blockInjectDelayMs = it))
                }
                Text(stringResource(R.string.publish_delay_help), style = MaterialTheme.typography.bodySmall)
            }
            Row { Checkbox(profile.snarker, { onChange(profile.copy(snarker = it)) }, enabled = enabled); Text(stringResource(R.string.run_snarker), Modifier.padding(top = 12.dp)) }
            if (profile.snarker) {
                OutlinedTextField(profile.snarkerPublicKey, { onChange(profile.copy(snarkerPublicKey = it.trim())) },
                    label = { Text(stringResource(R.string.snarker_key)) }, enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(profile.snarkerFee, { onChange(profile.copy(snarkerFee = it)) },
                    label = { Text(stringResource(R.string.snarker_fee)) }, enabled = enabled, singleLine = true)
                Row {
                    listOf("rand", "seq").forEach { strategy -> FilterChip(profile.snarkerStrategy == strategy,
                        onClick = { onChange(profile.copy(snarkerStrategy = strategy)) }, enabled = enabled, label = { Text(strategy) }) }
                }
                Text(stringResource(R.string.snarker_key_help), style = MaterialTheme.typography.bodySmall)
            }
            if (profile.producer) {
                NumberSetting(stringResource(R.string.block_first_lead), profile.snarkerBlockFirstLead.toLong(), enabled) {
                    onChange(profile.copy(snarkerBlockFirstLead = it.toIntOrNullSafe()))
                }
                Text(stringResource(R.string.block_first_help), style = MaterialTheme.typography.bodySmall)
            }
            Row { Checkbox(profile.allowWorkerControl, { onChange(profile.copy(allowWorkerControl = it)) }, enabled = enabled); Text(stringResource(R.string.worker_control), Modifier.padding(top = 12.dp)) }
            Text(stringResource(R.string.worker_control_help), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(stringResource(R.string.network), style = MaterialTheme.typography.titleMedium)
            NumberSetting(stringResource(R.string.max_peers), profile.maxPeers.toLong(), enabled) {
                onChange(profile.copy(maxPeers = it.toIntOrNullSafe()))
            }
            NumberSetting(stringResource(R.string.p2p_port), profile.libp2pPort.toLong(), enabled) {
                onChange(profile.copy(libp2pPort = it.toIntOrNullSafe()))
            }
            Text(stringResource(R.string.local_api, NodeApi.PORT), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(stringResource(R.string.storage), style = MaterialTheme.typography.titleMedium)
            Row { Checkbox(profile.persistence, { onChange(profile.copy(persistence = it)) }, enabled = enabled); Text(stringResource(R.string.save_chain), Modifier.padding(top = 12.dp)) }
            if (!profile.persistence) Text(stringResource(R.string.save_disabled_help), style = MaterialTheme.typography.bodySmall)
            if (profile.persistence) {
            Row { Checkbox(profile.restore, { onChange(profile.copy(restore = it)) }, enabled = enabled); Text(stringResource(R.string.restore_chain), Modifier.padding(top = 12.dp)) }
            OutlinedTextField(profile.checkpointSeconds.takeIf { it > 0 }?.toString() ?: "", {
                onChange(profile.copy(checkpointSeconds = it.toIntOrNull() ?: 0))
            }, label = { Text(stringResource(R.string.checkpoint_interval)) }, enabled = enabled, singleLine = true)
            NumberSetting(stringResource(R.string.restore_timeout), profile.restoreTimeoutSeconds.toLong(), enabled) {
                onChange(profile.copy(restoreTimeoutSeconds = it.toIntOrNullSafe()))
            }
            Text(stringResource(R.string.restore_timeout_help), style = MaterialTheme.typography.bodySmall)
            }
            Row { Checkbox(profile.slowNetwork, { onChange(profile.copy(slowNetwork = it)) }, enabled = enabled); Text(stringResource(R.string.extended_timeouts), Modifier.padding(top = 12.dp)) }
            Text(stringResource(R.string.extended_help), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(stringResource(R.string.log_level), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("error", "warn", "info", "debug", "trace").forEach { level ->
                    FilterChip(profile.logLevel == level, { onChange(profile.copy(logLevel = level)) }, enabled = enabled, label = { Text(level) })
                }
            }
            Text(stringResource(R.string.log_level_help), style = MaterialTheme.typography.bodySmall)
            Button(enabled = enabled, onClick = onSave) { Text(stringResource(R.string.save_profile)) }
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
