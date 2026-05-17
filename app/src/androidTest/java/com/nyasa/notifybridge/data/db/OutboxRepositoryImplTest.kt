package com.nyasa.notifybridge.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nyasa.notifybridge.domain.model.OutboxItem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutboxRepositoryImplTest {
    private lateinit var db: NotifyBridgeDatabase
    private lateinit var repo: OutboxRepositoryImpl

    @Before fun s() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotifyBridgeDatabase::class.java).build()
        repo = OutboxRepositoryImpl(db.outboxDao())
    }
    @After fun e() = db.close()

    @Test fun enqueue_batch_markPublished() = runTest {
        repo.enqueue(OutboxItem(topic = "t", payload = "a", createdAt = 1))
        val b = repo.nextBatch(10)
        assertEquals("a", b.single().payload)
        repo.markPublished(b.single().id)
        assertEquals(emptyList<OutboxItem>(), repo.nextBatch(10))
    }

    @Test fun ttl_only_deletes_expired_without_cap_interference() = runTest {
        // Isolate the TTL path: large maxRows so the cap never fires.
        // createdAt = epoch-ms-like values; cutoff = nowMs - ttlMs.
        repo.enqueue(OutboxItem(topic="t", payload="old", createdAt = 1_000L))
        repo.enqueue(OutboxItem(topic="t", payload="fresh", createdAt = 9_000L))
        repo.pruneExpired(nowMs = 10_000L, ttlMs = 5_000L, maxRows = 1_000)
        // cutoff = 5_000 -> deletes createdAt < 5_000 ("old"), keeps "fresh"
        assertEquals(listOf("fresh"), repo.nextBatch(10).map { it.payload })
    }

    @Test fun cap_only_trims_to_newest_without_ttl_interference() = runTest {
        // Isolate the cap path: ttl window so wide nothing expires.
        repeat(10) { repo.enqueue(OutboxItem(topic="t", payload="$it", createdAt=it.toLong())) }
        repo.pruneExpired(nowMs = 100L, ttlMs = 100L, maxRows = 3) // cutoff = 0, none expire
        assertEquals(listOf("7","8","9"), repo.nextBatch(10).map { it.payload })
    }

    @Test fun ttl_then_cap_combined() = runTest {
        repeat(10) { repo.enqueue(OutboxItem(topic="t", payload="$it", createdAt=it.toLong())) }
        repo.pruneExpired(nowMs = 5, ttlMs = 0, maxRows = 3)
        // cutoff = 5 -> deletes createdAt < 5 (rows 0,1,2,3,4); 5..9 remain;
        // cap 3 -> keep newest by id -> 7,8,9
        assertEquals(listOf("7","8","9"), repo.nextBatch(10).map { it.payload })
    }
}
