package com.nyasa.notifybridge.domain.model

enum class TlsMode { OFF, SYSTEM_CA, PINNED }

data class BrokerConfig(
    val host: String = "",
    val port: Int = 1883,
    val deviceName: String = "phone",
    val username: String? = null,
    val password: String? = null,
    val tlsMode: TlsMode = TlsMode.OFF,
    val pinnedCertPem: String? = null,
)

data class AppLockPrefs(
    val enabled: Boolean = true,
    val idleTimeoutMs: Long = 60_000L,
    val redactBody: Boolean = true,
)

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }
