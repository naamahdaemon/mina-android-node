package org.minanode.android

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

enum class NodePage(val title: Int) { NODE(R.string.node), MONITOR(R.string.monitor), DASHBOARD(R.string.dashboard), SETTINGS(R.string.settings), JOURNAL(R.string.journal) }

@Composable
fun NodeMenu(selected: NodePage, onSelect: (NodePage) -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.size(48.dp).semantics { contentDescription = context.getString(R.string.menu_previous) }
                .clickable(enabled = scroll.canScrollBackward, role = Role.Button, onClick = { scope.launch {
                    scroll.animateScrollTo((scroll.value - maxOf(1, scroll.viewportSize * 3 / 4)).coerceAtLeast(0))
                } }), contentAlignment = Alignment.CenterStart) {
                Text("‹", fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (scroll.canScrollBackward) 1f else 0.38f))
            }
            Row(Modifier.weight(1f).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NodePage.entries.forEach { page ->
                    val requester = remember(page) { BringIntoViewRequester() }
                    LaunchedEffect(selected) { if (selected == page) requester.bringIntoView() }
                    FilterChip(selected == page, { onSelect(page) }, modifier = Modifier.bringIntoViewRequester(requester), label = { Text(stringResource(page.title)) })
                }
            }
            Box(Modifier.size(48.dp).semantics { contentDescription = context.getString(R.string.menu_next) }
                .clickable(enabled = scroll.canScrollForward, role = Role.Button, onClick = { scope.launch {
                    scroll.animateScrollTo((scroll.value + maxOf(1, scroll.viewportSize * 3 / 4)).coerceAtMost(scroll.maxValue))
                } }), contentAlignment = Alignment.CenterEnd) {
                Text("›", fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (scroll.canScrollForward) 1f else 0.38f))
            }
        }
        if (scroll.maxValue > 0) {
            Text(stringResource(R.string.menu_swipe),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
