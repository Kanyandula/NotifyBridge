package com.nyasa.notifybridge.domain.notif

import android.service.notification.StatusBarNotification
import com.nyasa.notifybridge.domain.model.CapturedNotification

interface NotificationMapper {
    fun map(sbn: StatusBarNotification, appLabel: String): CapturedNotification
}
