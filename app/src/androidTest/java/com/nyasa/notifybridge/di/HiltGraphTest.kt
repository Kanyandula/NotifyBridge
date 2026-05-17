package com.nyasa.notifybridge.di

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HiltGraphTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var outbox: OutboxRepository

    @Test fun graph_resolves_core_deps() {
        hilt.inject()
        assertNotNull(settings); assertNotNull(outbox)
    }
}
