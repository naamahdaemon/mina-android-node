package org.minanode.android

import android.content.Context
import androidx.annotation.StringRes

/** Resolve at display time so a running node's status follows language changes. */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList(), val raw: String? = null) {
    fun resolve(context: Context): String = raw ?: context.getString(id,
        *args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray())
    companion object { fun raw(value: String) = UiText(0, raw = value) }
}

fun uiText(@StringRes id: Int, vararg args: Any) = UiText(id, args.toList())
class UiException(val text: UiText) : IllegalArgumentException("UI validation error ${text.id}")
fun requireUi(condition: Boolean, @StringRes id: Int, vararg args: Any) {
    if (!condition) throw UiException(uiText(id, *args))
}
fun Throwable.userText(): UiText = (this as? UiException)?.text ?: UiText.raw(message ?: javaClass.simpleName)

fun phaseText(context: Context, value: String): String {
    if (value.isBlank()) return context.getString(R.string.waiting_api)
    return value.split(" · ").distinct().joinToString(" · ") { phase ->
        val id = when (phase) {
            "Synced" -> R.string.sync_synced
            "Catchup" -> R.string.sync_catchup
            "Bootstrap" -> R.string.sync_bootstrap
            "StakingLedgerPending" -> R.string.sync_staking
            "NextEpochLedgerPending" -> R.string.sync_next_epoch
            "RootLedgerPending" -> R.string.sync_root
            "BlocksPending" -> R.string.sync_blocks
            else -> null
        }
        if (id == null) phase else context.getString(id)
    }
}

fun productionStatus(context: Context, value: String): String {
    val id = when (value) {
        "Canonical" -> R.string.block_canonical
        "Orphaned" -> R.string.block_orphaned
        "Scheduled" -> R.string.block_scheduled
        "Missed" -> R.string.block_missed
        "Discarded" -> R.string.block_discarded
        "Produced" -> R.string.block_produced
        "Committed" -> R.string.block_committed
        else -> null
    }
    return if (id == null) value else context.getString(id)
}
