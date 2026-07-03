package com.nyasa.notifybridge.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object NotifyBridgeMigrations {
    val from1To2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recent_notifications` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `packageName` TEXT NOT NULL,
                    `app` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `postTime` INTEGER NOT NULL,
                    `publishedAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    /** v3 adds [OutboxEntity.status] for spec §3.2 head-of-line protection. */
    val from2To3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE outbox ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'",
            )
        }
    }
}
