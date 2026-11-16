package org.minanode.android

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

class NodeFiles(context: Context, network: String = "devnet") {
    val work = File(context.noBackupFilesDir, "node/$network").apply { mkdirs() }
    val logs = File(context.noBackupFilesDir, "logs").apply { mkdirs() }
    val console = File(logs, "console.log")
    val previous = File(logs, "console.previous.log")

    @Synchronized fun append(text: String) {
        if (console.length() > 4 * 1024 * 1024) {
            if (previous.exists()) requireUi(previous.delete(), R.string.error_log_rotation)
            requireUi(console.renameTo(previous), R.string.error_log_rotation)
        }
        console.appendText(text + "\n")
    }

    @Synchronized fun tail(): String {
        if (!console.exists()) return ""
        return RandomAccessFile(console, "r").use {
            val size = minOf(it.length(), 32 * 1024L).toInt()
            it.seek(it.length() - size)
            val bytes = ByteArray(size)
            it.readFully(bytes)
            bytes.toString(Charsets.UTF_8).lines().takeLast(100).joinToString("\n")
        }
    }

    @Synchronized fun export(destination: java.io.OutputStream) {
        destination.writer().use { out ->
            out.write("Mina Android Node\nApplication ${BuildConfig.VERSION_NAME}\nSource ${BuildConfig.SOURCE_COMMIT}\n\n")
            for (file in listOf(previous, console)) {
                if (file.exists()) {
                    out.write("\n--- ${file.name} ---\n")
                    file.reader().use { it.copyTo(out) }
                }
            }
        }
    }
}
