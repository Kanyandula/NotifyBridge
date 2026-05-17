package com.nyasa.notifybridge.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nyasa.notifybridge.domain.model.AppLockPrefs
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.TlsMode
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val ds: DataStore<Preferences>,
) : SettingsRepository {
    private object K {
        val host = stringPreferencesKey("host")
        val port = intPreferencesKey("port")
        val device = stringPreferencesKey("device")
        val user = stringPreferencesKey("user")
        val pass = stringPreferencesKey("pass")
        val tls = stringPreferencesKey("tls")
        val pin = stringPreferencesKey("pinnedCert")
        val allow = stringSetPreferencesKey("allow")
        val lockEnabled = booleanPreferencesKey("lockEnabled")
        val lockIdle = longPreferencesKey("lockIdleMs")
        val lockRedact = booleanPreferencesKey("lockRedact")
    }

    override val brokerConfig: Flow<BrokerConfig> = ds.data.map { p ->
        BrokerConfig(
            host = p[K.host] ?: "",
            port = p[K.port] ?: 1883,
            deviceName = p[K.device] ?: "phone",
            username = p[K.user],
            password = p[K.pass],
            tlsMode = p[K.tls]?.let { TlsMode.valueOf(it) } ?: TlsMode.OFF,
            pinnedCertPem = p[K.pin],
        )
    }
    override suspend fun setBrokerConfig(c: BrokerConfig) {
        ds.edit {
            it[K.host] = c.host; it[K.port] = c.port; it[K.device] = c.deviceName
            it[K.tls] = c.tlsMode.name
            if (c.username != null) it[K.user] = c.username else it.remove(K.user)
            if (c.password != null) it[K.pass] = c.password else it.remove(K.pass)
            if (c.pinnedCertPem != null) it[K.pin] = c.pinnedCertPem else it.remove(K.pin)
        }
    }
    override val allowList: Flow<Set<String>> =
        ds.data.map { it[K.allow] ?: emptySet() }
    override suspend fun setAllowList(packages: Set<String>) {
        ds.edit { it[K.allow] = packages }
    }
    override val appLock: Flow<AppLockPrefs> = ds.data.map {
        AppLockPrefs(
            enabled = it[K.lockEnabled] ?: true,
            idleTimeoutMs = it[K.lockIdle] ?: 60_000L,
            redactBody = it[K.lockRedact] ?: true,
        )
    }
    override suspend fun setAppLock(prefs: AppLockPrefs) {
        ds.edit {
            it[K.lockEnabled] = prefs.enabled
            it[K.lockIdle] = prefs.idleTimeoutMs
            it[K.lockRedact] = prefs.redactBody
        }
    }
}
