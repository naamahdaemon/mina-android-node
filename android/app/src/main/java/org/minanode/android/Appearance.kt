package org.minanode.android

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

class MinaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val manager = requireNotNull(getSystemService(LocaleManager::class.java))
        if (manager.applicationLocales.isEmpty) manager.applicationLocales = LocaleList.forLanguageTags("en")
    }
}

class AppearancePreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    fun dark() = prefs.getBoolean("dark", true)
    fun setDark(value: Boolean) { prefs.edit().putBoolean("dark", value).apply() }
    fun productionAlertLeadSeconds() = prefs.getInt("production-alert-lead-seconds", 60).coerceIn(1, 3600)
    fun productionAlertReminderSeconds(): Int? = prefs.getInt("production-alert-reminder-seconds", 0)
        .takeIf { it in 1 until productionAlertLeadSeconds() }
    fun setProductionAlertTiming(lead: Int, reminder: Int?) {
        require(lead in 1..3600)
        require(reminder == null || reminder in 1 until lead)
        prefs.edit().putInt("production-alert-lead-seconds", lead)
            .putInt("production-alert-reminder-seconds", reminder ?: 0).apply()
    }
    fun fullscreen() = prefs.getBoolean("fullscreen", false)
    fun setFullscreen(value: Boolean) { prefs.edit().putBoolean("fullscreen", value).apply() }
    fun hamburger() = prefs.getBoolean("hamburger", false)
    fun setHamburger(value: Boolean) { prefs.edit().putBoolean("hamburger", value).apply() }
    fun explorer() = prefs.getString("explorer", "https://minascan.io/{network}/block/{hash}/txs")!!
    fun setExplorer(value: String) { prefs.edit().putString("explorer", value).apply() }
    fun language(): String = requireNotNull(context.getSystemService(LocaleManager::class.java))
        .applicationLocales.get(0)?.language?.takeIf { it in listOf("en", "fr") } ?: "en"
    fun setLanguage(value: String) {
        require(value in listOf("en", "fr"))
        if (language() != value) requireNotNull(context.getSystemService(LocaleManager::class.java))
            .applicationLocales = LocaleList.forLanguageTags(value)
    }
    fun activityTheme() = if (dark()) android.R.style.Theme_Material_NoActionBar
        else android.R.style.Theme_Material_Light_NoActionBar
}

val LocalFullscreen = staticCompositionLocalOf { false }

val LocalDarkTheme = staticCompositionLocalOf { true }

@Composable
fun MinaTheme(dark: Boolean, fullscreen: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(primary = Color(0xFFBBAAFF),
        background = Color(0xFF101018), surface = Color(0xFF1C1C29))
    else lightColorScheme(primary = Color(0xFF6045A5), background = Color(0xFFFAF8FF), surface = Color(0xFFF2EDF8))
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalFullscreen provides fullscreen) { MaterialTheme(colorScheme = colors, content = content) }
}

@Composable
fun AppearanceSettings(language: String, dark: Boolean, enabled: Boolean, fullscreen: Boolean, alertLeadSeconds: Int, alertReminderSeconds: Int?,
    onLanguage: (String) -> Unit, onDark: (Boolean) -> Unit, onFullscreen: (Boolean) -> Unit, onAlertTiming: (Int, Int?) -> Unit) {
    var leadInput by rememberSaveable(alertLeadSeconds) { mutableStateOf(alertLeadSeconds.toString()) }
    var reminderInput by rememberSaveable(alertReminderSeconds) { mutableStateOf(alertReminderSeconds?.toString().orEmpty()) }
    val parsedLead = leadInput.toIntOrNull()?.takeIf { it in 1..3600 }
    val reminderBlank = reminderInput.isBlank()
    val parsedReminder = reminderInput.toIntOrNull()
    val reminderValid = reminderBlank || (parsedReminder != null && parsedLead != null && parsedReminder in 1 until parsedLead)
    val darkLabel = stringResource(R.string.dark_theme)
    val fullscreenLabel = stringResource(R.string.fullscreen)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.application_settings), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("en" to R.string.language_english, "fr" to R.string.language_french).forEach { (code, label) ->
                    FilterChip(language == code, { onLanguage(code) }, enabled = enabled, label = { Text(stringResource(label)) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dark_theme))
                    Text(stringResource(if (dark) R.string.theme_dark_selected else R.string.theme_light_selected),
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = dark, onCheckedChange = onDark, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = darkLabel })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.fullscreen))
                    Text(stringResource(R.string.fullscreen_help), style = MaterialTheme.typography.bodySmall)
                }
                Switch(fullscreen, onFullscreen, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = fullscreenLabel })
            }
            OutlinedTextField(leadInput, { leadInput = it }, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
                label = { Text(stringResource(R.string.production_alert_lead)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = parsedLead == null,
                supportingText = { Text(stringResource(R.string.production_alert_lead_range)) })
            Text(stringResource(R.string.production_alert_lead_current, alertLeadSeconds), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(reminderInput, { reminderInput = it }, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
                label = { Text(stringResource(R.string.production_alert_reminder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = !reminderValid,
                supportingText = { Text(stringResource(R.string.production_alert_reminder_help)) })
            Text(if (alertReminderSeconds == null) stringResource(R.string.production_alert_reminder_off)
                else stringResource(R.string.production_alert_reminder_current, alertReminderSeconds), style = MaterialTheme.typography.bodySmall)
            Button(enabled = enabled && parsedLead != null && reminderValid &&
                (parsedLead != alertLeadSeconds || (if (reminderBlank) null else parsedReminder) != alertReminderSeconds),
                onClick = { parsedLead?.let { onAlertTiming(it, if (reminderBlank) null else parsedReminder) } }) { Text(stringResource(R.string.save)) }
            Text(stringResource(R.string.production_alert_help), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.appearance_help), style = MaterialTheme.typography.bodySmall)
        }
    }
}
