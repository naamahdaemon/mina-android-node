package org.minanode.android

import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** A short accidental flick must snap back, even if its release velocity is high. */
@Composable
fun deliberatePagerFling(pager: PagerState): TargetedFlingBehavior {
    val normal = PagerDefaults.flingBehavior(pager, snapPositionalThreshold = 0.65f)
    return remember(pager, normal) {
        object : TargetedFlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float, onRemainingScrollOffset: (Float) -> Unit): Float {
                val velocity = if (abs(pager.currentPageOffsetFraction) < 0.30f) 0f else initialVelocity
                return with(normal) { performFling(velocity, onRemainingScrollOffset) }
            }
        }
    }
}

@Composable
fun NodeStatusBadge(run: RunPhase, phase: String) {
    val context = LocalContext.current
    val color = when {
        run == RunPhase.ERROR -> MaterialTheme.colorScheme.error
        run == RunPhase.SUSPENDED -> MaterialTheme.colorScheme.primary
        run == RunPhase.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        run == RunPhase.RUNNING && phase.split(" · ").firstOrNull() == "Synced" ->
            if (LocalDarkTheme.current) Color(0xFF53D9BC) else Color(0xFF006A50)
        else -> if (LocalDarkTheme.current) Color(0xFFFFD75A) else Color(0xFF805000)
    }
    val label = when (run) {
        RunPhase.STOPPED -> stringResource(R.string.stopped)
        RunPhase.STARTING -> stringResource(R.string.starting)
        RunPhase.RUNNING -> phaseText(context, phase)
        RunPhase.SUSPENDED -> stringResource(R.string.power_suspended)
        RunPhase.STOPPING -> stringResource(R.string.stopping)
        RunPhase.ERROR -> stringResource(R.string.attention_required)
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = color.copy(alpha = 0.14f), contentColor = color) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(9.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
