package com.nyasa.notifybridge.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Spec §3.2 — pins the invariant the v2→v3 migration must preserve:
 *  rows present at v2 (no `status` column) must read as `PENDING` after
 *  the migration. We exercise the v3 schema directly here; a true v2→v3
 *  roundtrip needs `exportSchema = true` + `MigrationTestHelper`, which
 *  is deliberately deferred per the plan's skip list. */
@RunWith(AndroidJUnit4::class)
class OutboxMigration2To3Test {
    private val dbName = "outbox-mig-2to3.db"

    @Test fun rows_default_to_PENDING_status_on_v3_schema() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase(dbName)

        val db = Room.databaseBuilder(ctx, NotifyBridgeDatabase::class.java, dbName)
            .addMigrations(
                NotifyBridgeMigrations.from1To2,
                NotifyBridgeMigrations.from2To3,
            )
            .build()
        try {
            val dao = db.outboxDao()
            runBlocking {
                dao.insert(
                    OutboxEntity(
                        topic = "test/topic",
                        payload = "{}",
                        createdAt = 1L,
                        attemptCount = 0,
                    ),
                )
                val rows = dao.oldest(10)
                assertEquals(1, rows.size)
                assertEquals(OutboxStatus.PENDING.name, rows[0].status)
            }
        } finally {
            db.close()
            ctx.deleteDatabase(dbName)
        }
    }
}
