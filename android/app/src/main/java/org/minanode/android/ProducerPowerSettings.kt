package org.minanode.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class ProducerPowerPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("producer-power", Context.MODE_PRIVATE)
    fun enabled() = prefs.getBoolean("enabled", false)
    fun wakeSeconds() = prefs.getInt("wake-seconds", 600).coerceIn(60, 7200)
    fun save(enabled: Boolean, seconds: Int) {
        require(seconds in 60..7200)
        prefs.edit().putBoolean("enabled", enabled).putInt("wake-seconds", seconds).apply()
    }
}

@Composable
fun ProducerPowerSettings(enabled: Boolean) {
    val context = LocalContext.current
    val settings = remember { ProducerPowerPreferences(context) }
    var selected by rememberSaveable { mutableStateOf(settings.enabled()) }
    var seconds by rememberSaveable { mutableStateOf(settings.wakeSeconds().toString()) }
    var savedEnabled by remember { mutableStateOf(settings.enabled()) }
    var savedSeconds by remember { mutableIntStateOf(settings.wakeSeconds()) }
    val parsed = seconds.toIntOrNull()?.takeIf { it in 60..7200 }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.power_title), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.power_enable), Modifier.weight(1f))
                Switch(selected, { selected = it }, enabled = enabled)
            }
            Text(stringResource(R.string.power_help), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(seconds, { seconds = it }, Modifier.fillMaxWidth(), enabled = enabled && selected,
                label = { Text(stringResource(R.string.power_wake_lead)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = selected && parsed == null,
                supportingText = { Text(stringResource(R.string.power_wake_range)) })
            Button(enabled = enabled && (!selected || parsed != null) &&
                (selected != savedEnabled || (selected && parsed != savedSeconds)), onClick = {
                    val lead = parsed ?: savedSeconds
                    settings.save(selected, lead)
                    savedEnabled = selected; savedSeconds = lead
                    // Only contact an already-running service. Never launch a node or request a password here.
                    if (NodeState.flow.value.run in listOf(RunPhase.RUNNING, RunPhase.SUSPENDED)) {
                        context.startService(Intent(context, NodeService::class.java).setAction(NodeService.RECONFIGURE))
                    }
                }) { Text(stringResource(R.string.save)) }
            Text(stringResource(if (savedEnabled) R.string.power_applied_on else R.string.power_applied_off),
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")))
            }) { Text(stringResource(R.string.power_alarm_access)) }
            Text(stringResource(R.string.power_alarm_help), style = MaterialTheme.typography.bodySmall)
        }
    }
}
