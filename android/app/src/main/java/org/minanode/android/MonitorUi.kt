package org.minanode.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.net.URI
import java.util.Locale

object ExplorerLink {
    const val DEFAULT = "https://minascan.io/{network}/block/{hash}/txs"
    fun valid(template: String): Boolean = runCatching {
        val uri = URI(template.replace("{network}", "mainnet").replace("{hash}", "3Nexample"))
        "{hash}" in template && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
    fun url(template: String, network: String, hash: String): String? {
        if (!valid(template) || network !in listOf("mainnet", "devnet") || !hash.matches(Regex("3N[1-9A-HJ-NP-Za-km-z]{20,}"))) return null
        return template.replace("{network}", network).replace("{hash}", hash)
    }
}

@Composable
fun NavigationSettings(preferences: AppearancePreferences, hamburger: Boolean, onHamburger: (Boolean) -> Unit, onAndroidSettings: () -> Unit) {
    var custom by rememberSaveable { mutableStateOf(preferences.explorer() != ExplorerLink.DEFAULT) }
    var template by rememberSaveable { mutableStateOf(preferences.explorer()) }
    var saved by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.hamburger_menu), Modifier.weight(1f))
                Switch(hamburger, onHamburger)
            }
            Text(stringResource(R.string.explorer), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!custom, { custom = false; template = ExplorerLink.DEFAULT; preferences.setExplorer(template) }, label = { Text("Minascan") })
                FilterChip(custom, { custom = true; saved = false }, label = { Text(stringResource(R.string.custom_explorer)) })
            }
            if (custom) {
                OutlinedTextField(template, { template = it; saved = false }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.explorer_template)) }, isError = !ExplorerLink.valid(template))
                Text(stringResource(R.string.explorer_help), style = MaterialTheme.typography.bodySmall)
                Button(enabled = ExplorerLink.valid(template), onClick = { preferences.setExplorer(template); saved = true }) { Text(stringResource(if (saved) R.string.explorer_saved else R.string.save)) }
            }
            TextButton(onClick = onAndroidSettings) { Text(stringResource(R.string.android_settings)) }
        }
    }
}

@Composable
fun SlotIndicator(state: MonitorState, enabled: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(enabled, state.nextSlotTimeMs) {
        while (enabled) { now = System.currentTimeMillis(); delay(1000) }
    }
    val target = state.nextSlotTimeMs
    val remaining = target?.minus(now)
    val fresh = enabled && target != null && remaining != null && remaining >= 0
    val seconds = ((remaining ?: 0) / 1000).coerceAtLeast(0)
    val color = when {
        !fresh -> MaterialTheme.colorScheme.onSurfaceVariant
        seconds <= 60 -> MaterialTheme.colorScheme.error
        seconds <= 300 -> if (LocalDarkTheme.current) Color(0xFFFFC45A) else Color(0xFF805000)
        else -> if (LocalDarkTheme.current) Color(0xFF53D9BC) else Color(0xFF006A55)
    }
    Column(Modifier.widthIn(max = 185.dp).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
        Text(if (fresh) stringResource(R.string.header_slot, state.nextSlot ?: "—") else stringResource(R.string.header_no_slot),
            style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        if (fresh) {
            Text(String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60),
                style = MaterialTheme.typography.titleSmall, color = color)
            LinearProgressIndicator(progress = { (1f - (remaining!! / 3_600_000f)).coerceIn(0f, 1f) },
                modifier = Modifier.width(140.dp).height(3.dp), color = color)
        }
    }
}

/** A real-time viewport, shared by RAM/CPU. Offset zero follows the newest sample. */
data class ChartWindow(val spanMs: Long = 0, val offsetMs: Long = 0, val endTimeMs: Long? = null) {
    fun bounds(first: Long, last: Long): LongRange {
        if (last <= first) return first..first
        val available = (last - first).coerceAtLeast(1)
        val span = if (spanMs == 0L) available else spanMs.coerceIn(minOf(5000, available), available)
        // A restored view can outlive its recording (restart, retention, clock correction).
        // Follow the latest data in that case, retaining the selected zoom span.
        val requestedEnd = endTimeMs?.takeIf { it in first..last } ?: if (endTimeMs != null) last
            else last - offsetMs.coerceIn(0, (available - span).coerceAtLeast(0))
        val end = requestedEnd.coerceIn(first, last)
            .coerceAtLeast(first + span).coerceAtMost(last)
        return (end - span).coerceAtLeast(first)..end
    }
    fun transform(first: Long, last: Long, zoom: Float, panFraction: Float, anchor: Float): ChartWindow {
        if (last - first < 5000 || (zoom == 1f && panFraction == 0f) || !zoom.isFinite() || !panFraction.isFinite() || !anchor.isFinite()) return this
        val old = bounds(first, last)
        val available = (last - first).coerceAtLeast(1)
        val span = ((old.last - old.first).toDouble() / zoom.coerceIn(0.1f, 10f).toDouble()).toLong().coerceIn(minOf(5000, available), available)
        // Absolute epoch milliseconds must stay Double/Long, never Float (minutes of rounding at today's dates).
        val position = anchor.coerceIn(0f, 1f).toDouble()
        val center = old.first.toDouble() + (old.last - old.first).toDouble() * position
        val end = (center + span.toDouble() * (1.0 - position) - panFraction.toDouble() * (old.last - old.first)).toLong()
            .coerceIn(first + span, last.coerceAtLeast(first + span))
        return ChartWindow(span, last - end, end.takeIf { it < last })
    }
}

object BlockPages {
    const val SIZE = 5
    fun count(size: Int) = maxOf(1, (size + SIZE - 1) / SIZE)
    fun <T> items(blocks: List<T>, page: Int): List<T> = blocks.drop(page.coerceIn(0, count(blocks.size) - 1) * SIZE).take(SIZE)
}

// Bundle-compatible state survives disposal of an off-screen pager page and Activity recreation.
val ChartWindowSaver = Saver<ChartWindow, LongArray>(
    save = { longArrayOf(it.spanMs, it.offsetMs, it.endTimeMs ?: Long.MIN_VALUE) },
    restore = { ChartWindow(it[0], it[1], it[2].takeUnless { value -> value == Long.MIN_VALUE }) },
)

/** Ignore the startup interval with no measurements when defining the navigable recording. */
data class ResourceViewport(val recording: LongRange, val bounds: LongRange, val samples: List<ResourceSample>) {
    companion object {
        fun select(samples: List<ResourceSample>, window: ChartWindow): ResourceViewport {
            val ordered = samples.sortedBy { it.timeMs }
            fun measured(sample: ResourceSample) = sample.cpu?.isFinite() == true || sample.ramMiB?.isFinite() == true
            val first = ordered.firstOrNull(::measured)?.timeMs
            val last = ordered.lastOrNull(::measured)?.timeMs
            if (first == null || last == null) return ResourceViewport(0L..0L, 0L..0L, emptyList())
            val bounds = window.bounds(first, last)
            return ResourceViewport(first..last, bounds, ordered.filter { it.timeMs in bounds })
        }
    }
}
