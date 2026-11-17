package org.minanode.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Announced opportunities, not a guarantee that a block will be produced or canonical. */
data class ProductionSlot(val slot: Long, val timeMs: Long) {
    fun key(network: String) = "$network:$slot:$timeMs"
}

object ProductionAlertPolicy {
    fun due(slots: List<ProductionSlot>, network: String, nowMs: Long, sent: Set<String>): List<ProductionSlot> =
        slots.filter { it.slot >= 0 && it.timeMs - nowMs in 1..60000L && it.key(network) !in sent }
            .distinctBy { it.key(network) }.sortedBy { it.timeMs }
}

class ProductionAlerts(private val context: Context) {
    private val prefs = context.getSharedPreferences("production-alerts", Context.MODE_PRIVATE)
    private val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
    private val sent = prefs.getStringSet("sent", emptySet()).orEmpty().toMutableSet()
    fun createChannel() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.production_alert_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.production_alert_help)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
    }
    fun check(network: String, slots: List<ProductionSlot>, nowMs: Long = System.currentTimeMillis()) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ||
            !manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val valid = slots.map { it.key(network) }.toSet()
        manager.activeNotifications.filter { it.notification.channelId == CHANNEL && it.tag !in valid }
            .forEach { manager.cancel(it.tag, it.id) }
        ProductionAlertPolicy.due(slots, network, nowMs, sent).forEach { slot ->
            val open = PendingIntent.getActivity(context, 2,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_node)
                .setContentTitle(context.getString(R.string.production_alert_title)).setContentText(context.getString(R.string.production_alert_private)).build()
            val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_node)
                .setContentTitle(context.getString(R.string.production_alert_title))
                .setContentText(context.getString(R.string.production_alert_body, network, slot.slot, ((slot.timeMs - nowMs + 999) / 1000)))
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
                .setTimeoutAfter((slot.timeMs - nowMs).coerceAtLeast(1)).build()
            // The tag separates slots and networks from each other and from the foreground notification.
            manager.notify(slot.key(network), 2, notification)
            sent += slot.key(network)
            val retained = sent.sortedBy { it.substringAfterLast(':').toLongOrNull() ?: 0 }.takeLast(256).toSet()
            prefs.edit().putStringSet("sent", retained).commit()
            sent.retainAll(retained)
        }
    }
    fun cancel() {
        manager.activeNotifications.filter { it.notification.channelId == CHANNEL }.forEach { manager.cancel(it.tag, it.id) }
    }
    companion object { const val CHANNEL = "mina-production-alerts" }
}
