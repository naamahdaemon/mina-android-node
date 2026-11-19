package org.minanode.android

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.json.JSONObject

data class MonitorChartSettings(val autoscale: Boolean = true, val window: ChartWindow = ChartWindow()) {
    fun encode(): String = JSONObject().put("version", 1).put("autoscale", autoscale)
        .put("span_ms", window.spanMs).put("offset_ms", window.offsetMs)
        .put("end_ms", window.endTimeMs ?: JSONObject.NULL).toString()

    companion object {
        private const val HISTORY_MS = 6 * 3600_000L
        fun decode(text: String?): MonitorChartSettings = runCatching {
            val value = JSONObject(requireNotNull(text))
            require(value.getInt("version") == 1)
            MonitorChartSettings(value.optBoolean("autoscale", true), ChartWindow(
                value.optLong("span_ms", 0).coerceIn(0, HISTORY_MS),
                value.optLong("offset_ms", 0).coerceIn(0, HISTORY_MS),
                value.optLong("end_ms", Long.MIN_VALUE).takeIf { it >= 0 }))
        }.getOrDefault(MonitorChartSettings())
    }
}

class MonitorChartPreferences(context: Context, network: String) {
    private val prefs = context.getSharedPreferences("monitor-chart-view", Context.MODE_PRIVATE)
    private val key = "view-$network"
    fun load() = MonitorChartSettings.decode(prefs.getString(key, null))
    fun save(value: MonitorChartSettings) {
        val text = value.encode()
        if (prefs.getString(key, null) != text) prefs.edit().putString(key, text).apply()
    }
}

/** Disk-backed per-network view state, not tied to a pager page's composition. */
@Composable
fun rememberMonitorChartSettings(network: String): MutableState<MonitorChartSettings> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val prefs = remember(context, network) { MonitorChartPreferences(context, network) }
    val state = remember(prefs) { mutableStateOf(prefs.load()) }
    // Avoid a disk write for every pinch event; flush immediately on page exit/ON_STOP.
    LaunchedEffect(state.value, prefs) { delay(250); prefs.save(state.value) }
    DisposableEffect(owner, prefs, state) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) prefs.save(state.value) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); prefs.save(state.value) }
    }
    return state
}
