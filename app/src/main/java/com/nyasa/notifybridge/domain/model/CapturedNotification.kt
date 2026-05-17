package com.nyasa.notifybridge.domain.model

data class CapturedNotification(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val body: String?,
    val subText: String?,
    val category: String?,
    val channelId: String?,
    val postTime: Long,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val tag: String?,
    val id: Int,
) {
    val dedupeKey: String get() = "$packageName|${tag ?: ""}|$id"
}
