package com.nyasa.notifybridge.data.di

import com.nyasa.notifybridge.data.db.OutboxRepositoryImpl
import com.nyasa.notifybridge.data.localization.LocalizationRepositoryImpl
import com.nyasa.notifybridge.data.notif.NotificationMapperImpl
import com.nyasa.notifybridge.data.recent.RecentNotificationsRepositoryImpl
import com.nyasa.notifybridge.data.settings.SettingsRepositoryImpl
import com.nyasa.notifybridge.domain.notif.NotificationMapper
import com.nyasa.notifybridge.domain.repo.LocalizationRepository
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import com.nyasa.notifybridge.domain.repo.RecentNotificationsRepository
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module @InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun outbox(i: OutboxRepositoryImpl): OutboxRepository
    @Binds abstract fun settings(i: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun mapper(i: NotificationMapperImpl): NotificationMapper
    @Binds abstract fun recent(i: RecentNotificationsRepositoryImpl): RecentNotificationsRepository
    @Binds abstract fun localization(i: LocalizationRepositoryImpl): LocalizationRepository
}
