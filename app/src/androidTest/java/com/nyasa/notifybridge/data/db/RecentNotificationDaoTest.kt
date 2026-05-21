package com.nyasa.notifybridge.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentNotificationDaoTest {
    private lateinit var db: NotifyBridgeDatabase
    private lateinit var dao: RecentNotificationDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotifyBridgeDatabase::class.java,
        ).build()
        dao = db.recentNotificationDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun insert_then_observe_newest_first() = runTest {
        dao.insert(entity(title = "first"))
        dao.insert(entity(title = "second"))
        dao.insert(entity(title = "third"))

        assertEquals(
            listOf("third", "second", "first"),
            dao.observeNewest(20).first().map { it.title },
        )
    }

    @Test
    fun insertAndTrim_keeps_newest_rows() = runTest {
        repeat(25) { dao.insertAndTrim(entity(title = "$it"), max = 20) }

        val rows = dao.observeNewest(100).first()
        assertEquals(20, rows.size)
        assertEquals("24", rows.first().title)
        assertEquals("5", rows.last().title)
        assertEquals(20, dao.count())
    }

    private fun entity(title: String) =
        RecentNotificationEntity(
            packageName = "com.x",
            app = "App",
            title = title,
            body = "body",
            postTime = 100L,
            publishedAt = 200L,
        )
}
