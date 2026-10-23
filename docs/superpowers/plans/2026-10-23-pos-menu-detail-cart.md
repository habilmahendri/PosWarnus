# POS Menu Detail Dialog + Cart (Phase 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the cashier tap a catalog item, configure variant/modifiers/quantity in a dialog whose options come from Supabase, and manage the resulting in-memory order in a cart (tablet side pane, phone bottom sheet).

**Architecture:** Option config lives in two new normalized Supabase tables (`option_groups`, `options`), cached in Room (DB v1 -> v2, non-destructive migration) and read through `CatalogRepository.observeOptionGroups`. A pure-Kotlin core (`OrderDraft`, `CartLine`, `CartStore`) holds all pricing/merge rules in whole-Rupiah `Long` and is unit-tested without Android; a singleton in-memory `CartStore` is the cart, driven by a `CartViewModel` shared by both Dashboard panes. UI (dialog, cart panel, phone bar/sheet, card badge) is thin Compose over that state.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Hilt, Room 2.8 (+ Robolectric for DAO/migration tests), Supabase Postgrest, JUnit/Turbine/coroutines-test. No new dependencies.

**Spec:** [docs/superpowers/specs/2026-10-23-menu-detail-cart-design.md](../specs/2026-10-23-menu-detail-cart-design.md) (section numbers below refer to it). Upstream: [PRD 4.4/4.5](../../PRD-pos.md), [design-reference.md](../../design-reference.md). Where PRD and design reference conflict, PRD wins.

## Global Constraints

- Package `com.example.poswarnus`; minSdk 24; no new Gradle dependencies; no icon library (new glyphs are `Canvas` drawings in `ui/components/Glyphs.kt`).
- Money in new code is whole Rupiah `Long`; convert the existing `Double` price with `price.roundToLong()` at the boundary only. `formatRupiah(Long)` returns `Rp25.000` (no space, dot thousands, no decimals).
- Cart lines are snapshots (name, base price, selections); a catalog refresh never alters existing lines or totals.
- `price_delta >= 0` (check constraint); a variant group is always `single` + required, at most one per item.
- Cart quantity: dialog minimum 1 (decrement disabled at 1); decrement to 0 **in the cart** removes the line (PRD 4.5, overrides design reference); trash button also removes.
- No tax/service charge, no payment, no cart persistence, no featured-menu row. "Bayar" calls an `onCheckout` callback that is a no-op in this phase.
- Design tokens/radii/typography come from the existing `ui/theme` (6dp controls, 10dp cards/dialogs, scrim `rgba(28,27,25,0.5)`); the dialog is the only elevated surface.
- Unavailable items never open the dialog; unavailable options are dimmed, tagged "Habis", never selectable.
- Verification is JVM-only: `./gradlew testDebugUnitTest assembleDebug` (no emulator/adb exists). Compose UI is verified by compile + the manual checklist in Task 9.
- Baseline note: the working tree contains uncommitted Phase 2 UI restyle files (`DashboardScreen.kt`, `DashboardComponents.kt`, `LoginScreen.kt`, `Glyphs.kt`, PRD edits). Commit only the files each task lists; never `git add -A`. Task 7-9 modify `DashboardScreen.kt`/`DashboardComponents.kt`/`Glyphs.kt` on top of those uncommitted edits, so those files' pre-existing changes ride along in the first commit that touches them — acceptable, mention it in that commit message.
- Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Options fetch fails after items fetch succeeded** (or vice versa): previous Room cache must stay intact and `refresh()` returns `Failure` (Task 5 test).
2. **Double-tap on "Tambah ke Pesanan"**: second `confirmDialog()` must not add a second line (Task 6 test).
3. **Editing a line to a config equal to another line**: lines merge, no duplicate, edited line gone (Task 3 + Task 6 tests).
4. **Required group whose options are all unavailable**: not enforced, otherwise the item is unorderable (Task 2 test).
5. **Cart line whose menu item vanished or became unavailable after a refresh**: row shows the "Menu tidak tersedia lagi" note, tap does nothing / edit still allowed respectively, no crash (Task 6 test).
6. **App upgrade with an existing v1 cache**: cached categories/items survive the migration and `description` is null (Task 4 test).
7. **Logout with items in the cart or the dialog open**: cart cleared, dialog gone (Task 6 test).

---

### Task 1: Supabase schema migration + seed (Option A)

**Files:** none in the repo (executed against live project `mpprrdmyxdhdcvrponra`, "POS Warnus"). No commit.

**Interfaces:**
- Produces: tables `public.option_groups`, `public.options`, column `public.menu_items.description`, RLS select-for-`authenticated` policies, seed rows for Ayam Katsu / Nasi Goreng Spesial / Es Teh Manis. Tasks 4-5 DTO/entity field names mirror these columns exactly (spec section 2).

- [ ] **Step 1: Confirm seed targets exist**

Run (Supabase MCP `execute_sql`): `select name from public.menu_items order by name;` Expected: includes `Ayam Katsu`, `Nasi Goreng Spesial`, `Es Teh Manis`, `Kopi Hitam`. If any name differs, fix the names in the seed before Step 3 (the seed's `limit 1` select silently yields no row on a mismatch, then fails with a not-null violation).

- [ ] **Step 2: Apply the DDL**

Supabase MCP `apply_migration`, name `add_option_groups_and_options`, SQL = spec section 2 parts 1-4 verbatim (alter table, both `create table`s, indexes, RLS enable, two policies). Expected: success, no error.

- [ ] **Step 3: Apply the seed**

Supabase MCP `execute_sql` with the spec section 2 `do $$ ... $$;` seed block verbatim. Expected: success.

- [ ] **Step 4: Verify data**

`execute_sql`: `select (select count(*) from option_groups) g, (select count(*) from options) o, (select count(*) from options where not is_available) unavailable, (select count(*) from menu_items where description is not null) d;` Expected: `g=7, o=16, unavailable=1, d=3`.

- [ ] **Step 5: Verify RLS and advisors**

`execute_sql`: `select tablename, policyname, roles from pg_policies where tablename in ('option_groups','options');` Expected: one SELECT policy each, roles `{authenticated}`. Then Supabase MCP `get_advisors` (security): expected no new "RLS disabled" finding for the two tables. Anonymous read check: `curl -s "$SUPABASE_URL/rest/v1/option_groups?select=id" -H "apikey: $ANON_KEY" -H "Authorization: Bearer $ANON_KEY"` (values from `local.properties`, do not print the key). Expected: `[]`.

---

### Task 2: Pure domain core — models, money, `OrderDraft`, `CartLine`

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/data/catalog/CatalogRepository.kt` (only the `MenuItem` data class: add `val description: String? = null`)
- Create: `app/src/main/java/com/example/poswarnus/data/catalog/OptionModels.kt`
- Create: `app/src/main/java/com/example/poswarnus/ui/cart/OrderDraft.kt`
- Create: `app/src/main/java/com/example/poswarnus/ui/cart/CartLine.kt`
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardComponents.kt` (add `fun formatRupiah(amount: Long): String`; keep the `Double` overload)
- Test: `app/src/test/java/com/example/poswarnus/ui/cart/OrderDraftTest.kt`, `.../ui/cart/CartLineTest.kt`

**Interfaces:**
- Produces (exactly as spec 3.1 / 4.1 / 4.3):
  - `enum class OptionKind { VARIANT, MODIFIER }`, `enum class SelectionMode { SINGLE, MULTI }`
  - `data class Option(id, groupId, name, priceDelta: Long, sortOrder: Int, isAvailable: Boolean)`
  - `data class OptionGroup(id, menuItemId, name, kind: OptionKind, selection: SelectionMode, isRequired: Boolean, sortOrder: Int, options: List<Option>)`
  - `data class SelectedOption(optionId, groupId, name, priceDelta: Long, kind: OptionKind)`
  - `data class CartLine(lineId: String, menuItemId: String, name: String, basePrice: Long, selections: List<SelectedOption>, quantity: Int)` with computed `configKey: String` (menuItemId + sorted optionIds, order-independent), `unitPrice: Long`, `lineTotal: Long`, `summary: String` (variant first, then modifiers, joined `"Large · Extra Keju, Extra Telur"`; empty string when no selections)
  - `data class OrderDraft(item: MenuItem, groups: List<OptionGroup>, selected: Map<String, Set<String>> = emptyMap(), quantity: Int = 1)` with `toggle(groupId, optionId): OrderDraft`, `withQuantity(q: Int): OrderDraft` (coerced `>= 1`), `missingRequiredGroups: List<OptionGroup>`, `canSubmit: Boolean`, `unitPrice: Long`, `lineTotal: Long`, `toCartSelections(): List<SelectedOption>` (sorted by group sortOrder, then option sortOrder), and `companion fun fromLine(item, groups, line): OrderDraft` (pre-fill by option id; drops options that no longer exist or are unavailable)
  - `fun formatRupiah(amount: Long): String`

- [ ] **Step 1: Write failing `OrderDraftTest`** — tests: `single group toggle replaces selection`, `multi group toggle adds then removes`, `unavailable option toggle is ignored`, `canSubmit false while a required group is empty and true once selected`, `required group with no available options is not enforced`, `item with no groups can submit immediately`, `unitPrice adds variant and modifier deltas to base`, `lineTotal multiplies by quantity` (e.g. base 25000, Large +10000, Extra Keju +5000, qty 2 -> `80000`), `withQuantity coerces to at least 1`, `fromLine drops options missing from current groups`, `price 12500.5 rounds to 12501 base`.
- [ ] **Step 2: Write failing `CartLineTest`** — `configKey is independent of selection order`, `configKey differs when an option differs`, `summary lists variant first then modifiers`, `summary empty without selections`, `lineTotal is unitPrice times quantity`.
- [ ] **Step 3: Run** `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.cart.*"` — Expected: FAIL (compilation: types undefined).
- [ ] **Step 4: Implement** the types above in the listed files. `OrderDraft` treats a required group as enforced only if `isRequired && options.any { it.isAvailable }` (spec section 6). A single-select toggle on the already-selected option keeps it selected (radio semantics); multi toggles. Add the `Long` `formatRupiah` overload using the same `NumberFormat` as the `Double` one.
- [ ] **Step 5: Run** the same command — Expected: PASS. Then `./gradlew compileDebugKotlin` — Expected: BUILD SUCCESSFUL (the new `MenuItem.description` default keeps existing callers compiling).
- [ ] **Step 6: Commit** `git add` the listed files only; message `feat: add cart domain core (OrderDraft, CartLine, option models)`.

---

### Task 3: `CartStore`

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/cart/CartStore.kt`
- Test: `app/src/test/java/com/example/poswarnus/ui/cart/CartStoreTest.kt`

**Interfaces:**
- Consumes: `CartLine`, `SelectedOption` (Task 2).
- Produces:
  - `data class CartState(val lines: List<CartLine>)` with `val subtotal: Long`, `val totalQuantity: Int`, `val quantityByMenuItem: Map<String, Int>`
  - `@Singleton class CartStore @Inject constructor()` with `val state: StateFlow<CartState>` and `add(line: CartLine)`, `replace(lineId: String, selections: List<SelectedOption>, quantity: Int)`, `setQuantity(lineId: String, quantity: Int)`, `remove(lineId: String)`, `clear()`

- [ ] **Step 1: Write failing `CartStoreTest`** — `add appends a new line`, `add merges quantity for an equal configKey`, `replace updates selections and quantity in place keeping lineId`, `replace into a configKey held by another line merges and removes the edited line` (assert 1 line, summed quantity), `setQuantity zero removes the line`, `setQuantity on unknown id is a no-op`, `remove deletes only that line`, `clear empties`, `subtotal sums lineTotals`, `quantityByMenuItem sums per menu item across configs`, `lines are unaffected by later changes to the source MenuItem` (lines are plain snapshots).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.cart.CartStoreTest"` — Expected: FAIL (CartStore undefined).
- [ ] **Step 3: Implement** `CartStore` with a `MutableStateFlow<CartState>` updated via `update {}` (thread-safe). `replace` recomputes the line's `configKey` from the new selections; collision with a different `lineId` merges into the existing line (quantities summed) and drops the edited one.
- [ ] **Step 4: Run** the same command — Expected: PASS.
- [ ] **Step 5: Commit** `feat: add in-memory CartStore with merge and in-place edit`.

---

### Task 4: Room layer — entities, DAOs, DB v2 migration

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/data/local/CatalogEntities.kt` (add `description: String? = null` to `MenuItemEntity`; add `OptionGroupEntity`, `OptionEntity` exactly as spec 3.3)
- Create: `app/src/main/java/com/example/poswarnus/data/local/OptionDaos.kt` (`OptionGroupWithOptions`, `OptionGroupDao`, `OptionDao`)
- Modify: `app/src/main/java/com/example/poswarnus/data/local/AppDatabase.kt` (entities + `version = 2`, new DAO accessors, top-level `val MIGRATION_1_2: Migration`)
- Modify: `app/src/main/java/com/example/poswarnus/di/DatabaseModule.kt` (`.addMigrations(MIGRATION_1_2)`, provide both new DAOs)
- Test: `app/src/test/java/com/example/poswarnus/data/local/OptionGroupDaoTest.kt`, `.../data/local/AppDatabaseMigrationTest.kt`

**Interfaces:**
- Produces:
  - `data class OptionGroupWithOptions(@Embedded val group: OptionGroupEntity, @Relation(parentColumn = "id", entityColumn = "groupId") val options: List<OptionEntity>)`
  - `OptionGroupDao`: `fun observeForItem(menuItemId: String): Flow<List<OptionGroupWithOptions>>` (`@Transaction`, `ORDER BY sortOrder`), `suspend fun upsertAll(groups: List<OptionGroupEntity>)`, `suspend fun clear()`
  - `OptionDao`: `suspend fun upsertAll(options: List<OptionEntity>)`, `suspend fun clear()`
  - `AppDatabase.optionGroupDao()`, `.optionDao()`; `MIGRATION_1_2` with the SQL of spec 3.3

- [ ] **Step 1: Write failing `OptionGroupDaoTest`** (Robolectric, same setup as `MenuItemDaoTest`) — `observeForItem is empty when nothing stored`, `observeForItem returns only groups of that item ordered by sortOrder`, `relation attaches the options of each group`, `clear removes groups`.
- [ ] **Step 2: Write failing `AppDatabaseMigrationTest`** (Robolectric) — create a raw SQLite file with the v1 schema (the Room-generated `categories` and `menu_items` DDL from the v1 entities, `PRAGMA user_version = 1`), insert one category and one menu item, open `Room.databaseBuilder(..., AppDatabase::class.java, file).addMigrations(MIGRATION_1_2).build()`, then assert `menuItemDao().observeAll().first()` returns the old row with `description == null`, `categoryDao()` row survives, and `optionGroupDao().observeForItem("x").first()` is empty (tables exist and Room's schema validation passed).
- [ ] **Step 3: Run** `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.local.*"` — Expected: FAIL (compilation).
- [ ] **Step 4: Implement** the entities/DAOs/`AppDatabase`/`MIGRATION_1_2`/`DatabaseModule` changes. Existing `MenuItemDaoTest` constructors compile because `description` has a default. Do not use `fallbackToDestructiveMigration`.
- [ ] **Step 5: Run** the same command plus `./gradlew compileDebugKotlin` — Expected: PASS / BUILD SUCCESSFUL (Hilt/KSP regenerates; fix `CatalogTransactionRunner` consumers only if the compile points at them).
- [ ] **Step 6: Commit** `feat: add option_groups/options Room tables with v1->v2 migration`.

---

### Task 5: Remote DTOs + repository (fetch, atomic replace, observe)

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/data/remote/CatalogDtos.kt` (`MenuItemDto.description: String? = null`; new `OptionGroupDto`, `OptionDto` per spec 3.2)
- Modify: `app/src/main/java/com/example/poswarnus/data/remote/CatalogRemoteDataSource.kt` and `SupabaseCatalogRemoteDataSource.kt` (`fetchOptionGroups()`, `fetchOptions()`; tables `option_groups`, `options`)
- Modify: `app/src/main/java/com/example/poswarnus/data/catalog/CatalogRepository.kt` (interface + `CatalogRepositoryImpl`)
- Modify: `app/src/main/java/com/example/poswarnus/di/CatalogModule.kt` (pass the two new DAOs)
- Test: modify `app/src/test/java/com/example/poswarnus/data/catalog/CatalogRepositoryTest.kt` and `.../ui/dashboard/DashboardViewModelTest.kt` (fakes only)

**Interfaces:**
- Consumes: Task 2 models, Task 4 DAOs/entities.
- Produces: `CatalogRepository.observeOptionGroups(menuItemId: String): Flow<List<OptionGroup>>` (groups sorted by `sortOrder`, options sorted by `sortOrder`); `CatalogRepositoryImpl(categoryDao, menuItemDao, optionGroupDao, optionDao, remote, transactionRunner)`; `refresh()` fetches all four collections first and writes all four tables in one `runInTransaction`. DTO `price_delta: Double` -> `Long` via `roundToLong()`; groups with unknown `kind`/`selection` are skipped (and their options are not stored as orphans of a skipped group is acceptable; options are still stored by `groupId`).

- [ ] **Step 1: Extend fakes and write failing tests in `CatalogRepositoryTest`** — add `FakeOptionGroupDao`/`FakeOptionDao`, extend `FakeCatalogRemoteDataSource` with `optionGroupsResult`/`optionsResult`. New tests: `refresh writes categories items groups and options`, `refresh stores description`, `observeOptionGroups returns groups with options sorted by sortOrder`, `observeOptionGroups filters by menu item`, `failing options fetch leaves previous cache intact and returns Failure` (**Review Focus 1**; pre-populate all four fakes, make `fetchOptions` throw, assert nothing changed), `failing option groups fetch leaves cache intact`, `unknown kind group is skipped without failing refresh`, `price_delta 5000.0 maps to 5000L`.
- [ ] **Step 2: Update `FakeCatalogRepository` in `DashboardViewModelTest`** to implement `observeOptionGroups` (return `flowOf(emptyList())`); existing tests must keep passing unchanged.
- [ ] **Step 3: Run** `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.catalog.*"` — Expected: FAIL (compilation/behaviour).
- [ ] **Step 4: Implement** DTOs, data source, repository, `CatalogModule`. `observeOptionGroups` maps `OptionGroupDao.observeForItem` and skips groups whose `kind`/`selection` strings don't parse. Keep the `try/catch (CancellationException)` structure of the current `refresh()`.
- [ ] **Step 5: Run** `./gradlew testDebugUnitTest` — Expected: all PASS (whole suite, because the repository interface changed).
- [ ] **Step 6: Commit** `feat: fetch and cache option groups/options in CatalogRepository`.

---

### Task 6: `CartViewModel` + logout clears the cart

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/cart/CartViewModel.kt`
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt` (`LogoutViewModel` only: inject `CartStore`, call `cartStore.clear()` in `logout`)
- Test: `app/src/test/java/com/example/poswarnus/ui/cart/CartViewModelTest.kt`

**Interfaces:**
- Consumes: `CatalogRepository.menuItems` / `observeOptionGroups` (Task 5), `CartStore` (Task 3), `OrderDraft`/`CartLine` (Task 2).
- Produces:
  - `sealed interface DialogTarget { data class New(val itemId: String) : DialogTarget; data class Edit(val lineId: String) : DialogTarget }`
  - `data class DialogUiState(val target: DialogTarget, val item: MenuItem, val draft: OrderDraft?)` (`draft == null` while groups load)
  - `data class CartLineUi(val line: CartLine, val unavailable: Boolean)`
  - `data class CartUiState(val lines: List<CartLineUi>, val subtotal: Long, val totalQuantity: Int, val quantityByMenuItem: Map<String, Int>, val dialog: DialogUiState?, val showClearConfirm: Boolean)`
  - `@HiltViewModel class CartViewModel @Inject constructor(repository: CatalogRepository, cartStore: CartStore)` with `val uiState: StateFlow<CartUiState>` and `onMenuItemClicked(item: MenuItem)`, `onLineClicked(lineId: String)`, `toggleOption(groupId: String, optionId: String)`, `incrementQuantity()`, `decrementQuantity()`, `confirmDialog()`, `dismissDialog()`, `incrementLine(lineId)`, `decrementLine(lineId)`, `removeLine(lineId)`, `requestClearAll()`, `confirmClearAll()`, `cancelClearAll()`

- [ ] **Step 1: Write failing `CartViewModelTest`** (fake `CatalogRepository` with settable items/groups per item, real `CartStore`, `StandardTestDispatcher` like `DashboardViewModelTest`) — tests: `unavailable item does not open the dialog`, `available item opens New dialog and draft loads from groups`, `toggle then confirm adds a line with snapshot selections`, `confirm is blocked while a required group is unmet`, `double confirm adds only one line` (**Review Focus 2**), `dismiss closes without adding`, `line click opens Edit dialog pre-filled`, `edit confirm updates in place without duplicate`, `edit to a config equal to another line merges` (**Focus 3**), `edit drops an option that vanished and re-applies required check`, `line whose menu item no longer exists is flagged unavailable and click is a no-op` (**Focus 5**), `line whose menu item became unavailable is flagged and still editable`, `decrementLine at quantity 1 removes the line`, `clear all needs confirmation` (request shows confirm, cancel keeps lines, confirm empties; clear on empty cart is a no-op), `logout via store clear removes lines and dialog` (**Focus 7**: after `cartStore.clear()` and `dismiss` semantics, `uiState.lines` is empty; also assert the dialog closes when its target line disappears).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.cart.CartViewModelTest"` — Expected: FAIL (CartViewModel undefined).
- [ ] **Step 3: Implement** `CartViewModel`. `uiState` = `combine(cartStore.state, repository.menuItems, dialogState, showClearConfirm)`; draft is built once from `repository.observeOptionGroups(itemId).first()` when a dialog opens (spec 4.4); `confirmDialog` first checks `dialog != null && draft?.canSubmit == true`, then adds/replaces and nulls the dialog in the same update (this is what makes double confirm safe). Edit pre-fill uses `OrderDraft.fromLine`. `LogoutViewModel.logout` calls `cartStore.clear()` after `authRepository.logout()`.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest` — Expected: all PASS.
- [ ] **Step 5: Commit** (note the carried-along `DashboardScreen.kt` restyle in the message) `feat: add CartViewModel and clear cart on logout`.

---

### Task 7: Menu Detail Dialog UI

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/cart/MenuDetailDialog.kt`
- Modify: `app/src/main/java/com/example/poswarnus/ui/components/Glyphs.kt` (add `MinusGlyph`, `PlusGlyph`, `CheckGlyph`, `TrashGlyph`)

**Interfaces:**
- Consumes: `DialogUiState`, `OrderDraft`, `OptionGroup` (Tasks 2, 6); `formatRupiah(Long)`.
- Produces: `@Composable fun MenuDetailDialog(state: DialogUiState, onToggle: (groupId: String, optionId: String) -> Unit, onIncrement: () -> Unit, onDecrement: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit)`; glyph signatures `fun MinusGlyph(tint: Color, modifier: Modifier = Modifier)` (same shape for Plus/Check/Trash).

- [ ] **Step 1: Implement `MenuDetailDialog`** per spec 5.1: `Dialog(properties = DialogProperties(usesPlatformDefaultWidth = false))`; wide layout when `maxWidth >= 600.dp` (`min(840, w-32)` x `min(660, h-32)`, left column 296dp `#FAF9F6`), phone layout single scrolling column; variant cards row (selected = `AccentTint` bg + 2dp `AccentOrange` border; "Wajib" tag), modifier rows 52dp min with checkbox (6dp square) vs radio (circle) indicators, price delta `+Rp5.000` right-aligned (blank at 0), unavailable option at 40% alpha with "Habis" tag and no click; footer 84dp: stepper (decrement disabled at 1), hint (`Pilih {missing group names}` in `ErrorRed` while blocked, else "Total") above live total (26sp/700), "Batal", primary button "Tambah ke Pesanan" (`isEdit` -> "Simpan Perubahan") disabled when `draft == null || !draft.canSubmit`. Description hidden when null. Use `@Preview`-free code (no previews needed).
- [ ] **Step 2: Verify** `./gradlew compileDebugKotlin` — Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `feat: add Menu Detail Dialog UI`.

---

### Task 8: Cart panel UI

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/cart/CartPanel.kt`

**Interfaces:**
- Consumes: `CartUiState`, `CartLineUi` (Task 6); glyphs (Task 7).
- Produces: `@Composable fun CartPanel(state: CartUiState, onLineClick: (String) -> Unit, onIncrement: (String) -> Unit, onDecrement: (String) -> Unit, onRemove: (String) -> Unit, onRequestClear: () -> Unit, onConfirmClear: () -> Unit, onCancelClear: () -> Unit, onCheckout: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Implement `CartPanel`** per spec 5.2: header "Pesanan" + "Kosongkan" text button only when lines exist; empty state (reuse the existing `EmptyCartGlyph` copy: "Belum ada pesanan" / "Pilih menu di sebelah kiri untuk mulai menambahkan item."); rows: name 16sp/600, summary line (hidden when empty), unit price, right column line total 17sp/700 with `fontFeatureSettings = "tnum"`, trash + minus + qty + plus (44dp targets, row body clickable except controls); `unavailable` rows show an `ErrorRed` note "Menu tidak tersedia lagi"; footer: Subtotal, "Jumlah item", divider, "Total" 32sp/700 (equal to subtotal this phase), "Bayar" 56dp primary disabled when empty, calling `onCheckout`; `AlertDialog` "Kosongkan pesanan?" (Batal / Kosongkan) when `state.showClearConfirm`. No order-number chip.
- [ ] **Step 2: Verify** `./gradlew compileDebugKotlin` — Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `feat: add CartPanel UI`.

---

### Task 9: Dashboard wiring — card badge, tablet pane, phone bar + sheet, final verification

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardComponents.kt` (`MenuItemCard(item, inCartQuantity: Int = 0, onClick)`)
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt` (use `CartViewModel`; replace the placeholder `CartPane`; add phone bar/sheet; show `MenuDetailDialog`)
- Modify: `app/src/main/java/com/example/poswarnus/ui/navigation/PosNavGraph.kt` (pass `onCheckout = { }` to `DashboardScreen`)

**Interfaces:**
- Consumes: `CartViewModel`/`CartUiState` (Task 6), `MenuDetailDialog` (Task 7), `CartPanel` (Task 8).
- Produces: `DashboardScreen(onLoggedOut: () -> Unit, onCheckout: () -> Unit = {}, dashboardViewModel: DashboardViewModel = hiltViewModel(), logoutViewModel: LogoutViewModel = hiltViewModel(), cartViewModel: CartViewModel = hiltViewModel())`.

- [ ] **Step 1: Implement the card badge** per spec 5.4: when `inCartQuantity > 0` a 28dp accent circle badge (top-right) with the quantity and card border `AccentOrange` + 1dp ring; card click calls `cartViewModel.onMenuItemClicked(item)` from the grid.
- [ ] **Step 2: Implement layouts** per spec 5.3: `>= 840.dp` fixed 440dp `CartPanel` right of the catalog; below it a bottom bar "{N} item · {Rp total}" + "Lihat Pesanan" (hidden when empty) opening a `ModalBottomSheet` hosting `CartPanel`, auto-closing when the cart becomes empty; grid `contentPadding.bottom` grows to clear the bar. Render `MenuDetailDialog` when `uiState.dialog != null`.
- [ ] **Step 3: Verify build and tests** `./gradlew testDebugUnitTest assembleDebug` — Expected: BUILD SUCCESSFUL, all tests PASS.
- [ ] **Step 4: Manual checklist** (needs a device; no emulator available here, so report these as NOT verified in the hand-off): log in as the test cashier; Ayam Katsu dialog shows Ukuran cards, required "Tingkat Pedas" blocks Add until chosen, Extra Keju/Telur checkboxes, live total `(25000+10000+5000)*qty` style math; Nasi Goreng "Kerupuk" is dimmed with "Habis" and not selectable; Kopi Hitam opens a quantity-only dialog; card badge updates; tapping a cart row edits in place; Kosongkan asks for confirmation; phone width shows the bottom bar + sheet; options still load after toggling airplane mode (Room cache); an unauthenticated app state yields no options (RLS).
- [ ] **Step 5: Commit** `feat: wire menu detail dialog and cart into Dashboard (tablet pane, phone sheet)`.

---

## Self-Review

- **Spec coverage:** schema (T1), models/money/draft/line (T2), CartStore incl. merge/replace/setQuantity(0) (T3), Room v2 + migration + DAOs (T4), DTOs/remote/repository atomic refresh (T5), CartViewModel incl. edit pre-fill, clear confirm, logout clear (T6), dialog 5.1 (T7), cart pane 5.2 (T8), layouts/badge/onCheckout 5.3-5.4 (T9), testing section 7 mapped one-to-one (OrderDraft/CartStore/CartLine/CartViewModel/OptionGroupDao/Migration/Repository tests). Out-of-scope items are not planned.
- **Type consistency:** `SelectedOption`, `CartLine`, `OrderDraft`, `CartState`, `DialogUiState`, `CartUiState`, `observeOptionGroups`, and the 6-arg `CatalogRepositoryImpl` constructor use identical names in producing and consuming tasks.
- **Proportion:** plan is signatures, test names and decisions; migration SQL and seed stay in the spec and are applied verbatim in T1.
