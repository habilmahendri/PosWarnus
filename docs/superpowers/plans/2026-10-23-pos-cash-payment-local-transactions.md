# POS Tax, Cash Payment and Local Transactions (Phase 4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make "Bayar" live: the cart shows subtotal / tax / total, a payment dialog takes cash with live change, the sale is written to Room (UUID + `isTraining`), and a Success state ends the order.

**Architecture:** Tax config flows Supabase `tax_settings` -> Room (cache) -> `TaxRepository`. Pure `OrderTotals`/`CashQuickAmounts`/`CashInput` hold the maths. `TransactionRepository.save` writes header + lines atomically (idempotent on the transaction UUID). `PaymentViewModel` (scoped to the dashboard back-stack entry, like `CartViewModel`) is a small state machine Closed -> Paying -> Success; `PaymentDialog` is hosted by `DashboardScreen`. DB v3 -> v4 with a non-destructive migration.

**Tech Stack:** Kotlin, Compose Material3, Hilt, Room 2.8 (+ Robolectric), Supabase Postgrest, kotlinx.serialization, JUnit/Turbine/coroutines-test. No new dependencies.

**Spec:** [docs/superpowers/specs/2026-10-23-cash-payment-local-transactions-design.md](../specs/2026-10-23-cash-payment-local-transactions-design.md) (section numbers below refer to it). Upstream: [PRD 4.6-4.9](../../PRD-pos.md), [roadmap](../../roadmap.md) "Phase 4".

## Global Constraints

- Package `com.example.poswarnus`; minSdk 24; **no new Gradle dependencies**.
- **Never execute anything against the live Supabase project** (no `apply_migration`, no `execute_sql`). `supabase/migrations/20261023200000_add_tax_settings.sql` is only written to the repo; the owner applies it later. The app must work without the table: no cache => 0% + warning, a failed fetch never throws into the UI.
- **Work in a git worktree on branch `feature/phase4-cash-payment`; do not merge to `main`, do not push.** Copy `local.properties` into the worktree. Execution stops at a reviewed branch.
- Money is whole-Rupiah `Long` everywhere; tax uses Long arithmetic only: `taxAmount = (subtotal * basisPoints + 5000) / 10000`, `grandTotal = subtotal + taxAmount`, **no other rounding**.
- Room: DB version 4, `exportSchema` stays on (commit `app/schemas/.../4.json`), `MIGRATION_3_4` is non-destructive, no `fallbackToDestructiveMigration`. Entity `@ColumnInfo(defaultValue=...)` must equal the migration DDL exactly.
- `transactions.isTraining` is always `false` in this phase. Sync fields/queue are NOT added (Phase 5).
- Quick cash amounts: "Uang pas" plus up to 3 distinct values `ceil(total, d)` for `d` in {5.000, 10.000, 50.000, 100.000} that are strictly greater than the total, ascending. Cash input: digits only, max 9 digits.
- Copy is Indonesian: "Pajak", "Bayar", "Uang diterima", "Uang pas", "Konfirmasi", "Selesai", "Cetak struk" (disabled, "segera hadir"), "Pajak belum dimuat", "Gagal menyimpan transaksi. Coba lagi.", "Uang kurang Rp X".
- Success state cannot be dismissed by back or outside tap; only "Selesai" closes it and clears the cart.
- Verification gate: `./gradlew testDebugUnitTest assembleDebug` green. UI is verified by compile + the emulator checklist in Task 8; anything not verified is reported as NOT verified.
- Test-cashier credentials never go into any repo file; the controller types them from the conversation (`adb input text` can drop the first character: read back before submitting).
- Shell in the worktree: plain separate commands (no compound/`cd` chains). `git add` only the files a task lists. Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Upgrade from a v3 database** keeps catalog rows and creates the three new tables (Task 2).
2. **Double tap / retry after failure** writes exactly one transaction, with the same UUID, and keeps the typed cash (Tasks 4, 5).
3. **No `tax_settings` cached or table missing on the server:** app works with 0% and the warning; a failed refresh keeps the previous cached value (Tasks 3, 6).
4. **Cash input edge cases:** empty, leading zeros, pasted non-digits, more than 9 digits, exact amount, cash one rupiah short (Tasks 1, 5).
5. **Tax refreshed or cart emptied while the dialog is open:** the frozen totals do not change; `open()` on an empty cart does nothing (Task 5).

---

### Task 1: Pure maths — TaxConfig, OrderTotals, CashQuickAmounts, CashInput

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/tax/TaxConfig.kt`
- Create: `app/src/main/java/com/example/poswarnus/ui/cart/OrderTotals.kt`
- Create: `app/src/main/java/com/example/poswarnus/ui/payment/CashMath.kt`
- Test: `app/src/test/java/com/example/poswarnus/ui/cart/OrderTotalsTest.kt`, `app/src/test/java/com/example/poswarnus/ui/payment/CashMathTest.kt`

**Interfaces:**
- Produces:
  - `data class TaxConfig(val label: String, val basisPoints: Int)`
  - `data class OrderTotals(val subtotal: Long, val taxLabel: String, val taxBasisPoints: Int, val taxAmount: Long, val grandTotal: Long, val taxMissing: Boolean)` with `val taxLine: String` ("Pajak 10%") and `companion fun of(subtotal: Long, tax: TaxConfig?): OrderTotals`
  - `fun percentText(basisPoints: Int): String` in `OrderTotals.kt` (1000 -> "10%", 750 -> "7.5%", 1050 -> "10.5%", 0 -> "0%")
  - `object CashMath { fun quickAmounts(total: Long): List<Long>; fun parseInput(raw: String): Long; fun change(cash: Long, total: Long): Long; fun canPay(cash: Long, total: Long): Boolean; const val MAX_DIGITS = 9 }`

- [ ] **Step 1: Write failing tests**

`OrderTotalsTest`: `of(43000, TaxConfig("Pajak",1000))` -> tax 4300, grand 47300, `taxLine == "Pajak 10%"`, `taxMissing == false`; `of(25, bps 1000)` -> tax 3 (2.5 rounds up); `of(24, bps 1000)` -> tax 2; `of(10000, bps 750)` -> tax 750, `taxLine == "Pajak 7.5%"`; `of(0, bps 1000)` -> all zero; `of(43000, null)` -> tax 0, grand 43000, label "Pajak", `taxMissing == true`; `of(43000, TaxConfig("Service",0))` -> tax 0, `taxMissing == false`; `percentText` table above.

`CashMathTest`: `quickAmounts(43000) == [45000, 50000, 100000]`; `quickAmounts(50000) == [100000]`; `quickAmounts(120000) == [150000, 200000]`; `quickAmounts(100000) == emptyList()`; `quickAmounts(0) == emptyList()`; `parseInput("") == 0`; `parseInput("0045") == 45`; `parseInput("Rp 50.000") == 50000` (non-digits dropped); `parseInput("1234567890123") == 123456789` (first 9 digits kept); `change(50000, 43000) == 7000`; `canPay(43000, 43000)`, `!canPay(42999, 43000)`, `!canPay(0, 0)`.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.cart.OrderTotalsTest" --tests "com.example.poswarnus.ui.payment.CashMathTest"`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement the signatures above**

`percentText` renders `basisPoints / 100` with one decimal only when `basisPoints % 100 != 0` (use integer maths, e.g. `"${bps / 100}.${(bps % 100) / 10}"` when the remainder is a multiple of 10; two-decimal remainders drop to two decimals, trailing zero trimmed). `quickAmounts` as in Global Constraints.

- [ ] **Step 4: Run to verify they pass** (same command; Expected: PASS)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/tax/TaxConfig.kt app/src/main/java/com/example/poswarnus/ui/cart/OrderTotals.kt app/src/main/java/com/example/poswarnus/ui/payment/CashMath.kt app/src/test/java/com/example/poswarnus/ui/cart/OrderTotalsTest.kt app/src/test/java/com/example/poswarnus/ui/payment/CashMathTest.kt
git commit -m "feat: add OrderTotals, quick cash amounts and cash input maths"
```

---

### Task 2: Room v4 — tax_settings, transactions, transaction_lines + MIGRATION_3_4

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/data/local/AppDatabase.kt` (version 4, entities, DAO accessors, `MIGRATION_3_4`)
- Create: `app/src/main/java/com/example/poswarnus/data/local/TaxSettingsEntity.kt` (entity + `TaxSettingsDao`)
- Create: `app/src/main/java/com/example/poswarnus/data/local/TransactionEntities.kt` (entities + `TransactionDao`)
- Modify: `app/src/main/java/com/example/poswarnus/di/DatabaseModule.kt` (`addMigrations(..., MIGRATION_3_4)`, provide the two DAOs)
- Create: `app/schemas/com.example.poswarnus.data.local.AppDatabase/4.json` (generated by the build)
- Test: `app/src/test/java/com/example/poswarnus/data/local/AppDatabaseMigrationTest.kt` (add), `app/src/test/java/com/example/poswarnus/data/local/TransactionDaoTest.kt`

**Interfaces:**
- Produces:
  - `@Entity("tax_settings") data class TaxSettingsEntity(@PrimaryKey val id: Int = 1, val label: String, val basisPoints: Int)`
  - `@Dao interface TaxSettingsDao { fun observe(): Flow<TaxSettingsEntity?>; suspend fun replace(entity: TaxSettingsEntity) }` (`@Upsert` is fine; single row id 1)
  - `@Entity("transactions") data class TransactionEntity(@PrimaryKey val id: String, val createdAt: Long, val cashierId: String, val subtotal: Long, val taxLabel: String, val taxBasisPoints: Int, val taxAmount: Long, val grandTotal: Long, val cashReceived: Long, val changeAmount: Long, @ColumnInfo(defaultValue = "0") val isTraining: Boolean = false)`
  - `@Entity("transaction_lines", foreignKeys = [FK -> transactions.id, onDelete = CASCADE], indices = [Index("transactionId")]) data class TransactionLineEntity(@PrimaryKey val id: String, val transactionId: String, val menuItemId: String, val name: String, val basePrice: Long, val quantity: Int, val selectionsJson: String)`
  - `@Dao abstract class TransactionDao { @Insert(onConflict = IGNORE) abstract suspend fun insertHeader(t: TransactionEntity): Long; @Insert abstract suspend fun insertLines(lines: List<TransactionLineEntity>); @Transaction open suspend fun insertSale(header: TransactionEntity, lines: List<TransactionLineEntity>): Boolean; fun observeAll(): Flow<List<TransactionEntity>>; suspend fun linesFor(transactionId: String): List<TransactionLineEntity> }`. `insertSale` returns `true` when newly inserted; when `insertHeader` returns -1 (id already stored) it inserts no lines and returns `false`. A failing line insert rolls the header back.

- [ ] **Step 1: Write failing tests**
  - `AppDatabaseMigrationTest`: `v3 database upgrades with MIGRATION_3_4 keeping catalog rows and creating the new tables` — build a raw v3 database (follow the existing v2 test, adding `isFeatured`), open via Room with `.addMigrations(MIGRATION_3_4)`, assert the menu item and category survive, `db.transactionDao().observeAll().first()` is empty and `db.taxSettingsDao().observe().first()` is null.
  - `TransactionDaoTest` (in-memory Room, follows `MenuItemDaoTest`): `insertSale stores header and lines` (assert `linesFor`); `insertSale with an existing id inserts nothing and returns false` (second call with different lines leaves the original lines); `a failing line insert rolls the header back` (two lines with the same line id -> exception, `observeAll()` empty); `isTraining round-trips and defaults to false`; `TaxSettingsDao.replace overwrites the single row`.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.local.*"`
Expected: FAIL (unresolved references / version mismatch).

- [ ] **Step 3: Implement**
  - Entities/DAOs per Interfaces. `MIGRATION_3_4` uses `CREATE TABLE` statements identical to what Room generates (copy the DDL from the exported `4.json` after the first build; `isTraining INTEGER NOT NULL DEFAULT 0`, FK `ON UPDATE NO ACTION ON DELETE CASCADE`, index `index_transaction_lines_transactionId`).
  - Build once (`./gradlew assembleDebug`) to generate `4.json`, then make the migration test pass against it.

- [ ] **Step 4: Run to verify they pass** (same command; Expected: PASS, all earlier DAO/migration tests still green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/local/AppDatabase.kt app/src/main/java/com/example/poswarnus/data/local/TaxSettingsEntity.kt app/src/main/java/com/example/poswarnus/data/local/TransactionEntities.kt app/src/main/java/com/example/poswarnus/di/DatabaseModule.kt app/schemas app/src/test/java/com/example/poswarnus/data/local/AppDatabaseMigrationTest.kt app/src/test/java/com/example/poswarnus/data/local/TransactionDaoTest.kt
git commit -m "feat: Room v4 with tax_settings, transactions and transaction_lines"
```

---

### Task 3: Tax data layer — Supabase fetch, repository, SQL file, refresh hook

**Files:**
- Create: `supabase/migrations/20261023200000_add_tax_settings.sql`
- Modify: `app/src/main/java/com/example/poswarnus/data/remote/CatalogDtos.kt` (add `TaxSettingsDto`)
- Create: `app/src/main/java/com/example/poswarnus/data/remote/TaxRemoteDataSource.kt` (interface + `SupabaseTaxRemoteDataSource`)
- Create: `app/src/main/java/com/example/poswarnus/data/tax/TaxRepository.kt`
- Create: `app/src/main/java/com/example/poswarnus/di/TaxModule.kt`
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardViewModel.kt` (constructor gains `taxRepository: TaxRepository`; `refresh()` also launches `taxRepository.refresh()`, independent of catalog result)
- Test: `app/src/test/java/com/example/poswarnus/data/tax/TaxRepositoryTest.kt`, `app/src/test/java/com/example/poswarnus/data/remote/TaxSettingsDtoTest.kt`; update `DashboardViewModelTest` (pass a fake `TaxRepository`; add: `refresh also refreshes tax and a tax failure does not affect catalog state`)

**Interfaces:**
- Consumes: `TaxSettingsDao`, `TaxSettingsEntity` (Task 2), `TaxConfig` (Task 1).
- Produces:
  - `@Serializable data class TaxSettingsDto(val label: String, val percent: Double)`
  - `interface TaxRemoteDataSource { suspend fun fetchTaxSettings(): TaxSettingsDto? }` (null when the table has no row)
  - `interface TaxRepository { val taxConfig: Flow<TaxConfig?>; suspend fun refresh() }` and `class TaxRepositoryImpl(dao: TaxSettingsDao, remote: TaxRemoteDataSource)`; `refresh()` never throws (except `CancellationException`); `basisPoints = (percent * 100).roundToInt()`.
  - SQL: per spec 3.1 (header comment: applied manually, not idempotent, RLS select for `authenticated` only, seed row `(1,'Pajak',10)`).

- [ ] **Step 1: Write failing tests**
  - `TaxRepositoryTest` (fake remote + in-memory DAO or fake DAO): `refresh stores basis points from percent` (10.0 -> 1000, 7.5 -> 750); `taxConfig is null before any successful refresh`; `failed refresh keeps the previously cached value`; `remote returning null keeps the cache`; `refresh swallows exceptions`.
  - `TaxSettingsDtoTest`: decodes `{"label":"Pajak","percent":10}` and `{"label":"Pajak","percent":10.00}`.
  - The `DashboardViewModelTest` addition above.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.tax.*" --tests "com.example.poswarnus.data.remote.TaxSettingsDtoTest" --tests "com.example.poswarnus.ui.dashboard.*"`
Expected: FAIL.

- [ ] **Step 3: Implement**
  - `SupabaseTaxRemoteDataSource(client, sessionAvailable)` mirrors `SupabaseCatalogRemoteDataSource`: throw `NoSessionException` without a session; `from("tax_settings").select().decodeList<TaxSettingsDto>().firstOrNull()`.
  - Hilt: `TaxModule` provides `TaxRemoteDataSource` (same `sessionAvailable` lambda as `CatalogRemoteModule`) and `TaxRepository`.
  - The SQL file is **not applied** anywhere.

- [ ] **Step 4: Run to verify they pass** (same command; Expected: PASS)

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations/20261023200000_add_tax_settings.sql app/src/main/java/com/example/poswarnus/data/remote/CatalogDtos.kt app/src/main/java/com/example/poswarnus/data/remote/TaxRemoteDataSource.kt app/src/main/java/com/example/poswarnus/data/tax/TaxRepository.kt app/src/main/java/com/example/poswarnus/di/TaxModule.kt app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardViewModel.kt app/src/test/java/com/example/poswarnus/data/tax/TaxRepositoryTest.kt app/src/test/java/com/example/poswarnus/data/remote/TaxSettingsDtoTest.kt app/src/test/java/com/example/poswarnus/ui/dashboard/DashboardViewModelTest.kt
git commit -m "feat: tax_settings fetch, cache and repository (migration SQL not applied)"
```

---

### Task 4: TransactionRepository and selections snapshot codec

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/transaction/SelectionsJson.kt`
- Create: `app/src/main/java/com/example/poswarnus/data/transaction/TransactionRepository.kt`
- Create: `app/src/main/java/com/example/poswarnus/di/TransactionModule.kt`
- Test: `app/src/test/java/com/example/poswarnus/data/transaction/SelectionsJsonTest.kt`, `app/src/test/java/com/example/poswarnus/data/transaction/TransactionRepositoryTest.kt`

**Interfaces:**
- Consumes: `TransactionDao`, entities (Task 2); `CartLine`, `SelectedOption` (`ui.cart`); `OrderTotals` (Task 1).
- Produces:
  - `object SelectionsJson { fun encode(selections: List<SelectedOption>): String; fun decode(json: String): List<SelectedOption> }` (private `@Serializable` mirror class with `optionId, groupId, name, priceDelta, kind` where kind is `OptionKind.name`).
  - `data class SaleSnapshot(val id: String, val createdAt: Long, val cashierId: String, val lines: List<CartLine>, val totals: OrderTotals)`
  - `interface TransactionRepository { suspend fun save(sale: SaleSnapshot, cashReceived: Long): Result<Unit> }` and `TransactionRepositoryImpl(dao: TransactionDao)`: maps to entities (`changeAmount = cashReceived - totals.grandTotal`, `isTraining = false`, line ids `"${sale.id}:${index}"`), calls `dao.insertSale`, returns `Result.success(Unit)` for both new and already-stored ids, `Result.failure(e)` for exceptions (rethrows `CancellationException`).

- [ ] **Step 1: Write failing tests**
  - `SelectionsJsonTest`: round-trips two options (one VARIANT, one MODIFIER, with a negative `priceDelta`), round-trips an empty list, and a name with quotes/unicode survives.
  - `TransactionRepositoryTest` (in-memory Room via Robolectric): `save writes header and lines with change and totals`; `saving the same snapshot twice leaves one row and succeeds both times`; `save returns failure when the dao throws` (fake DAO); `selections are stored as the snapshot json`.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.transaction.*"`
Expected: FAIL.

- [ ] **Step 3: Implement** per Interfaces; provide `TransactionRepository` as `@Singleton` in `TransactionModule`.

- [ ] **Step 4: Run to verify they pass** (same command; Expected: PASS)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/transaction app/src/main/java/com/example/poswarnus/di/TransactionModule.kt app/src/test/java/com/example/poswarnus/data/transaction
git commit -m "feat: TransactionRepository with atomic idempotent local save"
```

---

### Task 5: PaymentViewModel state machine and cashier id

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/data/auth/AuthGateway.kt` (add `fun currentUserId(): String?`), `SupabaseAuthGateway.kt` (`client.auth.currentUserOrNull()?.id`), `AuthRepository.kt` (add `fun currentUserId(): String?` delegating to the gateway), and any existing fake `AuthGateway`/`AuthRepository` in tests (return a fixed id)
- Create: `app/src/main/java/com/example/poswarnus/ui/payment/PaymentViewModel.kt`
- Test: `app/src/test/java/com/example/poswarnus/ui/payment/PaymentViewModelTest.kt`

**Interfaces:**
- Consumes: `CartStore`, `TaxRepository.taxConfig`, `TransactionRepository.save`, `OrderTotals.of`, `CashMath`, `SaleSnapshot`, `AuthRepository.currentUserId()`.
- Produces:
  - `sealed interface PaymentUiState { data object Closed; data class Paying(val totals: OrderTotals, val cash: Long, val quickAmounts: List<Long>, val change: Long, val canConfirm: Boolean, val isSaving: Boolean, val error: Boolean) ; data class Success(val totals: OrderTotals, val cash: Long, val change: Long) }`
  - `@HiltViewModel class PaymentViewModel @Inject constructor(cartStore: CartStore, taxRepository: TaxRepository, transactionRepository: TransactionRepository, authRepository: AuthRepository)` with `val uiState: StateFlow<PaymentUiState>`, `fun open()`, `fun onCashInput(raw: String)`, `fun onQuickAmount(amount: Long)`, `fun onExactAmount()`, `fun confirm()`, `fun dismiss()`, `fun finish()`.
  - Behaviour: `open()` is a no-op when the cart is empty or the state is not Closed; otherwise reads `taxRepository.taxConfig.first()` and `cartStore.state.value` once and **freezes** `OrderTotals`, the cart lines, a new `UUID.randomUUID()` and `createdAt = System.currentTimeMillis()`. `confirm()` is a no-op unless `canPay` and not saving; sets `isSaving`, calls `save`; success -> `Success`, failure -> `Paying(error = true, isSaving = false)` with `cash` kept and the same UUID reused on retry. `dismiss()` only closes `Paying` when not saving. `finish()` only from `Success`: `cartStore.clear()` then `Closed`. `onCashInput`/quick/exact clear `error`.

- [ ] **Step 1: Write failing tests** (fakes for repositories; `StandardTestDispatcher` like `CartViewModelTest`):
  - `open freezes totals so a later tax refresh does not change them` (emit new `TaxConfig` after open; `Paying.totals` unchanged).
  - `open with an empty cart stays closed`; `open with no cached tax uses 0 percent and taxMissing`.
  - `confirm is disabled while cash is below total` and `confirm with cash one rupiah short does nothing`.
  - `onCashInput drops non-digits and caps at 9 digits`.
  - `onExactAmount sets cash to the grand total and enables confirm`; `quick amounts come from the grand total`.
  - `successful confirm saves once with change and moves to Success; the cart is untouched until finish`.
  - `double confirm saves once` (second call while saving).
  - `failed save keeps the cash, sets error, and retry reuses the same transaction id` (fake repo records ids; first call fails, second succeeds).
  - `dismiss during Paying closes without clearing the cart`; `dismiss in Success does nothing`; `finish clears the cart and closes`.
  - `uses the cashier id from AuthRepository`.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.payment.PaymentViewModelTest"`
Expected: FAIL.

- [ ] **Step 3: Implement** per Interfaces. Guard concurrent `confirm()` calls with an atomic state update (`MutableStateFlow.getAndUpdate`, same technique as `CartViewModel.confirmDialog`); a missing cashier id (`null`) makes `save` fail with the error state rather than writing a blank id.

- [ ] **Step 4: Run to verify they pass** (same command; Expected: PASS; then run the full `./gradlew testDebugUnitTest`)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/auth app/src/main/java/com/example/poswarnus/ui/payment/PaymentViewModel.kt app/src/test/java/com/example/poswarnus
git commit -m "feat: PaymentViewModel state machine and cashier id lookup"
```

---

### Task 6: Cart totals in the footer and phone bar

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/ui/cart/CartViewModel.kt` (constructor gains `taxRepository: TaxRepository`; `CartUiState` gains `totals: OrderTotals = OrderTotals.of(0, null)`; combined with `taxRepository.taxConfig`)
- Modify: `app/src/main/java/com/example/poswarnus/ui/cart/CartPanel.kt` (`PanelFooter`: Subtotal, tax row `state.totals.taxLine` + amount, "Pajak belum dimuat" when `taxMissing`, Total uses `grandTotal`; "Jumlah item" row stays)
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt` (`PhoneCartBar` shows `cartState.totals.grandTotal`)
- Test: `app/src/test/java/com/example/poswarnus/ui/cart/CartViewModelTest.kt` (update construction with a fake `TaxRepository`; add tests)

**Interfaces:**
- Consumes: `OrderTotals.of`, `TaxRepository.taxConfig` (Tasks 1, 3).
- Produces: `CartUiState.totals: OrderTotals`.

- [ ] **Step 1: Write failing tests** in `CartViewModelTest`: `totals add tax to the subtotal` (cart 2 x 25.000, `TaxConfig("Pajak",1000)` -> tax 5.000, grand 55.000); `totals update when the tax config changes`; `totals without cached tax have zero tax and taxMissing`; `empty cart totals are zero`.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.cart.CartViewModelTest"`
Expected: FAIL.

- [ ] **Step 3: Implement.** Footer layout: Subtotal / "Pajak 10%" / (small warning if missing) / divider / Total (32sp as today). Keep the `Bayar` button as is. UI is verified on the emulator in Task 8.

- [ ] **Step 4: Run** `./gradlew testDebugUnitTest assembleDebug` (Expected: PASS and BUILD SUCCESSFUL)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/cart/CartViewModel.kt app/src/main/java/com/example/poswarnus/ui/cart/CartPanel.kt app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt app/src/test/java/com/example/poswarnus/ui/cart/CartViewModelTest.kt
git commit -m "feat: show tax line and grand total in the cart"
```

---

### Task 7: PaymentDialog UI and Dashboard wiring

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/payment/PaymentDialog.kt`
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt` (`DashboardScreen` gains `paymentViewModel: PaymentViewModel = hiltViewModel()`; "Bayar" calls `paymentViewModel::open`; host the dialog after `MenuDetailDialog`)
- Modify: `app/src/main/java/com/example/poswarnus/ui/navigation/PosNavGraph.kt` (pass `paymentViewModel = hiltViewModel(viewModelStoreOwner = backStackEntry)`; remove the `onCheckout = { }` no-op and its Phase 4 comment)

**Interfaces:**
- Consumes: `PaymentUiState`, `PaymentViewModel` (Task 5), `formatRupiah(Long)`, `CashMath`.
- Produces: `@Composable fun PaymentDialog(state: PaymentUiState, onCashInput: (String) -> Unit, onQuickAmount: (Long) -> Unit, onExact: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, onFinish: () -> Unit)` renders nothing for `Closed`.

- [ ] **Step 1: Implement the dialog** (follow `MenuDetailDialog`'s `Dialog` styling and the existing colour tokens; 16dp gutters; touch targets >= 48dp; Indonesian copy from Global Constraints):
  - Paying: title "Pembayaran"; "Total" with large `formatRupiah(grandTotal)`; a numeric text field (`KeyboardType.Number`, value shown as `formatRupiah(cash)` or empty with placeholder "Uang diterima"; each change goes through `onCashInput(raw)`); chips "Uang pas" and the quick amounts; a line "Kembalian Rp X" or, when short and cash > 0, "Uang kurang Rp X" in the error colour; inline error text when `error`; "Batal" and "Konfirmasi" (disabled unless `canConfirm`; shows a small progress indicator while `isSaving`).
  - Success: `Dialog(properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false))`; large total, "Uang diterima", "Kembalian" (largest text); disabled "Cetak struk" with the text "segera hadir"; "Selesai" -> `onFinish`.
  - Paying uses `onDismissRequest = onDismiss`; back or outside tap dismisses (but not while saving, handled by the ViewModel).
  - Semantics: the total and the change carry readable content descriptions; the field has a label.

- [ ] **Step 2: Wire** the dialog into `DashboardScreen` after `MenuDetailDialog` inside the same `BoxWithConstraints`; collect `paymentViewModel.uiState`. Phone: after "Bayar" in the cart sheet, close the sheet (`showCartSheet = false`) before the dialog opens so windows do not stack.

- [ ] **Step 3: Verify build and tests**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS, BUILD SUCCESSFUL. (No JVM test for Compose here; behaviour is covered by Task 5 and the emulator pass.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/payment/PaymentDialog.kt app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt app/src/main/java/com/example/poswarnus/ui/navigation/PosNavGraph.kt
git commit -m "feat: payment dialog with success state, wired to Bayar"
```

---

### Task 8: Emulator verification and roadmap update

**Files:**
- Modify: `docs/roadmap.md` (Phase 4 status, what was verified / NOT verified, owner action for the SQL file)

- [ ] **Step 1: Run the full gate**

Run: `./gradlew testDebugUnitTest assembleDebug lint`
Expected: tests and build green; lint reports no new errors beyond the known two `LoginScreenTest` ones.

- [ ] **Step 2: Emulator checklist** (tablet `emulator-5554` and phone `emulator-5556`; tooling paths in the project memory; log in with the test cashier typed by the controller). Without the `tax_settings` table applied on the live project (expected state):
  1. Cart footer shows "Pajak 0%" with "Pajak belum dimuat"; Total equals Subtotal.
  2. Bayar -> dialog; quick amounts for the total are correct; "Uang pas" enables Konfirmasi; one rupiah short keeps it disabled with "Uang kurang".
  3. Konfirmasi -> Success shows total, cash, change; back and outside tap do nothing; "Cetak struk" disabled; "Selesai" empties the cart and returns to the catalog.
  4. Airplane mode: a sale still completes.
  5. Phone: cart sheet closes when the dialog opens; the dialog fits portrait and landscape with the keyboard open.
  6. Pull the DB (`adb exec-out run-as com.example.poswarnus cat databases/pos_warnus.db` to a scratch path) and confirm rows in `transactions` and `transaction_lines` (one per completed sale, `isTraining = 0`).
  Then, **only if the owner has applied the SQL**, confirm a restart shows "Pajak 10%" and totals include 10%.
  Record anything not checked as NOT verified.

- [ ] **Step 3: Update `docs/roadmap.md`** (Phase 4 row -> "Done on branch `feature/phase4-cash-payment`, not merged", add a "Phase 4 status" block like Phase 3b's: verified items, NOT verified items, owner action: apply `20261023200000_add_tax_settings.sql`).

- [ ] **Step 4: Commit**

```bash
git add docs/roadmap.md
git commit -m "docs: record Phase 4 status and verification"
```
