# NotifyBridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android app that captures posted notifications and publishes them to a local MQTT broker with Home Assistant discovery, reliably and locally.

**Architecture:** Layered Kotlin/Hilt app. `NotificationListenerService` → dedup → map → Room outbox → foreground-service MQTT publisher (HiveMQ) with buffer/replay and HA discovery. Compose UI; local biometric app-lock.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore, HiveMQ MQTT client, WorkManager, androidx.biometric, kotlinx-coroutines/serialization. Tests: JUnit4, Robolectric, Turbine, kotlinx-coroutines-test, Room in-memory, MockK, fakes.

Source of truth: `docs/superpowers/specs/2026-05-17-notifybridge-design.md` (rev. 4). Section references (§) point there.

**Plan-wide conventions:**
- Package root `com.nyasa.notifybridge`. Paths below are under `app/src/main/java/com/nyasa/notifybridge/` (main) and `app/src/test/...` / `app/src/androidTest/...`.
- TDD: failing test → run (fail) → minimal impl → run (pass) → commit. Commit messages: imperative, ≤72 char subject, no AI attribution.
- Instrumented (`connectedDebugAndroidTest`) commands use `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>` to target a single test class. (This AGP version rejects Gradle's `--tests` filter for the connected-test task; the property is the supported equivalent and was verified working.)
- `app/src/debug/AndroidManifest.xml` removes `MqttForegroundService`, `NotifListenerService`, and `BootReceiver` (`tools:node="remove"`) from the **debug** app-under-test APK. Required because instrumented tests run under `HiltTestRunner`/`HiltTestApplication` (Task 12): the OS recreates the `@AndroidEntryPoint` + `START_STICKY` `MqttForegroundService` into a process with no app Hilt component, crashing the run before any test executes (an uninstall/runtime workaround cannot prevent the OS-driven sticky restart — verified). Instrumented tests never exercise the service/worker/boot path (verified manually in Task 25), so this loses no intended coverage. The live bridge must be exercised with a **release** build (see Task 25).
- Stable interface names used across tasks (do not rename):
  - `SettingsRepository`: `brokerConfig: Flow<BrokerConfig>`, `setBrokerConfig(BrokerConfig)`, `allowList: Flow<Set<String>>`, `setAllowList(Set<String>)`, `appLock: Flow<AppLockPrefs>`, `setAppLock(AppLockPrefs)`.
  - `OutboxRepository`: `enqueue(OutboxItem)`, `nextBatch(limit: Int): List<OutboxItem>`, `markPublished(id: Long)`, `recordFailure(id: Long)`, `pruneExpired(nowMs: Long, ttlMs: Long, maxRows: Int)`, `depth(): Flow<Int>`.
  - `MqttClientManager`: `connectionState: StateFlow<ConnectionState>`, `suspend connect(BrokerConfig)`, `suspend publish(topic: String, payload: String, qos: Int, retained: Boolean): Boolean`, `suspend disconnect()`.
  - `NotificationMapper`: `map(sbn: StatusBarNotification, appLabel: String): CapturedNotification`.
  - `NotificationDeduplicator`: `shouldForward(n: CapturedNotification, nowMs: Long): Boolean`.
  - `DiscoveryPayloadBuilder`: `discoveryTopic(device): String`, `discoveryConfig(device): String`, `stateTopic(device): String`, `statusTopic(device): String`, `eventPayload(CapturedNotification): String`.

---

## File Structure

```
app/build.gradle.kts, build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml
app/src/main/AndroidManifest.xml
.../NotifyBridgeApp.kt                      @HiltAndroidApp
.../MainActivity.kt                         single Compose activity, FLAG_SECURE, lock gate
.../domain/model/CapturedNotification.kt
.../domain/model/BrokerConfig.kt            host/port/tls/auth/device + AppLockPrefs, ConnectionState
.../domain/model/OutboxItem.kt
.../domain/repo/SettingsRepository.kt
.../domain/repo/OutboxRepository.kt
.../domain/mqtt/MqttClientManager.kt
.../domain/notif/NotificationMapper.kt
.../domain/notif/NotificationDeduplicator.kt
.../domain/discovery/DiscoveryPayloadBuilder.kt
.../domain/usecase/EnqueueNotificationUseCase.kt
.../domain/usecase/DrainOutboxUseCase.kt
.../domain/usecase/TestConnectionUseCase.kt
.../data/db/{OutboxEntity,OutboxDao,NotifyBridgeDatabase}.kt
.../data/db/OutboxRepositoryImpl.kt
.../data/settings/SettingsRepositoryImpl.kt
.../data/notif/NotificationMapperImpl.kt
.../data/mqtt/HiveMqClientManager.kt
.../data/di/{DatabaseModule,RepositoryModule,MqttModule,DispatchersModule}.kt
.../service/{NotifListenerService,MqttForegroundService,BootReceiver,OutboxDrainWorker}.kt
.../applock/{AppLockManager,BiometricAuthenticator,AppLockGate}.kt
.../ui/theme/{Color,Type,Theme}.kt
.../ui/NotifyBridgeNavHost.kt
.../ui/onboarding/{OnboardingScreen,OnboardingViewModel}.kt
.../ui/status/{StatusScreen,StatusViewModel}.kt
.../ui/broker/{BrokerScreen,BrokerViewModel}.kt
.../ui/permissions/{PermissionsScreen,PermissionsViewModel}.kt
.../ui/apps/{AppsScreen,AppsViewModel}.kt
.../ui/locked/LockedScreen.kt
```

**Compose UI note (explicit, not a placeholder):** For screen tasks, the plan provides the full ViewModel (state + logic, unit-tested) and a complete composable that wires that state, plus a manual verification step. Exhaustive Modifier-level styling is intentionally the engineer's execution detail against the Stitch reference (project `6833643636006727286`) — the correctness-bearing code (state, events, gating, redaction) is fully specified and tested.

---

## Task 1: Project scaffold

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `.gitignore`, `app/proguard-rules.pro`
- Create: `.../NotifyBridgeApp.kt`, `.../MainActivity.kt`
- Test: `app/src/test/java/com/nyasa/notifybridge/SanityTest.kt`

- [ ] **Step 1: Write the failing test**

`SanityTest.kt`:
```kotlin
package com.nyasa.notifybridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SanityTest {
    @Test fun toolchain_runs() = assertEquals(4, 2 + 2)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*SanityTest*"`
Expected: FAIL — build fails, project not yet configured.

- [ ] **Step 3: Create the build files**

`settings.gradle.kts`:
```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "NotifyBridge"
include(":app")
```

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.27"
hilt = "2.52"
compose-bom = "2024.12.01"
room = "2.6.1"
datastore = "1.1.1"
hivemq = "1.3.5"
work = "2.10.0"
biometric = "1.1.0"
coroutines = "1.9.0"
serialization = "1.7.3"
lifecycle = "2.8.7"
turbine = "1.2.0"
mockk = "1.13.13"
robolectric = "4.14"
[libraries]
androidx-core = { module = "androidx.core:core-ktx", version = "1.15.0" }
androidx-lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version = "1.9.3" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
compose-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-test-junit = { module = "androidx.compose.ui:ui-test-junit4" }
compose-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version = "2.8.4" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version = "1.2.0" }
hilt-work = { module = "androidx.hilt:hilt-work", version = "1.2.0" }
hilt-work-compiler = { module = "androidx.hilt:hilt-compiler", version = "1.2.0" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
datastore = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
hivemq-mqtt = { module = "com.hivemq:hivemq-mqtt-client", version.ref = "hivemq" }
work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
biometric = { module = "androidx.biometric:biometric", version.ref = "biometric" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
junit = { module = "junit:junit", version = "4.13.2" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core-ktx", version = "1.6.1" }
androidx-test-runner = { module = "androidx.test:runner", version = "1.6.2" }
androidx-test-junit = { module = "androidx.test.ext:junit", version = "1.2.1" }
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

`build.gradle.kts` (root):
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "com.nyasa.notifybridge"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nyasa.notifybridge"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}
dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.hivemq.mqtt)
    implementation(libs.work.runtime)
    implementation(libs.biometric)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test.junit)
    debugImplementation(libs.compose.test.manifest)
}
```

`.gitignore`:
```
*.iml
.gradle/
local.properties
.idea/
build/
captures/
.externalNativeBuild/
.cxx/
*.keystore
```

`app/proguard-rules.pro`: empty file (release minify disabled for v1).

- [ ] **Step 4: Create app classes and manifest**

> **Correction (post-impl):** the manifest must remove WorkManager's default `androidx.startup` initializer (the `<provider tools:node="remove">` block below). `NotifyBridgeApp` is a `Configuration.Provider` (for `HiltWorkerFactory`); without the removal, WorkManager double-initializes WITHOUT the Hilt factory — Task 15's `@HiltWorker OutboxDrainWorker` fails to instantiate at runtime — and `lintVitalRelease` fails the release build (only surfaces on release; debug/unit/instrumented never run lint-vital). Requires `xmlns:tools` on `<manifest>`.

`NotifyBridgeApp.kt`:
```kotlin
package com.nyasa.notifybridge

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NotifyBridgeApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
```

`MainActivity.kt`:
```kotlin
package com.nyasa.notifybridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("NotifyBridge") }
    }
}
```

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".NotifyBridgeApp"
        android:label="NotifyBridge"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <!-- NotifyBridgeApp is a WorkManager Configuration.Provider; remove the
             default startup initializer or WorkManager double-inits without the
             HiltWorkerFactory (OutboxDrainWorker fails) and lintVitalRelease
             fails the release build. -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*SanityTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Scaffold Gradle project, Hilt app, manifest permissions"
```

---

## Task 2: Domain models

**Files:**
- Create: `domain/model/CapturedNotification.kt`, `domain/model/BrokerConfig.kt`, `domain/model/OutboxItem.kt`
- Test: `app/src/test/java/com/nyasa/notifybridge/domain/model/CapturedNotificationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.nyasa.notifybridge.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CapturedNotificationTest {
    @Test fun dedupeKey_combines_package_tag_id() {
        val n = CapturedNotification(
            packageName = "com.whatsapp", appLabel = "WhatsApp",
            title = "John", body = "hi", subText = null, category = "msg",
            channelId = "c", postTime = 1L, isOngoing = false,
            isClearable = true, tag = "t", id = 7)
        assertEquals("com.whatsapp|t|7", n.dedupeKey)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*CapturedNotificationTest*"`
Expected: FAIL — `CapturedNotification` unresolved.

- [ ] **Step 3: Write minimal implementation**

`CapturedNotification.kt`:
```kotlin
package com.nyasa.notifybridge.domain.model

data class CapturedNotification(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val body: String?,
    val subText: String?,
    val category: String?,
    val channelId: String?,
    val postTime: Long,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val tag: String?,
    val id: Int,
) {
    val dedupeKey: String get() = "$packageName|${tag ?: ""}|$id"
}
```

`BrokerConfig.kt`:
```kotlin
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
```

`OutboxItem.kt`:
```kotlin
package com.nyasa.notifybridge.domain.model

data class OutboxItem(
    val id: Long = 0,
    val topic: String,
    val payload: String,
    val createdAt: Long,
    val attemptCount: Int = 0,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*CapturedNotificationTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add domain models"
```

---

## Task 3: NotificationDeduplicator (§3.1 step 2, S5)

**Files:**
- Create: `domain/notif/NotificationDeduplicator.kt`
- Test: `app/src/test/java/com/nyasa/notifybridge/domain/notif/NotificationDeduplicatorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.nyasa.notifybridge.domain.notif

import com.nyasa.notifybridge.domain.model.CapturedNotification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDeduplicatorTest {
    private fun n(body: String?, ongoing: Boolean = false) = CapturedNotification(
        "com.x", "X", "t", body, null, null, null, 0L, ongoing, true, null, 1)

    @Test fun first_post_forwards() {
        assertTrue(NotificationDeduplicator().shouldForward(n("a"), 1000L))
    }

    @Test fun same_content_within_debounce_dropped() {
        val d = NotificationDeduplicator()
        d.shouldForward(n("a"), 1000L)
        assertFalse(d.shouldForward(n("a"), 1300L))
    }

    @Test fun content_change_forwards_even_within_debounce() {
        val d = NotificationDeduplicator()
        d.shouldForward(n("a"), 1000L)
        assertTrue(d.shouldForward(n("b"), 1100L))
    }

    @Test fun same_content_after_debounce_forwards() {
        val d = NotificationDeduplicator()
        d.shouldForward(n("a"), 1000L)
        assertTrue(d.shouldForward(n("a"), 1600L))
    }

    @Test fun ongoing_unchanged_dropped_regardless_of_time() {
        val d = NotificationDeduplicator()
        d.shouldForward(n("50%", ongoing = true), 1000L)
        assertFalse(d.shouldForward(n("50%", ongoing = true), 9000L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*NotificationDeduplicatorTest*"`
Expected: FAIL — class unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.nyasa.notifybridge.domain.notif

import com.nyasa.notifybridge.domain.model.CapturedNotification

class NotificationDeduplicator(private val debounceMs: Long = 500L) {
    private data class Seen(val contentHash: Int, val atMs: Long)
    private val last = HashMap<String, Seen>()

    fun shouldForward(n: CapturedNotification, nowMs: Long): Boolean {
        val hash = (n.title to n.body).hashCode()
        val prev = last[n.dedupeKey]
        val contentUnchanged = prev != null && prev.contentHash == hash
        if (contentUnchanged && n.isOngoing) return false
        if (contentUnchanged && nowMs - prev!!.atMs < debounceMs) return false
        last[n.dedupeKey] = Seen(hash, nowMs)
        return true
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*NotificationDeduplicatorTest*"`
Expected: PASS (all 5).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add notification deduplicator with debounce + ongoing rule"
```

---

## Task 4: DiscoveryPayloadBuilder (§3.4)

**Files:**
- Create: `domain/discovery/DiscoveryPayloadBuilder.kt`
- Test: `app/src/test/java/com/nyasa/notifybridge/domain/discovery/DiscoveryPayloadBuilderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.nyasa.notifybridge.domain.discovery

import com.nyasa.notifybridge.domain.model.CapturedNotification
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryPayloadBuilderTest {
    private val b = DiscoveryPayloadBuilder()

    @Test fun slugifies_device_in_topics() {
        assertEquals("notifybridge/pixel-7/notification", b.stateTopic("Pixel 7"))
        assertEquals("notifybridge/pixel-7/status", b.statusTopic("Pixel 7"))
        assertEquals(
            "homeassistant/sensor/notifybridge_pixel-7/config",
            b.discoveryTopic("Pixel 7"))
    }

    @Test fun discovery_config_wires_state_attrs_availability() {
        val o = Json.parseToJsonElement(b.discoveryConfig("Pixel 7")).jsonObject
        assertEquals("notifybridge/pixel-7/notification",
            o["state_topic"]!!.jsonPrimitive.content)
        assertEquals("notifybridge/pixel-7/notification",
            o["json_attributes_topic"]!!.jsonPrimitive.content)
        assertEquals("notifybridge/pixel-7/status",
            o["availability_topic"]!!.jsonPrimitive.content)
        assertTrue(o.containsKey("unique_id"))
    }

    @Test fun event_payload_truncates_state_to_255_and_keeps_attrs() {
        val n = CapturedNotification("com.x", "X", "Title",
            "y".repeat(400), null, "msg", "c", 99L, false, true, null, 1)
        val o = Json.parseToJsonElement(b.eventPayload(n)).jsonObject
        assertEquals(255, o["state"]!!.jsonPrimitive.content.length)
        assertEquals("Title", o["title"]!!.jsonPrimitive.content)
        assertEquals("com.x", o["package"]!!.jsonPrimitive.content)
        assertEquals("X", o["app"]!!.jsonPrimitive.content)
        assertEquals(99L, o["post_time"]!!.jsonPrimitive.content.toLong())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*DiscoveryPayloadBuilderTest*"`
Expected: FAIL — class unresolved.

- [ ] **Step 3: Write minimal implementation**

> **Correction (post-impl):** `DiscoveryPayloadBuilder` must have `@Inject constructor()`. It is a constructor dependency of `HiveMqClientManager` (Task 11) and the use cases (Task 10); without `@Inject` (and with no `@Provides`/`@Binds` anywhere in the plan) Hilt cannot construct it, and the first consumer that injects `MqttClientManager` (Task 13) fails the KSP build with `[Dagger/MissingBinding] DiscoveryPayloadBuilder`. Hilt's partial-graph validation is why Task 12's `HiltGraphTest` did not catch it. This matches the codebase idiom (`NotificationMapperImpl`, the use cases, `HiveMqClientManager` all use `@Inject constructor`). Behavior is unchanged; `DiscoveryPayloadBuilder()` still works for the unit test.

```kotlin
package com.nyasa.notifybridge.domain.discovery

import com.nyasa.notifybridge.domain.model.CapturedNotification
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class DiscoveryPayloadBuilder @Inject constructor() {
    private fun slug(device: String) =
        device.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-")
            .trim('-').ifEmpty { "phone" }

    fun stateTopic(device: String) = "notifybridge/${slug(device)}/notification"
    fun statusTopic(device: String) = "notifybridge/${slug(device)}/status"
    fun discoveryTopic(device: String) =
        "homeassistant/sensor/notifybridge_${slug(device)}/config"

    fun discoveryConfig(device: String): String {
        val s = slug(device)
        return buildJsonObject {
            put("name", "NotifyBridge $device")
            put("unique_id", "notifybridge_$s")
            put("state_topic", stateTopic(device))
            put("json_attributes_topic", stateTopic(device))
            put("availability_topic", statusTopic(device))
            put("payload_available", "online")
            put("payload_not_available", "offline")
            put("icon", "mdi:bell-ring")
        }.toString()
    }

    fun eventPayload(n: CapturedNotification): String {
        val state = (n.body ?: n.title ?: n.appLabel).take(255)
        return buildJsonObject {
            put("state", state)
            put("title", n.title ?: "")
            put("text", n.body ?: "")
            put("app", n.appLabel)
            put("package", n.packageName)
            put("category", n.category ?: "")
            put("post_time", n.postTime)
        }.toString()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*DiscoveryPayloadBuilderTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add HA MQTT discovery + event payload builder"
```

---

## Task 5: NotificationMapper (§3.1, S1)

**Files:**
- Create: `domain/notif/NotificationMapper.kt`, `data/notif/NotificationMapperImpl.kt`
- Test: `app/src/test/java/com/nyasa/notifybridge/data/notif/NotificationMapperImplTest.kt`

- [ ] **Step 1: Write the failing test** (Robolectric — real `Notification`/`Bundle`)

```kotlin
package com.nyasa.notifybridge.data.notif

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// sdk=33: extractMessagingStyleFromNotification is API 28+; the mapper guards
// SDK_INT < P and returns null, so MessagingStyle must be exercised at >= 28.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationMapperImplTest {
    private val mapper = NotificationMapperImpl()

    private fun sbn(extras: Bundle, ongoing: Boolean = false): StatusBarNotification {
        val notif = Notification().apply {
            this.extras = extras
            if (ongoing) flags = flags or Notification.FLAG_ONGOING_EVENT
            category = "msg"
        }
        return mockk(relaxed = true) {
            every { packageName } returns "com.x"
            every { notification } returns notif
            every { postTime } returns 42L
            every { tag } returns null
            every { id } returns 3
            every { isOngoing } returns ongoing
            every { isClearable } returns !ongoing
        }
    }

    @Test fun big_text_preferred_over_text() {
        val b = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "T")
            putCharSequence(Notification.EXTRA_TEXT, "short")
            putCharSequence(Notification.EXTRA_BIG_TEXT, "big body")
        }
        val r = mapper.map(sbn(b), "App X")
        assertEquals("big body", r.body)
        assertEquals("T", r.title)
        assertEquals("App X", r.appLabel)
    }

    @Test fun falls_back_to_text_then_null() {
        val r1 = mapper.map(sbn(Bundle().apply {
            putCharSequence(Notification.EXTRA_TEXT, "only text") }), "X")
        assertEquals("only text", r1.body)
        val r2 = mapper.map(sbn(Bundle()), "X")
        assertEquals(null, r2.body)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*NotificationMapperImplTest*"`
Expected: FAIL — classes unresolved.

- [ ] **Step 3: Write minimal implementation**

> **Correction (post-impl):** `android.app.Notification.MessagingStyle.extractMessagingStyleFromNotification(...)` is `@hide` in AOSP — not public SDK API, so the original verbatim code did not compile. Corrected to `androidx.core.app.NotificationCompat.MessagingStyle` (accessor `msg.person`, not `msg.senderPerson`); behavior is identical. The `SDK_INT < P` guard/comment are retained as-is for parity with the committed code (NotificationCompat backports extraction, so the guard is now conservative but harmless).

`domain/notif/NotificationMapper.kt`:
```kotlin
package com.nyasa.notifybridge.domain.notif

import android.service.notification.StatusBarNotification
import com.nyasa.notifybridge.domain.model.CapturedNotification

interface NotificationMapper {
    fun map(sbn: StatusBarNotification, appLabel: String): CapturedNotification
}
```

`data/notif/NotificationMapperImpl.kt`:
```kotlin
package com.nyasa.notifybridge.data.notif

import android.app.Notification
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.nyasa.notifybridge.domain.model.CapturedNotification
import com.nyasa.notifybridge.domain.notif.NotificationMapper
import javax.inject.Inject

class NotificationMapperImpl @Inject constructor() : NotificationMapper {
    override fun map(sbn: StatusBarNotification, appLabel: String): CapturedNotification {
        val x = sbn.notification.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = messagingLatest(sbn.notification)
            ?: x.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: x.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        return CapturedNotification(
            packageName = sbn.packageName,
            appLabel = appLabel,
            title = title,
            body = body,
            subText = x.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            category = sbn.notification.category,
            channelId = runCatching { sbn.notification.channelId }.getOrNull(),
            postTime = sbn.postTime,
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
            tag = sbn.tag,
            id = sbn.id,
        )
    }

    private fun messagingLatest(notification: Notification): String? {
        // extractMessagingStyleFromNotification is API 28+. minSdk is 26, so
        // calling it on API 26–27 throws NoSuchMethodError at runtime. Guard
        // it; on 26–27 the body falls through to BIG_TEXT/TEXT.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val style = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification) ?: return null
        val msg = style.messages.lastOrNull() ?: return null
        val sender = msg.person?.name?.toString()
        val text = msg.text?.toString() ?: return null
        return if (sender.isNullOrBlank()) text else "$sender: $text"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*NotificationMapperImplTest*"`
Expected: PASS.

- [ ] **Step 5: Add MessagingStyle test, run, confirm pass**

Append to the test class. Note the constraint: `extractMessagingStyleFromNotification`
returns `null` below API 28, and the mapper guards `SDK_INT < P`; the class-level
`@Config(sdk = [33])` is what makes this test meaningful. Use a real small-icon
resource — `setSmallIcon(0)` throws when the notification is built (no such
drawable) on Robolectric and on device.
```kotlin
    @Test fun messaging_style_takes_latest_with_sender_prefix() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val person = android.app.Person.Builder().setName("Alice").build()
        val style = Notification.MessagingStyle(person)
            .addMessage("first", 1L, person)
            .addMessage("latest", 2L, person)
        val built = Notification.Builder(ctx, "ch")
            .setStyle(style)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        val sbn = mockk<StatusBarNotification>(relaxed = true) {
            every { packageName } returns "com.msg"
            every { notification } returns built
            every { postTime } returns 1L
            every { tag } returns null
            every { id } returns 1
            every { isOngoing } returns false
            every { isClearable } returns true
        }
        assertEquals("Alice: latest", mapper.map(sbn, "Msg").body)
    }
```
Run: `./gradlew :app:testDebugUnitTest --tests "*NotificationMapperImplTest*"`
Expected: PASS (all). The class `@Config(sdk = [33])` is required — without it Robolectric runs a default SDK that may be < 28, the mapper's guard returns null, and `messaging_style_takes_latest_with_sender_prefix` fails. That is correct guard behaviour, not a flake.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "Add notification mapper with body priority chain"
```

---

## Task 6: Room outbox persistence (§3.2)

**Files:**
- Create: `data/db/OutboxEntity.kt`, `data/db/OutboxDao.kt`, `data/db/NotifyBridgeDatabase.kt`
- Test: `app/src/androidTest/java/com/nyasa/notifybridge/data/db/OutboxDaoTest.kt`

- [ ] **Step 1: Write the failing instrumented test**

```kotlin
package com.nyasa.notifybridge.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutboxDaoTest {
    private lateinit var db: NotifyBridgeDatabase
    private lateinit var dao: OutboxDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotifyBridgeDatabase::class.java).build()
        dao = db.outboxDao()
    }
    @After fun teardown() = db.close()

    @Test fun insert_then_oldest_batch_then_delete() = runTest {
        dao.insert(OutboxEntity(topic = "t", payload = "p1", createdAt = 1))
        dao.insert(OutboxEntity(topic = "t", payload = "p2", createdAt = 2))
        val batch = dao.oldest(10)
        assertEquals(listOf("p1", "p2"), batch.map { it.payload })
        dao.deleteById(batch.first().id)
        assertEquals(1, dao.count())
    }

    @Test fun prune_by_ttl_and_cap() = runTest {
        repeat(6) { dao.insert(OutboxEntity(topic = "t", payload = "$it", createdAt = it.toLong())) }
        dao.deleteOlderThan(2)            // removes createdAt 0,1
        dao.trimToMax(2)                  // keep newest 2
        assertEquals(2, dao.count())
        assertEquals(listOf("4", "5"), dao.oldest(10).map { it.payload })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nyasa.notifybridge.data.db.OutboxDaoTest` (emulator/device required)
Expected: FAIL — classes unresolved.

- [ ] **Step 3: Write minimal implementation**

`OutboxEntity.kt`:
```kotlin
package com.nyasa.notifybridge.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topic: String,
    val payload: String,
    val createdAt: Long,
    val attemptCount: Int = 0,
)
```

`OutboxDao.kt`:
```kotlin
package com.nyasa.notifybridge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert suspend fun insert(e: OutboxEntity): Long
    @Query("SELECT * FROM outbox ORDER BY id ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<OutboxEntity>
    @Query("DELETE FROM outbox WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("UPDATE outbox SET attemptCount = attemptCount + 1 WHERE id = :id")
    suspend fun bumpAttempt(id: Long)
    @Query("SELECT COUNT(*) FROM outbox") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM outbox") fun countFlow(): Flow<Int>
    @Query("DELETE FROM outbox WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
    @Query("DELETE FROM outbox WHERE id NOT IN (SELECT id FROM outbox ORDER BY id DESC LIMIT :max)")
    suspend fun trimToMax(max: Int)
}
```

`NotifyBridgeDatabase.kt`:
```kotlin
package com.nyasa.notifybridge.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [OutboxEntity::class], version = 1, exportSchema = false)
abstract class NotifyBridgeDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nyasa.notifybridge.data.db.OutboxDaoTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add Room outbox entity, DAO, database"
```

---

## Task 7: OutboxRepository (§3.2, §5)

**Files:**
- Create: `domain/repo/OutboxRepository.kt`, `data/db/OutboxRepositoryImpl.kt`
- Test: `app/src/androidTest/java/com/nyasa/notifybridge/data/db/OutboxRepositoryImplTest.kt`

- [ ] **Step 1: Write the failing instrumented test**

```kotlin
package com.nyasa.notifybridge.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nyasa.notifybridge.domain.model.OutboxItem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutboxRepositoryImplTest {
    private lateinit var db: NotifyBridgeDatabase
    private lateinit var repo: OutboxRepositoryImpl

    @Before fun s() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotifyBridgeDatabase::class.java).build()
        repo = OutboxRepositoryImpl(db.outboxDao())
    }
    @After fun e() = db.close()

    @Test fun enqueue_batch_markPublished() = runTest {
        repo.enqueue(OutboxItem(topic = "t", payload = "a", createdAt = 1))
        val b = repo.nextBatch(10)
        assertEquals("a", b.single().payload)
        repo.markPublished(b.single().id)
        assertEquals(emptyList<OutboxItem>(), repo.nextBatch(10))
    }

    @Test fun ttl_only_deletes_expired_without_cap_interference() = runTest {
        // Isolate the TTL path: large maxRows so the cap never fires.
        // createdAt = epoch-ms-like values; cutoff = nowMs - ttlMs.
        repo.enqueue(OutboxItem(topic="t", payload="old", createdAt = 1_000L))
        repo.enqueue(OutboxItem(topic="t", payload="fresh", createdAt = 9_000L))
        repo.pruneExpired(nowMs = 10_000L, ttlMs = 5_000L, maxRows = 1_000)
        // cutoff = 5_000 -> deletes createdAt < 5_000 ("old"), keeps "fresh"
        assertEquals(listOf("fresh"), repo.nextBatch(10).map { it.payload })
    }

    @Test fun cap_only_trims_to_newest_without_ttl_interference() = runTest {
        // Isolate the cap path: ttl window so wide nothing expires.
        repeat(10) { repo.enqueue(OutboxItem(topic="t", payload="$it", createdAt=it.toLong())) }
        repo.pruneExpired(nowMs = 100L, ttlMs = 100L, maxRows = 3) // cutoff = 0, none expire
        assertEquals(listOf("7","8","9"), repo.nextBatch(10).map { it.payload })
    }

    @Test fun ttl_then_cap_combined() = runTest {
        repeat(10) { repo.enqueue(OutboxItem(topic="t", payload="$it", createdAt=it.toLong())) }
        repo.pruneExpired(nowMs = 5, ttlMs = 0, maxRows = 3)
        // cutoff = 5 -> deletes createdAt < 5 (rows 0,1,2,3,4); 5..9 remain;
        // cap 3 -> keep newest by id -> 7,8,9
        assertEquals(listOf("7","8","9"), repo.nextBatch(10).map { it.payload })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nyasa.notifybridge.data.db.OutboxRepositoryImplTest`
Expected: FAIL — classes unresolved.

- [ ] **Step 3: Write minimal implementation**

`domain/repo/OutboxRepository.kt`:
```kotlin
package com.nyasa.notifybridge.domain.repo

import com.nyasa.notifybridge.domain.model.OutboxItem
import kotlinx.coroutines.flow.Flow

interface OutboxRepository {
    suspend fun enqueue(item: OutboxItem)
    suspend fun nextBatch(limit: Int): List<OutboxItem>
    suspend fun markPublished(id: Long)
    suspend fun recordFailure(id: Long)
    suspend fun pruneExpired(nowMs: Long, ttlMs: Long, maxRows: Int)
    fun depth(): Flow<Int>
}
```

`data/db/OutboxRepositoryImpl.kt`:
```kotlin
package com.nyasa.notifybridge.data.db

import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class OutboxRepositoryImpl @Inject constructor(
    private val dao: OutboxDao,
) : OutboxRepository {
    override suspend fun enqueue(item: OutboxItem) =
        dao.insert(OutboxEntity(
            topic = item.topic, payload = item.payload,
            createdAt = item.createdAt, attemptCount = item.attemptCount)).let {}
    override suspend fun nextBatch(limit: Int): List<OutboxItem> =
        dao.oldest(limit).map {
            OutboxItem(it.id, it.topic, it.payload, it.createdAt, it.attemptCount) }
    override suspend fun markPublished(id: Long) = dao.deleteById(id)
    override suspend fun recordFailure(id: Long) = dao.bumpAttempt(id)
    override suspend fun pruneExpired(nowMs: Long, ttlMs: Long, maxRows: Int) {
        dao.deleteOlderThan(nowMs - ttlMs)
        dao.trimToMax(maxRows)
    }
    override fun depth(): Flow<Int> = dao.countFlow()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nyasa.notifybridge.data.db.OutboxRepositoryImplTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add OutboxRepository with TTL + cap pruning"
```

---

## Task 8: SettingsRepository (DataStore, §3.5/§3.8)

**Files:**
- Create: `domain/repo/SettingsRepository.kt`, `data/settings/SettingsRepositoryImpl.kt`
- Test: `app/src/test/java/com/nyasa/notifybridge/data/settings/SettingsRepositoryImplTest.kt`

- [ ] **Step 1: Write the failing test** (Robolectric + temp DataStore)

```kotlin
package com.nyasa.notifybridge.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.TlsMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryImplTest {
    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File(ApplicationProvider.getApplicationContext<android.content.Context>()
                .cacheDir, "t${System.nanoTime()}.preferences_pb") }

    @Test fun broker_config_roundtrips() = runTest {
        val repo = SettingsRepositoryImpl(store())
        repo.setBrokerConfig(BrokerConfig(host = "h", port = 8883,
            tlsMode = TlsMode.PINNED, deviceName = "Pixel 7"))
        repo.brokerConfig.test {
            val c = awaitItem()
            assertEquals("h", c.host); assertEquals(8883, c.port)
            assertEquals(TlsMode.PINNED, c.tlsMode)
        }
    }

    @Test fun allow_list_roundtrips() = runTest {
        val repo = SettingsRepositoryImpl(store())
        repo.setAllowList(setOf("com.a", "com.b"))
        repo.allowList.test { assertEquals(setOf("com.a","com.b"), awaitItem()) }
    }

    @Test fun app_lock_defaults_enabled() = runTest {
        SettingsRepositoryImpl(store()).appLock.test {
            assertEquals(true, awaitItem().enabled)
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsRepositoryImplTest*"`
Expected: FAIL — classes unresolved.

- [ ] **Step 3: Write minimal implementation**

`domain/repo/SettingsRepository.kt`:
```kotlin
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
```

`data/settings/SettingsRepositoryImpl.kt`:
```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*SettingsRepositoryImplTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add SettingsRepository over DataStore"
```

---

## Task 9: MqttClientManager interface + fake

**Files:**
- Create: `domain/mqtt/MqttClientManager.kt`
- Create: `app/src/test/java/com/nyasa/notifybridge/fakes/FakeMqttClientManager.kt`
- Test: `app/src/test/java/com/nyasa/notifybridge/fakes/FakeMqttClientManagerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.nyasa.notifybridge.fakes

import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeMqttClientManagerTest {
    @Test fun connect_sets_state_and_records_publishes() = runTest {
        val m = FakeMqttClientManager()
        m.connect(BrokerConfig(host = "h"))
        assertEquals(ConnectionState.CONNECTED, m.connectionState.value)
        assertTrue(m.publish("t", "p", 1, false))
        assertEquals("t" to "p", m.published.single().let { it.first to it.second })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*FakeMqttClientManagerTest*"`
Expected: FAIL — classes unresolved.

- [ ] **Step 3: Write minimal implementation**

`domain/mqtt/MqttClientManager.kt`:
```kotlin
package com.nyasa.notifybridge.domain.mqtt

import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow

interface MqttClientManager {
    val connectionState: StateFlow<ConnectionState>
    suspend fun connect(config: BrokerConfig)
    suspend fun publish(topic: String, payload: String, qos: Int, retained: Boolean): Boolean
    suspend fun disconnect()
}
```

`fakes/FakeMqttClientManager.kt`:
```kotlin
package com.nyasa.notifybridge.fakes

import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMqttClientManager(
    var connectSucceeds: Boolean = true,
) : MqttClientManager {
    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = state
    val published = mutableListOf<Triple<String, String, Boolean>>()
    var failPublish = false

    override suspend fun connect(config: BrokerConfig) {
        state.value = if (connectSucceeds) ConnectionState.CONNECTED
        else ConnectionState.ERROR
    }
    override suspend fun publish(topic: String, payload: String, qos: Int, retained: Boolean): Boolean {
        if (failPublish) return false
        published += Triple(topic, payload, retained)
        return true
    }
    override suspend fun disconnect() { state.value = ConnectionState.DISCONNECTED }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*FakeMqttClientManagerTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add MqttClientManager interface and test fake"
```

---

## Task 10: Use cases (enqueue / drain / test connection)

**Files:**
- Create: `domain/usecase/EnqueueNotificationUseCase.kt`, `DrainOutboxUseCase.kt`, `TestConnectionUseCase.kt`
- Test: `app/src/test/java/com/nyasa/notifybridge/domain/usecase/UseCasesTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.discovery.DiscoveryPayloadBuilder
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.CapturedNotification
import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import com.nyasa.notifybridge.fakes.FakeMqttClientManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemOutbox : OutboxRepository {
    val items = mutableListOf<OutboxItem>()
    var seq = 0L
    override suspend fun enqueue(item: OutboxItem) { items += item.copy(id = ++seq) }
    override suspend fun nextBatch(limit: Int) = items.take(limit)
    override suspend fun markPublished(id: Long) { items.removeAll { it.id == id } }
    override suspend fun recordFailure(id: Long) {}
    override suspend fun pruneExpired(nowMs: Long, ttlMs: Long, maxRows: Int) {}
    override fun depth(): Flow<Int> = flowOf(items.size)
}

class UseCasesTest {
    private val n = CapturedNotification("com.x","X","t","b",null,null,null,9L,false,true,null,1)

    @Test fun enqueue_builds_event_payload_on_state_topic() = runTest {
        val ob = MemOutbox()
        EnqueueNotificationUseCase(ob, DiscoveryPayloadBuilder())(n, "Pixel 7")
        assertEquals("notifybridge/pixel-7/notification", ob.items.single().topic)
        assertTrue(ob.items.single().payload.contains("\"package\":\"com.x\""))
    }

    @Test fun drain_publishes_then_marks_published() = runTest {
        val ob = MemOutbox()
        ob.enqueue(OutboxItem(topic = "t", payload = "p", createdAt = 1))
        val mqtt = FakeMqttClientManager()
        DrainOutboxUseCase(ob, mqtt)()
        assertEquals("t" to "p", mqtt.published.single().let { it.first to it.second })
        assertEquals(0, ob.items.size)
    }

    @Test fun drain_keeps_item_on_publish_failure() = runTest {
        val ob = MemOutbox()
        ob.enqueue(OutboxItem(topic = "t", payload = "p", createdAt = 1))
        val mqtt = FakeMqttClientManager().apply { failPublish = true }
        DrainOutboxUseCase(ob, mqtt)()
        assertEquals(1, ob.items.size)
    }

    @Test fun drain_stops_on_first_failure_preserving_order() = runTest {
        // Two items, first publish fails: item 2 must NOT be published ahead
        // of item 1. Proves the stop-on-failure (ordered) behaviour is
        // intentional and covered, not an accident of single-item batches.
        val ob = MemOutbox()
        ob.enqueue(OutboxItem(topic = "t", payload = "p1", createdAt = 1))
        ob.enqueue(OutboxItem(topic = "t", payload = "p2", createdAt = 2))
        val mqtt = FakeMqttClientManager().apply { failPublish = true }
        DrainOutboxUseCase(ob, mqtt)()
        assertEquals(emptyList<Triple<String,String,Boolean>>(), mqtt.published)
        assertEquals(2, ob.items.size)
    }

    @Test fun test_connection_returns_true_on_connect() = runTest {
        val r = TestConnectionUseCase(FakeMqttClientManager())(BrokerConfig(host = "h"))
        assertTrue(r)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*UseCasesTest*"`
Expected: FAIL — use case classes unresolved.

- [ ] **Step 3: Write minimal implementation**

`EnqueueNotificationUseCase.kt`:
```kotlin
package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.discovery.DiscoveryPayloadBuilder
import com.nyasa.notifybridge.domain.model.CapturedNotification
import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import javax.inject.Inject

class EnqueueNotificationUseCase @Inject constructor(
    private val outbox: OutboxRepository,
    private val discovery: DiscoveryPayloadBuilder,
) {
    suspend operator fun invoke(n: CapturedNotification, device: String) {
        outbox.enqueue(OutboxItem(
            topic = discovery.stateTopic(device),
            payload = discovery.eventPayload(n),
            createdAt = n.postTime))
    }
}
```

`DrainOutboxUseCase.kt`:
```kotlin
package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import javax.inject.Inject

class DrainOutboxUseCase @Inject constructor(
    private val outbox: OutboxRepository,
    private val mqtt: MqttClientManager,
) {
    suspend operator fun invoke(batch: Int = 50) {
        for (item in outbox.nextBatch(batch)) {
            val ok = mqtt.publish(item.topic, item.payload, qos = 1, retained = false)
            // Stop on first failure — preserves delivery order. Skipping ahead
            // would reorder notifications; the remaining items are retried on
            // the next drain (foreground service / 15-min worker).
            if (ok) outbox.markPublished(item.id) else { outbox.recordFailure(item.id); break }
        }
    }
}
```

`TestConnectionUseCase.kt`:
```kotlin
package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import javax.inject.Inject

class TestConnectionUseCase @Inject constructor(
    private val mqtt: MqttClientManager,
) {
    suspend operator fun invoke(config: BrokerConfig): Boolean {
        mqtt.connect(config)
        return mqtt.connectionState.value == ConnectionState.CONNECTED
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*UseCasesTest*"`
Expected: PASS (4).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add enqueue/drain/test-connection use cases"
```

---

## Task 11: HiveMqClientManager implementation (§3.3)

**Files:**
- Create: `data/mqtt/HiveMqClientManager.kt`
- Test: `app/src/test/java/com/nyasa/notifybridge/data/mqtt/HiveMqOptionsTest.kt`

Live broker behavior is not unit-testable; unit-test only the pure config→options mapping. Full connect/LWT/discovery verified in Task 25 manual checklist.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.nyasa.notifybridge.data.mqtt

import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.TlsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiveMqOptionsTest {
    @Test fun tls_off_means_no_ssl() {
        assertFalse(HiveMqClientManager.useTls(BrokerConfig(tlsMode = TlsMode.OFF)))
    }
    @Test fun pinned_requires_cert() {
        assertTrue(HiveMqClientManager.useTls(BrokerConfig(tlsMode = TlsMode.PINNED)))
        assertEquals(true, HiveMqClientManager.requiresPinnedCert(
            BrokerConfig(tlsMode = TlsMode.PINNED)))
        assertEquals(false, HiveMqClientManager.requiresPinnedCert(
            BrokerConfig(tlsMode = TlsMode.SYSTEM_CA)))
    }
    @Test fun client_id_is_stable_per_device() {
        assertEquals(
            HiveMqClientManager.clientId(BrokerConfig(deviceName = "Pixel 7")),
            HiveMqClientManager.clientId(BrokerConfig(deviceName = "Pixel 7")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*HiveMqOptionsTest*"`
Expected: FAIL — class unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.nyasa.notifybridge.data.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.nyasa.notifybridge.domain.discovery.DiscoveryPayloadBuilder
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.ConnectionState
import com.nyasa.notifybridge.domain.model.TlsMode
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import kotlinx.coroutines.future.await
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HiveMqClientManager @Inject constructor(
    private val discovery: DiscoveryPayloadBuilder,
) : MqttClientManager {

    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState = state
    private var client: Mqtt5AsyncClient? = null
    private var device: String = "phone"

    override suspend fun connect(config: BrokerConfig) {
        state.value = ConnectionState.CONNECTING
        device = config.deviceName
        val builder = MqttClient.builder().useMqttVersion5()
            .identifier(clientId(config))
            .serverHost(config.host)
            .serverPort(config.port)
            .automaticReconnectWithDefaultConfig()
            .addDisconnectedListener { state.value = ConnectionState.DISCONNECTED }
        if (useTls(config)) {
            if (requiresPinnedCert(config)) {
                // Do NOT silently fall back to system-CA — that is the exact
                // validation downgrade §3.3/S4 forbids. Fail loud until the
                // pinned trust manager lands in Task 25.
                state.value = ConnectionState.ERROR
                throw IllegalStateException(
                    "Pinned-cert TLS not yet implemented (Task 25). " +
                    "Use TLS OFF or SYSTEM_CA until then.")
            }
            builder.sslWithDefaultConfig()
        }
        val c = builder.buildAsync()
        client = c
        try {
            val connect = c.connectWith()
                .willPublish()
                    .topic(discovery.statusTopic(device))
                    .payload("offline".toByteArray())
                    .applyWillPublish()
            if (!config.username.isNullOrBlank()) {
                connect.simpleAuth()
                    .username(config.username)
                    .password((config.password ?: "").toByteArray())
                    .applySimpleAuth()
            }
            connect.send().await()
            c.publishWith().topic(discovery.statusTopic(device))
                .payload("online".toByteArray()).retain(true)
                .qos(MqttQos.AT_LEAST_ONCE).send().await()
            c.publishWith().topic(discovery.discoveryTopic(device))
                .payload(discovery.discoveryConfig(device).toByteArray()).retain(true)
                .qos(MqttQos.AT_LEAST_ONCE).send().await()
            state.value = ConnectionState.CONNECTED
        } catch (t: Throwable) {
            state.value = ConnectionState.ERROR
        }
    }

    override suspend fun publish(topic: String, payload: String, qos: Int, retained: Boolean): Boolean {
        val c = client ?: return false
        return try {
            c.publishWith().topic(topic).payload(payload.toByteArray())
                .qos(if (qos >= 1) MqttQos.AT_LEAST_ONCE else MqttQos.AT_MOST_ONCE)
                .retain(retained).send().await()
            true
        } catch (t: Throwable) { false }
    }

    override suspend fun disconnect() {
        runCatching { client?.disconnect()?.await() }
        state.value = ConnectionState.DISCONNECTED
    }

    companion object {
        fun useTls(c: BrokerConfig) = c.tlsMode != TlsMode.OFF
        fun requiresPinnedCert(c: BrokerConfig) = c.tlsMode == TlsMode.PINNED
        fun clientId(c: BrokerConfig) =
            "notifybridge-" + c.deviceName.lowercase().replace(Regex("[^a-z0-9]+"), "-")
    }
}
```

> Note: pinned-cert trust (`requiresPinnedCert`) wiring into `sslConfig().trustManagerFactory(...)` is completed in Task 25 against a real PEM. Until then PINNED **throws loudly** (above) rather than silently downgrading to system-CA — a silent downgrade would be the precise validation footgun §3.3/S4 rejects. The Broker UI (Task 21) must also disable/grey the PINNED option with a "coming soon" note until Task 25 lands, so the throw is never user-reachable in a shipped build.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*HiveMqOptionsTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add HiveMQ client manager (connect, LWT, discovery, publish)"
```

---

## Task 12: Hilt DI wiring

**Files:**
- Create: `data/di/DatabaseModule.kt`, `RepositoryModule.kt`, `MqttModule.kt`, `DispatchersModule.kt`
- Test: `app/src/androidTest/java/com/nyasa/notifybridge/di/HiltGraphTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```
Add the Hilt test deps + runner. In `app/build.gradle.kts` add to `dependencies`:
```kotlin
androidTestImplementation("com.google.dagger:hilt-android-testing:2.52")
kspAndroidTest("com.google.dagger:hilt-compiler:2.52")
```
And in `android.defaultConfig`, change the runner line from:
```kotlin
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
```
to:
```kotlin
testInstrumentationRunner = "com.nyasa.notifybridge.HiltTestRunner"
```
Create `app/src/androidTest/java/com/nyasa/notifybridge/HiltTestRunner.kt`:
```kotlin
package com.nyasa.notifybridge

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader, className: String, context: Context,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nyasa.notifybridge.di.HiltGraphTest`
Expected: FAIL — no bindings.

- [ ] **Step 3: Write minimal implementation**

`DispatchersModule.kt`:
```kotlin
package com.nyasa.notifybridge.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier annotation class IoDispatcher

@Module @InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides @IoDispatcher @Singleton
    fun io(): CoroutineDispatcher = Dispatchers.IO
}
```

`DatabaseModule.kt`:
```kotlin
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
```

`RepositoryModule.kt`:
```kotlin
package com.nyasa.notifybridge.data.di

import com.nyasa.notifybridge.data.db.OutboxRepositoryImpl
import com.nyasa.notifybridge.data.notif.NotificationMapperImpl
import com.nyasa.notifybridge.data.settings.SettingsRepositoryImpl
import com.nyasa.notifybridge.domain.notif.NotificationMapper
import com.nyasa.notifybridge.domain.repo.OutboxRepository
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
}
```

`MqttModule.kt`:
```kotlin
package com.nyasa.notifybridge.data.di

import com.nyasa.notifybridge.data.mqtt.HiveMqClientManager
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module @InstallIn(SingletonComponent::class)
abstract class MqttModule {
    @Binds abstract fun mqtt(i: HiveMqClientManager): MqttClientManager
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nyasa.notifybridge.di.HiltGraphTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Wire Hilt modules"
```

---

## Task 13: MqttForegroundService (§3.3)

**Files:**
- Create: `service/MqttForegroundService.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add service)
- Test: manual (foreground services are not unit-testable); logic already covered by DrainOutboxUseCase tests.

- [ ] **Step 1: Implement the service**

```kotlin
package com.nyasa.notifybridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import com.nyasa.notifybridge.domain.usecase.DrainOutboxUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MqttForegroundService : Service() {
    @Inject lateinit var mqtt: MqttClientManager
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var drain: DrainOutboxUseCase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        scope.launch {
            val cfg = settings.brokerConfig.first()
            if (cfg.host.isNotBlank()) {
                mqtt.connect(cfg)
                drain()
            }
        }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        scope.launch { drain() }
        return START_STICKY
    }

    private fun startAsForeground() {
        val ch = "bridge"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ch, "Bridge", NotificationManager.IMPORTANCE_LOW))
        val n: Notification = Notification.Builder(this, ch)
            .setContentTitle("NotifyBridge active")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else startForeground(1, n)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null
}
```

Add to manifest `<application>`:
```xml
<service
    android:name=".service.MqttForegroundService"
    android:exported="false"
    android:foregroundServiceType="connectedDevice" />
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "Add MQTT foreground service (connectedDevice type)"
```

---

## Task 14: NotifListenerService (§3.1)

**Files:**
- Create: `service/NotifListenerService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/nyasa/notifybridge/service/NotifListenerLogicTest.kt` (test the pure pipeline helper)

- [ ] **Step 1: Write the failing test** for the extracted pure filter+pipeline

```kotlin
package com.nyasa.notifybridge.service

import com.nyasa.notifybridge.domain.model.CapturedNotification
import com.nyasa.notifybridge.domain.notif.NotificationDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Test

class NotifListenerLogicTest {
    private fun n(pkg: String) =
        CapturedNotification(pkg,"L","t","b",null,null,null,1L,false,true,null,1)

    @Test fun only_allowlisted_non_self_pass_dedup() {
        val accepted = mutableListOf<String>()
        val pipeline = NotifPipeline(
            allowList = { setOf("com.a") },
            selfPackage = "com.nyasa.notifybridge",
            dedup = NotificationDeduplicator(),
            onAccepted = { accepted += it.packageName })
        pipeline.handle(n("com.a"), 1000L)
        pipeline.handle(n("com.b"), 1000L)                       // not allow-listed
        pipeline.handle(n("com.nyasa.notifybridge"), 1000L)      // self
        assertEquals(listOf("com.a"), accepted)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*NotifListenerLogicTest*"`
Expected: FAIL — `NotifPipeline` unresolved.

- [ ] **Step 3: Write minimal implementation**

`service/NotifListenerService.kt`:
```kotlin
package com.nyasa.notifybridge.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nyasa.notifybridge.domain.model.CapturedNotification
import com.nyasa.notifybridge.domain.notif.NotificationDeduplicator
import com.nyasa.notifybridge.domain.notif.NotificationMapper
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import com.nyasa.notifybridge.domain.usecase.EnqueueNotificationUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class NotifPipeline(
    private val allowList: () -> Set<String>,
    private val selfPackage: String,
    private val dedup: NotificationDeduplicator,
    private val onAccepted: (CapturedNotification) -> Unit,
) {
    fun handle(n: CapturedNotification, nowMs: Long) {
        if (n.packageName == selfPackage) return
        if (n.packageName !in allowList()) return
        if (!dedup.shouldForward(n, nowMs)) return
        onAccepted(n)
    }
}

@AndroidEntryPoint
class NotifListenerService : NotificationListenerService() {
    @Inject lateinit var mapper: NotificationMapper
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var enqueue: EnqueueNotificationUseCase
    private val dedup = NotificationDeduplicator()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var pipeline: NotifPipeline
    @Volatile private var cachedAllow: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        scope.launch { settings.allowList.collect { cachedAllow = it } }
        pipeline = NotifPipeline(
            allowList = { cachedAllow },
            selfPackage = packageName,
            dedup = dedup,
            onAccepted = { n ->
                scope.launch {
                    val device = settings.brokerConfig.first().deviceName
                    enqueue(n, device)
                    startService(Intent(this@NotifListenerService,
                        MqttForegroundService::class.java))
                }
            })
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val captured = runCatching { mapper.map(sbn, label) }.getOrNull() ?: return
        runCatching { pipeline.handle(captured, System.currentTimeMillis()) }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
```

Add to manifest `<application>`:
```xml
<service
    android:name=".service.NotifListenerService"
    android:exported="false"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*NotifListenerLogicTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add NotificationListenerService with filter+dedup pipeline"
```

---

## Task 15: BootReceiver + periodic drain worker (§3.2, W3)

**Files:**
- Create: `service/OutboxDrainWorker.kt`, `service/BootReceiver.kt`
- Modify: manifest
- Test: build-only (WorkManager scheduling verified manually in Task 25)

- [ ] **Step 1: Implement worker + receiver**

`OutboxDrainWorker.kt`:
```kotlin
package com.nyasa.notifybridge.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import com.nyasa.notifybridge.domain.usecase.DrainOutboxUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class OutboxDrainWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val mqtt: MqttClientManager,
    private val drain: DrainOutboxUseCase,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val cfg = settings.brokerConfig.first()
        if (cfg.host.isBlank()) return Result.success()
        mqtt.connect(cfg)
        drain()
        return Result.success()
    }
}
```

`BootReceiver.kt`:
```kotlin
package com.nyasa.notifybridge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "outbox-drain",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<OutboxDrainWorker>(15, TimeUnit.MINUTES).build())
        // BOOT_COMPLETED receivers are exempt from the Android 12+ (API 31+)
        // background-start restrictions, so startForegroundService is allowed
        // here. Do not "simplify" this away. The service calls
        // startForeground() in onCreate within the required window.
        context.startForegroundService(
            Intent(context, MqttForegroundService::class.java))
    }
}
```

Add to manifest `<application>`:
```xml
<receiver android:name=".service.BootReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "Add boot receiver + 15-min periodic drain worker"
```

---

## Task 16: AppLockManager idle-timeout state machine (§3.8)

**Files:**
- Create: `applock/AppLockManager.kt`
- Test: `app/src/test/java/com/nyasa/notifybridge/applock/AppLockManagerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.nyasa.notifybridge.applock

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockManagerTest {
    @Test fun starts_locked_when_enabled() = runTest {
        val m = AppLockManager(enabled = { true }, idleMs = { 1000 })
        m.locked.test { assertEquals(true, awaitItem()) }
    }
    @Test fun unlock_then_relock_after_timeout() = runTest {
        val m = AppLockManager(enabled = { true }, idleMs = { 1000 })
        m.onAuthenticated()
        m.locked.test { assertEquals(false, awaitItem()) }
        m.onBackgrounded(atMs = 0)
        m.onForegrounded(atMs = 1500)            // exceeded idle window
        m.locked.test { assertEquals(true, awaitItem()) }
    }
    @Test fun within_timeout_stays_unlocked() = runTest {
        val m = AppLockManager(enabled = { true }, idleMs = { 1000 })
        m.onAuthenticated()
        m.onBackgrounded(atMs = 0)
        m.onForegrounded(atMs = 500)
        m.locked.test { assertEquals(false, awaitItem()) }
    }
    @Test fun disabled_never_locks() = runTest {
        val m = AppLockManager(enabled = { false }, idleMs = { 1 })
        m.locked.test { assertEquals(false, awaitItem()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*AppLockManagerTest*"`
Expected: FAIL — class unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.nyasa.notifybridge.applock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppLockManager(
    private val enabled: () -> Boolean,
    private val idleMs: () -> Long,
) {
    private val _locked = MutableStateFlow(enabled())
    val locked: StateFlow<Boolean> = _locked
    private var backgroundedAt: Long? = null

    fun onAuthenticated() { _locked.value = false }
    fun onBackgrounded(atMs: Long) { backgroundedAt = atMs }
    fun onForegrounded(atMs: Long) {
        if (!enabled()) { _locked.value = false; return }
        val bg = backgroundedAt ?: return
        if (atMs - bg >= idleMs()) _locked.value = true
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*AppLockManagerTest*"`
Expected: PASS (4).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add app-lock idle-timeout state machine"
```

---

## Task 17: BiometricAuthenticator + AppLockGate (§3.8, version caveat)

**Files:**
- Create: `applock/BiometricAuthenticator.kt`, `applock/AppLockGate.kt`
- Test: `app/src/androidTest/java/com/nyasa/notifybridge/applock/BiometricAuthenticatorTest.kt` (smoke)

- [ ] **Step 1: Write the failing smoke test**

```kotlin
package com.nyasa.notifybridge.applock

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiometricAuthenticatorTest {
    @Test fun canAuthenticate_returns_a_status() {
        val a = BiometricAuthenticator(ApplicationProvider.getApplicationContext())
        assertNotNull(a.availability())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nyasa.notifybridge.applock.BiometricAuthenticatorTest`
Expected: FAIL — class unresolved.

- [ ] **Step 3: Write minimal implementation**

`applock/BiometricAuthenticator.kt`:
```kotlin
package com.nyasa.notifybridge.applock

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class LockAvailability { AVAILABLE, NONE_ENROLLED, UNSUPPORTED }

class BiometricAuthenticator(private val context: Context) {
    fun availability(): LockAvailability {
        val bm = BiometricManager.from(context)
        val combined = bm.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        if (combined == BiometricManager.BIOMETRIC_SUCCESS) return LockAvailability.AVAILABLE
        val km = context.getSystemService(KeyguardManager::class.java)
        return if (km?.isDeviceSecure == true) LockAvailability.AVAILABLE
        else LockAvailability.NONE_ENROLLED
    }

    /** API 30+: combined authenticators. API 26–29: device-credential fallback
     *  is handled by the prompt's negative path; see §3.8 version caveat. */
    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit, onFail: () -> Unit) {
        val prompt = BiometricPrompt(activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) =
                    onSuccess()
                override fun onAuthenticationError(c: Int, s: CharSequence) = onFail()
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock NotifyBridge")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }
}
```

`applock/AppLockGate.kt`:
```kotlin
package com.nyasa.notifybridge.applock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun AppLockGate(
    manager: AppLockManager,
    locked: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val isLocked by manager.locked.collectAsState()
    if (isLocked) locked() else content()
}
```

> `MainActivity` must extend `FragmentActivity` (required by `BiometricPrompt`). Update Task 1's `MainActivity` base class to `FragmentActivity` when wiring Task 18.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nyasa.notifybridge.applock.BiometricAuthenticatorTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Add biometric authenticator + app-lock gate"
```

---

## Task 18: Theme + nav scaffold + MainActivity wiring (§3.7, §3.8)

> **Correction (post-impl):** the `appLock.collect` block must also `if (!it.enabled) lock.onAuthenticated()`. Without it, a user who has *disabled* app-lock is stuck on `LockedScreen` on every cold start: `_locked` initializes `true` (enabled-by-default) and the `hasStartedOnce` cold-open guard skips `onForegrounded`, so nothing unlocks them until a background/foreground cycle. (Separately, the API 26–29 `BiometricAuthenticator.prompt()` crash from the §3.8 version caveat remains a documented must-fix-before-release: effective app-lock minSdk is 30 until `prompt()` is SDK-branched.)

**Files:**
- Create: `ui/theme/Color.kt`, `Type.kt`, `Theme.kt`, `ui/NotifyBridgeNavHost.kt`
- Modify: `MainActivity.kt`
- Test: build + manual

- [ ] **Step 1: Implement theme and nav**

`ui/theme/Color.kt`:
```kotlin
package com.nyasa.notifybridge.ui.theme
import androidx.compose.ui.graphics.Color
val Teal = Color(0xFF2DD4BF)
val Amber = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFF87171)
val BgBase = Color(0xFF0B0F0E)
val Surface = Color(0xFF14201E)
```

`ui/theme/Theme.kt`:
```kotlin
package com.nyasa.notifybridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Scheme = darkColorScheme(
    primary = Teal, background = BgBase, surface = Surface, error = ErrorRed)

@Composable
fun NotifyBridgeTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Scheme, content = content)
```

`ui/theme/Type.kt`:
```kotlin
package com.nyasa.notifybridge.ui.theme
import androidx.compose.material3.Typography
val NbTypography = Typography()   // default scale; per-screen mono via FontFamily.Monospace
```

`ui/NotifyBridgeNavHost.kt`:
```kotlin
package com.nyasa.notifybridge.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nyasa.notifybridge.ui.apps.AppsScreen
import com.nyasa.notifybridge.ui.broker.BrokerScreen
import com.nyasa.notifybridge.ui.onboarding.OnboardingScreen
import com.nyasa.notifybridge.ui.permissions.PermissionsScreen
import com.nyasa.notifybridge.ui.status.StatusScreen

@Composable
fun NotifyBridgeNavHost(startOnboarding: Boolean) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = if (startOnboarding) "onboarding" else "status") {
        composable("onboarding") { OnboardingScreen(nav) }
        composable("status") { StatusScreen(nav) }
        composable("broker") { BrokerScreen(nav) }
        composable("permissions") { PermissionsScreen(nav) }
        composable("apps") { AppsScreen(nav) }
    }
}
```

`MainActivity.kt` (replace body):
```kotlin
package com.nyasa.notifybridge

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.nyasa.notifybridge.applock.AppLockGate
import com.nyasa.notifybridge.applock.AppLockManager
import com.nyasa.notifybridge.applock.BiometricAuthenticator
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import com.nyasa.notifybridge.ui.NotifyBridgeNavHost
import com.nyasa.notifybridge.ui.locked.LockedScreen
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var settings: SettingsRepository
    private lateinit var lock: AppLockManager
    private var prefsEnabled = true
    private var idle = 60_000L

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        lock = AppLockManager(enabled = { prefsEnabled }, idleMs = { idle })
        lifecycleScope.launch {
            settings.appLock.collect {
                prefsEnabled = it.enabled; idle = it.idleTimeoutMs
                // Cold start: _locked initializes true (enabled-by-default). Once
                // prefs load, unlock if the user has app-lock disabled — onStart
                // is skipped on cold open (hasStartedOnce), so nothing else would.
                if (!it.enabled) lock.onAuthenticated()
            }
        }
        val auth = BiometricAuthenticator(this)
        setContent {
            NotifyBridgeTheme {
                AppLockGate(
                    manager = lock,
                    locked = {
                        LockedScreen(onUnlock = {
                            auth.prompt(this, onSuccess = { lock.onAuthenticated() },
                                onFail = {})
                        })
                    },
                    content = { NotifyBridgeNavHost(startOnboarding = false) })
            }
        }
        // FLAG_SECURE applied per-screen in Status/Broker (Task 20/21).
    }

    private var hasStartedOnce = false
    override fun onStop() { super.onStop(); lock.onBackgrounded(System.currentTimeMillis()) }
    override fun onStart() {
        super.onStart()
        // Skip the first onStart (cold open): there was no prior background
        // session to time, and the initial lock state already reflects the
        // pref. (The old `currentState != INITIALIZED` guard was dead code —
        // by onStart the state is never INITIALIZED — and only worked by
        // accident via AppLockManager's null backgroundedAt guard.)
        if (hasStartedOnce) lock.onForegrounded(System.currentTimeMillis())
        hasStartedOnce = true
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (fails until Tasks 19–24 screens exist — implement those screens, then this compiles; this task lands the wiring, screens follow).

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "Add theme, nav host, lock-gated MainActivity"
```

---

## Tasks 19–24: Screens

> **REQUIRED at execution time — read before writing any composable in Tasks 19–23.**
> These tasks deliberately give the full ViewModel + tested pure logic but
> describe the composable layout in prose rather than pixel code (writing
> throwaway pixel markup here would bloat the plan and be rebuilt anyway —
> see the plan header's "Compose UI note"). The composable layout source of
> truth is the Stitch project. Before implementing each screen, fetch its
> Stitch screen and build the composable to match it:
> Stitch project `6833643636006727286`, screen IDs cited per task below
> (Onboarding `8adb31430c394a8cbe7b5a10e492a71b`, Status
> `b706f67ff307452fa027d44711ebdf3d`, Broker
> `59dfbc89acfd4e3b8016f034cbee817b`, Permissions
> `f0c268d81fc34bf18629428b48f6f276`, Apps
> `661a1b472f9b4433b57f85b9842e768c`). Use the Stitch MCP `get_screen` (or
> the screenshots in the Stitch web app) as the visual contract; the
> ViewModel/state/events in each task are the behavioural contract and are
> non-negotiable. If Stitch is unreachable at execution time, stop and
> surface that — do not improvise the layout.

Each screen task follows the same shape. ViewModel logic is unit-tested; the composable wires state and is verified by a Compose UI test for its one critical behavior plus a manual check against the Stitch reference above.

### Task 19: Onboarding (§3.6 — screen `8adb31430c394a8cbe7b5a10e492a71b`)

**Files:** `ui/onboarding/OnboardingViewModel.kt`, `OnboardingScreen.kt`
**Test:** `app/src/test/java/com/nyasa/notifybridge/ui/onboarding/OnboardingViewModelTest.kt`

- [ ] **Step 1: Failing test**
```kotlin
package com.nyasa.notifybridge.ui.onboarding
import org.junit.Assert.assertEquals
import org.junit.Test
class OnboardingViewModelTest {
    @Test fun steps_gate_sequentially() {
        val s = onboardingState(notifAccess = false, brokerSet = false, appsChosen = false)
        assertEquals(OnboardingStep.GRANT_ACCESS, s.activeStep)
        val s2 = onboardingState(notifAccess = true, brokerSet = false, appsChosen = false)
        assertEquals(OnboardingStep.CONNECT_BROKER, s2.activeStep)
        val s3 = onboardingState(notifAccess = true, brokerSet = true, appsChosen = true)
        assertEquals(OnboardingStep.DONE, s3.activeStep)
    }
}
```
- [ ] **Step 2: Run → FAIL** `./gradlew :app:testDebugUnitTest --tests "*OnboardingViewModelTest*"`
- [ ] **Step 3: Implement**
```kotlin
package com.nyasa.notifybridge.ui.onboarding
enum class OnboardingStep { GRANT_ACCESS, CONNECT_BROKER, CHOOSE_APPS, DONE }
data class OnboardingUiState(val activeStep: OnboardingStep)
fun onboardingState(notifAccess: Boolean, brokerSet: Boolean, appsChosen: Boolean) =
    OnboardingUiState(when {
        !notifAccess -> OnboardingStep.GRANT_ACCESS
        !brokerSet -> OnboardingStep.CONNECT_BROKER
        !appsChosen -> OnboardingStep.CHOOSE_APPS
        else -> OnboardingStep.DONE
    })
```
`OnboardingScreen.kt` — composable: top bar "NotifyBridge", subtitle "Notifications → MQTT, local only"; a `Column` of 3 step `Card`s driven by `onboardingState(...)` (active step uses `MaterialTheme.colorScheme.primary`, later steps disabled), each with the title/description/CTA from §3.6; muted footer "Everything stays on your network. No cloud, no Google, no Firebase." CTAs navigate: GRANT_ACCESS → `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` intent; CONNECT_BROKER → `nav.navigate("broker")`; CHOOSE_APPS → `nav.navigate("apps")`. Read state in a `@HiltViewModel OnboardingViewModel` exposing notif-access (via `NotificationManagerCompat.getEnabledListenerPackages`), `settings.brokerConfig`, `settings.allowList`.
- [ ] **Step 4: Run → PASS**
- [ ] **Step 5: Commit** `git commit -am "Add onboarding screen"`

### Task 20: Status / Dashboard (§3.5, §3.8 redaction — screen `b706f67ff307452fa027d44711ebdf3d`)

**Files:** `ui/status/StatusViewModel.kt`, `StatusScreen.kt`
**Test:** `StatusViewModelTest.kt`

- [ ] **Step 1: Failing test** — body redaction logic
```kotlin
package com.nyasa.notifybridge.ui.status
import org.junit.Assert.assertEquals
import org.junit.Test
class StatusViewModelTest {
    @Test fun body_redacted_until_revealed() {
        assertEquals("••••••", displayBody("secret", redact = true, revealed = false))
        assertEquals("secret", displayBody("secret", redact = true, revealed = true))
        assertEquals("secret", displayBody("secret", redact = false, revealed = false))
    }
}
```
- [ ] **Step 2: Run → FAIL**
- [ ] **Step 3: Implement**
```kotlin
package com.nyasa.notifybridge.ui.status
fun displayBody(body: String, redact: Boolean, revealed: Boolean): String =
    if (redact && !revealed) "•".repeat(6) else body
```
`StatusViewModel` (`@HiltViewModel`): exposes `mqtt.connectionState`, `outbox.depth()`, `settings.brokerConfig`, `settings.allowList`, `settings.appLock` and a recent-list flow (last N from a lightweight in-memory ring updated by enqueue — for v1, surface from `outbox.nextBatch` parsed, read-only). `StatusScreen` composable: call `window.addFlags(FLAG_SECURE)` via `LocalView`/activity in a `DisposableEffect`; render connection chip (teal CONNECTED / amber CONNECTING / red ERROR), Broker/Outbox/Forwarding cards, "Recent" list using `displayBody(...)` with a tap that fires the biometric prompt then sets `revealed=true` for that row; bottom nav (Status active).
- [ ] **Step 4: Run → PASS**
- [ ] **Step 5: Commit** `git commit -am "Add status dashboard with redacted recent list"`

### Task 21: Broker Setup (§3.5, §3.3 S4 — screen `59dfbc89acfd4e3b8016f034cbee817b`)

**Files:** `ui/broker/BrokerViewModel.kt`, `BrokerScreen.kt`
**Test:** `BrokerViewModelTest.kt`

- [ ] **Step 1: Failing test**
```kotlin
package com.nyasa.notifybridge.ui.broker
import com.nyasa.notifybridge.domain.model.BrokerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class BrokerViewModelTest {
    @Test fun validates_host_and_port() {
        assertFalse(isValid(BrokerConfig(host = "", port = 1883)))
        assertFalse(isValid(BrokerConfig(host = "h", port = 0)))
        assertTrue(isValid(BrokerConfig(host = "h", port = 1883)))
    }
    @Test fun pinned_requires_cert() {
        assertEquals("Select a CA/cert file",
            certError(com.nyasa.notifybridge.domain.model.TlsMode.PINNED, null))
        assertEquals(null,
            certError(com.nyasa.notifybridge.domain.model.TlsMode.PINNED, "PEM"))
    }
}
```
- [ ] **Step 2: Run → FAIL**
- [ ] **Step 3: Implement**
```kotlin
package com.nyasa.notifybridge.ui.broker
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.TlsMode
fun isValid(c: BrokerConfig) = c.host.isNotBlank() && c.port in 1..65535
fun certError(mode: TlsMode, pem: String?): String? =
    if (mode == TlsMode.PINNED && pem.isNullOrBlank()) "Select a CA/cert file" else null
```
`BrokerViewModel` (`@HiltViewModel`): holds editable `BrokerConfig` state; `test()` calls `TestConnectionUseCase` and exposes a result line; `save()` writes via `settings.setBrokerConfig` (guarded by `isValid`) and starts `MqttForegroundService`. `BrokerScreen`: grouped form per §3.5 (mono fields), TLS switch + System-CA/Pinned segmented control + file picker (`ActivityResultContracts.OpenDocument`) reading PEM text, amber caution note, sticky Test/Save with the live result line. Apply `FLAG_SECURE`.
- [ ] **Step 4: Run → PASS**
- [ ] **Step 5: Commit** `git commit -am "Add broker setup screen"`

### Task 22: Permissions + App-lock card (§3.5, §3.8, §8 — screen `f0c268d81fc34bf18629428b48f6f276`)

**Files:** `ui/permissions/PermissionsViewModel.kt`, `PermissionsScreen.kt`
**Test:** `PermissionsViewModelTest.kt`

- [ ] **Step 1: Failing test**
```kotlin
package com.nyasa.notifybridge.ui.permissions
import org.junit.Assert.assertEquals
import org.junit.Test
class PermissionsViewModelTest {
    @Test fun status_pills_map_correctly() {
        assertEquals(PermPill.GRANTED, permPill(granted = true))
        assertEquals(PermPill.ACTION_NEEDED, permPill(granted = false))
    }
}
```
- [ ] **Step 2: Run → FAIL**
- [ ] **Step 3: Implement**
```kotlin
package com.nyasa.notifybridge.ui.permissions
enum class PermPill { GRANTED, ACTION_NEEDED }
fun permPill(granted: Boolean) = if (granted) PermPill.GRANTED else PermPill.ACTION_NEEDED
```
`PermissionsViewModel` (`@HiltViewModel`): exposes notif-access state, battery-exemption state (`PowerManager.isIgnoringBatteryOptimizations`), and `settings.appLock` with setters. `PermissionsScreen`: three permission cards (notification access → settings intent; battery → `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; run-at-startup info with the Android-12 caveat text from §8) + an **App lock** card (enable switch, idle-timeout dropdown, "require auth to reveal body" switch) writing `settings.setAppLock(...)` + the OTP/bank reassurance card. Bottom nav.
- [ ] **Step 4: Run → PASS**
- [ ] **Step 5: Commit** `git commit -am "Add permissions screen with app-lock card"`

### Task 23: Apps allow-list (§3.5 — screen `661a1b472f9b4433b57f85b9842e768c`)

**Files:** `ui/apps/AppsViewModel.kt`, `AppsScreen.kt`
**Test:** `AppsViewModelTest.kt`

- [ ] **Step 1: Failing test**
```kotlin
package com.nyasa.notifybridge.ui.apps
import org.junit.Assert.assertEquals
import org.junit.Test
class AppsViewModelTest {
    private val apps = listOf(
        AppRow("WhatsApp","com.whatsapp",true),
        AppRow("Gmail","com.google.android.gm",false))
    @Test fun search_filters_by_label_or_package() {
        assertEquals(1, filterApps(apps, "whats").size)
        assertEquals(1, filterApps(apps, "gm").size)
        assertEquals(2, filterApps(apps, "").size)
    }
    @Test fun toggle_updates_selection_set() {
        assertEquals(setOf("com.whatsapp","com.x"),
            toggle(setOf("com.whatsapp"), "com.x", on = true))
        assertEquals(emptySet<String>(),
            toggle(setOf("com.whatsapp"), "com.whatsapp", on = false))
    }
}
```
- [ ] **Step 2: Run → FAIL**
- [ ] **Step 3: Implement**
```kotlin
package com.nyasa.notifybridge.ui.apps
data class AppRow(val label: String, val pkg: String, val enabled: Boolean)
fun filterApps(all: List<AppRow>, q: String) =
    if (q.isBlank()) all else all.filter {
        it.label.contains(q, true) || it.pkg.contains(q, true) }
fun toggle(current: Set<String>, pkg: String, on: Boolean) =
    if (on) current + pkg else current - pkg
```
`AppsViewModel` (`@HiltViewModel`): loads launchable apps from `PackageManager`, joins with `settings.allowList`; `setEnabled(pkg,on)` writes `settings.setAllowList(toggle(...))`. `AppsScreen`: top bar "Apps", search field, header strip "N of M apps forwarding", dismissible "Empty by design" banner, list rows (icon, label, mono package, switch, teal left edge when enabled), bottom nav (Apps active).
- [ ] **Step 4: Run → PASS**
- [ ] **Step 5: Commit** `git commit -am "Add apps allow-list screen"`

### Task 24: Locked placeholder (§3.8)

**Files:** `ui/locked/LockedScreen.kt`
**Test:** `app/src/androidTest/java/com/nyasa/notifybridge/ui/locked/LockedScreenTest.kt`

- [ ] **Step 1: Failing Compose test**
```kotlin
package com.nyasa.notifybridge.ui.locked
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
class LockedScreenTest {
    @get:Rule val c = createComposeRule()
    @Test fun unlock_button_invokes_callback() {
        var clicked = false
        c.setContent { LockedScreen(onUnlock = { clicked = true }) }
        c.onNodeWithText("Unlock").performClick()
        assertTrue(clicked)
    }
}
```
- [ ] **Step 2: Run → FAIL** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nyasa.notifybridge.ui.locked.LockedScreenTest`
- [ ] **Step 3: Implement**
```kotlin
package com.nyasa.notifybridge.ui.locked

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LockedScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("NotifyBridge")
        Spacer(Modifier.height(8.dp))
        Text("Unlock to continue")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock) { Text("Unlock") }
    }
}
```
- [ ] **Step 4: Run → PASS**
- [ ] **Step 5: Commit** `git commit -am "Add locked placeholder screen"`

---

## Task 25: Integration, manual verification, README

**Files:**
- Create: `README.md`
- Modify: `data/mqtt/HiveMqClientManager.kt` (complete pinned-cert trust manager — the tracked TODO from Task 11)
- Test: full suite + device manual checklist

- [ ] **Step 1: Complete pinned-cert trust**

In `HiveMqClientManager.connect`, when `requiresPinnedCert(config)`, build an `SSLContext`/`TrustManagerFactory` from `config.pinnedCertPem` (parse PEM → `CertificateFactory.generateCertificate` → `KeyStore` → `TrustManagerFactory`) and apply via `builder.sslConfig().trustManagerFactory(tmf).applySslConfig()`. Add `app/src/test/java/.../mqtt/PinnedCertParseTest.kt` asserting a known PEM parses to one `X509Certificate`; TDD it (fail → impl → pass).

- [ ] **Step 2: Run the full suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — all unit tests.
Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: PASS — Room/Hilt/biometric/Compose tests (device or emulator API 30+ recommended; note the §3.8 caveat for API 26–29). The `debug` APK excludes `MqttForegroundService`/`NotifListenerService`/`BootReceiver` (see the `src/debug/AndroidManifest.xml` note in plan-wide conventions), so this suite does NOT cover the live MQTT/notification/boot path — that path is verified by the **manual checklist below using a non-debug (release) build** where those components are present.

- [ ] **Step 3: Device manual verification checklist** (record results in commit message)

1. Install; onboarding shows GRANT_ACCESS active, others gated.
2. Grant notification access → step 2 unlocks.
3. Configure broker against a local Mosquitto (TLS OFF) → Test Connection shows success.
4. Enable WhatsApp in Apps; post a test notification → appears in HA via discovery sensor; attributes (`title`,`text`,`app`,`package`) present.
5. Kill broker → notifications buffer (outbox depth rises); restore broker → outbox drains.
6. Lock phone, post 3 rapid notifications → all 3 arrive (no clobber, vs. Companion sensor).
7. Background app past idle timeout → returns locked; biometric/PIN unlocks.
8. Recent list bodies show redacted until reveal.
9. Reboot device, open app once, post notification → still forwarded (BootReceiver path).
10. With SYSTEM_CA + a CA-signed broker, and PINNED + self-signed PEM → both connect; trust-all is not an option (S4).

- [ ] **Step 4: Write README**

`README.md`: one-paragraph purpose, link to `docs/superpowers/specs/2026-05-17-notifybridge-design.md`, build/run commands, the manual checklist above, and the §3.8 API-version caveat.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "Complete pinned-cert trust, add README, verify end to end"
```

---

## Self-Review

**1. Spec coverage:**
- §2.1 permissions → Task 1 manifest ✓ (USE_BIOMETRIC Task 1; FGS type Task 13).
- §3.1 capture/dedup/body chain/model → Tasks 2,3,5,14 ✓.
- §3.2 outbox/TTL/cap/boot/worker → Tasks 6,7,15 ✓.
- §3.3 transport/LWT/FGS connectedDevice/TLS → Tasks 11,13; S4 pinned cert Task 11+25 ✓.
- §3.4 discovery → Task 4, used Task 10 ✓.
- §3.5 UI screens → Tasks 19–23 ✓.
- §3.6 onboarding → Task 19 ✓.
- §3.7 Stitch reference → screen IDs cited in Tasks 19–23 ✓.
- §3.8 app lock/redaction/FLAG_SECURE/version caveat → Tasks 16,17,18,20,22; FLAG_SECURE Tasks 20,21 ✓.
- §5 error handling → outbox prune (7), publish-fail keeps item (10), defensive map (14 runCatching) ✓.
- §6 testing → unit+instrumented across tasks ✓.

**2. Placeholder scan:** No "TBD/TODO-without-task". The two deferred items (pinned-cert trust, FLAG_SECURE) are explicit tasks (25, 20/21), not placeholders.

**3. Type consistency:** Interface signatures fixed in the conventions header and used identically in Tasks 7–14 (`enqueue`, `nextBatch`, `markPublished`, `recordFailure`, `pruneExpired`, `depth`; `connect`/`publish`/`disconnect`; `stateTopic`/`discoveryConfig`/`eventPayload`). `MainActivity` base-class change to `FragmentActivity` flagged in Task 17 and applied in Task 18. Consistent.

No gaps requiring new tasks.
