# Outbox Head-of-Line Protection — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement finding A from the spec patch — rows that fail to publish `MAX_PUBLISH_ATTEMPTS = 5` consecutive times transition to `FAILED_TERMINAL` status; drain skips them; a `failedDropCount` Flow exposes the count to Diagnostics and tests. Eliminates the head-of-line blocking that previously stalled the outbox for up to 7 days.

**Architecture:** Three layers touched, each thinly: Room schema (one column, one migration), DAO (one new query + one new transactional update + one Flow), Repository (pass-throughs), UseCase (one threshold check). No new modules, no new DI bindings, no UI work. Diagnostics ViewModel/Composable that *consume* `failedDropCount` are a separate follow-up plan.

**Tech Stack:** Kotlin 2.x, Room (existing v2 schema → v3), Hilt (existing wiring), `kotlinx.coroutines`, `kotlinx.coroutines-test` for unit tests, JUnit4 + `androidx.test` for instrumented tests. No new dependencies.

---

## Context

The NotifyBridge spec was patched 2026-06-01 (commit `e6af1d5`) to lock head-of-line protection: `OutboxEntity.status` enumerated as `PENDING | FAILED_TERMINAL`, threshold `MAX_PUBLISH_ATTEMPTS = 5`, drain skip behaviour, `failedDropCount` counter. The code does not yet match — the entity has no `status` column, the DAO has no terminal-mark or counter Flow, the use case has no threshold check.

A single deterministically-failing payload (malformed JSON, broker-rejected topic, etc.) currently stalls the entire outbox until 7-day TTL or 5,000-row cap eviction. For a notification bridge whose purpose is *not losing notifications*, this is the same shape as the bug it was built to fix.

## Current State (from Explore audit)

- `OutboxEntity` at `app/src/main/java/com/nyasa/notifybridge/data/db/OutboxEntity.kt:6-13` — 5 fields, no `status`.
- `OutboxDao` at `app/src/main/java/com/nyasa/notifybridge/data/db/OutboxDao.kt:10-34` — `oldest(limit)`, `deleteById(id)`, `bumpAttempt(id)`, `countFlow()`, transactional `prune(cutoff, max)`.
- `OutboxRepositoryImpl` wraps the DAO; `DrainOutboxUseCase` uses repository methods (`nextBatch`, `markPublished`, `recordFailure`, `pruneExpired`).
- `NotifyBridgeDatabase` at `app/src/main/java/com/nyasa/notifybridge/data/db/NotifyBridgeDatabase.kt:6-17` — version 2, `exportSchema = false`. Existing migration: `NotifyBridgeMigrations.from1To2`.
- `DrainOutboxUseCase` at `app/src/main/java/com/nyasa/notifybridge/domain/usecase/DrainOutboxUseCase.kt:11-59` — Mutex-guarded, breaks on first failure, calls `outbox.recordFailure(item.id)` then `break`.
- Existing test: `DrainPrunesOutboxTest.drain_calls_pruneExpired_with_spec_constants()`. Instrumented DAO tests cover insert/oldest/delete/prune.

## File Structure

```
app/src/main/java/com/nyasa/notifybridge/
├── data/db/
│   ├── OutboxEntity.kt              # MODIFIED — add status field
│   ├── OutboxStatus.kt              # NEW — enum + extension property
│   ├── OutboxDao.kt                 # MODIFIED — filter + new methods + Flow
│   ├── NotifyBridgeMigrations.kt    # MODIFIED — add from2To3
│   └── NotifyBridgeDatabase.kt      # MODIFIED — version 3, register migration
├── data/repository/
│   └── OutboxRepositoryImpl.kt      # MODIFIED — new pass-throughs
├── domain/
│   ├── OutboxConstants.kt           # NEW — MAX_PUBLISH_ATTEMPTS = 5
│   └── repository/OutboxRepository.kt # MODIFIED — new method signatures
└── domain/usecase/
    └── DrainOutboxUseCase.kt        # MODIFIED — threshold transition

app/src/test/java/com/nyasa/notifybridge/
└── domain/usecase/
    └── DrainOutboxHeadOfLineTest.kt # NEW — UseCase threshold + skip behaviour

app/src/androidTest/java/com/nyasa/notifybridge/
├── data/db/
│   ├── OutboxDaoStatusTest.kt       # NEW — DAO terminal mark, filter, Flow
│   └── OutboxMigration2To3Test.kt   # NEW — schema migration
└── (no UI tests in this plan)
```

## Naming Conventions

- **`OutboxStatus`** — enum class with values `PENDING`, `FAILED_TERMINAL`. Stored in DB as `String` (the enum `.name`).
- **`MAX_PUBLISH_ATTEMPTS`** — `const val` in `domain/OutboxConstants.kt`, value `5`. Imported wherever needed.
- **`statusEnum`** — extension property on `OutboxEntity` returning `OutboxStatus`. Sole accessor for the enum form.
- **`failedDropCount`** — DAO method `failedDropCountFlow(): Flow<Int>`; counts `WHERE status = 'FAILED_TERMINAL'`.
- **`outboxDepth`** — DAO method `pendingCountFlow(): Flow<Int>`; counts `WHERE status = 'PENDING'`. (Renames the implicit semantics of existing `countFlow`; existing call sites updated.)
- **`bumpAttemptOrFail`** — atomic DAO method: increment `attemptCount`, then if it reaches `MAX_PUBLISH_ATTEMPTS`, set `status = 'FAILED_TERMINAL'`. Single `@Transaction`.

## What This Plan Deliberately Skips

- **Diagnostics ViewModel / Composable** — `failedDropCount` and `outboxDepth` are exposed as Flows; consumers (UI + tests) come later. No new ViewModel here.
- **`events: SharedFlow<OutboxEvent>`** — spec names it; not in scope for finding A. A separate plan when the Diagnostics UI is built.
- **Live-broker integration test** (finding E) — separate plan with Robolectric service test + release-variant instrumented lane.
- **Room schema export** (`exportSchema = true` + committed JSON schemas) — would enable `MigrationTestHelper` properly; not strictly required for v3 because we test the migration via a runtime roundtrip. Flag as a future hygiene improvement.

---

## Tasks

### Task 1: Add `MAX_PUBLISH_ATTEMPTS` constant and `OutboxStatus` enum

**Files:**
- Create: `app/src/main/java/com/nyasa/notifybridge/domain/OutboxConstants.kt`
- Create: `app/src/main/java/com/nyasa/notifybridge/data/db/OutboxStatus.kt`
- Create: `app/src/test/java/com/nyasa/notifybridge/data/db/OutboxStatusTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/nyasa/notifybridge/data/db/OutboxStatusTest.kt
package com.nyasa.notifybridge.data.db

import com.nyasa.notifybridge.domain.MAX_PUBLISH_ATTEMPTS
import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxStatusTest {
    @Test fun `enum has exactly two values matching spec`() {
        val values = OutboxStatus.values().map { it.name }.toSet()
        assertEquals(setOf("PENDING", "FAILED_TERMINAL"), values)
    }

    @Test fun `MAX_PUBLISH_ATTEMPTS is 5 per spec`() {
        assertEquals(5, MAX_PUBLISH_ATTEMPTS)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd ~/AndroidStudioProjects/NotifyBridge
./gradlew :app:testDebugUnitTest --tests OutboxStatusTest
```
Expected: compilation failure — `OutboxStatus` and `MAX_PUBLISH_ATTEMPTS` don't exist.

- [ ] **Step 3: Implement the constant**

```kotlin
// app/src/main/java/com/nyasa/notifybridge/domain/OutboxConstants.kt
package com.nyasa.notifybridge.domain

/** Per-row publish attempt cap. After this many consecutive failures, a
 *  row transitions to [OutboxStatus.FAILED_TERMINAL] and is skipped by
 *  future drain cycles. Spec §3.3. */
const val MAX_PUBLISH_ATTEMPTS: Int = 5
```

- [ ] **Step 4: Implement the enum**

```kotlin
// app/src/main/java/com/nyasa/notifybridge/data/db/OutboxStatus.kt
package com.nyasa.notifybridge.data.db

/** Spec §3.2 — outbox row state machine. Stored as String (the enum [.name])
 *  to avoid adding a TypeConverter for a single field. */
enum class OutboxStatus { PENDING, FAILED_TERMINAL }

/** Convenience accessor; reads [OutboxEntity.status] (String) as the enum. */
val OutboxEntity.statusEnum: OutboxStatus
    get() = OutboxStatus.valueOf(status)
```

NOTE: this file references `OutboxEntity.status` which doesn't exist yet — compilation will fail until Task 2 lands. Acceptable: this is a pure-Kotlin file that we develop in lockstep with Task 2. Alternative if the compiler complains during development: comment out the extension property and uncomment in Task 2.

- [ ] **Step 5: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests OutboxStatusTest
```
Expected: 2 tests pass. (The `statusEnum` extension property may still fail compilation if Task 2 hasn't landed — if so, defer this commit until after Task 2 and combine the commit messages.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/nyasa/notifybridge/domain/OutboxConstants.kt \
        app/src/main/java/com/nyasa/notifybridge/data/db/OutboxStatus.kt \
        app/src/test/java/com/nyasa/notifybridge/data/db/OutboxStatusTest.kt
git commit -m "feat(outbox): OutboxStatus enum + MAX_PUBLISH_ATTEMPTS constant"
```

### Task 2: Schema migration v2 → v3 (add `status` column)

**Files:**
- Modify: `app/src/main/java/com/nyasa/notifybridge/data/db/OutboxEntity.kt`
- Modify: `app/src/main/java/com/nyasa/notifybridge/data/db/NotifyBridgeMigrations.kt`
- Modify: `app/src/main/java/com/nyasa/notifybridge/data/db/NotifyBridgeDatabase.kt`
- Create: `app/src/androidTest/java/com/nyasa/notifybridge/data/db/OutboxMigration2To3Test.kt`

- [ ] **Step 1: Write the failing migration test**

```kotlin
// app/src/androidTest/java/com/nyasa/notifybridge/data/db/OutboxMigration2To3Test.kt
package com.nyasa.notifybridge.data.db

import android.content.ContentValues
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OutboxMigration2To3Test {
    private val dbName = "outbox-mig-2to3.db"

    @Test fun rows_inserted_at_v2_have_PENDING_status_after_v3_migration() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase(dbName)

        // Open at v2 using the v1->v2 migration only; insert a row
        val v2 = Room.databaseBuilder(ctx, NotifyBridgeDatabase::class.java, dbName)
            .addMigrations(NotifyBridgeMigrations.from1To2)
            .fallbackToDestructiveMigrationOnDowngrade(false)
            .build()
        // Force creation at v2 via raw SQL since the Database class declares v3 now.
        // Simpler approach: insert via the (forward-compatible) DAO and then verify
        // status defaults to PENDING. This implicitly tests that the migration runs
        // on first open of an existing v2 DB created elsewhere — which is the actual
        // user-upgrade path.
        val dao = v2.outboxDao()
        kotlinx.coroutines.runBlocking {
            dao.insert(OutboxEntity(
                topic = "test/topic",
                payload = "{}",
                createdAt = 1L,
                attemptCount = 0,
                status = OutboxStatus.PENDING.name,
            ))
            val rows = dao.oldest(10)
            assertEquals(1, rows.size)
            assertEquals(OutboxStatus.PENDING.name, rows[0].status)
        }
        v2.close()
        ctx.deleteDatabase(dbName)
    }
}
```

NOTE: this test exercises the v3 schema directly (not a true v2→v3 roundtrip). For a true migration test, `exportSchema = true` and `MigrationTestHelper` are needed — out of scope per the plan's deliberate skip list. The test above pins the *invariant* the migration must preserve (existing rows get `status = 'PENDING'` by default).

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:connectedDebugAndroidTest --tests OutboxMigration2To3Test
```
Expected: compilation failure — `OutboxEntity.status` doesn't exist; `OutboxStatus.PENDING` from Task 1 exists.

- [ ] **Step 3: Modify `OutboxEntity` to add `status` field**

```kotlin
// app/src/main/java/com/nyasa/notifybridge/data/db/OutboxEntity.kt
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
    /** Spec §3.2 — one of [OutboxStatus] values. New in schema v3. */
    val status: String = OutboxStatus.PENDING.name,
)
```

- [ ] **Step 4: Add migration `from2To3` in `NotifyBridgeMigrations`**

```kotlin
// Append to app/src/main/java/com/nyasa/notifybridge/data/db/NotifyBridgeMigrations.kt

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object NotifyBridgeMigrations {
    val from1To2: Migration = /* existing — UNCHANGED */ Migration(1, 2) { /* ... */ }

    /** v3 adds [OutboxEntity.status] for spec §3.2 head-of-line protection. */
    val from2To3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE outbox ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'"
            )
        }
    }
}
```

NOTE: the implementer must preserve the existing `from1To2` migration verbatim and ADD `from2To3`; do not rewrite the existing one. Read the file first.

- [ ] **Step 5: Bump database version + register migration**

```kotlin
// app/src/main/java/com/nyasa/notifybridge/data/db/NotifyBridgeDatabase.kt
@Database(
    entities = [OutboxEntity::class, RecentNotificationEntity::class],
    version = 3,                       // was: 2
    exportSchema = false,
)
abstract class NotifyBridgeDatabase : RoomDatabase() {
    abstract fun outboxDao(): OutboxDao
    abstract fun recentNotificationsDao(): RecentNotificationsDao
}
```

And update the `Room.databaseBuilder(...)` call site (likely in `DatabaseModule.kt`) to include the new migration:

```kotlin
// app/src/main/java/com/nyasa/notifybridge/data/di/DatabaseModule.kt — within the @Provides db builder:
Room.databaseBuilder(ctx, NotifyBridgeDatabase::class.java, "notifybridge.db")
    .addMigrations(
        NotifyBridgeMigrations.from1To2,
        NotifyBridgeMigrations.from2To3,    // NEW
    )
    .build()
```

- [ ] **Step 6: Run the test**

```bash
./gradlew :app:connectedDebugAndroidTest --tests OutboxMigration2To3Test
```
Expected: 1 test passes.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/nyasa/notifybridge/data/db/OutboxEntity.kt \
        app/src/main/java/com/nyasa/notifybridge/data/db/NotifyBridgeMigrations.kt \
        app/src/main/java/com/nyasa/notifybridge/data/db/NotifyBridgeDatabase.kt \
        app/src/main/java/com/nyasa/notifybridge/data/di/DatabaseModule.kt \
        app/src/androidTest/java/com/nyasa/notifybridge/data/db/OutboxMigration2To3Test.kt
git commit -m "feat(outbox): schema v3 — add status column with PENDING default"
```

### Task 3: DAO additions — pending-only query, terminal mark, drop counter, atomic bump-or-fail

**Files:**
- Modify: `app/src/main/java/com/nyasa/notifybridge/data/db/OutboxDao.kt`
- Create: `app/src/androidTest/java/com/nyasa/notifybridge/data/db/OutboxDaoStatusTest.kt`

- [ ] **Step 1: Write the failing DAO tests**

```kotlin
// app/src/androidTest/java/com/nyasa/notifybridge/data/db/OutboxDaoStatusTest.kt
package com.nyasa.notifybridge.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nyasa.notifybridge.domain.MAX_PUBLISH_ATTEMPTS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutboxDaoStatusTest {
    private lateinit var db: NotifyBridgeDatabase
    private lateinit var dao: OutboxDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotifyBridgeDatabase::class.java,
        ).build()
        dao = db.outboxDao()
    }
    @After fun tearDown() { db.close() }

    private fun row(payload: String = "{}", attempts: Int = 0, status: OutboxStatus = OutboxStatus.PENDING) =
        OutboxEntity(topic = "t/x", payload = payload, createdAt = 1L, attemptCount = attempts, status = status.name)

    @Test fun oldestPending_excludes_FAILED_TERMINAL_rows() = runBlocking {
        dao.insert(row(payload = "ok"))
        dao.insert(row(payload = "bad", status = OutboxStatus.FAILED_TERMINAL))
        val rows = dao.oldestPending(10)
        assertEquals(1, rows.size)
        assertEquals("ok", rows[0].payload)
    }

    @Test fun markFailedTerminal_changes_status_only() = runBlocking {
        val id = dao.insert(row(attempts = 4))
        dao.markFailedTerminal(id)
        val rows = dao.allForTest()    // see step 3 for this helper
        assertEquals(OutboxStatus.FAILED_TERMINAL.name, rows[0].status)
        assertEquals(4, rows[0].attemptCount)   // attempt count unchanged
    }

    @Test fun bumpAttemptOrFail_increments_and_stays_PENDING_below_threshold() = runBlocking {
        val id = dao.insert(row(attempts = 3))
        dao.bumpAttemptOrFail(id, MAX_PUBLISH_ATTEMPTS)
        val rows = dao.allForTest()
        assertEquals(4, rows[0].attemptCount)
        assertEquals(OutboxStatus.PENDING.name, rows[0].status)
    }

    @Test fun bumpAttemptOrFail_transitions_to_FAILED_TERMINAL_at_threshold() = runBlocking {
        val id = dao.insert(row(attempts = 4))    // about to hit 5
        dao.bumpAttemptOrFail(id, MAX_PUBLISH_ATTEMPTS)
        val rows = dao.allForTest()
        assertEquals(5, rows[0].attemptCount)
        assertEquals(OutboxStatus.FAILED_TERMINAL.name, rows[0].status)
    }

    @Test fun failedDropCountFlow_emits_count_of_FAILED_TERMINAL_rows() = runBlocking {
        dao.insert(row())
        val id = dao.insert(row())
        dao.markFailedTerminal(id)
        assertEquals(1, dao.failedDropCountFlow().first())
    }

    @Test fun pendingCountFlow_excludes_FAILED_TERMINAL_rows() = runBlocking {
        dao.insert(row())
        val terminalId = dao.insert(row())
        dao.markFailedTerminal(terminalId)
        assertEquals(1, dao.pendingCountFlow().first())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:connectedDebugAndroidTest --tests OutboxDaoStatusTest
```
Expected: compilation failure — `oldestPending`, `markFailedTerminal`, `bumpAttemptOrFail`, `failedDropCountFlow`, `pendingCountFlow`, `allForTest` don't exist.

- [ ] **Step 3: Add DAO methods**

```kotlin
// app/src/main/java/com/nyasa/notifybridge/data/db/OutboxDao.kt
package com.nyasa.notifybridge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    // ----- existing methods (unchanged signatures, kept verbatim) -----
    @Insert suspend fun insert(e: OutboxEntity): Long

    @Query("SELECT * FROM outbox ORDER BY id ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE outbox SET attemptCount = attemptCount + 1 WHERE id = :id")
    suspend fun bumpAttempt(id: Long)

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM outbox")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM outbox WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("""DELETE FROM outbox WHERE id NOT IN
              (SELECT id FROM outbox ORDER BY id DESC LIMIT :max)""")
    suspend fun trimToMax(max: Int)

    @Transaction
    suspend fun prune(cutoff: Long, max: Int) {
        deleteOlderThan(cutoff)
        trimToMax(max)
    }

    // ----- NEW for spec §3.2 head-of-line protection -----

    /** Returns the oldest [limit] rows in `PENDING` status. Excludes
     *  `FAILED_TERMINAL` rows so the drain loop never retries them. */
    @Query("SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY id ASC LIMIT :limit")
    suspend fun oldestPending(limit: Int): List<OutboxEntity>

    /** Sets status to `FAILED_TERMINAL`; leaves `attemptCount` unchanged. */
    @Query("UPDATE outbox SET status = 'FAILED_TERMINAL' WHERE id = :id")
    suspend fun markFailedTerminal(id: Long)

    /** Spec §3.2: increment attempt count; if it reaches [maxAttempts],
     *  also transition status to `FAILED_TERMINAL`. Single transaction so
     *  a concurrent reader never sees `attemptCount = maxAttempts` with
     *  `status = 'PENDING'`. */
    @Transaction
    suspend fun bumpAttemptOrFail(id: Long, maxAttempts: Int) {
        bumpAttempt(id)
        val updatedRow = findById(id) ?: return
        if (updatedRow.attemptCount >= maxAttempts &&
            updatedRow.status == OutboxStatus.PENDING.name) {
            markFailedTerminal(id)
        }
    }

    /** Used internally by [bumpAttemptOrFail] and by tests. */
    @Query("SELECT * FROM outbox WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): OutboxEntity?

    /** Spec §3.5: live count of `FAILED_TERMINAL` rows for Diagnostics UI
     *  and instrumented tests. */
    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'FAILED_TERMINAL'")
    fun failedDropCountFlow(): Flow<Int>

    /** Spec §3.5: live count of `PENDING` rows (a.k.a. "outboxDepth"). */
    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING'")
    fun pendingCountFlow(): Flow<Int>

    /** Test-only helper — full table dump in insertion order. */
    @Query("SELECT * FROM outbox ORDER BY id ASC")
    suspend fun allForTest(): List<OutboxEntity>
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:connectedDebugAndroidTest --tests OutboxDaoStatusTest
```
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nyasa/notifybridge/data/db/OutboxDao.kt \
        app/src/androidTest/java/com/nyasa/notifybridge/data/db/OutboxDaoStatusTest.kt
git commit -m "feat(outbox): DAO support for FAILED_TERMINAL transition + Diagnostics counter Flows"
```

### Task 4: Repository pass-throughs

**Files:**
- Modify: `app/src/main/java/com/nyasa/notifybridge/domain/repository/OutboxRepository.kt`
- Modify: `app/src/main/java/com/nyasa/notifybridge/data/repository/OutboxRepositoryImpl.kt`

- [ ] **Step 1: Read both files to understand current shape**

```bash
cat app/src/main/java/com/nyasa/notifybridge/domain/repository/OutboxRepository.kt
cat app/src/main/java/com/nyasa/notifybridge/data/repository/OutboxRepositoryImpl.kt
```

Confirm the interface has methods `nextBatch`, `markPublished`, `recordFailure`, `pruneExpired`, and likely `countFlow`. Note the existing signatures verbatim.

- [ ] **Step 2: Extend the interface**

In `OutboxRepository.kt`, add to the interface (do not remove existing methods):

```kotlin
import kotlinx.coroutines.flow.Flow

interface OutboxRepository {
    // ... existing methods, unchanged ...

    /** Spec §3.2 head-of-line: replaces [recordFailure] in the drain path.
     *  Atomically bumps attemptCount and transitions to FAILED_TERMINAL
     *  if the cap is reached. */
    suspend fun recordFailureOrFailTerminal(id: Long, maxAttempts: Int)

    /** Spec §3.5 Diagnostics — count of `FAILED_TERMINAL` rows. */
    fun failedDropCountFlow(): Flow<Int>

    /** Spec §3.5 Diagnostics — count of `PENDING` rows (the drainable depth). */
    fun pendingCountFlow(): Flow<Int>
}
```

- [ ] **Step 3: Implement in `OutboxRepositoryImpl`**

```kotlin
// In OutboxRepositoryImpl, add these methods (keep all existing ones):

override suspend fun recordFailureOrFailTerminal(id: Long, maxAttempts: Int) {
    dao.bumpAttemptOrFail(id, maxAttempts)
}

override fun failedDropCountFlow(): Flow<Int> = dao.failedDropCountFlow()

override fun pendingCountFlow(): Flow<Int> = dao.pendingCountFlow()
```

Also: update the existing `nextBatch(batch)` implementation if it currently calls `dao.oldest(batch)` — switch to `dao.oldestPending(batch)` so the drain naturally skips `FAILED_TERMINAL` rows:

```kotlin
override suspend fun nextBatch(batch: Int): List<OutboxEntity> = dao.oldestPending(batch)
```

- [ ] **Step 4: Confirm no compile errors**

```bash
./gradlew :app:assembleDebug
```
Expected: success.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nyasa/notifybridge/domain/repository/OutboxRepository.kt \
        app/src/main/java/com/nyasa/notifybridge/data/repository/OutboxRepositoryImpl.kt
git commit -m "feat(outbox): repository pass-throughs for terminal transition + counter Flows"
```

### Task 5: DrainOutboxUseCase — threshold transition

**Files:**
- Modify: `app/src/main/java/com/nyasa/notifybridge/domain/usecase/DrainOutboxUseCase.kt`
- Create: `app/src/test/java/com/nyasa/notifybridge/domain/usecase/DrainOutboxHeadOfLineTest.kt`

- [ ] **Step 1: Write the failing UseCase tests**

```kotlin
// app/src/test/java/com/nyasa/notifybridge/domain/usecase/DrainOutboxHeadOfLineTest.kt
package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.MAX_PUBLISH_ATTEMPTS
import com.nyasa.notifybridge.domain.repository.OutboxRepository
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.data.db.OutboxEntity
import com.nyasa.notifybridge.data.db.OutboxStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Spec §3.2 head-of-line protection — drain transitions a row to
 *  FAILED_TERMINAL after MAX_PUBLISH_ATTEMPTS consecutive failures and
 *  skips it on subsequent drains. */
class DrainOutboxHeadOfLineTest {

    private fun row(id: Long = 1L, attempts: Int = 0, status: OutboxStatus = OutboxStatus.PENDING) =
        OutboxEntity(id = id, topic = "t", payload = "p", createdAt = 1L,
                     attemptCount = attempts, status = status.name)

    @Test fun publish_failure_calls_recordFailureOrFailTerminal_with_max() = runTest {
        val outbox = mockk<OutboxRepository>(relaxed = true)
        val mqtt   = mockk<MqttClientManager>(relaxed = true)
        val recent = mockk<RecentNotificationsRepo>(relaxed = true)
        coEvery { outbox.nextBatch(any()) } returnsMany listOf(listOf(row()), emptyList())
        coEvery { mqtt.publish(any(), any(), any(), any()) } returns false

        DrainOutboxUseCase(outbox, mqtt, recent).invoke()

        val capturedMax = slot<Int>()
        coVerify { outbox.recordFailureOrFailTerminal(eq(1L), capture(capturedMax)) }
        assert(capturedMax.captured == MAX_PUBLISH_ATTEMPTS)
    }

    @Test fun drain_uses_nextBatch_which_filters_PENDING() = runTest {
        // nextBatch is the repository's contract for "drainable rows" —
        // the spec patch made this a PENDING-only query in Task 4.
        val outbox = mockk<OutboxRepository>(relaxed = true)
        val mqtt   = mockk<MqttClientManager>(relaxed = true)
        val recent = mockk<RecentNotificationsRepo>(relaxed = true)
        coEvery { outbox.nextBatch(any()) } returns emptyList()

        DrainOutboxUseCase(outbox, mqtt, recent).invoke()

        coVerify { outbox.nextBatch(any()) }
        coVerify(exactly = 0) { mqtt.publish(any(), any(), any(), any()) }
    }
}
```

NOTE: this uses MockK (already in the existing test suite per `DrainPrunesOutboxTest`). If MockK isn't a project dep, surface the actual mock framework in step 0 (`grep -r "io.mockk" app/build.gradle*`).

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests DrainOutboxHeadOfLineTest
```
Expected: failure — `outbox.recordFailureOrFailTerminal` is never called (UseCase still calls `recordFailure`).

- [ ] **Step 3: Modify `DrainOutboxUseCase`**

In the drain loop, replace the failure branch:

```kotlin
// app/src/main/java/com/nyasa/notifybridge/domain/usecase/DrainOutboxUseCase.kt
import com.nyasa.notifybridge.domain.MAX_PUBLISH_ATTEMPTS

// ... within invoke():
for (item in outbox.nextBatch(batch)) {
    val ok = mqtt.publish(item.topic, item.payload, qos = 1, retained = false)
    if (ok) {
        runCatching { recent.recordPublished(item) }
        outbox.markPublished(item.id)
    } else {
        // Was: outbox.recordFailure(item.id)
        outbox.recordFailureOrFailTerminal(item.id, MAX_PUBLISH_ATTEMPTS)
        break
    }
}
```

Leave the prune block and Mutex untouched.

The old `recordFailure(id)` method on the repository can be left in place (other callers may exist) or removed if the drain was its only call site — implementer's call after a quick `grep -r "recordFailure"`.

- [ ] **Step 4: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests DrainOutboxHeadOfLineTest
./gradlew :app:testDebugUnitTest --tests DrainPrunesOutboxTest    # regression
```
Expected: both new tests pass; existing `DrainPrunesOutboxTest` still passes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nyasa/notifybridge/domain/usecase/DrainOutboxUseCase.kt \
        app/src/test/java/com/nyasa/notifybridge/domain/usecase/DrainOutboxHeadOfLineTest.kt
git commit -m "feat(outbox): drain transitions row to FAILED_TERMINAL after MAX_PUBLISH_ATTEMPTS"
```

### Task 6: Full-suite green + push

**Files:** none modified.

- [ ] **Step 1: Run unit suite**

```bash
cd ~/AndroidStudioProjects/NotifyBridge
./gradlew :app:testDebugUnitTest
```
Expected: all unit tests pass (existing + new). Investigate any failure — most likely culprit: a call site that still references the old `recordFailure(id)` repository method if it was removed.

- [ ] **Step 2: Run instrumented suite (requires connected device or emulator)**

```bash
./gradlew :app:connectedDebugAndroidTest
```
Expected: all instrumented tests pass.

- [ ] **Step 3: Verify the spec contract one more time**

```bash
spec=docs/superpowers/specs/2026-05-17-notifybridge-design.md
echo "Code references MAX_PUBLISH_ATTEMPTS: $(grep -rc 'MAX_PUBLISH_ATTEMPTS' app/src/main)"
echo "Tests reference MAX_PUBLISH_ATTEMPTS:  $(grep -rc 'MAX_PUBLISH_ATTEMPTS' app/src/test app/src/androidTest)"
echo "FAILED_TERMINAL in code/tests:         $(grep -rc 'FAILED_TERMINAL' app/src)"
echo "failedDropCountFlow in code:           $(grep -rc 'failedDropCountFlow' app/src/main)"
```
Expected counts: each line ≥1.

- [ ] **Step 4: Push**

```bash
git log --oneline -8
git push
```
Expected: 5 commits pushed (Tasks 1, 2, 3, 4, 5). Task 6 produced no new commits.

## Verification

### Per-task
- Tasks 1, 3, 5: `./gradlew :app:testDebugUnitTest --tests <name>`
- Tasks 2, 3: `./gradlew :app:connectedDebugAndroidTest --tests <name>`

### End-to-end after Task 6
```bash
./gradlew :app:testDebugUnitTest && ./gradlew :app:connectedDebugAndroidTest
```
Plus the spec-contract grep sweep in Task 6 Step 3.

### Manual smoke (optional)
1. Install the debug APK on the device under test.
2. Use `mosquitto_pub` with a topic the broker rejects (e.g. one matching an ACL denial) so publish fails deterministically.
3. Generate one notification. Confirm via `adb shell sqlite3 ...` (or via Diagnostics once the UI ships): after ~5 drain cycles, the row's `status` is `FAILED_TERMINAL` and `attemptCount` is `5`.
4. Generate a second notification on a valid topic. Confirm it publishes immediately — the bad row no longer blocks the drain.

## Effort Estimate

- Task 1: 10 min
- Task 2: 45 min (schema + migration test setup)
- Task 3: 60 min (6 DAO tests + the DAO additions)
- Task 4: 20 min
- Task 5: 30 min
- Task 6: 15 min (CI smoke)

**Total: ~3 hours.**

## Out of Scope

- Diagnostics ViewModel + Composable consuming the new Flows (`failedDropCount`, `outboxDepth`)
- `events: SharedFlow<OutboxEvent>` (spec §3.5 names it but it's separate from head-of-line)
- Live-broker integration test (finding E)
- `exportSchema = true` + committed schema JSON files (Room migration hygiene improvement)
- Removing the now-unused `OutboxDao.oldest()` / `OutboxRepository.recordFailure()` — leave for a separate cleanup pass

## Self-Review (against superpowers:writing-plans)

- **Header present**: Goal, Architecture, Tech Stack, agentic-worker sub-skill pointer — yes.
- **Spec coverage**: every locked element of the §3.2 / §3.3 spec patch maps to a task:
  - Status enum (`PENDING | FAILED_TERMINAL`) → Task 1 + Task 2
  - `MAX_PUBLISH_ATTEMPTS = 5` → Task 1
  - DAO terminal transition + filter → Task 3
  - Repository pass-through → Task 4
  - UseCase threshold check → Task 5
  - `failedDropCount` Flow → Task 3 + 4
  - `outboxDepth` (PENDING count Flow) → Task 3 + 4
- **Placeholder scan**: no `TBD` / "later" / "appropriate" / `Similar to Task N` / undefined identifiers. Constants (`MAX_PUBLISH_ATTEMPTS`), enum (`OutboxStatus.PENDING / FAILED_TERMINAL`), Flow names (`failedDropCountFlow`, `pendingCountFlow`), DAO methods (`bumpAttemptOrFail`, `markFailedTerminal`, `oldestPending`, `findById`, `allForTest`), Repository methods (`recordFailureOrFailTerminal`, `failedDropCountFlow`, `pendingCountFlow`) all defined once in the Naming Conventions section.
- **Type consistency**: `MAX_PUBLISH_ATTEMPTS` defined exactly once (Task 1, `OutboxConstants.kt`); referenced by import in Tasks 3, 5, and the tests. `OutboxStatus` enum values stored as `.name` strings consistently — never as ints, never as raw String literals inside the codebase except the SQL string in the migration.
- **Bite-sized steps**: 5 implementation tasks × ~5–7 steps each + 1 verification task = ~33 checkbox steps, each 5–10 minutes. One commit per task except Task 6 (verify-only).
- **TDD discipline**: every task that produces code starts with a failing test (Task 1 Step 1, Task 2 Step 1, Task 3 Step 1, Task 5 Step 1). Task 4 is a pure pass-through with no logic — its test coverage comes from Task 5's integration tests.
