package com.nyasa.notifybridge.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
class NotifyBridgeDatabaseMigrationTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun teardown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun migration_1_2_preserves_outbox_and_creates_recent_table() = runTest {
        createVersion1Database()

        val db = Room.databaseBuilder(context, NotifyBridgeDatabase::class.java, DB_NAME)
            .addMigrations(NotifyBridgeMigrations.from1To2)
            .build()

        assertEquals(1, db.outboxDao().count())
        assertEquals(0, db.recentNotificationDao().count())
        db.close()
    }

    private fun createVersion1Database() {
        context.getDatabasePath(DB_NAME).parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DB_NAME), null)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `outbox` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `topic` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `attemptCount` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `outbox` (`topic`, `payload`, `createdAt`, `attemptCount`)
            VALUES ('t', 'p', 1, 0)
            """.trimIndent(),
        )
        db.version = 1
        db.close()
    }

    private companion object {
        const val DB_NAME = "notifybridge-migration-test.db"
    }
}
