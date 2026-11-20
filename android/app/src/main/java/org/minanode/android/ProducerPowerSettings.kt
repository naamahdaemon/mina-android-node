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
    fun syncSeconds() = prefs.getInt("sync-seconds", ProducerPowerPolicy.DEFAULT_SYNC_SECONDS).coerceIn(60, 7200)
    fun timings() = PowerTimings(PowerTiming.entries.associateWith {
        prefs.getInt(it.key, it.defaultSeconds).coerceIn(it.range)
    })
    fun save(enabled: Boolean, seconds: Int, syncSeconds: Int, timings: PowerTimings) {
        require(seconds in 60..7200)
        require(syncSeconds in 60..7200)
        val editor = prefs.edit().putBoolean("enabled", enabled).putInt("wake-seconds", seconds).putInt("sync-seconds", syncSeconds)
        PowerTiming.entries.forEach { editor.putInt(it.key, timings[it]) }
        editor.apply()
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
    var syncMinutes by rememberSaveable { mutableStateOf((settings.syncSeconds() / 60).toString()) }
    var savedSyncSeconds by remember { mutableIntStateOf(settings.syncSeconds()) }
    val parsedSync = syncMinutes.toIntOrNull()?.takeIf { it in 1..120 }?.times(60)
    var timingTexts by rememberSaveable { mutableStateOf(PowerTiming.entries.map { settings.timings()[it].toString() }) }
    var savedTimings by remember { mutableStateOf(settings.timings()) }
    val parsedTimings = timingTexts.mapIndexed { index, text -> text.toIntOrNull()?.takeIf { it in PowerTiming.entries[index].range } }
    val validTimings = parsedTimings.all { it != null }
    val timings = if (validTimings) PowerTimings(PowerTiming.entries.mapIndexed { index, key -> key to requireNotNull(parsedTimings[index]) }.toMap()) else null
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
            OutlinedTextField(syncMinutes, { syncMinutes = it }, Modifier.fillMaxWidth(), enabled = enabled && selected,
                label = { Text(stringResource(R.string.power_sync_interval)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = selected && parsedSync == null,
                supportingText = { Text(stringResource(R.string.power_sync_range)) })
            Text(stringResource(R.string.power_timing_guards), style = MaterialTheme.typography.titleMedium)
            PowerTiming.entries.forEachIndexed { index, key ->
                val label = when (key) {
                    PowerTiming.AWAKE -> R.string.power_timing_awake
                    PowerTiming.STABLE -> R.string.power_timing_stable
                    PowerTiming.GRACE -> R.string.power_timing_grace
                    PowerTiming.GAP -> R.string.power_timing_gap
                    PowerTiming.UNFINISHED -> R.string.power_timing_unfinished
                    PowerTiming.SCHEDULE -> R.string.power_timing_schedule
                    PowerTiming.TIP -> R.string.power_timing_tip
                }
                OutlinedTextField(timingTexts[index], { value ->
                    timingTexts = timingTexts.toMutableList().also { it[index] = value }
                }, Modifier.fillMaxWidth(), enabled = enabled && selected,
                    label = { Text(stringResource(label)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = selected && parsedTimings[index] == null,
                    supportingText = { Text(stringResource(R.string.power_timing_range,
                        key.range.first, key.range.last, key.defaultSeconds)) })
            }
            Text(stringResource(R.string.power_timing_help), style = MaterialTheme.typography.bodySmall)
            Button(enabled = enabled && (!selected || (parsed != null && parsedSync != null && validTimings)) &&
                (selected != savedEnabled || (selected && (parsed != savedSeconds || parsedSync != savedSyncSeconds || timings != savedTimings))), onClick = {
                    val lead = parsed ?: savedSeconds
                    val sync = parsedSync ?: savedSyncSeconds
                    val guards = timings ?: savedTimings
                    settings.save(selected, lead, sync, guards)
                    savedEnabled = selected; savedSeconds = lead; savedSyncSeconds = sync; savedTimings = guards
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
