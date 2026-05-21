package com.nyasa.notifybridge.domain.model

/**
 * A successfully-published notification for the Status screen's "Recent" list.
 * Sourced from [com.nyasa.notifybridge.domain.repo.RecentNotificationsRepository],
 * which is fed by the publish-success branch of the outbox drain.
 *
 * [id] is the persisted recent row id, stable for per-row reveal state until
 * the row ages out of the bounded recent table.
 */
data class RecentItem(
    val id: Long,
    val packageName: String,
    val app: String,
    val title: String,
    val body: String,
    val postTime: Long,
)
