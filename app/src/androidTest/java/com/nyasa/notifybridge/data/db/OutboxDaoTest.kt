package com.nyasa.notifybridge.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutboxDaoTest {
    private lateinit var db: NotifyBridgeDatabase
    private lateinit var dao: OutboxDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotifyBridgeDatabase::class.java).build()
        dao = db.outboxDao()
    }
    @After fun teardown() = db.close()

    @Test fun insert_then_oldest_batch_then_delete() = runTest {
        dao.insert(OutboxEntity(topic = "t", payload = "p1", createdAt = 1))
        dao.insert(OutboxEntity(topic = "t", payload = "p2", createdAt = 2))
        val batch = dao.oldest(10)
        assertEquals(listOf("p1", "p2"), batch.map { it.payload })
        dao.deleteById(batch.first().id)
        assertEquals(1, dao.count())
    }

    @Test fun prune_by_ttl_and_cap() = runTest {
        repeat(6) { dao.insert(OutboxEntity(topic = "t", payload = "$it", createdAt = it.toLong())) }
        dao.deleteOlderThan(2)            // removes createdAt 0,1
        dao.trimToMax(2)                  // keep newest 2
        assertEquals(2, dao.count())
        assertEquals(listOf("4", "5"), dao.oldest(10).map { it.payload })
    }
}
