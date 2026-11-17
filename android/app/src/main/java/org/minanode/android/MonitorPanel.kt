package org.minanode.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun number(value: Double?, digits: Int = 1, locale: Locale) = value?.let { String.format(locale, "%.${digits}f", it) } ?: "—"

@Composable
fun MonitorPanel(state: MonitorState, producer: Boolean, network: String, explorer: String, running: Boolean, titleSwipe: Modifier = Modifier) {
    var window by rememberSaveable(stateSaver = ChartWindowSaver) { mutableStateOf(ChartWindow()) }
    var autoscale by rememberSaveable { mutableStateOf(true) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ProducedBlock?>(null) }
    val lastPage = BlockPages.count(state.blocks.size) - 1
    LaunchedEffect(lastPage) { page = page.coerceAtMost(lastPage) }
    selected?.let { old -> BlockDetail(state.blocks.firstOrNull { it.id == old.id } ?: old, state, network, explorer, autoscale, running) { selected = null } }
    val viewport = ResourceViewport.select(state.samples, window)
    val bounds = viewport.bounds
    val visible = viewport.samples
    val gestures = chartGestures { zoom, pan, anchor ->
        window = window.transform(viewport.recording.first, viewport.recording.last, zoom, pan, anchor)
    }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    fun n(value: Double?, digits: Int = 1) = number(value, digits, locale)
    val mib = stringResource(R.string.unit_mib)
    val gib = stringResource(R.string.unit_gib)
    val latest = state.samples.lastOrNull()
    Text(stringResource(R.string.resources), titleSwipe.fillMaxWidth(), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.resource_current, n(latest?.ramMiB), mib, n(latest?.cpu)), titleSwipe.fillMaxWidth())
    Text(stringResource(R.string.cpu_help), titleSwipe.fillMaxWidth(), style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(if (autoscale) R.string.autoscale else R.string.maximum_scale), Modifier.weight(1f))
        Switch(autoscale, { autoscale = it })
        TextButton(onClick = { window = ChartWindow() }) { Text(stringResource(R.string.chart_live)) }
    }
    Text(stringResource(R.string.chart_gestures), style = MaterialTheme.typography.bodySmall)
    SignalChart(stringResource(R.string.ram_chart, mib), visible, if (LocalDarkTheme.current) Color(0xFF53D9DB) else Color(0xFF00696D), gestures,
        if (autoscale) null else state.ramCapacityMiB, bounds) { it.ramMiB }
    SignalChart(stringResource(R.string.cpu_chart), visible, if (LocalDarkTheme.current) Color(0xFFFFD75A) else Color(0xFF806000), gestures,
        if (autoscale) null else state.cpuCapacity, bounds) { it.cpu }
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
        BlockBars(state.blocks, timings = false)
        BlockBars(state.blocks, timings = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = page > 0, onClick = { page-- }) { Text(stringResource(R.string.previous_page)) }
            Text(stringResource(R.string.block_page, page.coerceAtMost(lastPage) + 1, lastPage + 1))
            TextButton(enabled = page < lastPage, onClick = { page++ }) { Text(stringResource(R.string.next_page)) }
        }
        BlockPages.items(state.blocks, page).forEach { block ->
            val color = blockColor(block.status)
            Card(Modifier.fillMaxWidth().clickable { selected = block }, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.block_heading, block.height ?: "—", block.slot, productionStatus(context, block.status)),
                        color = color, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.block_metrics, block.tx, block.zkapps, n(block.proofSeconds, 3)))
                    ExplorerButton(block, network, explorer)
                }
            }
        }
    }
}

@Composable
private fun SignalChart(title: String, samples: List<ResourceSample>, color: Color, modifier: Modifier = Modifier,
    fixedMaximum: Double? = null, bounds: LongRange? = null, markers: List<Long> = emptyList(), valueOf: (ResourceSample) -> Double?) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    fun n(value: Double?, digits: Int = 1) = number(value, digits, locale)
    Text(title, style = MaterialTheme.typography.labelMedium)
    val values = samples.map { valueOf(it)?.takeIf { value -> value.isFinite() } }
    if (values.all { it == null }) {
        Text(stringResource(R.string.no_measurements_period), style = MaterialTheme.typography.bodySmall)
        return
    }
    val maximum = fixedMaximum?.takeIf { it.isFinite() && it > 0 } ?: ResourceChartScale.maximum(values)
    val start = bounds?.first ?: samples.first().timeMs
    val end = bounds?.last ?: samples.last().timeMs
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
    Canvas(modifier.fillMaxWidth().height(180.dp).semantics {
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
        markers.filter { it in start..end }.forEach { time ->
            val x = left + ResourceChartScale.timeFraction(time, start, end) * (right - left)
            drawLine(color.copy(alpha = 0.5f), Offset(x, top), Offset(x, bottom), 1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        }
        val path = Path(); var connected = false
        reduced.forEach { sample ->
            val value = valueOf(sample)?.takeIf { it.isFinite() }
            if (value == null) connected = false else {
                val x = left + ResourceChartScale.timeFraction(sample.timeMs, start, end) * (right - left)
                val y = bottom - (value / maximum * (bottom - top)).toFloat().coerceIn(0f, bottom - top)
                if (connected) path.lineTo(x, y) else path.moveTo(x, y)
                if (!connected) drawCircle(color, 2.dp.toPx(), Offset(x, y))
                connected = true
            }
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
    Text(stringResource(R.string.chart_axis_help), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun blockColor(status: String): Color = when (status) {
    "Canonical" -> if (LocalDarkTheme.current) Color(0xFF53D9BC) else Color(0xFF006A50)
    "Orphaned", "Discarded", "Missed" -> MaterialTheme.colorScheme.error
    else -> if (LocalDarkTheme.current) Color(0xFFFFD75A) else Color(0xFF805000)
}

@Composable
private fun ExplorerButton(block: ProducedBlock, network: String, explorer: String) {
    val context = LocalContext.current
    val url = ExplorerLink.url(explorer, network, block.hash)
    val scope = rememberCoroutineScope()
    var loading by remember(block.id) { mutableStateOf(false) }
    var failure by remember(block.id) { mutableStateOf<UiText?>(null) }
    failure?.let { message ->
        AlertDialog(onDismissRequest = { failure = null }, title = { Text(stringResource(R.string.explorer)) },
            text = { Text(message.resolve(context)) }, confirmButton = { TextButton(onClick = { failure = null }) { Text(stringResource(R.string.close)) } })
    }
    Column {
        TextButton(enabled = url != null, onClick = {
            url?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
        }) { Text(stringResource(if (url == null) R.string.no_block_hash else R.string.open_explorer)) }
        block.height?.let { height ->
            TextButton(enabled = !loading, onClick = {
                loading = true
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { runCatching { NodeApi.bestChainHash(height, network) } }
                        result.onSuccess { hash ->
                            val canonicalUrl = ExplorerLink.url(explorer, network, hash)
                            if (canonicalUrl == null) failure = uiText(R.string.error_explorer_open)
                            else runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(canonicalUrl))) }
                                .onFailure { failure = uiText(R.string.error_explorer_open) }
                        }.onFailure { failure = if (it is UiException) it.text else uiText(R.string.canonical_lookup_failed) }
                    } finally { loading = false }
                }
            }) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.open_canonical_height, height))
            }
        }
    }
}

/** Each column is a block; scrolling these plots consumes the pager's horizontal gesture. */
@Composable
private fun BlockBars(blocks: List<ProducedBlock>, timings: Boolean) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    val ordered = blocks.asReversed()
    val cyan = if (LocalDarkTheme.current) Color(0xFF53D9DB) else Color(0xFF00696D)
    val magenta = if (LocalDarkTheme.current) Color(0xFFF380E7) else Color(0xFF9F187E)
    val yellow = if (LocalDarkTheme.current) Color(0xFFFFD75A) else Color(0xFF806000)
    val green = if (LocalDarkTheme.current) Color(0xFF53D9BC) else Color(0xFF006A50)
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val colors = if (timings) listOf(yellow, green, gray.copy(alpha = 0.65f)) else listOf(cyan, magenta)
    val segments = ordered.map { b -> if (timings) listOf(b.prepMs?.div(1000), b.proofSeconds, b.delaySeconds)
        else if (b.hash.isBlank()) listOf(null, null) else listOf((b.tx - b.zkapps).coerceAtLeast(0).toDouble(), b.zkapps.toDouble()) }
    val maximum = ResourceChartScale.maximum(segments.map { values -> values.filterNotNull().takeIf { it.isNotEmpty() }?.sum() })
    val statusColors = ordered.map { blockColor(it.status) }
    Text(stringResource(if (timings) R.string.block_timings_chart else R.string.block_transactions_chart), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val labels = if (timings) listOf(R.string.preparation_seconds, R.string.proof_seconds, R.string.delay_seconds)
            else listOf(R.string.ordinary_transactions, R.string.zkapps)
        labels.forEachIndexed { i, label -> Text(stringResource(label), color = colors[i], style = MaterialTheme.typography.labelSmall) }
    }
    if (blocks.isEmpty()) { Text(stringResource(R.string.no_production_history)); return }
    val paint = remember(textColor) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor.toArgb(); typeface = android.graphics.Typeface.MONOSPACE
    } }
    val scroll = rememberScrollState()
    var initial by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(scroll.maxValue) { if (!initial && scroll.maxValue > 0) { scroll.scrollTo(scroll.maxValue); initial = true } }
    val description = stringResource(if (timings) R.string.block_timings_chart else R.string.block_transactions_chart)
    Row(Modifier.fillMaxWidth()) {
        // Keep the Y axis visible while the block columns scroll underneath the fingers.
        Canvas(Modifier.width(48.dp).height(210.dp)) {
            paint.textSize = 10.sp.toPx(); paint.textAlign = android.graphics.Paint.Align.RIGHT
            (0..4).forEach { i ->
                val y = 12.dp.toPx() + (1 - i / 4f) * 150.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(String.format(locale, if (timings) "%.1f" else "%.0f", maximum * i / 4), size.width - 4.dp.toPx(), y, paint)
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val width = maxOf(maxWidth, (ordered.size * 68).dp)
            Canvas(Modifier.horizontalScroll(scroll).width(width).height(210.dp).semantics { contentDescription = description }) {
                paint.textSize = 10.sp.toPx(); paint.textAlign = android.graphics.Paint.Align.CENTER
                val column = size.width / ordered.size
                val top = 12.dp.toPx(); val bottom = 162.dp.toPx()
                (0..4).forEach { i ->
                    val y = bottom - i / 4f * (bottom - top)
                    drawLine(textColor.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y))
                }
                ordered.forEachIndexed { index, block ->
                    val x = column * (index + 0.5f)
                    var base = bottom
                    segments[index].forEachIndexed { part, value ->
                        if (value != null) {
                            val height = (value / maximum * (bottom - top)).toFloat()
                            drawRect(colors[part], Offset(x - column * 0.33f, base - height), androidx.compose.ui.geometry.Size(column * 0.66f, height))
                            base -= height
                        }
                    }
                    paint.color = statusColors[index].toArgb()
                    drawContext.canvas.nativeCanvas.drawText(block.height?.toString() ?: "s${block.slot}", x, 181.dp.toPx(), paint)
                    paint.color = textColor.toArgb()
                    val total = segments[index].filterNotNull().takeIf { it.isNotEmpty() }?.sum()
                    drawContext.canvas.nativeCanvas.drawText(total?.let { String.format(locale, if (timings) "%.2f" else "%.0f", it) } ?: "—", x, 202.dp.toPx(), paint)
                }
            }
        }
    }
    Text(stringResource(R.string.block_chart_help), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun BlockDetail(block: ProducedBlock, state: MonitorState, network: String, explorer: String, autoscale: Boolean, running: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    fun n(value: Double?, digits: Int = 1) = number(value, digits, locale)
    val bounds = block.resourcePeriod()
    val recorded = ((state.productionSamples[block.id] ?: emptyList()) + state.samples)
        .associateBy { it.timeMs }.values.sortedBy { it.timeMs }
    var detailWindow by rememberSaveable(block.id, stateSaver = ChartWindowSaver) { mutableStateOf(ChartWindow()) }
    val viewport = bounds?.let { detailWindow.bounds(it.first, it.last) }
    val samples = recorded.filter { viewport != null && it.timeMs in viewport }
    val gestures = chartGestures { zoom, pan, anchor ->
        bounds?.let { detailWindow = detailWindow.transform(it.first, it.last, zoom, pan, anchor) }
    }
    val markers = listOfNotNull(block.proofStartMs, block.proofEndMs, block.committedMs, block.discardedMs)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FullscreenDialogWindow()
        Surface(Modifier.fillMaxSize().windowInsetsPadding(appContentInsets())) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("MINA / ANDROID", Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    SlotIndicator(state, running)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.block_detail), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.block_heading, block.height ?: "—", block.slot, productionStatus(context, block.status)), color = blockColor(block.status))
                    Text(stringResource(R.string.block_metrics, block.tx, block.zkapps, n(block.proofSeconds, 3)))
                    Text(stringResource(R.string.block_timing, n(block.prepMs), n(block.delaySeconds, 3)))
                    Text(stringResource(R.string.completed_snarks, block.snarks?.toString() ?: "—"))
                    block.discardReason?.let { Text(stringResource(R.string.discard_reason, it), color = blockColor(block.status)) }
                    SelectionContainer { Text(block.hash.ifBlank { stringResource(R.string.no_block_hash) }, style = MaterialTheme.typography.bodySmall) }
                    ExplorerButton(block, network, explorer)
                    Text(stringResource(R.string.sampled_peaks, samples.size, n(samples.mapNotNull { it.cpu }.maxOrNull()), n(samples.mapNotNull { it.ramMiB }.maxOrNull()), stringResource(R.string.unit_mib)))
                    Text(stringResource(R.string.detail_samples_help), style = MaterialTheme.typography.bodySmall)
                    if (state.resourceArchiveFailed) Text(stringResource(R.string.resource_archive_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { detailWindow = ChartWindow() }) { Text(stringResource(R.string.reset_zoom)) }
                    SignalChart(stringResource(R.string.ram_chart, stringResource(R.string.unit_mib)), samples, Color(0xFF29B6B8), gestures,
                        fixedMaximum = if (autoscale) null else state.ramCapacityMiB, bounds = viewport, markers = markers) { it.ramMiB }
                    SignalChart(stringResource(R.string.cpu_chart), samples, Color(0xFFC99B10), gestures,
                        fixedMaximum = if (autoscale) null else state.cpuCapacity, bounds = viewport, markers = markers) { it.cpu }
                    Text(stringResource(R.string.detail_markers), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun chartGestures(onTransform: (Float, Float, Float) -> Unit): Modifier {
    val transform by rememberUpdatedState(onTransform)
    return Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var horizontal = 0f; var vertical = 0f; var claimed = false
            do {
                val event = awaitPointerEvent()
                val pan = event.calculatePan()
                horizontal += pan.x; vertical += pan.y
                if (event.changes.count { it.pressed } >= 2 ||
                    (!claimed && kotlin.math.abs(horizontal) > viewConfiguration.touchSlop && kotlin.math.abs(horizontal) > kotlin.math.abs(vertical))) claimed = true
                if (claimed && event.changes.any { it.pressed }) {
                    transform(event.calculateZoom(), pan.x / size.width.coerceAtLeast(1), event.calculateCentroid().x / size.width.coerceAtLeast(1))
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
