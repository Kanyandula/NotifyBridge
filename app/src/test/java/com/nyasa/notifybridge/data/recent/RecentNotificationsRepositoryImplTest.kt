package com.nyasa.notifybridge.data.recent

import com.nyasa.notifybridge.data.db.RecentNotificationDao
import com.nyasa.notifybridge.data.db.RecentNotificationEntity
import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.model.RecentItem
import com.nyasa.notifybridge.domain.repo.RecentNotificationsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRecentNotificationDao : RecentNotificationDao {
    private val rows = MutableStateFlow<List<RecentNotificationEntity>>(emptyList())

    override suspend fun insert(entity: RecentNotificationEntity): Long {
        val nextId = (rows.value.maxOfOrNull { it.id } ?: 0L) + 1L
        rows.value += entity.copy(id = nextId)
        return nextId
    }

    override fun observeNewest(limit: Int): Flow<List<RecentNotificationEntity>> =
        rows.map { current -> current.sortedByDescending { it.id }.take(limit) }

    override suspend fun trimToMax(max: Int) {
        rows.value = rows.value
            .sortedByDescending { it.id }
            .take(max)
            .sortedBy { it.id }
    }

    override suspend fun count(): Int = rows.value.size
}

class RecentNotificationsRepositoryImplTest {
    private fun repo(dao: FakeRecentNotificationDao = FakeRecentNotificationDao()) =
        RecentNotificationsRepositoryImpl(dao)

    private fun outbox(
        title: String? = "t",
        body: String? = "b",
        app: String = "App",
        postTime: Long = 100L,
        createdAt: Long = postTime,
    ) = OutboxItem(
        topic = "notifybridge/pixel/notification",
        payload = """
            {
              "package": "com.x",
              "app": "$app",
              "title": ${title.jsonValue()},
              "text": ${body.jsonValue()},
              "post_time": $postTime
            }
        """.trimIndent(),
        createdAt = createdAt,
    )

    @Test fun initial_state_is_empty() = runTest {
        assertEquals(emptyList<RecentItem>(), repo().recent.first())
    }

    @Test fun record_prepends_newest_first() = runTest {
        val repo = repo()
        repo.recordPublished(outbox(title = "first"))
        repo.recordPublished(outbox(title = "second"))
        repo.recordPublished(outbox(title = "third"))
        assertEquals(listOf("third", "second", "first"), repo.recent.first().map { it.title })
    }

    @Test fun record_assigns_monotonic_ids() = runTest {
        val repo = repo()
        repeat(3) { repo.recordPublished(outbox()) }
        assertEquals(listOf(3L, 2L, 1L), repo.recent.first().map { it.id })
    }

    @Test fun record_maps_payload_fields_into_recent_item() = runTest {
        val repo = repo()
        repo.recordPublished(outbox(app = "WhatsApp", title = "Alice", body = "hi", postTime = 42L))
        val recent = repo.recent.first().single()
        assertEquals("com.x", recent.packageName)
        assertEquals("WhatsApp", recent.app)
        assertEquals("Alice", recent.title)
        assertEquals("hi", recent.body)
        assertEquals(42L, recent.postTime)
    }

    @Test fun record_handles_null_title_and_body() = runTest {
        val repo = repo()
        repo.recordPublished(outbox(title = null, body = null))
        val recent = repo.recent.first().single()
        assertEquals("", recent.title)
        assertEquals("", recent.body)
    }

    @Test fun malformed_payload_is_ignored() = runTest {
        val repo = repo()
        repo.recordPublished(OutboxItem(topic = "t", payload = "not json", createdAt = 1L))
        assertEquals(emptyList<RecentItem>(), repo.recent.first())
    }

    @Test fun cap_evicts_oldest_when_full() = runTest {
        val repo = repo()
        val cap = RecentNotificationsRepository.MAX_ITEMS
        repeat(cap + 5) { repo.recordPublished(outbox(title = "$it")) }
        val ids = repo.recent.first().map { it.id }
        assertEquals(cap, ids.size)
        assertEquals((cap + 5).toLong(), ids.first())
        assertEquals(6L, ids.last())
        assertTrue(ids.all { it >= 6L })
    }
}

private fun String?.jsonValue(): String =
    this?.let { "\"$it\"" } ?: "null"
