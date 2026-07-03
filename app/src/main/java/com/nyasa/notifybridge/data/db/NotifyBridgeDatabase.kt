package com.nyasa.notifybridge.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        OutboxEntity::class,
        RecentNotificationEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class NotifyBridgeDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun recentNotificationDao(): RecentNotificationDao
}
