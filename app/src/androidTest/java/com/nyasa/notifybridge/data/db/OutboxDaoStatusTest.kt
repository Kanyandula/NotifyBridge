package com.nyasa.notifybridge.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nyasa.notifybridge.domain.MAX_PUBLISH_ATTEMPTS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutboxDaoStatusTest {
    private lateinit var db: NotifyBridgeDatabase
    private lateinit var dao: OutboxDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotifyBridgeDatabase::class.java,
        ).build()
        dao = db.outboxDao()
    }

    @After fun tearDown() { db.close() }

    private fun row(
        payload: String = "{}",
        attempts: Int = 0,
        status: OutboxStatus = OutboxStatus.PENDING,
    ) = OutboxEntity(
        topic = "t/x",
        payload = payload,
        createdAt = 1L,
        attemptCount = attempts,
        status = status.name,
    )

    @Test fun oldestPending_excludes_FAILED_TERMINAL_rows() = runBlocking {
        dao.insert(row(payload = "ok"))
        dao.insert(row(payload = "bad", status = OutboxStatus.FAILED_TERMINAL))
        val rows = dao.oldestPending(10)
        assertEquals(1, rows.size)
        assertEquals("ok", rows[0].payload)
    }

    @Test fun markFailedTerminal_changes_status_only() = runBlocking {
        val id = dao.insert(row(attempts = 4))
        dao.markFailedTerminal(id)
        val rows = dao.allForTest()
        assertEquals(OutboxStatus.FAILED_TERMINAL.name, rows[0].status)
        assertEquals(4, rows[0].attemptCount)
    }

    @Test fun bumpAttemptOrFail_increments_and_stays_PENDING_below_threshold() = runBlocking {
        val id = dao.insert(row(attempts = 3))
        dao.bumpAttemptOrFail(id, MAX_PUBLISH_ATTEMPTS)
        val rows = dao.allForTest()
        assertEquals(4, rows[0].attemptCount)
        assertEquals(OutboxStatus.PENDING.name, rows[0].status)
    }

    @Test fun bumpAttemptOrFail_transitions_to_FAILED_TERMINAL_at_threshold() = runBlocking {
        val id = dao.insert(row(attempts = 4))
        dao.bumpAttemptOrFail(id, MAX_PUBLISH_ATTEMPTS)
        val rows = dao.allForTest()
        assertEquals(5, rows[0].attemptCount)
        assertEquals(OutboxStatus.FAILED_TERMINAL.name, rows[0].status)
    }

    @Test fun failedDropCountFlow_emits_count_of_FAILED_TERMINAL_rows() = runBlocking {
        dao.insert(row())
        val id = dao.insert(row())
        dao.markFailedTerminal(id)
        assertEquals(1, dao.failedDropCountFlow().first())
    }

    @Test fun pendingCountFlow_excludes_FAILED_TERMINAL_rows() = runBlocking {
        dao.insert(row())
        val terminalId = dao.insert(row())
        dao.markFailedTerminal(terminalId)
        assertEquals(1, dao.pendingCountFlow().first())
    }
}
