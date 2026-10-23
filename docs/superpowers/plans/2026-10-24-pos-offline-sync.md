# POS Offline Sync of Transactions (Phase 5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upload the sales that Phase 4 stores in Room to Supabase in the background, never blocking or duplicating a sale, and show the cashier one honest sync pill (Offline / N menunggu sinkron / Tersinkron HH:mm).

**Architecture:** Room v5 adds sync columns to `transactions`. A plain, JVM-testable `TransactionSyncer` walks PENDING sales (keyset paging, oldest first) through a `TransactionRemoteDataSource` (one idempotent RPC `submit_transaction`) and a `TransactionSyncStore` (Room). A thin `@HiltWorker` runs it under WorkManager (one-time "sync now" with REPLACE, plus a 15-minute periodic safety net). `TransactionRepositoryImpl` and `DashboardViewModel.refresh()` trigger it; `DashboardViewModel` derives a `SyncPill` from one Room `Flow`.

**Tech Stack:** Kotlin, Room 2.8 (+ Robolectric), WorkManager, Hilt + hilt-work, supabase-kt 3.1.4 Postgrest `rpc`, kotlinx.serialization, Compose Material3, JUnit/Turbine/coroutines-test, work-testing.

**Spec:** [docs/superpowers/specs/2026-10-24-offline-sync-design.md](../specs/2026-10-24-offline-sync-design.md) (section numbers below refer to it). Upstream: [PRD 4.7, 4.9, 4.11, 4.13](../../PRD-pos.md), [roadmap](../../roadmap.md) "Phase 5". Format precedent: `docs/superpowers/plans/2026-10-23-pos-cash-payment-local-transactions.md`.

## Global Constraints

- Package `com.example.poswarnus`; minSdk 24; Kotlin; Compose Material3; Hilt; single module `:app`.
- **Never execute SQL against the live Supabase project** (no `apply_migration`, no `execute_sql`, no Supabase MCP write). `supabase/migrations/20261024000000_add_transactions_sync.sql` is only written to the repo; the owner applies it later. The app must work, and never crash, while the tables/function do not exist on the server.
- **Work in a git worktree on branch `feature/phase5-offline-sync`; do not merge to `main`, do not push.** Copy `local.properties` into the worktree. Execution stops at a reviewed branch.
- Shell in the worktree: plain separate commands (no `&&`, `;`, subshells, or `cd` chains; use `git -C <worktree>` and `./gradlew` from the worktree root). `git add` only the files a task lists. Commit messages end with the line `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Money is always `Long` (whole Rupiah). Never `Double` for money.
- Room: DB version 5, `exportSchema` stays on, commit `app/schemas/com.example.poswarnus.data.local.AppDatabase/5.json`; `MIGRATION_4_5` is non-destructive; no `fallbackToDestructiveMigration`. Entity `@ColumnInfo(defaultValue=...)` must equal the migration DDL exactly (spec 4.2). Sync statuses are only `PENDING` and `SYNCED`; no `FAILED`, no retry cap.
- Dependencies only through `gradle/libs.versions.toml` (aliases `libs.*`). Versions in spec 7 are proposals: the implementer must confirm each resolves with Gradle (`./gradlew :app:dependencies --configuration debugRuntimeClasspath` or a build), use a stable release at least two weeks old, and fall back to `work` 2.10.x/2.9.1 and `androidxHilt` 1.2.0 (both families are already in the Gradle cache) if the proposed ones do not resolve or need a higher minSdk/AGP/compileSdk.
- **minSdk 24 has no `java.time` without desugaring and the project has none:** do not use `java.time` anywhere (spec 5.1's `Instant.ofEpochMilli(...)` and the pill's time formatting use `java.text.SimpleDateFormat` with explicit `Locale` and `TimeZone` instead). Do not add desugaring.
- **Never store or show a raw `RestException` message:** it embeds the request URL and headers (including the bearer token). `SyncErrors.describe` for a `RestException` uses `error` and `description` only (Task 4).
- `lastSyncError` format: `"<prefix>: <ClassName>: <detail>"` cut to 200 chars; prefixes `offline`, `session`, `rejected` only (spec 4.2). Only `offline:` drives the "Offline" pill.
- Pill texts (exact): `Offline`, `N menunggu sinkron` (e.g. `3 menunggu sinkron`), `Tersinkron HH:mm`, and `Tersinkron dd/MM HH:mm` when not today.
- The payment screen/dialog and `PaymentViewModel` are not modified in this phase.
- Verification gate for code tasks: `./gradlew testDebugUnitTest assembleDebug` green. `./gradlew lint` (Task 8) may show only the 2 known errors in androidTest `LoginScreenTest`. Anything not verified is reported as NOT verified.
- Test-cashier credentials never go into any repo file or commit message; the controller types them from the conversation (`adb input text` can drop the first character: read back before submitting).

## Review Focus

1. **Migration from a real v4 database** keeps headers, lines and catalog rows, existing sales become `PENDING` with zero attempts, the index exists, and Room's own schema validation passes (Task 2; the three older migration tests must also include `MIGRATION_4_5`).
2. **`REPLACE` cancelling a run right after the server accepted a sale:** the sale is still marked `SYNCED` (`withContext(NonCancellable)`), and `CancellationException` still propagates (Task 4); the scheduler really replaces and keeps one periodic request (Task 6).
3. **No session deletes nothing:** `syncPending()` without a cashier id writes and reads nothing and reports `stoppedEarly`; `ensureSessionForSync()` never clears the stored session when `restoreSession` fails (Tasks 4, 5).
4. **Sales of another cashier** are never fetched, never counted as failures and never block the signed-in cashier's rows (Tasks 2, 4).
5. **Server tables/function missing (404 / PGRST202) or RLS 42501** never crash: the row gets `rejected:`, the pill says "N menunggu sinkron" not "Offline", and later rows still upload (Tasks 4, 6, 7, 8).
6. **Training rows are never queued or counted:** `isTraining = 1` is excluded by `pendingBatch` and `observeSyncSummary` (Task 2).
7. **Upload order and paging:** oldest `createdAt` first, ties by `id`, more rows than `batchSize` all upload, a failed row is not re-read in the same run (Tasks 2, 4).

---

### Task 1: Dependencies and WorkManager/Hilt-work wiring

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Modify: `app/src/main/java/com/example/poswarnus/PosWarNusApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/example/poswarnus/WorkManagerWiringTest.kt`

**Interfaces:**
- Produces:
  - Catalog aliases: `libs.androidx.work.runtime.ktx`, `libs.androidx.work.testing`, `libs.androidx.hilt.work`, `libs.androidx.hilt.compiler` (the last one used as `ksp(libs.androidx.hilt.compiler)`, next to the existing `ksp(libs.hilt.compiler)`; the name must not clash with the Dagger alias). `work-testing` is `testImplementation`.
  - `class PosWarNusApplication : Application(), Configuration.Provider` with `@Inject lateinit var workerFactory: HiltWorkerFactory`; `override val workManagerConfiguration: Configuration` built with that factory. (Task 6 adds `ensurePeriodic()`.)
  - Manifest no longer auto-initialises WorkManager.

- [ ] **Step 1: Write the failing smoke test** `WorkManagerWiringTest` (Robolectric, `RuntimeEnvironment.getApplication()`):
  - `default WorkManager initializer is removed from the manifest`: read `InitializationProvider` meta-data via `packageManager.getProviderInfo(ComponentName(context, "androidx.startup.InitializationProvider"), PackageManager.GET_META_DATA).metaData` (null-safe) and assert it does not contain the key `androidx.work.WorkManagerInitializer`.
  - `application provides a HiltWorkerFactory configuration`: `assertTrue((app as Configuration.Provider).workManagerConfiguration.workerFactory is HiltWorkerFactory)`.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.WorkManagerWiringTest"`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement.**
  - Catalog: `work = "2.11.0"`, `androidxHilt = "1.3.0"` under `[versions]` and the four library entries (`androidx.work:work-runtime-ktx`, `androidx.work:work-testing` on `version.ref = "work"`; `androidx.hilt:hilt-work`, `androidx.hilt:hilt-compiler` on `version.ref = "androidxHilt"`). Leave `hiltNavigationCompose` as is. Apply the version fallback from Global Constraints if resolution fails; record the final versions in the commit message body.
  - `build.gradle.kts`: `implementation(libs.androidx.work.runtime.ktx)`, `implementation(libs.androidx.hilt.work)`, `ksp(libs.androidx.hilt.compiler)`, `testImplementation(libs.androidx.work.testing)`.
  - Manifest: under `<application>` add the `androidx.startup.InitializationProvider` provider (`android:authorities="${applicationId}.androidx-startup"`, `android:exported="false"`, `tools:node="merge"`) with `<meta-data android:name="androidx.work.WorkManagerInitializer" android:value="androidx.startup" tools:node="remove" />`.
  - Application: implement `Configuration.Provider` with `Configuration.Builder().setWorkerFactory(workerFactory).build()`.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS (all existing tests still green), BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/example/poswarnus/PosWarNusApplication.kt app/src/main/AndroidManifest.xml app/src/test/java/com/example/poswarnus/WorkManagerWiringTest.kt
git commit -m "build: add WorkManager and hilt-work with HiltWorkerFactory configuration"
```

---

### Task 2: Room v5 — sync columns, MIGRATION_4_5, DAO queries, 5.json

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/sync/SyncStatus.kt`
- Modify: `app/src/main/java/com/example/poswarnus/data/local/TransactionEntities.kt`, `app/src/main/java/com/example/poswarnus/data/local/AppDatabase.kt` (version 5, `MIGRATION_4_5`), `app/src/main/java/com/example/poswarnus/di/DatabaseModule.kt` (`addMigrations(..., MIGRATION_4_5)`)
- Create: `app/schemas/com.example.poswarnus.data.local.AppDatabase/5.json` (generated by the build)
- Test: `app/src/test/java/com/example/poswarnus/data/local/AppDatabaseMigrationTest.kt` (add + fix), `app/src/test/java/com/example/poswarnus/data/local/TransactionDaoTest.kt` (add), `app/src/test/java/com/example/poswarnus/data/transaction/TransactionRepositoryTest.kt` (anonymous DAO needs the new overrides)

**Interfaces:**
- Produces:
  - `object SyncStatus { const val PENDING = "PENDING"; const val SYNCED = "SYNCED" }`
  - `TransactionEntity` gains, after `isTraining`, in this order: `@ColumnInfo(defaultValue = "'PENDING'") val syncStatus: String = SyncStatus.PENDING`, `val syncedAt: Long? = null`, `@ColumnInfo(defaultValue = "0") val syncAttempts: Int = 0`, `val lastSyncError: String? = null`; `@Entity(tableName = "transactions", indices = [Index("syncStatus")])`.
  - `data class SyncSummaryRow(val pendingCount: Int, val offlineCount: Int, val lastSyncedAt: Long?)` (Room POJO, in `TransactionEntities.kt`).
  - `TransactionDao` additions, queries copied verbatim from spec 4.3:
    - `abstract suspend fun pendingBatch(cashierId: String, afterCreatedAt: Long, afterId: String, limit: Int): List<TransactionEntity>`
    - `abstract suspend fun markSynced(id: String, at: Long)`
    - `abstract suspend fun recordFailure(id: String, error: String)`
    - `abstract fun observeSyncSummary(): Flow<SyncSummaryRow>`
  - `val MIGRATION_4_5: Migration` with the five statements of spec 4.2.

- [ ] **Step 1: Write failing tests**
  - `TransactionDaoTest` (reuse `header(...)`/`line(...)`; extend `header` helper with `createdAt`, `cashierId` parameters with defaults equal to today's values):
    - `pendingBatch returns only PENDING non-training rows of the cashier ordered by createdAt then id`: rows a(created 2, u1), b(created 1, u1), c(created 1, u1, id sorts after b... use ids "b","c"), d(u2), e(isTraining), f(SYNCED via `markSynced`); expect `["b","c","a"]`.
    - `pendingBatch keyset cursor skips rows up to and including the cursor`: with the data above, cursor `(1, "b")` returns `["c","a"]`; first call uses `afterCreatedAt = Long.MIN_VALUE, afterId = ""`.
    - `pendingBatch honours limit`: limit 2 over three rows returns the first two.
    - `markSynced sets status and time and clears the error`: after `recordFailure("x","offline: IOException")` then `markSynced("x", 5_000)` expect `syncStatus == "SYNCED"`, `syncedAt == 5_000L`, `lastSyncError == null`.
    - `recordFailure increments attempts and stores the error`: twice, expect `syncAttempts == 2`, last error text kept, status still `PENDING`.
    - `observeSyncSummary counts pending and offline rows and returns the latest syncedAt`: rows: pending with `offline: x`, pending with `rejected: y`, pending without error, one SYNCED at 7_000 and one at 3_000, one training pending with `offline: z` (excluded): expect `SyncSummaryRow(3, 1, 7_000L)`.
    - `observeSyncSummary on an empty table gives zeros and null`: `SyncSummaryRow(0, 0, null)`.
    - `new rows default to PENDING with zero attempts`: `insertSale(header(), ...)` then read: `syncStatus == "PENDING"`, `syncAttempts == 0`, `syncedAt == null`, `lastSyncError == null`.
  - `AppDatabaseMigrationTest`:
    - Add `MIGRATION_4_5` to the `addMigrations(...)` of the three existing tests (the DB is v5 now, so each needs a path to it).
    - New `v4 database upgrades with MIGRATION_4_5 keeping sales and marking them PENDING`: build a **real v4 database** with raw SQL using the exact `createSql` of every table and index in `app/schemas/.../4.json` (categories, menu_items with `isFeatured`, option_groups, options, tax_settings, transactions, transaction_lines + their indexes; same raw-`SQLiteDatabase` approach as the existing tests, `raw.version = 4`), insert one category, one menu item, two `transactions` rows (one `isTraining = 1`) and three `transaction_lines`. Open via `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`. Assert: both transactions survive with their original totals; `syncStatus == "PENDING"`, `syncedAt == null`, `syncAttempts == 0`, `lastSyncError == null`; `linesFor` still returns the lines in order; catalog rows survive; a query on `sqlite_master` (via `db.openHelper.readableDatabase`) lists `index_transactions_syncStatus`; the `isTraining` row is not returned by `pendingBatch` for its cashier.
  - `TransactionRepositoryTest` `save returns failure when the dao throws`: add `override` stubs for the four new abstract DAO methods in the anonymous `TransactionDao` (return empty list / `Unit` / `flowOf(SyncSummaryRow(0, 0, null))`).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.local.*" --tests "com.example.poswarnus.data.transaction.*"`
Expected: FAIL (unresolved references / version mismatch).

- [ ] **Step 3: Implement** per Interfaces. `MIGRATION_4_5` is exactly the five `execSQL` calls of spec 4.2 (no table rebuild). Register it in `AppDatabase.kt` (version 5) and `DatabaseModule`. Run `./gradlew assembleDebug` once so Room writes `5.json`; confirm its `transactions.createSql` and index `createSql` equal spec 4.2 character for character (`grep` it), then make the migration test pass against it.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS (all earlier DAO/migration tests still green), BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/sync/SyncStatus.kt app/src/main/java/com/example/poswarnus/data/local/TransactionEntities.kt app/src/main/java/com/example/poswarnus/data/local/AppDatabase.kt app/src/main/java/com/example/poswarnus/di/DatabaseModule.kt app/schemas app/src/test/java/com/example/poswarnus/data/local/AppDatabaseMigrationTest.kt app/src/test/java/com/example/poswarnus/data/local/TransactionDaoTest.kt app/src/test/java/com/example/poswarnus/data/transaction/TransactionRepositoryTest.kt
git commit -m "feat: Room v5 with sync columns, MIGRATION_4_5 and sync DAO queries"
```

---

### Task 3: Supabase migration SQL file (written only, never applied)

**Files:**
- Create: `supabase/migrations/20261024000000_add_transactions_sync.sql`

**Interfaces:**
- Consumes: spec section 4.1 (the SQL text).
- Produces: the file the owner applies later (table/column names used by Task 5's payload: `transactions`, `transaction_lines`, `submit_transaction(p jsonb)` with keys `id, created_at, cashier_id, subtotal, tax_label, tax_basis_points, tax_amount, grand_total, cash_received, change_amount, lines[]{id, menu_item_id, name, base_price, quantity, selections}`).

- [ ] **Step 1: Write the file.** A header comment first (same style as `20261023200000_add_tax_settings.sql`) stating: Phase 5; applied manually by the project owner; NOT executed by the app or any build step; NOT yet applied to the live project; NOT idempotent (re-running `create table` / `create policy` / `create function` fails; `create function` is written with `create or replace` only so a fixed function body can be re-applied on its own); until it is applied the app keeps working and shows "N menunggu sinkron"; source `docs/superpowers/specs/2026-10-24-offline-sync-design.md` section 4.1. Then the SQL block of spec 4.1 **verbatim** (spec lines 50-138): do not reformat, reorder or "improve" it.

- [ ] **Step 2: Verify the file by inspection** (no SQL is run anywhere):

Run: `grep -c "create policy" supabase/migrations/20261024000000_add_transactions_sync.sql`
Expected: `2`

Run: `grep -ci "for select\|for update\|for delete\|for all" supabase/migrations/20261024000000_add_transactions_sync.sql`
Expected: `0` (write-only RLS: the file has no select/update/delete policy).

Run: `grep -c "security invoker" supabase/migrations/20261024000000_add_transactions_sync.sql`
Expected: `1`

Also confirm by reading that `submit_transaction` ends with `grant execute ... to authenticated;` and the `transaction_lines` table has `cashier_id`.

- [ ] **Step 3: Commit**

```bash
git add supabase/migrations/20261024000000_add_transactions_sync.sql
git commit -m "docs: add transactions sync SQL migration (not applied to live Supabase)"
```

---

### Task 4: Sync core — SyncErrors, TransactionSyncStore, TransactionSyncer

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/sync/SyncErrors.kt`
- Create: `app/src/main/java/com/example/poswarnus/data/sync/TransactionSyncStore.kt` (interface, `SyncCursor`, `PendingSale`, `RoomTransactionSyncStore`)
- Create: `app/src/main/java/com/example/poswarnus/data/sync/TransactionSyncer.kt` (`SyncResult`, `TransactionSyncer`)
- Create: `app/src/main/java/com/example/poswarnus/data/remote/TransactionRemoteDataSource.kt` (interface only)
- Test: `app/src/test/java/com/example/poswarnus/data/sync/SyncErrorsTest.kt`, `TransactionSyncerTest.kt`, `RoomTransactionSyncStoreTest.kt`

**Interfaces:**
- Consumes: `TransactionDao.pendingBatch/markSynced/recordFailure/linesFor`, `TransactionEntity`, `TransactionLineEntity` (Task 2); `NoSessionException` (`data.remote`).
- Produces (signatures exactly as spec 5.1):
  - `interface TransactionSyncStore { suspend fun pendingBatch(cashierId: String, after: SyncCursor?, limit: Int): List<PendingSale>; suspend fun markSynced(id: String, at: Long); suspend fun recordFailure(id: String, error: String) }`
  - `data class SyncCursor(val createdAt: Long, val id: String)`, `data class PendingSale(val header: TransactionEntity, val lines: List<TransactionLineEntity>)`
  - `class RoomTransactionSyncStore(private val dao: TransactionDao) : TransactionSyncStore` (a null cursor maps to `afterCreatedAt = Long.MIN_VALUE, afterId = ""`; lines via `dao.linesFor`).
  - `interface TransactionRemoteDataSource { suspend fun submit(sale: PendingSale) }` (idempotent; returns normally for first upload and "already there"; throws on any failure).
  - `data class SyncResult(val synced: Int, val failed: Int, val stoppedEarly: Boolean) { val needsRetry: Boolean }`
  - `class TransactionSyncer(store, remote, currentCashierId: () -> String?, now: () -> Long = System::currentTimeMillis, stopsBatch: (Throwable) -> Boolean = SyncErrors::stopsBatch, batchSize: Int = 20) { suspend fun syncPending(): SyncResult }`
  - `object SyncErrors { fun stopsBatch(e: Throwable): Boolean; fun describe(e: Throwable): String }`

- [ ] **Step 1: Write failing tests**
  - `SyncErrorsTest` (exceptions built like `AuthRepositoryTest` builds `AuthRestException`: a `MockEngine` `HttpResponse` with the wanted status, then `RestException("error", "description", response)`):
    - `stopsBatch` is true for `NoSessionException()`, `java.io.IOException`, `HttpRequestException` (built from a Ktor `HttpRequestBuilder`), `HttpRequestTimeoutException`, and a `RestException` with HTTP 401; false for a `RestException` with 403 and 404, and for `IllegalStateException("x")`.
    - `describe` prefixes: `offline: ` for IOException / `HttpRequestException` / `HttpRequestTimeoutException`; `session: ` for `NoSessionException` and RestException 401; `rejected: ` for RestException 403/404 and any other exception.
    - `describe(RestException("PGRST202", "function missing", response404))` contains `PGRST202` and `function missing`, does NOT contain `https://`, `URL` or `Headers` (the raw message must never be stored).
    - `describe` result is at most 200 characters for a 1000-character message.
  - `TransactionSyncerTest` with private `FakeStore` (in-memory list of `PendingSale`; models `pendingBatch` faithfully: PENDING only, cashier filter, `createdAt, id` order, keyset `after`, `limit`; records every call incl. cursors; `markSynced` calls `yield()` first so it is cancellation-sensitive) and `FakeRemote` (scripted per sale id: success, or a throwable; records submit order). Cases:
    - `uploads in createdAt order and marks SYNCED with now()` (three sales given out of order, `now = { 42L }`).
    - `a rejected row records rejected prefix and the next row still uploads` (`RestException` 403 on the first: `recordFailure` once with `rejected:` prefix, `SyncResult(synced = 1, failed = 1, stoppedEarly = false)`, `needsRetry`).
    - `an IOException records offline prefix and stops the batch` (later rows untouched: not submitted, no store writes; `stoppedEarly = true`).
    - `NoSessionException records session prefix and stops`.
    - `no cashier id returns stoppedEarly with zero store reads and writes` (assert the fake saw no calls at all).
    - `rows of another cashier are never fetched` (the cashier-filter is part of the fake; assert the other cashier's id is never submitted and the cashier id passed to `pendingBatch` is the signed-in one).
    - `more rows than batchSize all upload` (`batchSize = 2`, five rows; assert the cursors passed were `null`, then the last row of each page).
    - `a failed row is not re-read in the same run` (rejected first row of a page smaller than all rows: it is submitted exactly once in the run).
    - `cancellation propagates` (remote throws `CancellationException`: `syncPending()` rethrows, nothing recorded for that row).
    - `a success followed by cancellation still marks SYNCED`: `FakeRemote.submit` returns normally after cancelling the caller's job (`currentCoroutineContext().job.cancel()`); run `syncPending()` in a `launch`, `join`, assert the job is cancelled AND the store recorded `markSynced` for that id.
    - `empty queue returns needsRetry false` (`SyncResult(0, 0, false)`).
    - `a custom stopsBatch predicate is honoured` (inject `{ true }` for a non-network error).
  - `RoomTransactionSyncStoreTest` (Robolectric in-memory DB like `TransactionDaoTest`): `pendingBatch returns headers with their lines in order for a null cursor`; `markSynced and recordFailure delegate to the dao` (row reflects both).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.sync.*"`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement** per Interfaces and spec 5.2.
  - `syncPending()`: no cashier id -> `SyncResult(0, 0, stoppedEarly = true)` before touching the store. Loop `pendingBatch(cashierId, cursor, batchSize)` until empty; per sale `remote.submit`; success -> `withContext(NonCancellable) { store.markSynced(id, now()) }`, `synced++`; `CancellationException` is rethrown first; `stopsBatch(e)` -> `store.recordFailure(id, SyncErrors.describe(e))` (also under `NonCancellable`), return `stoppedEarly = true`; otherwise `recordFailure`, `failed++`, continue. The cursor moves to the current row in every case.
  - `SyncErrors`: classification per spec 5.2 (`RestException.response.status.value == 401` for the expired-JWT case; class check order: `NoSessionException`, `IOException` / `HttpRequestException` / `HttpRequestTimeoutException`, `RestException`). For a `RestException` build the text from `error` and `description` only, never `message`. Cut to 200 chars.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.sync.*"`
Expected: PASS; then `./gradlew testDebugUnitTest` all green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/sync/SyncErrors.kt app/src/main/java/com/example/poswarnus/data/sync/TransactionSyncStore.kt app/src/main/java/com/example/poswarnus/data/sync/TransactionSyncer.kt app/src/main/java/com/example/poswarnus/data/remote/TransactionRemoteDataSource.kt app/src/test/java/com/example/poswarnus/data/sync
git commit -m "feat: TransactionSyncer with keyset paging, error classification and Room store"
```

---

### Task 5: Supabase remote data source, payload DTO and ensureSessionForSync

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/remote/SubmitTransactionDto.kt`
- Create: `app/src/main/java/com/example/poswarnus/data/remote/SupabaseTransactionRemoteDataSource.kt`
- Modify: `app/src/main/java/com/example/poswarnus/data/auth/AuthRepository.kt`
- Modify (fakes of `AuthRepository`, one added method each): `app/src/test/java/com/example/poswarnus/ui/dashboard/LogoutViewModelTest.kt`, `app/src/test/java/com/example/poswarnus/ui/login/LoginViewModelTest.kt`, `app/src/test/java/com/example/poswarnus/ui/payment/PaymentViewModelTest.kt`, `app/src/androidTest/java/com/example/poswarnus/ui/login/LoginScreenTest.kt`
- Test: `app/src/test/java/com/example/poswarnus/data/remote/SubmitTransactionDtoTest.kt`, `SupabaseTransactionRemoteDataSourceTest.kt`; `app/src/test/java/com/example/poswarnus/data/auth/AuthRepositoryTest.kt` (add)

**Interfaces:**
- Consumes: `PendingSale`, `TransactionRemoteDataSource` (Task 4); `NoSessionException`.
- Produces:
  - `@Serializable data class SubmitTransactionDto(id, @SerialName("created_at") createdAt: String, @SerialName("cashier_id") cashierId, subtotal: Long, @SerialName("tax_label") taxLabel, @SerialName("tax_basis_points") taxBasisPoints: Int, @SerialName("tax_amount") taxAmount: Long, @SerialName("grand_total") grandTotal: Long, @SerialName("cash_received") cashReceived: Long, @SerialName("change_amount") changeAmount: Long, lines: List<SubmitLineDto>)` and `@Serializable data class SubmitLineDto(id, @SerialName("menu_item_id") menuItemId, name, @SerialName("base_price") basePrice: Long, quantity: Int, selections: JsonElement)`; `fun PendingSale.toSubmitDto(): SubmitTransactionDto`. `is_training` and per-line `cashier_id` are not sent (the SQL function uses the header's cashier for lines).
  - `createdAt` is the ISO-8601 UTC string of `header.createdAt`, built with `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)` and `TimeZone.getTimeZone("UTC")` (no `java.time`). Line ids are `header.id:index` (already stored in `TransactionLineEntity.id`; send as is). `selections = Json.parseToJsonElement(line.selectionsJson)`.
  - `class SupabaseTransactionRemoteDataSource(client: SupabaseClient, sessionAvailable: () -> Boolean) : TransactionRemoteDataSource`: throws `NoSessionException` before any network call when `sessionAvailable()` is false; otherwise `client.postgrest.rpc("submit_transaction", buildJsonObject { put("p", Json.encodeToJsonElement(dto)) })` (the overload `rpc(function: String, parameters: JsonObject, request: RpcRequestBuilder.() -> Unit = {})` exists in supabase-kt 3.1.4; confirmed in `Postgrest.kt` of the cached sources jar).
  - `AuthRepository.ensureSessionForSync(): Boolean` (spec 5.3): returns true when `currentUserId() != null`; otherwise loads the stored session JSON (`sessionStore.load()`, null -> false) and calls `gateway.restoreSession(json)`; on success refresh `cachedUserId = gateway.currentUserId()` and return true; on failure return false. It **never** calls `sessionStore.save(null)` and never throws except cancellation. `isLoggedIn()` stays exactly as it is.

- [ ] **Step 1: Write failing tests**
  - `SubmitTransactionDtoTest`: for a `PendingSale` with `createdAt = 1_000L`, two lines (one with a real `SelectionsJson.encode(...)` string with a negative `priceDelta`, one `"[]"`): `Json.encodeToJsonElement(dto)` has exactly the snake_case keys `id, created_at, cashier_id, subtotal, tax_label, tax_basis_points, tax_amount, grand_total, cash_received, change_amount, lines`; no `is_training` key; `created_at == "1970-01-01T00:00:01.000Z"`; lines have keys `id, menu_item_id, name, base_price, quantity, selections`; `selections` is a JSON array equal to the parsed stored string (not a string); money fields are JSON numbers.
  - `SupabaseTransactionRemoteDataSourceTest` (same `createSupabaseClient` + `MockEngine` pattern as `SupabaseCatalogRemoteDataSourceTest`, capturing path and request body): `submit throws NoSessionException without a session and never hits the network`; `submit posts to /rest/v1/rpc/submit_transaction with body {"p": {...}}` (body parsed as JSON: `p.id`, two lines); `submit propagates a server error` (MockEngine answers 404 with a PostgREST error body -> a `RestException` surfaces, not swallowed).
  - `AuthRepositoryTest` additions (existing `FakeAuthGateway`/`InMemorySessionStore`): `ensureSessionForSync returns true with a current user without touching the gateway` (`restoreSessionCallCount == 0`); `ensureSessionForSync restores from the stored session` (set `gateway.userId = null` first and extend the private `FakeAuthGateway` so a successful `restoreSession` sets `userId = "user-1"`; assert the result is true, `restoreSessionCallCount == 1` and `currentUserId() == "user-1"`); `ensureSessionForSync does NOT clear the store when restoreSession fails` (`restoreSessionResult = success(false)`: result false, `sessionStore.current` unchanged); `ensureSessionForSync returns false when nothing is stored` (no gateway call); `ensureSessionForSync does not clear the store when restoreSession throws` (`Result.failure(RuntimeException)`: result false, store unchanged).

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.remote.*" --tests "com.example.poswarnus.data.auth.*"`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement** per Interfaces. Add `override suspend fun ensureSessionForSync(): Boolean = true` (or `currentUserId() != null`) to the four fake `AuthRepository` classes. If `RestException`/`rpc` differs from the description above in 3.1.4, read the cached sources jar under `~/.gradle/caches/modules-2/files-2.1/io.github.jan-tennert.supabase/` and report the difference.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/remote/SubmitTransactionDto.kt app/src/main/java/com/example/poswarnus/data/remote/SupabaseTransactionRemoteDataSource.kt app/src/main/java/com/example/poswarnus/data/auth/AuthRepository.kt app/src/test/java/com/example/poswarnus/ui/dashboard/LogoutViewModelTest.kt app/src/test/java/com/example/poswarnus/ui/login/LoginViewModelTest.kt app/src/test/java/com/example/poswarnus/ui/payment/PaymentViewModelTest.kt app/src/androidTest/java/com/example/poswarnus/ui/login/LoginScreenTest.kt app/src/test/java/com/example/poswarnus/data/remote/SubmitTransactionDtoTest.kt app/src/test/java/com/example/poswarnus/data/remote/SupabaseTransactionRemoteDataSourceTest.kt app/src/test/java/com/example/poswarnus/data/auth/AuthRepositoryTest.kt
git commit -m "feat: Supabase submit_transaction data source and non-destructive ensureSessionForSync"
```

---

### Task 6: Scheduler, worker, Hilt module and triggers

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/sync/SyncScheduler.kt` (interface + `WorkManagerSyncScheduler`), `app/src/main/java/com/example/poswarnus/data/sync/TransactionSyncWorker.kt`, `app/src/main/java/com/example/poswarnus/di/SyncModule.kt`
- Modify: `app/src/main/java/com/example/poswarnus/data/transaction/TransactionRepository.kt` (constructor + call), `app/src/main/java/com/example/poswarnus/di/TransactionModule.kt`, `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardViewModel.kt`, `app/src/main/java/com/example/poswarnus/PosWarNusApplication.kt`
- Test: `app/src/test/java/com/example/poswarnus/data/sync/TransactionSyncWorkerTest.kt`, `WorkManagerSyncSchedulerTest.kt`; `app/src/test/java/com/example/poswarnus/data/transaction/TransactionRepositoryTest.kt` (update + add), `app/src/test/java/com/example/poswarnus/ui/dashboard/DashboardViewModelTest.kt` (update + add)

**Interfaces:**
- Consumes: `TransactionSyncer`, `RoomTransactionSyncStore`, `TransactionRemoteDataSource`, `SupabaseTransactionRemoteDataSource` (Tasks 4, 5), `AuthRepository.ensureSessionForSync/currentUserId`, `HiltWorkerFactory` setup (Task 1).
- Produces:
  - `interface SyncScheduler { fun requestSync(); fun ensurePeriodic() }`
  - `class WorkManagerSyncScheduler(context: Context) : SyncScheduler`: unique names `"transaction-sync-now"` (`OneTimeWorkRequest`, `ExistingWorkPolicy.REPLACE`) and `"transaction-sync-periodic"` (`PeriodicWorkRequest`, 15 minutes, `ExistingPeriodicWorkPolicy.KEEP`); both with `Constraints(requiredNetworkType = NetworkType.CONNECTED)`, `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)`, not expedited; `WorkManager.getInstance` is obtained lazily on first use, not at construction.
  - `@HiltWorker class TransactionSyncWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters, private val auth: AuthRepository, private val syncer: TransactionSyncer) : CoroutineWorker`: `doWork()` calls `auth.ensureSessionForSync()` (result ignored), then `syncer.syncPending()`; returns `Result.retry()` when `needsRetry`, else `Result.success()`.
  - `SyncModule` (Singleton, `@InstallIn(SingletonComponent)`): provides `TransactionSyncStore` (`RoomTransactionSyncStore(dao)`), `TransactionRemoteDataSource` (`SupabaseTransactionRemoteDataSource(client, sessionAvailable = { client.auth.currentSessionOrNull() != null })`), `TransactionSyncer` (`currentCashierId = { authRepository.currentUserId() }`), `SyncScheduler` (`WorkManagerSyncScheduler(@ApplicationContext context)`).
  - `class TransactionRepositoryImpl(private val dao: TransactionDao, private val syncScheduler: SyncScheduler)`: after `dao.insertSale(...)` returns (true or false) it calls `syncScheduler.requestSync()` inside its own try/catch for `Exception` (swallowed; `save` still returns `Result.success`). A failed write never schedules. `TransactionModule.provideTransactionRepository(dao, syncScheduler)`.
  - `DashboardViewModel(repository: CatalogRepository, taxRepository: TaxRepository, syncScheduler: SyncScheduler)`; `refresh()` calls `syncScheduler.requestSync()` (swallowing any exception) right after the `refreshInProgress` guard, next to the tax refresh. Task 7 appends `transactionRepository` as the fourth constructor parameter.
  - `PosWarNusApplication` also has `@Inject lateinit var syncScheduler: SyncScheduler` and, in `onCreate()` after `super.onCreate()`, calls `syncScheduler.ensurePeriodic()` inside try/catch (an init failure must not crash app start).

- [ ] **Step 1: Write failing tests**
  - `TransactionRepositoryTest` (use a `FakeSyncScheduler` recording `requestSyncCount`, optionally throwing; update the three `TransactionRepositoryImpl(...)` constructions): `save calls requestSync once after a successful write`; `a throwing scheduler does not fail save` (row still stored, result success); `a failed write does not schedule` (the existing failing anonymous DAO); `saving an existing id still succeeds and schedules` (second `save` of the same snapshot: success, `requestSync` called twice in total).
  - `DashboardViewModelTest`: update `viewModel(...)` to pass a `FakeSyncScheduler`; add `refresh calls requestSync on init and on a manual refresh` (count 1 after init, 2 after `refresh()` once idle); `a concurrent refresh while one is in flight does not schedule again`; `a throwing scheduler does not break refresh` (catalog still loads, `isLoading` ends false).
  - `TransactionSyncWorkerTest` (Robolectric, `TestListenableWorkerBuilder<TransactionSyncWorker>` with a hand-made `WorkerFactory` that calls the constructor with a fake `AuthRepository` and a real `TransactionSyncer` over the Task 4 style fakes; copy the fakes locally, they are private to their test files): `returns retry when the syncer reports needsRetry` (remote throws IOException); `returns success when nothing is left to retry` (empty queue and a fully successful queue); `calls ensureSessionForSync before syncing` (fake auth records an event list shared with the fake store's first `pendingBatch` call; auth event comes first).
  - `WorkManagerSyncSchedulerTest` (Robolectric, `WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())`): `requestSync twice keeps exactly one unique one-time work` (second call replaces; `getWorkInfosForUniqueWork("transaction-sync-now")` has one non-cancelled info); `ensurePeriodic twice keeps one periodic work` (KEEP; one info for `"transaction-sync-periodic"`). If `work-testing` cannot drive this under Robolectric, delete this test, say so in the report, and cover it in Task 8.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.sync.*" --tests "com.example.poswarnus.data.transaction.*" --tests "com.example.poswarnus.ui.dashboard.*"`
Expected: FAIL (unresolved references / constructor mismatch).

- [ ] **Step 3: Implement** per Interfaces. `ensurePeriodic()` and `requestSync()` only enqueue; they never block. Update `TransactionModule` and the Application.

- [ ] **Step 4: Run the full gate**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS, BUILD SUCCESSFUL. Watch for slowdowns or failures in other Robolectric tests caused by `ensurePeriodic()` running in `Application.onCreate()`; if any appear, keep the try/catch and the lazy `WorkManager.getInstance` and report it.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/sync/SyncScheduler.kt app/src/main/java/com/example/poswarnus/data/sync/TransactionSyncWorker.kt app/src/main/java/com/example/poswarnus/di/SyncModule.kt app/src/main/java/com/example/poswarnus/data/transaction/TransactionRepository.kt app/src/main/java/com/example/poswarnus/di/TransactionModule.kt app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/example/poswarnus/PosWarNusApplication.kt app/src/test/java/com/example/poswarnus/data/sync app/src/test/java/com/example/poswarnus/data/transaction/TransactionRepositoryTest.kt app/src/test/java/com/example/poswarnus/ui/dashboard/DashboardViewModelTest.kt
git commit -m "feat: sync worker, scheduler and triggers after save, dashboard refresh and periodic"
```

---

### Task 7: Sync status pill — summary flow, state machine and header UI

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/sync/SyncSummary.kt`, `app/src/main/java/com/example/poswarnus/ui/dashboard/SyncPill.kt` (pure: `SyncPill`, `syncPillOf`, `syncPillLabel`), `app/src/main/java/com/example/poswarnus/ui/dashboard/SyncStatusPill.kt` (composable)
- Modify: `app/src/main/java/com/example/poswarnus/data/transaction/TransactionRepository.kt` (`syncSummary`), `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardViewModel.kt`, `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt` (`CatalogPane`, `CatalogHeader`)
- Modify (fakes of `TransactionRepository`): `app/src/test/java/com/example/poswarnus/ui/payment/PaymentViewModelTest.kt`
- Test: `app/src/test/java/com/example/poswarnus/ui/dashboard/SyncPillTest.kt`; `DashboardViewModelTest.kt` (add)

**Interfaces:**
- Consumes: `TransactionDao.observeSyncSummary()` / `SyncSummaryRow` (Task 2), `SyncScheduler` in `DashboardViewModel` (Task 6), theme tokens `ui/theme/Color.kt` (`AccentTint`, `AccentOrangeDark`, `LineDefault`, `TextSecondary`, `TextPrimary`, `SuccessGreen`).
- Produces:
  - `data class SyncSummary(val pendingCount: Int, val offlineCount: Int, val lastSyncedAt: Long?)`
  - `TransactionRepository.syncSummary: Flow<SyncSummary>`; `TransactionRepositoryImpl` maps `dao.observeSyncSummary()`. (`FakeTransactionRepository` in `PaymentViewModelTest` gets `override val syncSummary = flowOf(SyncSummary(0, 0, null))`; the `TransactionRepository` fake needed by `DashboardViewModelTest` is a `MutableStateFlow<SyncSummary>` exposed as `syncSummary`.)
  - ```kotlin
    sealed interface SyncPill {
        data object None : SyncPill
        data class Offline(val pendingCount: Int) : SyncPill   // spec 6.1 shows a data object; the count is needed for the accessible description the same section requires
        data class Pending(val count: Int) : SyncPill
        data class Synced(val atMillis: Long) : SyncPill
    }
    fun syncPillOf(catalogOffline: Boolean, summary: SyncSummary): SyncPill
    fun syncPillLabel(pill: SyncPill, nowMillis: Long, timeZone: TimeZone): String
    fun syncPillDescription(pill: SyncPill): String
    ```
    Rules, first match wins: `catalogOffline || offlineCount > 0` -> `Offline(pendingCount)`; `pendingCount > 0` -> `Pending(pendingCount)`; `lastSyncedAt != null` -> `Synced(lastSyncedAt)`; else `None`. `syncPillLabel` uses `SimpleDateFormat` with `Locale.US` and the given `TimeZone` (no `java.time`): `Offline` / `3 menunggu sinkron` / `Tersinkron 14:05`, or `Tersinkron 23/10 14:05` when the instant is not on the same calendar day (in `timeZone`) as `nowMillis`; `None` -> empty string. `syncPillDescription`: `Offline` -> `"Offline"` when `pendingCount == 0`, else `"Offline, N transaksi menunggu sinkron"`; other pills describe themselves like their label.
  - `DashboardUiState.syncPill: SyncPill = SyncPill.None`; `DashboardViewModel(repository, taxRepository, syncScheduler, transactionRepository: TransactionRepository)`; the existing two-flow `combine` gets a third step with `transactionRepository.syncSummary` and sets `syncPill = syncPillOf(isOffline || loadError, summary)` (no formatting in the ViewModel).
  - `@Composable fun SyncStatusPill(pill: SyncPill, modifier: Modifier = Modifier)`: renders nothing for `None`; a small rounded pill with a leading dot plus the label, `maxLines = 1`, `semantics { contentDescription = syncPillDescription(pill) }`, not clickable; colours from the tokens above (neutral grey `LineDefault`/`TextSecondary` for Offline matching the existing banner, `AccentTint`/`AccentOrangeDark` for Pending, plain `TextSecondary` text with a `SuccessGreen` dot for Synced).
  - `CatalogHeader(compact, searchQuery, onSearchQueryChange, onLogout, syncPill: SyncPill)`: wide layout puts the pill between the `Spacer` and the search field; compact layout puts it in the first row between the brand block and "Keluar". Brand title "Warung Nusantara" and subtitle "RestoPOS" get `maxLines = 1` with `TextOverflow.Ellipsis`; the pill and "Keluar" never shrink (the brand column, which carries the `weight`, gives way). `CatalogPane` passes `state.syncPill`.

- [ ] **Step 1: Write failing tests**
  - `SyncPillTest` (fixed zone `TimeZone.getTimeZone("Asia/Jakarta")`, fixed `nowMillis`; epoch values computed in the test): `catalogOffline wins over synced` (`Offline`); `catalogOffline with pending keeps the count in Offline`; `offlineCount greater than zero wins over pending` (`summary(5, 1, null)` -> `Offline(5)`); `rejected or session failures give Pending` (`summary(2, 0, 123)` -> `Pending(2)`); `pending wins over synced`; `lastSyncedAt only gives Synced`; `nothing gives None`; `label for Pending is "3 menunggu sinkron"`; `label for Synced today is "Tersinkron HH:mm"` and `for an earlier day is "Tersinkron dd/MM HH:mm"` in `Asia/Jakarta`, including a case where UTC and Jakarta fall on different calendar days; `label for Offline is "Offline"`; `description for Offline with 4 pending is "Offline, 4 transaksi menunggu sinkron"` and with 0 pending is `"Offline"`.
  - `DashboardViewModelTest` additions (fake `TransactionRepository` with a `MutableStateFlow<SyncSummary>`; update `viewModel(...)`): `syncPill follows the summary flow` (None -> Pending(2) -> Synced(t) when the flow changes); `syncPill is Offline when the catalog refresh failed with a cache` and `when it failed with no cache (loadError)`; `offlineCount makes the pill Offline even when the catalog loaded`; `existing assertions still pass with the new constructor` is covered by the unchanged suite.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.dashboard.*"`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement** per Interfaces; keep `CatalogHeader` private in `DashboardScreen.kt` as today. UI has no JVM test: it is verified by compile here and on the emulator in Task 8.

- [ ] **Step 4: Run the full gate**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/sync/SyncSummary.kt app/src/main/java/com/example/poswarnus/ui/dashboard/SyncPill.kt app/src/main/java/com/example/poswarnus/ui/dashboard/SyncStatusPill.kt app/src/main/java/com/example/poswarnus/data/transaction/TransactionRepository.kt app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt app/src/test/java/com/example/poswarnus/ui/payment/PaymentViewModelTest.kt app/src/test/java/com/example/poswarnus/ui/dashboard/SyncPillTest.kt app/src/test/java/com/example/poswarnus/ui/dashboard/DashboardViewModelTest.kt
git commit -m "feat: sync status pill in the catalog header driven by Room"
```

---

### Task 8: Emulator verification and roadmap update

**Files:**
- Modify: `docs/roadmap.md` (Phase 5 status block, what was verified / NOT verified, owner action for the SQL file)

- [ ] **Step 1: Run the full gate**

Run: `./gradlew testDebugUnitTest assembleDebug lint`
Expected: tests and build green; lint reports no new errors beyond the known two in androidTest `LoginScreenTest`.

- [ ] **Step 2: Emulator checklist** (tablet `emulator-5554`, phone `emulator-5556`; `adb` at `~/Library/Android/sdk/platform-tools/adb`, emulator at `~/Library/Android/sdk/emulator/emulator`, neither is on PATH; log in with the test cashier typed by the controller; phone size via `adb shell wm size 1080x2400`, reset with `wm size reset`). The live Supabase project does **not** have the new tables/function (nobody applies the SQL), which is the expected state:
  1. **Upgrade v4 -> v5 keeps data:** before installing, pull the installed Phase 4 database (`adb exec-out run-as com.example.poswarnus cat databases/pos_warnus.db`, to the scratchpad) and note `PRAGMA user_version` (expect 4) and the sale count with host `sqlite3`; `adb install -r` the new debug APK over it (no uninstall); launch, log in if needed; pull again: `user_version` 5, same sales, every old sale `syncStatus = 'PENDING'`.
  2. **Table not applied on the server:** the app opens normally, no crash (check `adb logcat` for `FATAL`/`AndroidRuntime`); make a sale (cash, "Uang pas"): it completes; the pill reads "N menunggu sinkron" (not "Offline"); pull the DB: the rows have `lastSyncError` starting `rejected:` and `syncAttempts` grows after a dashboard reopen or "Coba lagi" tap; nothing is deleted; `lastSyncError` contains no `https://` or `Authorization`.
  3. **Offline:** Wi-Fi/airplane mode on (`adb shell cmd connectivity airplane-mode enable`, or the emulator's network toggle), make a sale: it completes, the pill reads "N menunggu sinkron" (or "Offline" once a catalog refresh fails); network back on and the dashboard reopened: the app does not crash, attempts resume (rejected, as in 2).
  4. **Phone width:** at 1080x2400 the pill and "Keluar" both fit in the compact header; with `12 menunggu sinkron` (queue enough sales, or temporarily trust the count from earlier sales) the brand text ellipsizes, "Keluar" stays fully visible; the wide (tablet) header shows the pill between the spacer and search.
  5. **No cache, no network (regression of spec 6.3):** with the app force-stopped, delete only the Room DB (`adb shell run-as com.example.poswarnus rm databases/pos_warnus.db databases/pos_warnus.db-shm databases/pos_warnus.db-wal`; the DataStore session stays), airplane mode on, launch: "Tidak bisa memuat menu, periksa koneksi" with "Coba lagi" appears. If the session cannot be restored offline, record it as NOT verified (a fresh install cannot log in offline).
  6. **Training rows:** nothing to do on the device (no Training Mode yet); the DAO tests cover it. Record as verified by unit tests only.
  Record anything not checked as NOT verified. Do not run SQL against Supabase and do not ask anyone to.

- [ ] **Step 3: Update `docs/roadmap.md`:** Phase 5 row -> "Done on branch `feature/phase5-offline-sync`, not merged"; add a "Phase 5 status" block like Phase 4's: what was implemented, what was verified (items above with results), NOT verified (live upload, the idempotent re-submit, "authenticated cannot read", Tersinkron pill, kill-between-save-and-upload duplicate check, Doze/battery-saver periodic timing, a physical device), and **Owner action: apply `supabase/migrations/20261024000000_add_transactions_sync.sql` to the live project, then re-run spec section 8 scenarios 1, 2 and 4 against it**. List known leftovers: `isLoggedIn()` clears the stored session on a failed restore including a network timeout (tracked separately, spec 9); the two `LoginScreenTest` lint errors.

- [ ] **Step 4: Commit**

```bash
git add docs/roadmap.md
git commit -m "docs: record Phase 5 status and verification"
```
