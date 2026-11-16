package org.minanode.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun number(value: Double?, digits: Int = 1, locale: Locale) = value?.let { String.format(locale, "%.${digits}f", it) } ?: "—"

@Composable
fun MonitorPanel(state: MonitorState, producer: Boolean) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    fun n(value: Double?, digits: Int = 1) = number(value, digits, locale)
    val mib = stringResource(R.string.unit_mib)
    val gib = stringResource(R.string.unit_gib)
    val latest = state.samples.lastOrNull()
    Text(stringResource(R.string.resources), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.resource_current, n(latest?.ramMiB), mib, n(latest?.cpu)))
    Text(stringResource(R.string.cpu_help), style = MaterialTheme.typography.bodySmall)
    SignalChart(stringResource(R.string.ram_chart, mib), state.samples, if (LocalDarkTheme.current) Color(0xFF53D9DB) else Color(0xFF00696D)) { it.ramMiB }
    SignalChart(stringResource(R.string.cpu_chart), state.samples, if (LocalDarkTheme.current) Color(0xFFFFD75A) else Color(0xFF806000)) { it.cpu }
    Text(stringResource(R.string.storage_current, n(state.usedMiB), mib, n(state.freeGiB), gib))
    Text(stringResource(R.string.battery_current, state.battery?.toString() ?: "—", if (state.charging) stringResource(R.string.charging) else "", n(state.temperature)))
    if (state.thermal >= 3) Text(stringResource(R.string.thermal_warning), color = MaterialTheme.colorScheme.error)
    if (producer) {
        Text(stringResource(R.string.production), style = MaterialTheme.typography.titleLarge)
        val remaining = state.nextSlotTimeMs?.let { ((it - System.currentTimeMillis()) / 1000).coerceAtLeast(0) }
        Text(if (state.nextSlot == null) stringResource(R.string.no_next_slot) else stringResource(R.string.next_slot, state.nextSlot, remaining ?: "—"))
        Text(stringResource(R.string.production_totals, state.blocks.count { it.status == "Canonical" }, state.blocks.count { it.status == "Orphaned" }, state.blocks.size))
        state.producerError?.let { Text(it.resolve(context), color = MaterialTheme.colorScheme.error) }
        Text(stringResource(R.string.production_history_help), style = MaterialTheme.typography.bodySmall)
        state.blocks.take(50).forEach { block ->
            var expanded by remember(block.hash) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.block_heading, block.height ?: "—", block.slot, productionStatus(context, block.status)), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.block_metrics, block.tx, block.zkapps, n(block.proofSeconds, 3)))
                    if (expanded) {
                        Text(stringResource(R.string.block_timing, n(block.prepMs), n(block.delaySeconds, 3)))
                        val samples = state.samples.filter { sample -> block.proofStartMs != null && block.proofEndMs != null && sample.timeMs in block.proofStartMs..block.proofEndMs }
                        Text(stringResource(R.string.sampled_peaks, samples.size, n(samples.mapNotNull { it.cpu }.maxOrNull()), n(samples.mapNotNull { it.ramMiB }.maxOrNull()), mib))
                        Text(block.hash, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalChart(title: String, samples: List<ResourceSample>, color: Color, valueOf: (ResourceSample) -> Double?) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    fun n(value: Double?, digits: Int = 1) = number(value, digits, locale)
    Text(title, style = MaterialTheme.typography.labelMedium)
    val values = samples.map { valueOf(it)?.takeIf { value -> value.isFinite() } }
    if (values.all { it == null }) {
        Text(stringResource(R.string.waiting_measurements), style = MaterialTheme.typography.bodySmall)
        return
    }
    val maximum = ResourceChartScale.maximum(values)
    val start = samples.first().timeMs
    val end = samples.last().timeMs
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("HH:mm:ss", locale).withZone(ZoneId.systemDefault()) }
    val description = stringResource(R.string.chart_description, title, n(maximum), formatter.format(Instant.ofEpochMilli(start)), formatter.format(Instant.ofEpochMilli(end)))
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = textColor.copy(alpha = 0.2f)
    val paint = remember(textColor) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = textColor.toArgb()
        typeface = android.graphics.Typeface.MONOSPACE
    } }
    // Keep peaks when downsampling the six-hour window to a phone-sized chart.
    val reduced = samples.chunked(maxOf(1, (samples.size + 239) / 240)).map { bucket ->
        bucket.filter { valueOf(it)?.isFinite() == true }.maxByOrNull { valueOf(it)!! } ?: bucket.first()
    }
    Canvas(Modifier.fillMaxWidth().height(160.dp).semantics {
        contentDescription = description
    }) {
        paint.textSize = 11.sp.toPx()
        val labels = (0..4).map { n(maximum * it / 4, if (maximum < 10) 2 else if (maximum < 100) 1 else 0) }
        val left = labels.maxOf { paint.measureText(it) } + 10.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val fontHeight = paint.fontMetrics.descent - paint.fontMetrics.ascent
        val top = fontHeight / 2 + 2.dp.toPx()
        val bottom = size.height - fontHeight * 1.8f - 4.dp.toPx()
        if (right <= left || bottom <= top) return@Canvas
        val native = drawContext.canvas.nativeCanvas
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        labels.forEachIndexed { index, label ->
            val y = bottom - index / 4f * (bottom - top)
            drawLine(gridColor, Offset(left, y), Offset(right, y), 1.dp.toPx())
            native.drawText(label, left - 7.dp.toPx(), y - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2, paint)
        }
        drawLine(gridColor, Offset(left, top), Offset(left, bottom), 1.dp.toPx())
        val timeWidth = paint.measureText("00:00:00")
        val fractions = when {
            start == end || right - left < timeWidth * 2 + 12.dp.toPx() -> listOf(0.5f)
            right - left < timeWidth * 3 + 24.dp.toPx() -> listOf(0f, 1f)
            else -> listOf(0f, 0.5f, 1f)
        }
        fractions.forEach { fraction ->
            val time = start + ((end - start) * fraction.toDouble()).toLong()
            val x = left + fraction * (right - left)
            drawLine(gridColor, Offset(x, top), Offset(x, bottom), 1.dp.toPx())
            paint.textAlign = when (fraction) {
                0f -> android.graphics.Paint.Align.LEFT
                1f -> android.graphics.Paint.Align.RIGHT
                else -> android.graphics.Paint.Align.CENTER
            }
            native.drawText(formatter.format(Instant.ofEpochMilli(time)), x, size.height - paint.fontMetrics.descent, paint)
        }
        val path = Path(); var connected = false
        reduced.forEach { sample ->
            val value = valueOf(sample)?.takeIf { it.isFinite() }
            if (value == null) connected = false else {
                val x = left + ResourceChartScale.timeFraction(sample.timeMs, start, end) * (right - left)
                val y = bottom - (value / maximum * (bottom - top)).toFloat()
                if (connected) path.lineTo(x, y) else path.moveTo(x, y)
                if (!connected) drawCircle(color, 2.dp.toPx(), Offset(x, y))
                connected = true
            }
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
    Text(stringResource(R.string.chart_axis_help), style = MaterialTheme.typography.bodySmall)
}
