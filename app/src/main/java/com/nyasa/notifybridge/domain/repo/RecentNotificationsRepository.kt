package com.nyasa.notifybridge.domain.repo

import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.model.RecentItem
import kotlinx.coroutines.flow.Flow

/**
 * Bounded, newest-first ring of successfully-published notifications.
 * Backed by Room so the Status screen survives process death and reads the
 * same persisted state that can be inspected in the app database.
 */
interface RecentNotificationsRepository {
    val recent: Flow<List<RecentItem>>

    /** Map a published [OutboxItem] payload to a [RecentItem] and persist it. */
    suspend fun recordPublished(
        item: OutboxItem,
        publishedAt: Long = System.currentTimeMillis(),
    )

    companion object {
        /** Hard cap on persisted recent rows. Matches the Status screen's display budget. */
        const val MAX_ITEMS = 20
    }
}
