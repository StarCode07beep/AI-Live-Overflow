package com.starcode.ai_live_pet

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotiListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            val n = sbn ?: return
            val pkg = n.packageName ?: return
            if (!WATCH.containsKey(pkg)) return
            if (n.isOngoing) return
            val ex = n.notification?.extras ?: return
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            val who = if (title.isNotEmpty() && title.length <= 10) title else ""
            PetOverlayService.notifyFromApp(pkg, who)
        } catch (t: Throwable) {
        }
    }

    companion object {
        @Volatile
        var instance: NotiListener? = null

        private val WATCH = mapOf(
            "com.tencent.mm" to "微信",
            "com.tencent.mobileqq" to "QQ",
            "com.tencent.wework" to "企业微信",
            "com.alibaba.android.rimet" to "钉钉",
            "com.android.mms" to "短信",
            "com.google.android.apps.messaging" to "短信",
            "org.telegram.messenger" to "Telegram"
        )

        fun watching(): Boolean = instance != null
    }
}
