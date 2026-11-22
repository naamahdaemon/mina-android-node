package org.minanode.android

import java.io.File

/** The confirmed Android profile takes precedence over the dashboard's saved startup setting. */
object SnarkWorkerStartup {
    fun prepare(work: File, enabled: Boolean): Boolean {
        if (enabled) return false
        val setting = File(work, "snark-worker.json")
        if (!setting.exists()) return false
        requireUi(setting.isFile && setting.delete(), R.string.error_snarker_saved_setting)
        return true
    }
}
