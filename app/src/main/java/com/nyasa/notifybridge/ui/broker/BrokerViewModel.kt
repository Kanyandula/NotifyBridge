package com.nyasa.notifybridge.ui.broker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.TlsMode
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import com.nyasa.notifybridge.domain.usecase.TestConnectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

fun isValid(c: BrokerConfig) = c.host.isNotBlank() && c.port in 1..65535

fun certError(mode: TlsMode, pem: String?): String? =
    if (mode == TlsMode.PINNED && pem.isNullOrBlank()) "Select a CA/cert file" else null

@HiltViewModel
class BrokerViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val testConnection: TestConnectionUseCase,
) : ViewModel() {

    private val _config = MutableStateFlow(BrokerConfig())
    val config: StateFlow<BrokerConfig> = _config.asStateFlow()

    /** Null = no result yet; non-null = "Connected" or "Failed" */
    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    /** True while save() is running (guards duplicate presses). */
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** One-shot event: true means save succeeded; collect in composable to start service + nav. */
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    /** Set once the user touches any field; gates the async seed (§3.4). */
    private var userEdited = false

    init {
        // Seed the persisted config off the main thread (no runBlocking on the
        // UI thread). If the user already started editing before DataStore
        // emits, keep their in-progress edits instead of overwriting them.
        // This coroutine and every updateX run on Dispatchers.Main, so the
        // userEdited flag needs no synchronization.
        viewModelScope.launch {
            val persisted = settings.brokerConfig.first()
            if (!userEdited) _config.value = persisted
        }
    }

    private fun edit(transform: (BrokerConfig) -> BrokerConfig) {
        userEdited = true
        _config.update(transform)
    }

    fun updateHost(v: String) = edit { it.copy(host = v) }
    fun updatePort(v: String) = edit { it.copy(port = v.toIntOrNull() ?: 0) }
    fun updateDeviceName(v: String) = edit { it.copy(deviceName = v) }
    fun updateUsername(v: String) = edit { it.copy(username = v.ifBlank { null }) }
    fun updatePassword(v: String) = edit { it.copy(password = v.ifBlank { null }) }
    fun updateTlsMode(v: TlsMode) = edit { it.copy(tlsMode = v) }
    fun updatePinnedCertPem(v: String?) = edit { it.copy(pinnedCertPem = v) }

    fun test() {
        viewModelScope.launch {
            _testResult.value = null
            val ok = runCatching { testConnection(_config.value) }.getOrDefault(false)
            _testResult.value = if (ok) "Connected" else "Failed"
        }
    }

    /** Persists config if valid. Composable observes [saveSuccess] to start the service + navigate. */
    fun save() {
        val current = _config.value
        if (!isValid(current)) return
        viewModelScope.launch {
            _saving.value = true
            val result = runCatching { settings.setBrokerConfig(current) }
            _saving.value = false
            if (result.isSuccess) {
                _saveSuccess.value = true
            } else {
                _testResult.value = "Save failed"
            }
        }
    }

    /** Called by composable after consuming the save-success event. */
    fun consumeSaveSuccess() {
        _saveSuccess.value = false
    }
}
