# Phase 5 — Offline Sync of Transactions to Supabase: Design

Date: 2026-10-24. PRD: rest of 4.9 (4.7, 4.11 and 4.13 constrain it). Roadmap: Phase 5.

## 1. Goal and scope

Upload the sales that Phase 4 stores in Room to Supabase, in the background, without ever blocking a sale and without ever duplicating one. Show the cashier an honest status: offline, N waiting, or last synced time.

In scope: Supabase tables `transactions` and `transaction_lines` plus one atomic, idempotent RPC `submit_transaction` (SQL in section 4.1, owner applies it); Room v5 (`MIGRATION_4_5`) with sync columns on `transactions`; `TransactionSyncer` (core logic, JVM-testable), `TransactionRemoteDataSource`, `TransactionSyncWorker` (WorkManager), `SyncScheduler`; a single sync-status pill in the Dashboard catalog header; verification of the "cannot load catalog, check connection" state.

Out of scope: any change to the payment screen; conflict resolution UI; deleting local transactions after sync (Phase 7 reads the local history); a FAILED state or a retry cap; reading transactions back from Supabase; Training Mode (Phase 6; `isTraining = true` rows are never queued, and Phase 4 never writes them anyway); applying the SQL to the live project (owner action).

## 2. Decisions

| Topic | Decision |
|---|---|
| Server tables | `transactions` (id uuid = local UUID) and `transaction_lines` (id text = `<txId>:<index>`, FK with `on delete cascade`). |
| Upload call | One RPC `public.submit_transaction(p jsonb)`, `security invoker`, inserts header and all lines in one database transaction. Idempotent: `ON CONFLICT (id) DO NOTHING`; if the header already exists nothing else is written and the call succeeds. |
| RLS | `authenticated` may only INSERT with `cashier_id = auth.uid()`. No SELECT / UPDATE / DELETE policy: the cashier app is write-only on the server. History (Phase 7) reads Room. |
| `cashier_id` on lines | Added to `transaction_lines` (a consequence of the write-only RLS rule, not a change of intent). A lines policy that checked the parent header would need a SELECT policy on `transactions`, which does not exist. A column on the line itself lets the policy stay `cashier_id = auth.uid()`. |
| `menu_item_id` | `uuid` (same type as `menu_items.id`), deliberately without a foreign key so history survives a menu item being deleted. |
| Local state | `syncStatus` `PENDING` or `SYNCED`, `syncedAt`, `syncAttempts`, `lastSyncError`. Old rows become `PENDING`. No `FAILED`. |
| Retry policy | Unbounded, exponential backoff (WorkManager). A failure only raises `syncAttempts` and fills `lastSyncError`. |
| Engine | WorkManager; logic in a plain class `TransactionSyncer`; `TransactionSyncWorker` is a thin `@HiltWorker`. |
| Unique-work policy | `ExistingWorkPolicy.REPLACE` for "sync now" triggers (analysis in 5.4). |
| Save trigger | `TransactionRepositoryImpl` calls `SyncScheduler.requestSync()` after a successful local write (analysis in 5.4). |
| Other triggers | Dashboard ViewModel `refresh()` (runs on dashboard open and on every manual retry) and a 15-minute periodic request registered in `Application.onCreate`. |
| Indicator | One pill: `Offline`, `N menunggu sinkron`, `Tersinkron HH:mm`, or none. Fed by one Room `Flow`; no DataStore. |
| "Offline" evidence | Real results only: a failed catalog refresh, or pending rows whose last upload failed with a connectivity error. `ConnectivityManager` is not used for the pill. |
| Catalog "cannot load" state | Already implemented in Phase 2 (`loadError`); verified in section 7, no change needed. |
| Local test data | Phase 4 sales on the emulator become `PENDING`; expected. The owner applies the SQL, Phase 5 never does. |

## 3. Existing code this design relies on (verified)

- `TransactionEntity` / `TransactionLineEntity` / `TransactionDao.insertSale` (atomic, `OnConflictStrategy.IGNORE` on the header). `AppDatabase` is v4, `exportSchema = true`, schemas in `app/schemas/.../{2,3,4}.json`.
- `SelectionsJson` already documents itself as "the shape Phase 5 uploads as `jsonb`"; the stored string is a JSON array of `{optionId, groupId, name, priceDelta, kind}`.
- `NoSessionException` (in `data/remote/SupabaseCatalogRemoteDataSource.kt`) and the `sessionAvailable: () -> Boolean` pattern used by `SupabaseTaxRemoteDataSource`.
- `AuthRepository.currentUserId()` (cached after login, survives an expired token).
- `DashboardViewModel` already derives `isOffline` / `loadError` / `isLoading` from the catalog refresh and Room caches; `CatalogHeader` has a `compact` mode (width < 600dp).
- `PosWarNusApplication` is a bare `@HiltAndroidApp` class; there is no WorkManager or Hilt-work dependency yet.
- **Pitfall found:** `AuthRepositoryImpl.isLoggedIn()` clears the stored session whenever `restoreSession` returns false, and `restoreSession` also returns false on a network timeout. A background worker must therefore NOT call `isLoggedIn()`; a flaky connection would log the cashier out. Section 5.3 adds a non-destructive method instead.

## 4. Data

### 4.1 Supabase (owner applies manually, like the earlier migrations)

File to be created in the plan (this spec only contains its text): `supabase/migrations/20261024000000_add_transactions_sync.sql`. Its header comment must say: applied manually by the project owner, NOT executed by the app or any build step, NOT idempotent (re-running `create table` / `create policy` / `create function` fails; `create function` is written with `create or replace` only so a fixed function body can be re-applied on its own), and that until it is applied the app keeps working and shows "N menunggu sinkron".

```sql
create table public.transactions (
  id               uuid primary key,
  created_at       timestamptz not null,
  cashier_id       uuid not null references auth.users (id),
  subtotal         bigint not null check (subtotal >= 0),
  tax_label        text not null,
  tax_basis_points int not null check (tax_basis_points >= 0),
  tax_amount       bigint not null check (tax_amount >= 0),
  grand_total      bigint not null check (grand_total >= 0),
  cash_received    bigint not null check (cash_received >= 0),
  change_amount    bigint not null check (change_amount >= 0),
  is_training      boolean not null default false check (not is_training),
  received_at      timestamptz not null default now(),
  check (grand_total = subtotal + tax_amount),
  check (cash_received >= grand_total),
  check (change_amount = cash_received - grand_total)
);

create index transactions_created_at_idx on public.transactions (created_at desc);
create index transactions_cashier_created_idx on public.transactions (cashier_id, created_at desc);

create table public.transaction_lines (
  id             text primary key,
  transaction_id uuid not null references public.transactions (id) on delete cascade,
  cashier_id     uuid not null references auth.users (id),
  menu_item_id   uuid not null,
  name           text not null,
  base_price     bigint not null check (base_price >= 0),
  quantity       int not null check (quantity > 0),
  selections     jsonb not null default '[]'::jsonb
);

create index transaction_lines_transaction_idx on public.transaction_lines (transaction_id);

alter table public.transactions enable row level security;
alter table public.transaction_lines enable row level security;

-- Write-only for cashiers: INSERT of their own rows, nothing else. No select/update/delete policy exists.
create policy "Cashiers insert own transactions"
  on public.transactions for insert to authenticated
  with check (cashier_id = (select auth.uid()));

create policy "Cashiers insert own transaction lines"
  on public.transaction_lines for insert to authenticated
  with check (cashier_id = (select auth.uid()));

revoke all on public.transactions, public.transaction_lines from anon;

create or replace function public.submit_transaction(p jsonb)
returns void
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_id      uuid := (p ->> 'id')::uuid;
  v_cashier uuid := (p ->> 'cashier_id')::uuid;
  v_rows    int;
begin
  if coalesce(jsonb_array_length(p -> 'lines'), 0) = 0 then
    raise exception 'a transaction needs at least one line';
  end if;

  insert into public.transactions (
    id, created_at, cashier_id, subtotal, tax_label, tax_basis_points,
    tax_amount, grand_total, cash_received, change_amount
  ) values (
    v_id, (p ->> 'created_at')::timestamptz, v_cashier, (p ->> 'subtotal')::bigint, p ->> 'tax_label',
    (p ->> 'tax_basis_points')::int, (p ->> 'tax_amount')::bigint, (p ->> 'grand_total')::bigint,
    (p ->> 'cash_received')::bigint, (p ->> 'change_amount')::bigint
  )
  on conflict (id) do nothing;

  get diagnostics v_rows = row_count;
  if v_rows = 0 then
    return; -- already uploaded earlier: write nothing, report success
  end if;

  insert into public.transaction_lines (
    id, transaction_id, cashier_id, menu_item_id, name, base_price, quantity, selections
  )
  select l ->> 'id', v_id, v_cashier, (l ->> 'menu_item_id')::uuid, l ->> 'name',
         (l ->> 'base_price')::bigint, (l ->> 'quantity')::int, coalesce(l -> 'selections', '[]'::jsonb)
  from jsonb_array_elements(p -> 'lines') as l;
end;
$$;

revoke all on function public.submit_transaction(jsonb) from public, anon;
grant execute on function public.submit_transaction(jsonb) to authenticated;
```

Notes:
- The function is `security invoker`, so RLS applies to its inserts: a payload whose `cashier_id` is not the caller's id fails with 42501 and rolls everything back. The header and its lines are one database transaction, so a partial sale can never exist on the server.
- `check (not is_training)` is a server-side guard for the PRD 4.13 rule that training sales never reach Supabase. The app never sends the field; the column exists so Phase 6 and any admin tooling can rely on it.
- Table privileges for `authenticated` are deliberately not revoked: `INSERT ... ON CONFLICT` may need column-level SELECT on the conflict target, and RLS (no SELECT policy) already hides every row from cashiers. The verification step in section 8 checks both "re-submit is a no-op" and "authenticated cannot read".
- The SQL is NOT idempotent (see header comment) and only the owner applies it to the live project.
- **Correction after the live run (2026-10-24):** the assumption above that `INSERT ... ON CONFLICT DO NOTHING` needs only an INSERT policy is wrong. Postgres also applies SELECT policies to an INSERT with an ON CONFLICT clause, so with no SELECT policy every upload failed with 42501. The function is therefore `security definer` (`set search_path = public`) and checks `cashier_id = auth.uid()` itself, which keeps cashiers write-only; see `supabase/migrations/20261024100000_fix_submit_transaction_security_definer.sql` (applied with the owner's approval). The "security invoker" wording elsewhere in this spec describes the first version.

### 4.2 Room v5 (`MIGRATION_4_5`, schema `5.json` exported)

Entity change (columns appended after `isTraining`, in this order, so the migrated table matches what Room generates):

```kotlin
@ColumnInfo(defaultValue = "'PENDING'") val syncStatus: String = SyncStatus.PENDING,
val syncedAt: Long? = null,
@ColumnInfo(defaultValue = "0") val syncAttempts: Int = 0,
val lastSyncError: String? = null,
```

plus `indices = [Index("syncStatus")]` on `TransactionEntity`, and `object SyncStatus { const val PENDING = "PENDING"; const val SYNCED = "SYNCED" }`.

Room's generated DDL (as it will appear in `5.json`):

```sql
CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `cashierId` TEXT NOT NULL, `subtotal` INTEGER NOT NULL, `taxLabel` TEXT NOT NULL, `taxBasisPoints` INTEGER NOT NULL, `taxAmount` INTEGER NOT NULL, `grandTotal` INTEGER NOT NULL, `cashReceived` INTEGER NOT NULL, `changeAmount` INTEGER NOT NULL, `isTraining` INTEGER NOT NULL DEFAULT 0, `syncStatus` TEXT NOT NULL DEFAULT 'PENDING', `syncedAt` INTEGER, `syncAttempts` INTEGER NOT NULL DEFAULT 0, `lastSyncError` TEXT, PRIMARY KEY(`id`))
CREATE INDEX IF NOT EXISTS `index_transactions_syncStatus` ON `transactions` (`syncStatus`)
```

`MIGRATION_4_5` (non-destructive; `transaction_lines` and every other table untouched):

```kotlin
db.execSQL("ALTER TABLE `transactions` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'PENDING'")
db.execSQL("ALTER TABLE `transactions` ADD COLUMN `syncedAt` INTEGER")
db.execSQL("ALTER TABLE `transactions` ADD COLUMN `syncAttempts` INTEGER NOT NULL DEFAULT 0")
db.execSQL("ALTER TABLE `transactions` ADD COLUMN `lastSyncError` TEXT")
db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_syncStatus` ON `transactions` (`syncStatus`)")
```

Registered in `DatabaseModule` after `MIGRATION_3_4`; `AppDatabase.version = 5`. Existing rows get `syncStatus = 'PENDING'`, which is exactly right: they were never uploaded.

`lastSyncError` is a short string with a kind prefix, written by the syncer, never shown raw to the cashier:

| Prefix | Meaning | Examples |
|---|---|---|
| `offline: ` | Server not reachable | `IOException`, `HttpRequestException`, `HttpRequestTimeoutException` |
| `session: ` | No usable login | `NoSessionException`, HTTP 401 |
| `rejected: ` | Server answered with an error | other `RestException` (RLS 42501, missing function PGRST202, constraint violation) |

Messages are cut to 200 characters. Only the `offline:` prefix drives the "Offline" pill.

### 4.3 DAO additions (`TransactionDao`)

```kotlin
@Query("""SELECT * FROM transactions
          WHERE syncStatus = 'PENDING' AND isTraining = 0 AND cashierId = :cashierId
            AND (createdAt > :afterCreatedAt OR (createdAt = :afterCreatedAt AND id > :afterId))
          ORDER BY createdAt, id LIMIT :limit""")
abstract suspend fun pendingBatch(cashierId: String, afterCreatedAt: Long, afterId: String, limit: Int): List<TransactionEntity>

@Query("UPDATE transactions SET syncStatus = 'SYNCED', syncedAt = :at, lastSyncError = NULL WHERE id = :id")
abstract suspend fun markSynced(id: String, at: Long)

@Query("UPDATE transactions SET syncAttempts = syncAttempts + 1, lastSyncError = :error WHERE id = :id")
abstract suspend fun recordFailure(id: String, error: String)

@Query("""SELECT
            COALESCE(SUM(CASE WHEN syncStatus = 'PENDING' THEN 1 ELSE 0 END), 0) AS pendingCount,
            COALESCE(SUM(CASE WHEN syncStatus = 'PENDING' AND lastSyncError LIKE 'offline:%' THEN 1 ELSE 0 END), 0) AS offlineCount,
            MAX(syncedAt) AS lastSyncedAt
          FROM transactions WHERE isTraining = 0""")
abstract fun observeSyncSummary(): Flow<SyncSummaryRow>
```

`SyncSummaryRow(pendingCount: Int, offlineCount: Int, lastSyncedAt: Long?)` is a plain data class (Room POJO). `pendingBatch` filters by `cashierId` on purpose: the server rejects a sale whose `cashier_id` differs from the session user, so sales of another cashier wait (still `PENDING`, not counted as failures) until that cashier is signed in again, instead of failing forever. The keyset cursor (`afterCreatedAt`, `afterId`) lets one run walk past rows that failed without re-reading them.

## 5. Sync engine

### 5.1 Components and signatures (package `data/sync`, remote part in `data/remote`)

```kotlin
/** What the syncer needs from Room; implemented by RoomTransactionSyncStore(dao). Lets JVM tests use a fake. */
interface TransactionSyncStore {
    suspend fun pendingBatch(cashierId: String, after: SyncCursor?, limit: Int): List<PendingSale>
    suspend fun markSynced(id: String, at: Long)
    suspend fun recordFailure(id: String, error: String)
}
data class SyncCursor(val createdAt: Long, val id: String)
data class PendingSale(val header: TransactionEntity, val lines: List<TransactionLineEntity>)

interface TransactionRemoteDataSource {
    /** Idempotent upload of one sale. Returns normally both for a first upload and for "already there". Throws on any failure. */
    suspend fun submit(sale: PendingSale)
}

class SupabaseTransactionRemoteDataSource(
    private val client: SupabaseClient,
    private val sessionAvailable: () -> Boolean,
) : TransactionRemoteDataSource   // throws NoSessionException first when there is no session

data class SyncResult(val synced: Int, val failed: Int, val stoppedEarly: Boolean) {
    /** True when something is still PENDING because of an error in this run. */
    val needsRetry: Boolean get() = failed > 0 || stoppedEarly
}

class TransactionSyncer(
    private val store: TransactionSyncStore,
    private val remote: TransactionRemoteDataSource,
    private val currentCashierId: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val stopsBatch: (Throwable) -> Boolean = SyncErrors::stopsBatch,
    private val batchSize: Int = 20,
) {
    /** Walks the PENDING, non-training sales of the signed-in cashier in createdAt order. Never throws except cancellation. */
    suspend fun syncPending(): SyncResult
}

object SyncErrors {
    fun stopsBatch(e: Throwable): Boolean   // offline or session kind
    fun describe(e: Throwable): String      // "<prefix>: <ClassName>: <message>" cut to 200 chars
}

interface SyncScheduler {
    fun requestSync()      // enqueue "sync now" (REPLACE)
    fun ensurePeriodic()   // idempotent registration of the 15-minute request (KEEP)
}

class WorkManagerSyncScheduler(context: Context) : SyncScheduler

@HiltWorker
class TransactionSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val syncer: TransactionSyncer,
) : CoroutineWorker(context, params)
```

The payload sent to the RPC is built in `SupabaseTransactionRemoteDataSource` from `PendingSale` with a `@Serializable` DTO (`SubmitTransactionDto`, `@SerialName` snake_case: `id`, `created_at` as `Instant.ofEpochMilli(createdAt).toString()`, `cashier_id`, `subtotal`, `tax_label`, `tax_basis_points`, `tax_amount`, `grand_total`, `cash_received`, `change_amount`, `lines[]` with `id`, `menu_item_id`, `name`, `base_price`, `quantity`, `selections` = `Json.parseToJsonElement(selectionsJson)`). The call is `client.postgrest.rpc("submit_transaction", buildJsonObject { put("p", payload) })`; the implementer confirms the exact `rpc` overload in supabase-kt 3.1.4. `is_training` is not sent.

### 5.2 `TransactionSyncer.syncPending()` flow

1. `cashierId = currentCashierId() ?: return SyncResult(0, 0, stoppedEarly = true)`. Nothing is written or deleted without a session.
2. Loop: `batch = store.pendingBatch(cashierId, cursor, batchSize)`; stop when empty.
3. For each sale in order: `remote.submit(sale)`.
   - Success: `store.markSynced(id, now())` (inside `withContext(NonCancellable)` so a cancellation right after the server accepted the sale still records it), `synced++`.
   - Failure with `CancellationException`: rethrown.
   - Failure with `stopsBatch(e)` (offline or session): `store.recordFailure(id, describe(e))` for this row, `stoppedEarly = true`, return. The remaining rows are not tried; there is no point hammering a dead connection.
   - Any other failure (the server rejected this sale): `store.recordFailure(id, describe(e))`, `failed++`, continue with the next row.
   - In every case the cursor moves to this row, so a failed row is not re-read in the same run.
4. Return the `SyncResult`.

`SyncErrors.stopsBatch` is true for `NoSessionException`, `java.io.IOException`, `HttpRequestException`, `HttpRequestTimeoutException`, and a `RestException` with HTTP status 401 (expired JWT). The implementer verifies the status property name on supabase-kt's `RestException`. Everything else counts as "rejected".

### 5.3 Worker, session and constraints

`TransactionSyncWorker.doWork()`:

```kotlin
auth.ensureSessionForSync()                 // best effort, result ignored
val result = syncer.syncPending()
return if (result.needsRetry) Result.retry() else Result.success()
```

New method `AuthRepository.ensureSessionForSync(): Boolean`: if `currentUserId()` is non-null return true; otherwise load the stored session JSON and call `gateway.restoreSession(json)` WITHOUT ever clearing the `SessionStore` on failure (unlike `isLoggedIn()`), and refresh `cachedUserId` on success. A failure just returns false, so the syncer sees no cashier id and the worker retries later. Existing `isLoggedIn()` behaviour is left alone (flagged in section 9).

`TransactionSyncer` is constructed in a Hilt module (`SyncModule`) with `currentCashierId = { authRepository.currentUserId() }`.

Request configuration (shared by both requests):
- `Constraints(requiredNetworkType = NetworkType.CONNECTED)`.
- `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)`; WorkManager caps backoff at 5 hours.
- Not expedited (expedited work needs a foreground notification on API < 31 for no real gain here).

### 5.4 Triggers and the scheduling analysis

Unique names: `"transaction-sync-now"` (one-time) and `"transaction-sync-periodic"` (periodic, 15 minutes, `ExistingPeriodicWorkPolicy.KEEP`, registered by `ensurePeriodic()` from `PosWarNusApplication.onCreate` after `super.onCreate()`). The periodic request is the safety net that also resumes work after a reboot or process death.

Policy for the one-time request:

| Policy | Problem |
|---|---|
| `KEEP` | A new sale saved while an earlier run is running can be missed (the run already read its batch), and while the previous run waits in backoff the new request is dropped, so a fresh sale could wait up to the 5 h backoff cap. |
| `APPEND_OR_REPLACE` | The new request is chained behind the old one; a predecessor that keeps returning `retry()` blocks it. |
| `REPLACE` (chosen) | Cancels an in-flight run and resets backoff, so every trigger means "try right now". Safe because the upload is idempotent and `markSynced` runs non-cancellably: the worst case is one repeated, harmless RPC. |

Triggers:
- (a) After a successful local save: `TransactionRepositoryImpl` gets a `SyncScheduler` constructor parameter and calls `requestSync()` after `dao.insertSale(...)` returns (also when it returns false because the id already existed: harmless). The call is wrapped so any scheduler exception is swallowed; the sale is already safe in Room and `save` must still return success. Why the repository and not `PaymentViewModel`: it is the single choke point for every local write, the ViewModel and payment screen stay untouched (a Phase 5 requirement), and Phase 6's training path, which never calls `save`, can never schedule an upload by accident. Cost: one more constructor parameter, covered by a fake in `TransactionRepositoryTest`.
- (b) `DashboardViewModel.refresh()` calls `syncScheduler.requestSync()` next to the tax and catalog refresh. `refresh()` already runs in `init` (app start and each dashboard open after login) and on every manual "Coba lagi" / banner tap.
- (c) The 15-minute periodic request.

No session at run time (process restarted, token expired, `restoreSession` fails): the syncer returns `stoppedEarly`, the worker returns `retry()`, nothing is deleted, and rows stay `PENDING`.

### 5.5 Error handling per case

| Case | Behaviour |
|---|---|
| No session | Syncer returns early (no cashier id) or the remote throws `NoSessionException`; rows stay `PENDING`; worker retries with backoff. If the throw happened on a row, that row gets `session: ...` and `syncAttempts + 1`. |
| Network drops mid-batch | The failing row gets `offline: ...`, attempts + 1, batch stops; rows already uploaded stay `SYNCED`; worker retries. The pill goes to "Offline". |
| Server rejects (RLS 42501, function or table missing, constraint) | That row gets `rejected: ...`, attempts + 1; the run continues with the next row; worker retries later, so a fixed server gets the row on the next run. A poison row costs one request per run and blocks nothing. |
| Duplicate (retry after a lost response, or two devices racing) | The RPC finds the header and returns success without writing; the row is marked `SYNCED`. |
| App killed during upload | The row is still `PENDING`; the next run re-sends; if the server had already committed it, the RPC is a no-op. |
| Sale made while a run is in flight | Trigger (a) uses `REPLACE`; the in-flight run is cancelled and a fresh run reads the new row. |
| Sale of a different cashier than the signed-in one | Not selected by `pendingBatch`; waits for that cashier's session. |

## 6. UI: sync-status pill

### 6.1 State machine

`DashboardUiState` gains `syncPill: SyncPill`:

```kotlin
sealed interface SyncPill {
    data object None : SyncPill
    data object Offline : SyncPill
    data class Pending(val count: Int) : SyncPill
    data class Synced(val atMillis: Long) : SyncPill
}

fun syncPillOf(catalogOffline: Boolean, summary: SyncSummary): SyncPill
// catalogOffline = state.isOffline || state.loadError
```

Rules, first match wins (`summary` = `pendingCount`, `offlineCount`, `lastSyncedAt` from `observeSyncSummary()`):

| Order | Condition | Pill | Text (exact) |
|---|---|---|---|
| 1 | `catalogOffline` or `offlineCount > 0` | `Offline` | `Offline` |
| 2 | `pendingCount > 0` | `Pending(n)` | `N menunggu sinkron` (e.g. `3 menunggu sinkron`) |
| 3 | `lastSyncedAt != null` | `Synced(t)` | `Tersinkron HH:mm` in the device time zone; when `t` is not today: `Tersinkron dd/MM HH:mm` |
| 4 | otherwise | `None` | no pill |

Why "Offline" is evidence-based: it needs either a catalog refresh that actually failed (existing `refreshFailed`) or a pending sale whose last upload actually failed with a connectivity error (`offline:` prefix). `ConnectivityManager` reports a link, not a reachable Supabase (captive portals, Wi-Fi without internet), so it can only mislead; it is used only indirectly, through WorkManager's `NetworkType.CONNECTED` constraint that decides when to run. A server-rejected sale (`rejected:`) or a missing session (`session:`) does not claim "Offline"; it shows "N menunggu sinkron". The tax refresh result is deliberately not part of "Offline": `TaxRepository.refresh()` swallows failures and, before `tax_settings` exists on the server, fails for reasons unrelated to connectivity. For the Offline pill the accessible description adds the count when there are pending sales (`Offline, N transaksi menunggu sinkron`).

The existing orange/grey banner "Offline — menampilkan data tersimpan. Ketuk untuk mencoba lagi." stays as is; the pill is the always-visible compact status.

### 6.2 Data flow and layout

- `TransactionRepository` gains `val syncSummary: Flow<SyncSummary>` (maps `observeSyncSummary()`); `DashboardViewModel` combines it with the existing catalog state in a third `combine` step and formats nothing (the UI formats the time). It is injected with `TransactionRepository` and `SyncScheduler` (constructor grows; `DashboardViewModelTest` is updated).
- `CatalogHeader` receives `syncPill: SyncPill` and renders `SyncStatusPill` when it is not `None`. Wide layout: between the spacer and the search field. Compact layout (the existing `compact` flag): in the first row between the brand block and "Keluar". The pill is a single line (`maxLines = 1`); the brand title and subtitle get `maxLines = 1` with ellipsis (this also closes the known "header title lacks ellipsis" leftover) so a long pill never pushes "Keluar" out of view.
- Appearance: small rounded pill with a leading dot plus the text (colour is never the only signal); reuse tokens from `ui/theme/Color.kt` (neutral grey for Offline matching the existing banner, accent tint for Pending, quiet secondary text for Synced). Not interactive; retry stays on the existing banner and "Coba lagi".

### 6.3 "Cannot load catalog" state (item verified, no change)

Checked in `DashboardViewModel` and `DashboardScreen`: when a refresh fails and Room holds no categories and no items, `loadError = failed && !hasCache` is true and `CatalogPane` shows "Tidak bisa memuat menu, periksa koneksi" with a "Coba lagi" button (disabled while loading); with a cache the user sees the cached menu plus the offline banner; a first load shows a spinner. `DashboardViewModelTest` already covers "refresh failure with no cache sets loadError" and "loadError flips to isOffline once Room's cache arrives". No gap found, so Phase 5 only extends this with the pill (a `loadError` also counts as `catalogOffline`).

## 7. Components

New:
- `data/sync/`: `SyncStatus`, `TransactionSyncStore` + `RoomTransactionSyncStore`, `PendingSale`, `SyncCursor`, `SyncResult`, `SyncErrors`, `TransactionSyncer`, `SyncScheduler`, `WorkManagerSyncScheduler`, `TransactionSyncWorker`, `SyncSummary`.
- `data/remote/`: `TransactionRemoteDataSource`, `SupabaseTransactionRemoteDataSource`, `SubmitTransactionDto` (+ line DTO).
- `di/SyncModule.kt` (provides `TransactionSyncStore`, `TransactionRemoteDataSource`, `TransactionSyncer`, `SyncScheduler`).
- `ui/dashboard/SyncPill.kt` (`SyncPill`, `syncPillOf`) and `SyncStatusPill` composable.
- `supabase/migrations/20261024000000_add_transactions_sync.sql` (text in section 4.1).
- `app/schemas/.../5.json` (generated by Room).

Changed:
- `data/local/TransactionEntities.kt` (new columns, index, DAO queries, `SyncSummaryRow`), `data/local/AppDatabase.kt` (v5, `MIGRATION_4_5`), `di/DatabaseModule.kt` (register migration).
- `data/transaction/TransactionRepository.kt` (`SyncScheduler` parameter, `syncSummary`), `di/TransactionModule.kt`.
- `data/auth/AuthRepository.kt` (`ensureSessionForSync()`; the interface gains one method, test fakes follow).
- `ui/dashboard/DashboardViewModel.kt` (summary flow, `syncPill`, `requestSync()` in `refresh()`), `DashboardScreen.kt` (`CatalogHeader` parameter and layout).
- `PosWarNusApplication.kt` (implements `Configuration.Provider` with `HiltWorkerFactory`, calls `ensurePeriodic()`), `AndroidManifest.xml` (remove the default `WorkManagerInitializer` via `androidx.startup` `tools:node="remove"`).
- `gradle/libs.versions.toml`, `app/build.gradle.kts`.

Dependencies (all through the version catalog; the implementer must confirm each resolves with Gradle, and picks the newest stable release older than two weeks if these have been superseded):
- `androidx.work:work-runtime-ktx` 2.11.0 (`work = "2.11.0"`; fallback 2.10.x if 2.11 needs a higher minSdk or AGP than this project has).
- `androidx.hilt:hilt-work` and `androidx.hilt:hilt-compiler` 1.3.0 (`androidxHilt = "1.3.0"`; fallback 1.2.0, the family already used by `hilt-navigation-compose 1.2.0`). `hilt-compiler` is added with `ksp(...)` next to the existing `ksp(libs.hilt.compiler)`; name the alias `androidx-hilt-compiler` to avoid clashing with the Dagger `hilt-compiler` alias.
- `androidx.work:work-testing` (same version as `work`) as `testImplementation`.

## 8. Testing

JVM unit tests (fakes, no Room):
- `TransactionSyncerTest` with `FakeStore` and `FakeRemote`: uploads in `createdAt` order and marks `SYNCED` with `now()`; a rejected row is recorded (`rejected:` prefix, attempts + 1) and the next row is still uploaded; an offline error (`IOException`) records `offline:` on that row and stops the batch (later rows untouched); `NoSessionException` records `session:` and stops; no cashier id returns `stoppedEarly` with zero store writes; rows of another cashier are never fetched; more rows than `batchSize` (keyset paging) all upload; a failed row is not re-read in the same run; `CancellationException` propagates and a success followed by cancellation still marks `SYNCED`; error text is cut to 200 characters; an empty queue returns `needsRetry = false`.
- `SyncErrorsTest`: classification of each exception type and prefix text.
- `syncPillOfTest`: all four rules and their precedence (catalog offline wins over synced; `offlineCount` wins over pending; `rejected`/`session` failures give Pending; training rows are excluded by the query, not the function), date format for "today" and "not today" with a fixed clock and zone.
- `TransactionRepositoryTest`: `save` calls `requestSync()` once after a successful write; a throwing scheduler does not fail `save`; a failed write does not schedule; saving an existing id still succeeds.
- `DashboardViewModelTest`: `syncPill` follows the summary flow and `isOffline` / `loadError`; `refresh()` calls `requestSync()`; existing assertions keep passing with the new constructor.
- `AuthRepositoryTest`: `ensureSessionForSync()` returns true with a current user, restores from the store, and does NOT clear the store when `restoreSession` fails.

Room / Robolectric tests:
- `TransactionDaoTest` additions: `pendingBatch` (status, `isTraining`, cashier filter, order, keyset cursor, limit); `markSynced` sets status, time, clears the error; `recordFailure` increments and stores the error; `observeSyncSummary` counts pending and `offline:` rows and returns the max `syncedAt` (null when none; empty table gives zeros).
- `AppDatabaseMigrationTest` addition: build a real v4 database from the `4.json` DDL (the same raw-SQLite approach as the existing migration tests) with a header and lines, open it through `Room.databaseBuilder(...).addMigrations(1_2 ... 4_5)` (Room validates the schema against v5), and assert the old rows survive with `syncStatus = 'PENDING'`, `syncedAt = null`, `syncAttempts = 0`, `lastSyncError = null`, lines intact, and the `syncStatus` index exists.

Worker: one small Robolectric test with `work-testing` (`TestListenableWorkerBuilder` and a hand-made `WorkerFactory` that passes a fake `AuthRepository` and a `TransactionSyncer` over fakes) asserting `Result.retry()` when the syncer reports `needsRetry` and `Result.success()` otherwise, and that `ensureSessionForSync()` is called first. The worker is a few lines, so the logic itself is tested through the syncer; the scheduler (`WorkManagerSyncScheduler`) is not unit-tested beyond manual verification, because its behaviour is WorkManager's.

Emulator (tablet and phone), after the owner has applied the SQL for the happy path:
1. Fresh state: the Phase 4 test sales become `PENDING` after the upgrade (pull the DB and check); with the SQL applied they upload on dashboard open, rows become `SYNCED`, and the pill reads "Tersinkron HH:mm".
2. Verify on the server (owner or implementer through a read the owner allows): one `transactions` row per sale with the right totals, `transaction_lines` rows with `selections` jsonb, counts match, and a second `submit_transaction` call with the same id adds nothing.
3. Airplane mode: make a sale; the pill shows "Offline" after the first failed attempt (or "1 menunggu sinkron" before any attempt); turn the network on; the sale uploads without opening the payment screen and the pill moves to "Tersinkron HH:mm".
4. Kill the app between save and upload: reopen; the sale uploads once, no duplicate on the server.
5. **Table not applied on the server (the state before the owner runs the SQL)**: the app behaves normally, sales complete and save locally, the pill shows "N menunggu sinkron" (a `rejected:` error, not "Offline"), `syncAttempts` grows with each retry, nothing crashes, and applying the SQL later drains the queue.
6. Phone width: the pill and "Keluar" both fit in the compact header, long text (`12 menunggu sinkron`) truncates the brand text, not the logout button.
7. Fresh install with no cache and no network: "Tidak bisa memuat menu, periksa koneksi" with "Coba lagi" still appears (regression check of section 6.3).

NOT verifiable by the implementer: the live Supabase steps until the owner applies the SQL; a physical device; Doze / battery-saver timing of the periodic request.

## 9. Risks and notes

- **Owner action:** the Supabase SQL must be applied by the owner; Phase 5 never applies it. Until then every sale stays `PENDING` and the pill says "N menunggu sinkron". After applying, the existing Phase 4 emulator sales upload too (expected; they are test data).
- **Unbounded retry:** a permanently invalid row is retried forever, at most once per run (every 15 minutes from the periodic request, plus manual triggers). This is intentional (no data is ever dropped); `lastSyncError` keeps the reason for Phase 7 to display.
- **Poison row labelling:** a rejected row shows as "N menunggu sinkron", not "Offline", by design; the cashier cannot fix it, and the owner reads `lastSyncError`.
- **Other cashier's sales:** a sale stays pending until its own cashier signs in on that device again; the pill counts every pending sale on the device. Acceptable for a single-outlet shop.
- **Existing auth quirk, not fixed here:** `AuthRepositoryImpl.isLoggedIn()` clears the stored session on any failed restore, including a network timeout at app start. The sync worker avoids it with `ensureSessionForSync()`; changing `isLoggedIn()` itself is outside this phase and should be tracked separately.
- **Data volume:** one RPC per sale; lines are small. A long outage with hundreds of sales uploads sequentially in `createdAt` order, 20 per page; no batching across sales is needed.
- **REPLACE churn:** rapid triggers cancel and restart the run; cheap because idempotent. A sale made in the middle of an upload may be sent twice; the server ignores the second.
- **Clock:** `created_at` is the device time at payment; the server adds `received_at = now()` so a wrong device clock stays visible.
- **WorkManager initialisation:** removing the default initializer and providing `Configuration.Provider` is required for `@HiltWorker`; forgetting either gives a runtime `ClassCastException` or a worker that never gets its dependencies. Covered by emulator step 1.

## 10. Self-review

- No open questions: every item in the brief is stated as a decision (section 2) and detailed where it matters. Versions are proposals the implementer must confirm through Gradle, with explicit fallbacks.
- Consistency checks: statuses are only `PENDING` / `SYNCED` everywhere (DAO, DDL, pill); the "Offline" rule uses only the `offline:` prefix in both the DAO query and the pill table; `REPLACE` is the policy in the decision table, the analysis and the scheduler description; the pill texts are identical in 6.1, the tests and the emulator checks; lines have `cashier_id` in the SQL, the RPC and the DTO list (the one intentional addition to the stated column list, justified in the decision table).
- Training rows: excluded by `isTraining = 0` in both `pendingBatch` and `observeSyncSummary`, plus the server `check (not is_training)`.
- No code, SQL execution or push is part of this document; the SQL file and all code are created later in the plan.
