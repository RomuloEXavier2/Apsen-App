package com.mynd.watch.service

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mynd.watch.data.NotifFilter

/**
 * Repassa as notificações do celular para o relógio (substitui essa função do app Haylou).
 * O usuário precisa liberar em: Configurações > Notificações > Acesso a notificações > MYND Watch.
 */
class NotificationForwarder : NotificationListenerService() {

    private val ignore = setOf("com.mynd.watch", "android", "com.android.systemui")
    private val recent = LinkedHashMap<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotifFilter.init(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName in ignore || sbn.isOngoing) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val key = "${sbn.packageName}|$title|$text"
        val now = System.currentTimeMillis()
        if ((recent[key] ?: 0) > now - 10_000) return
        recent[key] = now; if (recent.size > 50) recent.remove(recent.keys.first())
        NotifFilter.addSeen(sbn.packageName)
        if (NotifFilter.isBlocked(sbn.packageName)) return
        WatchService.enqueueNotification(title.ifBlank { appLabel(sbn.packageName) }, text)
    }

    private fun appLabel(pkg: String) = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

/** Reinicia o serviço depois de reiniciar o celular. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) WatchService.start(ctx)
    }
}
