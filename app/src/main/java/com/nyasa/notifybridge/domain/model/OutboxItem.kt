package com.nyasa.notifybridge.domain.model

data class OutboxItem(
    val id: Long = 0,
    val topic: String,
    val payload: String,
    val createdAt: Long,
    val attemptCount: Int = 0,
)
