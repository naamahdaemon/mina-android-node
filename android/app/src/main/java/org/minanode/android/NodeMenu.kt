package org.minanode.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

enum class NodePage(val title: Int) { NODE(R.string.node), SETTINGS(R.string.settings), MONITOR(R.string.monitor), JOURNAL(R.string.journal) }

@Composable
fun NodeMenu(selected: NodePage, dashboardAvailable: Boolean, onSelect: (NodePage) -> Unit,
    onDashboard: () -> Unit, onAndroidSettings: () -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(enabled = scroll.canScrollBackward,
                modifier = Modifier.semantics { contentDescription = context.getString(R.string.menu_previous) },
                onClick = { scope.launch {
                    scroll.animateScrollTo((scroll.value - maxOf(1, scroll.viewportSize * 3 / 4)).coerceAtLeast(0))
                } }) { Text("‹", fontSize = 32.sp) }
            Row(Modifier.weight(1f).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NodePage.entries.forEach { page ->
                    FilterChip(selected == page, { onSelect(page) }, label = { Text(stringResource(page.title)) })
                }
                AssistChip(enabled = dashboardAvailable, onClick = onDashboard, label = { Text(stringResource(R.string.dashboard)) })
                AssistChip(onClick = onAndroidSettings, label = { Text(stringResource(R.string.android_settings)) })
            }
            IconButton(enabled = scroll.canScrollForward,
                modifier = Modifier.semantics { contentDescription = context.getString(R.string.menu_next) },
                onClick = { scope.launch {
                    scroll.animateScrollTo((scroll.value + maxOf(1, scroll.viewportSize * 3 / 4)).coerceAtMost(scroll.maxValue))
                } }) { Text("›", fontSize = 32.sp) }
        }
        if (scroll.maxValue > 0) {
            val primary = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val total = scroll.viewportSize.toFloat() + scroll.maxValue
                val thumb = (size.width * scroll.viewportSize / total).coerceIn(minOf(24.dp.toPx(), size.width), size.width)
                val position = (size.width - thumb) * scroll.value / scroll.maxValue
                drawRoundRect(primary.copy(alpha = 0.18f), cornerRadius = CornerRadius(2.dp.toPx()))
                drawRoundRect(primary, topLeft = Offset(position, 0f), size = Size(thumb, size.height), cornerRadius = CornerRadius(2.dp.toPx()))
            }
            Text(stringResource(R.string.menu_swipe),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
