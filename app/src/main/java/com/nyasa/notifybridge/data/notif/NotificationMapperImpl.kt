package com.nyasa.notifybridge.data.notif

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.nyasa.notifybridge.domain.model.CapturedNotification
import com.nyasa.notifybridge.domain.notif.NotificationMapper
import javax.inject.Inject

class NotificationMapperImpl @Inject constructor() : NotificationMapper {
    override fun map(sbn: StatusBarNotification, appLabel: String): CapturedNotification {
        val x = sbn.notification.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = messagingLatest(sbn.notification)
            ?: x.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: x.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        return CapturedNotification(
            packageName = sbn.packageName,
            appLabel = appLabel,
            title = title,
            body = body,
            subText = x.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            category = sbn.notification.category,
            channelId = runCatching { sbn.notification.channelId }.getOrNull(),
            postTime = sbn.postTime,
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
            tag = sbn.tag,
            id = sbn.id,
        )
    }

    private fun messagingLatest(notification: Notification): String? {
        // extractMessagingStyleFromNotification is API 28+. minSdk is 26, so
        // calling it on API 26–27 throws NoSuchMethodError at runtime. Guard
        // it; on 26–27 the body falls through to BIG_TEXT/TEXT.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val style = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification) ?: return null
        val msg = style.messages.lastOrNull() ?: return null
        val sender = msg.person?.name?.toString()
        val text = msg.text?.toString() ?: return null
        return if (sender.isNullOrBlank()) text else "$sender: $text"
    }
}
