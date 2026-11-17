package org.minanode.android

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException

/** Per-production snapshots, private to the selected network. No keys or credentials. */
class ProductionResourceArchive(private val directory: File, private val maxBytes: Long = 16L * 1024 * 1024,
    private val maxBlocks: Int = 500) {
    private val entries = linkedMapOf<String, List<ResourceSample>>()
    private var lastCapture: Long? = null
    var writeFailed: Boolean = false
        private set
    init {
        directory.mkdirs()
        var bytes = 0L
        files().forEach { file ->
            if (entries.size >= maxBlocks || file.length() > 256 * 1024 || bytes + file.length() > maxBytes) return@forEach
            runCatching {
                val json = JSONObject(file.readText())
                require(json.getInt("version") == 1)
                val slot = json.getLong("slot")
                require(file.name == "slot-$slot.json")
                val array = json.getJSONArray("samples")
                require(array.length() <= 600)
                val samples = (0 until array.length()).map { i ->
                    val item = array.getJSONArray(i)
                    fun value(index: Int) = if (item.isNull(index)) null else item.getDouble(index).takeIf { it.isFinite() && it >= 0 }
                    ResourceSample(item.getLong(0), value(1), value(2))
                }.sortedBy { it.timeMs }
                entries["slot:$slot"] = samples
                bytes += file.length()
            }
        }
        prune()
    }
    fun snapshot(): Map<String, List<ResourceSample>> = entries.toMap()
    fun capture(blocks: List<ProducedBlock>, recent: List<ResourceSample>, nowMs: Long): Map<String, List<ResourceSample>> {
        if (lastCapture?.let { nowMs >= it && nowMs - it < 5000 } == true) return snapshot()
        lastCapture = nowMs
        writeFailed = false
        val first = recent.minOfOrNull { it.timeMs }
        val last = recent.maxOfOrNull { it.timeMs }
        if (first != null && last != null) blocks.sortedByDescending { it.slot }.take(maxBlocks).forEach { block ->
            if (block.proofStartMs == null && block.producedMs == null) return@forEach
            val period = block.resourcePeriod(nowMs) ?: return@forEach
            if (period.last < first || period.first > last) return@forEach
            val samples = ((entries[block.id] ?: emptyList()) + recent.filter { it.timeMs in period })
                .associateBy { it.timeMs }.values.sortedBy { it.timeMs }.take(600)
            if (samples.isEmpty() || samples == entries[block.id]) return@forEach
            runCatching {
                val array = JSONArray()
                samples.forEach { array.put(JSONArray().put(it.timeMs).put(it.cpu ?: JSONObject.NULL).put(it.ramMiB ?: JSONObject.NULL)) }
                val bytes = JSONObject().put("version", 1).put("slot", block.slot).put("samples", array).toString().toByteArray(Charsets.UTF_8)
                require(bytes.size <= 256 * 1024 && bytes.size <= maxBytes)
                val target = File(directory, "slot-${block.slot}.json")
                val temp = File(directory, "slot-${block.slot}.tmp")
                FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
                try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                entries[block.id] = samples
            }.onFailure { writeFailed = true }
        }
        prune()
        return snapshot()
    }
    private fun files() = directory.listFiles().orEmpty().filter { it.name.matches(Regex("slot-[0-9]+\\.json")) }
        .sortedByDescending { it.name.removePrefix("slot-").removeSuffix(".json").toLongOrNull() ?: -1 }
    private fun prune() {
        var bytes = 0L; var count = 0
        files().forEach { file ->
            val id = "slot:" + file.name.removePrefix("slot-").removeSuffix(".json")
            if (id !in entries || count >= maxBlocks || bytes + file.length() > maxBytes) {
                if (file.delete()) entries.remove(id) else writeFailed = true
            } else { bytes += file.length(); count++ }
        }
    }
}

/** Five seconds of context, with a bounded ten-minute capture per production. */
fun ProducedBlock.resourcePeriod(nowMs: Long? = null): LongRange? {
    val begin = proofStartMs ?: producedMs ?: slotTimeMs ?: return null
    val end = discardedMs ?: committedMs ?: proofEndMs ?: nowMs ?: producedMs ?: begin
    return (begin - 5000)..(end.coerceIn(begin, begin + 590000) + 5000)
}
