package com.nyasa.notifybridge.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_notifications")
@Suppress("LongParameterList")
data class RecentNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val app: String,
    val title: String,
    val body: String,
    val postTime: Long,
    val publishedAt: Long,
)
