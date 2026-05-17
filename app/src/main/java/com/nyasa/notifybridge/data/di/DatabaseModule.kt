package com.nyasa.notifybridge.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.nyasa.notifybridge.data.db.NotifyBridgeDatabase
import com.nyasa.notifybridge.data.db.OutboxDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("notifybridge")

@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun db(@ApplicationContext c: Context) =
        Room.databaseBuilder(c, NotifyBridgeDatabase::class.java, "notifybridge.db").build()
    @Provides fun dao(db: NotifyBridgeDatabase): OutboxDao = db.outboxDao()
    @Provides @Singleton
    fun prefs(@ApplicationContext c: Context): DataStore<Preferences> = c.dataStore
}
