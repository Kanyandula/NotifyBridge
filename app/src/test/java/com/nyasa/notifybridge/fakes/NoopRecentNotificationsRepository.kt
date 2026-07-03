package com.nyasa.notifybridge.fakes

import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.model.RecentItem
import com.nyasa.notifybridge.domain.repo.RecentNotificationsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** No-op [RecentNotificationsRepository] for drain tests that don't assert on
 *  the Recent surface. */
class NoopRecentNotificationsRepository : RecentNotificationsRepository {
    override val recent: Flow<List<RecentItem>> = flowOf(emptyList())
    override suspend fun recordPublished(item: OutboxItem, publishedAt: Long) = Unit
}
