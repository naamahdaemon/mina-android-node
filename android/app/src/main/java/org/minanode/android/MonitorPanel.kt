package org.minanode.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import java.util.Locale

private fun number(value: Double?, digits: Int = 1) = value?.let { String.format(Locale.ROOT, "%.${digits}f", it) } ?: "—"

@Composable
fun MonitorPanel(state: MonitorState, producer: Boolean) {
    val latest = state.samples.lastOrNull()
    Text("Ressources", style = MaterialTheme.typography.titleLarge)
    Text("RAM : ${number(latest?.ramMiB)} Mio · CPU : ${number(latest?.cpu)} %")
    Text("CPU : 100 % = un cœur. Les mesures indisponibles restent vides.", style = MaterialTheme.typography.bodySmall)
    SignalChart("RAM du nœud (Mio)", state.samples.map { it.ramMiB }, Color(0xFF53D9DB))
    SignalChart("CPU du nœud (%)", state.samples.map { it.cpu }, Color(0xFFFFD75A))
    Text("Stockage du profil : ${number(state.usedMiB)} Mio · Libre : ${number(state.freeGiB)} Gio")
    Text("Batterie : ${state.battery?.toString() ?: "—"} %${if (state.charging) " · en charge" else ""} · Température batterie : ${number(state.temperature)} °C")
    if (state.thermal >= 3) Text("Android signale une contrainte thermique élevée. La production peut ralentir.", color = MaterialTheme.colorScheme.error)
    if (producer) {
        Text("Production", style = MaterialTheme.typography.titleLarge)
        val remaining = state.nextSlotTimeMs?.let { ((it - System.currentTimeMillis()) / 1000).coerceAtLeast(0) }
        Text(state.nextSlot?.let { "Prochain slot gagné : $it · dans $remaining s" } ?: "Aucun prochain slot gagné annoncé")
        Text("${state.blocks.count { it.status == "Canonical" }} canoniques · ${state.blocks.count { it.status == "Orphaned" }} orphelins · ${state.blocks.size} blocs suivis")
        state.producerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Historique limité aux 500 derniers blocs connus. Toucher un bloc pour ses détails.", style = MaterialTheme.typography.bodySmall)
        state.blocks.take(50).forEach { block ->
            var expanded by remember(block.hash) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("#${block.height ?: "—"} · slot ${block.slot} · ${block.status}", style = MaterialTheme.typography.titleSmall)
                    Text("TX ${block.tx} · zkApps ${block.zkapps} · preuve ${number(block.proofSeconds, 3)} s")
                    if (expanded) {
                        Text("Préparation ${number(block.prepMs)} ms · publication ${number(block.delaySeconds, 3)} s")
                        val samples = state.samples.filter { sample -> block.proofStartMs != null && block.proofEndMs != null && sample.timeMs in block.proofStartMs..block.proofEndMs }
                        Text("Pics échantillonnés (${samples.size} points) : CPU ${number(samples.mapNotNull { it.cpu }.maxOrNull())} % · RAM ${number(samples.mapNotNull { it.ramMiB }.maxOrNull())} Mio")
                        Text(block.hash, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalChart(title: String, values: List<Double?>, color: Color) {
    Text(title, style = MaterialTheme.typography.labelMedium)
    // Keep peaks when downsampling the six-hour window to a phone-sized chart.
    val reduced = values.chunked(maxOf(1, values.size / 240)).map { it.filterNotNull().maxOrNull() }
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val maximum = (reduced.filterNotNull().maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val path = Path(); var connected = false
        reduced.forEachIndexed { index, value ->
            if (value == null) connected = false else {
                val x = index.toFloat() / maxOf(1, reduced.size - 1) * size.width
                val y = size.height - (value / maximum * size.height).toFloat()
                if (connected) path.lineTo(x, y) else path.moveTo(x, y)
                connected = true
            }
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
}
