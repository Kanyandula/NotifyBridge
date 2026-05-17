package com.nyasa.notifybridge.domain.repo

import com.nyasa.notifybridge.domain.model.AppLockPrefs
import com.nyasa.notifybridge.domain.model.BrokerConfig
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val brokerConfig: Flow<BrokerConfig>
    suspend fun setBrokerConfig(config: BrokerConfig)
    val allowList: Flow<Set<String>>
    suspend fun setAllowList(packages: Set<String>)
    val appLock: Flow<AppLockPrefs>
    suspend fun setAppLock(prefs: AppLockPrefs)
}
