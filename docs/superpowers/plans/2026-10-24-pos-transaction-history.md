# POS Transaction History (Phase 7) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the signed-in cashier a read-only history of this device's real sales (today / yesterday tabs, search, detail with a "Menunggu sinkron" badge), following the owner's design.

**Architecture:** One new Room query (`observeHistory`) returns headers with their lines through a `@Relation`; the repository maps them to plain `HistoryEntry` models. A `HistoryViewModel` with an injectable clock and time zone splits them into today / yesterday, filters by search, and picks the selection. A new `history` route renders a two-pane layout on tablets and list-then-detail on narrow screens. No schema, sync or payment change.

**Tech Stack:** Kotlin, Room 2.8 (+ Robolectric), Hilt, Compose Material3, Navigation Compose, JUnit/coroutines-test.

**Spec:** [docs/superpowers/specs/2026-10-24-transaction-history-design.md](../specs/2026-10-24-transaction-history-design.md) (section numbers below refer to it). Upstream: [PRD 4.11](../../PRD-pos.md), [roadmap](../../roadmap.md) "Phase 7". Format precedent: `docs/superpowers/plans/2026-10-24-pos-settings-training-mode.md`. UI reference: owner's design canvas, artboards `History-Default`, `History-Yesterday`, `History-Empty` (scene `history*` in `Main.dc.html`, claude.ai/artifact/JjFZB66sa11r7k1Cbetivz).

## Global Constraints

- Package `com.example.poswarnus`; minSdk 24; Kotlin; Compose Material3; Hilt; single module `:app`. No new dependencies.
- **No Room schema change, no `submit_transaction` or Supabase change, never execute SQL on the live project, no push, no merge to `main`.** Work in a git worktree on branch `feature/phase7-transaction-history` (the harness may name it `worktree-...`; that is fine); copy `local.properties` into it. Execution stops at a reviewed branch.
- Shell in the worktree: plain separate commands (no `&&`, `;`, subshells, or `cd` chains; the shell guard refuses compound commands). `git add` only the files a task lists. Commit messages end with the line `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- **No `java.time`** (minSdk 24, no desugaring): use `Calendar` and `SimpleDateFormat` with explicit `Locale.US` and a `TimeZone`; one formatter per call (not thread-safe).
- Money is `Long` whole Rupiah; reuse `formatRupiah(Long)` from `ui/dashboard/DashboardComponents.kt`.
- Data scope (spec 3): only the signed-in cashier's rows (`cashierId`) and never `isTraining = 1`. No cashier name anywhere ("Kasir:" line is not built).
- Order code (spec 3): `"#" + id.replace("-", "").take(6).uppercase()`, e.g. `#A3F9C1`. It is the "No. pesanan" in list rows and the detail title, and it is searchable with or without the `#`.
- Day tabs (spec 3, 4.2): only "Hari ini" and "Kemarin", from device-local calendar days; repository `fromMillis` = start of yesterday. No older days.
- Exact UI copy: tabs `Hari ini` / `Kemarin`; search placeholder `No. pesanan atau menu`; title `Riwayat transaksi`; summary `"<N> transaksi · <Rp total>"`; chips `Selesai` and `Menunggu sinkron`; row meta `"<HH.mm> · <N> item"`; detail line `"<Hari ini|Kemarin> · <HH.mm>"`; detail rows `Subtotal`, `<taxLabel> <percent>%` (e.g. `Pajak 10%`), `Jumlah item`, `Total`, `Tunai diterima`, `Kembalian`; footer button `Cetak Ulang Struk (segera hadir)` (disabled); empty states `Belum ada transaksi` + `Penjualan hari ini akan muncul di sini.` / `Penjualan kemarin akan muncul di sini.`, `Transaksi tidak ditemukan` + `Coba nomor pesanan atau nama menu lain.`, `Pilih transaksi untuk melihat rincian.`; header icon `contentDescription` `Riwayat transaksi`, back `contentDescription` `Kembali ke kasir`. Time format `HH.mm` (dot, as in the design).
- Header rule (existing): brand title keeps `Modifier.weight(1f)` and ellipsizes first; the new icon is a 48dp target placed immediately before the gear in both header layouts.
- Chip text contrast >= 4.5:1 (reuse existing theme colors: green for Selesai, the sync pill's amber for Menunggu sinkron).
- Ruling (plan): the high-contrast `TrainingBanner` (existing) is also shown at the top of the history screen while training is on (PRD 4.13: banner on every screen of the sale flow), via the existing `TrainingBannerViewModel`. It is the only addition beyond the spec.
- Verification gate for code tasks: `./gradlew testDebugUnitTest assembleDebug` green. `./gradlew lint` may show only the 2 known errors in androidTest `LoginScreenTest`. Anything not verified is reported as NOT verified.
- Test-cashier credentials never go into any repo file or commit message; the controller types them from the conversation. `adb`/`emulator` live at `~/Library/Android/sdk/platform-tools/adb` and `~/Library/Android/sdk/emulator/emulator`; target `emulator-5554` with `-s` (a physical tablet may also be attached: never `installDebug`, use `adb -s emulator-5554 install -r`).

## Review Focus

1. **Midnight boundary:** a sale at 23:59:59.999 is "Kemarin", at 00:00:00.000 "Hari ini", and one older than the start of yesterday is in neither tab (Task 2).
2. **Another cashier's or a training sale never shows**, and no signed-in cashier gives an empty list rather than a crash (Tasks 1, 2).
3. **A corrupt `selectionsJson`** still shows the line (no meta) instead of crashing the whole list (Task 1).
4. **The pending badge is live:** after the sync marks a row `SYNCED` the same flow emits it without the badge, and the selection stays on the same id (Tasks 1, 2).
5. **Search and selection:** empty tab vs no search match show different empty states; a search that hides the selected row falls back to the first match; "#" and case do not matter; two rows with equal codes still select by full id (Task 2).

---

### Task 1: History data layer

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/data/local/TransactionEntities.kt` (add `TransactionWithLines` and `TransactionDao.observeHistory`), `app/src/main/java/com/example/poswarnus/data/transaction/TransactionRepository.kt` (interface + impl)
- Create: `app/src/main/java/com/example/poswarnus/data/transaction/HistoryEntry.kt` (models, `shortCode`, mapper)
- Test: `app/src/test/java/com/example/poswarnus/data/local/TransactionDaoTest.kt`, `app/src/test/java/com/example/poswarnus/data/transaction/HistoryEntryTest.kt`, `app/src/test/java/com/example/poswarnus/data/transaction/TransactionRepositoryTest.kt`; every existing fake `TransactionRepository` in `app/src/test` (e.g. in `ui/payment/PaymentViewModelTest.kt`, `ui/dashboard/DashboardViewModelTest.kt`) gets `override fun observeHistory(fromMillis: Long): Flow<List<HistoryEntry>> = flowOf(emptyList())`

**Interfaces:**
- Consumes: `TransactionEntity`, `TransactionLineEntity` (line `id` is `"<saleId>:<index>"`), `SelectionsJson.decode`, `SelectedOption`, `SyncStatus.PENDING`, `currentCashierId` already in `TransactionRepositoryImpl`.
- Produces:
  - `data class TransactionWithLines(@Embedded val header: TransactionEntity, @Relation(parentColumn = "id", entityColumn = "transactionId") val lines: List<TransactionLineEntity>)`
  - `@Transaction @Query("SELECT * FROM transactions WHERE cashierId = :cashierId AND isTraining = 0 AND createdAt >= :fromMillis ORDER BY createdAt DESC, id") abstract fun observeHistory(cashierId: String, fromMillis: Long): Flow<List<TransactionWithLines>>` on `TransactionDao`
  - `data class HistoryLine(val name: String, val meta: String, val quantity: Int, val unitPrice: Long, val subtotal: Long)`
  - `data class HistoryEntry(val id: String, val code: String, val createdAt: Long, val lines: List<HistoryLine>, val itemCount: Int, val subtotal: Long, val taxLabel: String, val taxBasisPoints: Int, val taxAmount: Long, val grandTotal: Long, val cashReceived: Long, val changeAmount: Long, val isPending: Boolean)`
  - `fun shortCode(id: String): String` and `fun TransactionWithLines.toHistoryEntry(): HistoryEntry` in `HistoryEntry.kt`
  - `TransactionRepository.observeHistory(fromMillis: Long): Flow<List<HistoryEntry>>` (cashier from `currentCashierId`; none => `flowOf(emptyList())`, evaluated at call time like `syncSummary`)

- [ ] **Step 1: Write the failing tests.**
  - `TransactionDaoTest`: `observeHistory returns newest first with id as tie-break`; `observeHistory only returns the requested cashier`; `observeHistory excludes training sales`; `observeHistory fromMillis is inclusive` (row at exactly `fromMillis` returned, one millisecond earlier not).
  - `HistoryEntryTest`: `shortCode is six uppercase hex characters without dashes with a hash` (`shortCode("a3f9c1d2-...")` == `"#A3F9C1"`); `unitPrice adds option price deltas and subtotal multiplies by quantity`; `meta joins variants first then modifiers with a middle dot` (e.g. `"Large · Extra Keju · Extra Telur"`) and is empty without options; `itemCount sums quantities`; `isPending is true only for PENDING`; `lines are ordered by the index suffix of their id` (ids `"x:10"`, `"x:2"` => 2 before 10); `corrupt selectionsJson keeps the line with empty meta`.
  - `TransactionRepositoryTest`: `observeHistory maps a saved sale and drops the badge after markSynced` (collect with `first()` twice around `db.transactionDao().markSynced(...)`: `isPending` true then false); `observeHistory is empty when nobody is signed in`.
- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*TransactionDaoTest" --tests "*HistoryEntryTest" --tests "*TransactionRepositoryTest"`
Expected: compile failure / FAIL (`observeHistory`, `HistoryEntry`, `shortCode` do not exist).

- [ ] **Step 3: Implement** the query and `TransactionWithLines` in `TransactionEntities.kt`; `HistoryEntry.kt` as specified (meta = variant names then modifier names, each joined with `" · "`; sort lines by the integer after the last `:` in the line id); `TransactionRepositoryImpl.observeHistory` mapping with `toHistoryEntry()`; update the fakes listed above.
- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, whole suite green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/local/TransactionEntities.kt app/src/main/java/com/example/poswarnus/data/transaction/TransactionRepository.kt app/src/main/java/com/example/poswarnus/data/transaction/HistoryEntry.kt app/src/test
git commit -m "feat: add the transaction history query, models and repository flow"
```

### Task 2: HistoryViewModel

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/history/HistoryViewModel.kt`
- Test: `app/src/test/java/com/example/poswarnus/ui/history/HistoryViewModelTest.kt`

**Interfaces:**
- Consumes: `TransactionRepository.observeHistory(fromMillis)`, `HistoryEntry`, `formatRupiah(Long)`.
- Produces:
  - `enum class HistoryDay { TODAY, YESTERDAY }`
  - `data class HistoryRow(val id: String, val code: String, val timeText: String, val itemCount: Int, val itemNames: String, val grandTotal: Long, val isPending: Boolean)`
  - `data class HistoryUiState(val day: HistoryDay = HistoryDay.TODAY, val query: String = "", val rows: List<HistoryRow> = emptyList(), val summary: String = "0 transaksi · Rp0", val selected: HistoryEntry? = null, val selectedWhen: String = "", val dayIsEmpty: Boolean = true, val detailOpen: Boolean = false)` (`rows` filtered by search; `dayIsEmpty` = the tab has no sales at all, before search; `selected` = the chosen entry, else the first row's, else `null`; `selectedWhen` = `"<Hari ini|Kemarin> · <HH.mm>"`; `detailOpen` is only for narrow screens)
  - `@HiltViewModel class HistoryViewModel` with `val uiState: StateFlow<HistoryUiState>`, `fun onDayChange(day: HistoryDay)`, `fun onQueryChange(query: String)`, `fun onSelect(id: String)` (selects and sets `detailOpen = true`), `fun onCloseDetail()`. Tab or query changes clear the explicit selection and set `detailOpen = false`.
  - Construction: `HistoryViewModel(repository: TransactionRepository, now: () -> Long, timeZone: () -> TimeZone)` for tests plus an `@Inject constructor(repository: TransactionRepository)` that passes `System::currentTimeMillis` and `TimeZone::getDefault` (Dagger cannot use default arguments).

- [ ] **Step 1: Write the failing tests** (fake repository emitting a `MutableStateFlow<List<HistoryEntry>>`; `now` and `timeZone` fixed, e.g. UTC, `now` = a chosen afternoon):
  - `sale at 23_59_59_999 yesterday is Kemarin and midnight is Hari ini`; `a sale older than the start of yesterday is in neither tab`; repository is called with `fromMillis` = start of yesterday.
  - `summary counts the tab's sales and sums grand totals` (`"3 transaksi · Rp..."` via `formatRupiah`), unaffected by the search.
  - `search matches code with and without hash, any case, and item names`; `search with no match gives empty rows but dayIsEmpty false`; `a tab without sales has dayIsEmpty true`.
  - `first row is selected when nothing is chosen`; `onSelect keeps the id when the list changes`; `a search that hides the selected row falls back to the first match`; `two entries with the same code select by full id`.
  - `onSelect opens detail and onCloseDetail closes it`; `changing tab or search closes detail and clears the choice`.
  - `a row that turns synced drops isPending and keeps the selection` (emit the same list with `isPending = false`).
  - `selectedWhen reads Hari ini or Kemarin plus HH.mm`.
- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*HistoryViewModelTest"`
Expected: compile failure (`HistoryViewModel` does not exist).

- [ ] **Step 3: Implement** `HistoryViewModel` (combine the repository flow with private `MutableStateFlow`s for day, query, chosen id and detail flag; day boundaries from `now()` and `timeZone()` via `Calendar`, recomputed on every emission and every input change; times with `SimpleDateFormat("HH.mm", Locale.US)` in the given time zone; `stateIn(WhileSubscribed(5000))` like `SettingsViewModel`). The repository `Flow` is created once with `fromMillis` computed at construction from `now()`.
- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, whole suite green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/history/HistoryViewModel.kt app/src/test/java/com/example/poswarnus/ui/history/HistoryViewModelTest.kt
git commit -m "feat: add the transaction history view model"
```

### Task 3: History screen and route

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/history/HistoryScreen.kt`
- Modify: `app/src/main/java/com/example/poswarnus/ui/navigation/PosNavGraph.kt` (`PosDestinations.HISTORY = "history"`, composable `HistoryScreen(onBack = { navController.popBackStack() })`)

**Interfaces:**
- Consumes: `HistoryViewModel`, `HistoryUiState`, `HistoryRow`, `HistoryEntry`, `HistoryDay`, `formatRupiah`, `TrainingBannerViewModel`/`TrainingBanner`, `ArrowBackGlyph`, `SearchGlyph`, theme tokens.
- Produces: `@Composable fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel())`.

- [ ] **Step 1: Implement the screen** per spec 4.3 and the global-constraints copy. Top bar 56dp: back button (48dp, `ArrowBackGlyph`, "Kembali ke kasir"), title, summary on the right; `TrainingBanner` above the bar when training is on. Wide (`maxWidth >= 840.dp`): left pane 520dp (tabs, search field with `SearchGlyph`, `LazyColumn` of rows with the selected row highlighted, "Menunggu sinkron" chip on pending rows, the two list empty states), right pane the detail card (title code, `selectedWhen`, `Selesai` chip plus `Menunggu sinkron` chip when pending, line rows with name, meta (only when non-empty), `qty × unit`, subtotal; then Subtotal, tax row, Jumlah item, Total (large), Tunai diterima, Kembalian; footer with a disabled `Cetak Ulang Struk (segera hadir)` button) or `Pilih transaksi untuk melihat rincian.` when `selected == null`. Narrow: list only; when `detailOpen` show the detail in place of the list; `BackHandler(enabled = detailOpen)` calls `onCloseDetail()`, otherwise the top-bar back calls `onBack`. Rows and the detail carry content descriptions that include code, time and total. Search field `aria`/label "Cari transaksi".
- [ ] **Step 2: Wire the route** in `PosNavGraph.kt` and keep the SystemBarIcons logic unchanged (history is a cream screen).
- [ ] **Step 3: Run to verify**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/history/HistoryScreen.kt app/src/main/java/com/example/poswarnus/ui/navigation/PosNavGraph.kt
git commit -m "feat: add the transaction history screen and route"
```

### Task 4: Dashboard entry icon

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/ui/components/Glyphs.kt` (add `HistoryGlyph(tint, modifier)`: counter-clockwise arrow around a clock hand, as in the design's `M3 12a9 9 0 109-9 9 9 0 00-6.4 2.6L3 8`, `M3 3v5h5`, `M12 7v5l3 2`), `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt` (`DashboardScreen` gets `onOpenHistory: () -> Unit`, passed through `CatalogPane` to `CatalogHeader`), `app/src/main/java/com/example/poswarnus/ui/navigation/PosNavGraph.kt` (`onOpenHistory = { navController.navigate(PosDestinations.HISTORY) }`)

**Interfaces:**
- Consumes: the `history` route (Task 3), the existing gear button and `onOpenSettings` plumbing in `CatalogHeader`.
- Produces: `HistoryGlyph`, `DashboardScreen(onOpenHistory = ...)`.

- [ ] **Step 1: Implement** a 48dp icon button with `HistoryGlyph`, `contentDescription` `Riwayat transaksi`, placed immediately before the gear in both the compact and the wide header layouts (brand keeps `weight(1f)`).
- [ ] **Step 2: Run to verify**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/components/Glyphs.kt app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt app/src/main/java/com/example/poswarnus/ui/navigation/PosNavGraph.kt
git commit -m "feat: open the transaction history from the catalog header"
```

### Task 5: Emulator verification and roadmap (controller)

**Files:**
- Modify: `docs/roadmap.md` (mark Phase 7, record verified / NOT verified, backlog: older-than-yesterday access)

**Interfaces:** none.

- [ ] **Step 1: Full gate.** Run `./gradlew testDebugUnitTest assembleDebug lint`. Expected: tests and build green; lint shows only the 2 known `LoginScreenTest` errors.
- [ ] **Step 2: Install on the tablet emulator** (`adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`, never `installDebug`), log in if needed (controller types the credentials), and verify spec section 6 items 1-5 and 7 with screenshots and a pulled database: the icon opens the screen; "Hari ini" lists today's sales newest first with code, `HH.mm`, items, total; a new sale appears without reopening and its badge disappears after the upload; search by code and by menu, and the no-match text; detail shows lines, options, Subtotal, Pajak, Jumlah item, Total, Tunai, Kembalian and the disabled print button; a training sale is absent; phone size (`wm size 1080x2400`, reset afterwards with `wm size reset`): header fits with both icons, list then detail, Back returns to the list.
- [ ] **Step 3: Item 6 (another cashier)** cannot be run with a single test account: report as NOT verified (covered by the DAO and ViewModel tests).
- [ ] **Step 4: Update `docs/roadmap.md`:** Phase 7 row Done on its branch with a "Phase 7 status" block (verified and NOT verified lists, deferred minors), and the backlog note that sales older than yesterday cannot be opened yet. Commit `docs: record Phase 7 status`.
