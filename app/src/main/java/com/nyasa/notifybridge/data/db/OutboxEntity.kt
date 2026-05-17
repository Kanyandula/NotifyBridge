package com.nyasa.notifybridge.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topic: String,
    val payload: String,
    val createdAt: Long,
    val attemptCount: Int = 0,
)
