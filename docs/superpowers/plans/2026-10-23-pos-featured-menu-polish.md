# POS Featured Menu + Phase 1-3 Polish (Phase 3b) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show admin-featured menu items in a "Menu Unggulan" row at the top of the catalog, and close the rough edges found while testing Phases 1-3 (Indonesian login errors, on-brand launcher icon and system splash, Room schema export, readable splash status-bar icons, phone-width catalog header).

**Architecture:** `menu_items.is_featured` is read through the existing Supabase -> Room -> `CatalogRepository` path (DB v2 -> v3, non-destructive `MIGRATION_2_3`); `DashboardViewModel` derives `featuredItems` from the existing `menuItems` flow, and the grid gets a full-span first item holding a `LazyRow` of `MenuItemCard`s. Polish items are independent, mostly resource/UI changes with JVM tests where logic exists.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Hilt, Room 2.8 (+ Robolectric), Supabase Postgrest, JUnit/Turbine/coroutines-test. No new dependencies (no `core-splashscreen`).

**Spec:** [docs/superpowers/specs/2026-10-23-featured-menu-polish-design.md](../specs/2026-10-23-featured-menu-polish-design.md) (section numbers below refer to it). Upstream: [PRD 4.12](../../PRD-pos.md), [roadmap](../../roadmap.md) "Phase 3b".

## Global Constraints

- Package `com.example.poswarnus`; minSdk 24; **no new Gradle dependencies**.
- **Never execute anything against the live Supabase project** (no `apply_migration`, no `execute_sql` writes). The SQL file is only written to the repo; the project owner applies it manually later. The app must keep working without the column (`MenuItemDto.isFeatured` defaults to `false`).
- **Work in a git worktree on branch `feature/phase3b-featured-menu-polish`; do not merge to `main` and do not push.** Execution stops with the branch ready for review. Copy `local.properties` into the worktree.
- `MenuItemEntity.isFeatured` uses `@ColumnInfo(defaultValue = "0")` and `MIGRATION_2_3` uses `... NOT NULL DEFAULT 0`: the two must be identical or Room's schema validation fails on upgrade. No `fallbackToDestructiveMigration`.
- Row order = order of the `menuItems` list (no `featured_order`, no new `ORDER BY`). Unavailable featured items are excluded from the row.
- The featured row is hidden whenever the search query is not blank, ignores the selected category, and is not shown when the grid branch is not shown (empty/loading/error states).
- Login messages never show raw Supabase text; copy is exactly as in spec 4.1. UI copy is Indonesian.
- Money stays whole-Rupiah `Long` in UI code; no price is computed from `item.price` in the UI.
- Verification gate: `./gradlew testDebugUnitTest assembleDebug` green. UI/resources are verified by compile + the emulator checklist in Task 8; anything not verifiable is reported as NOT verified, never as passed.
- Test-cashier credentials are never written to any repo file or plan; the controller types them from the conversation. `adb input text` can drop the first character after a tap, so any typed text must be read back before submitting.
- Shell commands in the worktree are plain and separate (compound or `cd`-chained commands are rejected there). Add only the files each task lists (never `git add -A`). Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Upgrade from a v2 database** keeps cached categories/items/options and sets `isFeatured = false`; Room opens it without a schema error (Task 2 migration tests, both from v1 and from v2).
2. **Supabase without the `is_featured` column** (migration not yet applied): refresh still succeeds and nothing is featured (Task 2 DTO decode test).
3. **Featured row visibility rules:** unavailable items excluded, hidden while searching, still shown when a category is selected, order equals the source list, `visibleItems` untouched (Task 3 tests).
4. **Selected category with no items:** the existing empty state only, no row (Task 4, by reading; spec D5).
5. **Same item in the row and in the grid:** no key clash (row lives in a nested `LazyRow`), badge quantity and click behave identically (Task 4, by reading).
6. **Login error mapping:** a credential error, a network error and an unknown error map to three distinct Indonesian messages; no raw exception text leaks (Task 5 tests).
7. **Catalog header** decided by the pane's own width, not the window's: a 840-1040dp window gives a 400-600dp pane that must use the compact header (Task 7, by reading + emulator).

---

### Task 1: Room schema export (while the DB is still v2)

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/data/local/AppDatabase.kt` (`@Database(..., version = 2, exportSchema = true)` — **keep `version = 2`**)
- Modify: `app/build.gradle.kts` (`ksp { arg("room.schemaLocation", "$projectDir/schemas") }`)
- Create (generated, committed): `app/schemas/com.example.poswarnus.data.local.AppDatabase/2.json`

**Interfaces:**
- Produces: schema export wiring that Task 2 relies on to generate `3.json`. No code API.

- [ ] **Step 1: Wire the export** — set `exportSchema = true` on `@Database` (version stays 2) and add the `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` block in `app/build.gradle.kts` after the `android {}` block.
- [ ] **Step 2: Generate** — run `./gradlew kspDebugKotlin`. Expected: BUILD SUCCESSFUL and `app/schemas/com.example.poswarnus.data.local.AppDatabase/2.json` exists with `"version": 2`, tables `categories`, `menu_items`, `option_groups`, `options`.
- [ ] **Step 3: Verify nothing else changed** — run `./gradlew testDebugUnitTest`. Expected: PASS. Confirm `app/schemas` is not git-ignored (`git check-ignore app/schemas/...` prints nothing).
- [ ] **Step 4: Commit** `AppDatabase.kt`, `app/build.gradle.kts`, `2.json`; message `build: export Room schema (v2)`.

---

### Task 2: Featured data layer — SQL file, DTO/entity/domain, `MIGRATION_2_3`, DB v3

**Files:**
- Create: `supabase/migrations/20261023100000_add_menu_items_is_featured.sql` (written only; never executed)
- Modify: `app/src/main/java/com/example/poswarnus/data/remote/CatalogDtos.kt` (`MenuItemDto.isFeatured`)
- Modify: `app/src/main/java/com/example/poswarnus/data/local/CatalogEntities.kt` (`MenuItemEntity.isFeatured`)
- Modify: `app/src/main/java/com/example/poswarnus/data/catalog/CatalogRepository.kt` (`MenuItem.isFeatured` + both mappings)
- Modify: `app/src/main/java/com/example/poswarnus/data/local/AppDatabase.kt` (`version = 3`, `MIGRATION_2_3`)
- Modify: `app/src/main/java/com/example/poswarnus/di/DatabaseModule.kt` (`.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`)
- Create (generated, committed): `app/schemas/com.example.poswarnus.data.local.AppDatabase/3.json`
- Test: `app/src/test/java/com/example/poswarnus/data/remote/CatalogDtosTest.kt` (new), modify `.../data/catalog/CatalogRepositoryTest.kt`, `.../data/local/MenuItemDaoTest.kt`, `.../data/local/AppDatabaseMigrationTest.kt`

**Interfaces:**
- Consumes: Task 1 schema export.
- Produces: `MenuItemDto(... @SerialName("is_featured") val isFeatured: Boolean = false)`; `MenuItemEntity(... @ColumnInfo(defaultValue = "0") val isFeatured: Boolean = false)`; `MenuItem(... val isFeatured: Boolean = false)` (defaults keep existing constructors compiling); `val MIGRATION_2_3: Migration` executing exactly `ALTER TABLE menu_items ADD COLUMN isFeatured INTEGER NOT NULL DEFAULT 0`; DB `version = 3`.

- [ ] **Step 1: Write failing tests**
  - `CatalogDtosTest`: `menu item without is_featured decodes as not featured` (Json decode of a row lacking the key, `ignoreUnknownKeys` as configured by the client — mirror how the Supabase serializer is built), `menu item with is_featured true decodes as featured`.
  - `CatalogRepositoryTest`: `refresh maps is_featured to the domain item` (true and false), `refresh keeps featured flag after full replace`.
  - `MenuItemDaoTest`: `isFeatured round-trips through the dao`.
  - `AppDatabaseMigrationTest`: `v2 database upgrades with MIGRATION_2_3 keeping rows and defaulting isFeatured to false` (hand-built v2 file with `user_version = 2`, schema exactly as exported in `2.json`, one category, one item, one option group, one option; open with `addMigrations(MIGRATION_2_3)`; assert all rows survive, `isFeatured == false`), and extend the existing v1 test so it opens with `MIGRATION_1_2, MIGRATION_2_3` and also asserts `isFeatured == false`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.*"` — Expected: FAIL (compilation: `isFeatured`, `MIGRATION_2_3` undefined).
- [ ] **Step 3: Implement** the DTO/entity/domain fields and both repository mappings (`CatalogRepositoryImpl.refresh` entity construction and `menuItems` observe mapping), `MIGRATION_2_3`, `version = 3`, `DatabaseModule` migrations list. Write the SQL file with the DDL from spec 3.1 verbatim (header comment: "applied manually by the project owner; not executed by the app or any build step; not idempotent"; keep the example `update` commented out).
- [ ] **Step 4: Run** the same command plus `./gradlew kspDebugKotlin` — Expected: PASS, and `3.json` generated with `"version": 3` and `menu_items` containing `isFeatured` with `"defaultValue": "0"`. Diff `3.json` against `2.json`: the only structural change is that column.
- [ ] **Step 5: Run** `./gradlew testDebugUnitTest` (whole suite; the domain model changed) — Expected: PASS.
- [ ] **Step 6: Commit** the listed files; message `feat: read menu_items.is_featured (DB v3, MIGRATION_2_3)`. Do **not** run the SQL anywhere.

---

### Task 3: `DashboardViewModel.featuredItems`

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardViewModel.kt` (`DashboardUiState.featuredItems`, the `combine`)
- Test: `app/src/test/java/com/example/poswarnus/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `MenuItem.isFeatured` (Task 2).
- Produces: `DashboardUiState(... val featuredItems: List<MenuItem> = emptyList())`, computed as `if (query.isBlank()) items.filter { it.isFeatured && it.isAvailable } else emptyList()` over the **unfiltered** repository list (independent of `selectedCategoryId`).

- [ ] **Step 1: Write failing tests** (use the existing fake repository/test style): `featured items appear for featured and available menu items`, `unavailable featured item is excluded`, `no featured items gives an empty list`, `featured row is hidden while the search query is not blank`, `featured row is still shown when a category is selected` (featured item from another category included), `featured items keep the source list order`, `featured items do not change visibleItems`, `blank-only query still shows the row`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.dashboard.DashboardViewModelTest"` — Expected: FAIL (`featuredItems` undefined).
- [ ] **Step 3: Implement** `featuredItems` inside the existing `combine`; leave `isOffline`, `loadError`, `isLoading`, `visibleItems` computations untouched.
- [ ] **Step 4: Run** the same command, then the full `./gradlew testDebugUnitTest` — Expected: PASS.
- [ ] **Step 5: Commit** `feat: derive featured menu items in DashboardViewModel`.

---

### Task 4: Menu Unggulan row UI

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardComponents.kt` (`MenuItemCard` gets a leading `modifier: Modifier = Modifier`)
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt` (featured item in the grid branch)

**Interfaces:**
- Consumes: `DashboardUiState.featuredItems` (Task 3), `cartState.quantityByMenuItem`, `cartViewModel.onMenuItemClicked`.
- Produces: `fun MenuItemCard(item: MenuItem, modifier: Modifier = Modifier, inCartQuantity: Int = 0, onClick: () -> Unit)` — the card's internal chain starts from `modifier.fillMaxWidth()`; every existing call site keeps compiling (named/ordered args as today).

- [ ] **Step 1: Implement the card parameter** and update call sites only if the parameter order forces it.
- [ ] **Step 2: Implement the row** per spec 3.4: in the grid branch only, when `state.featuredItems.isNotEmpty()` add `item(key = "featured", span = { GridItemSpan(maxLineSpan) })` containing a `Column`: label "Menu Unggulan" (`labelLarge`, `TextSecondary`, bottom padding 8dp) and a `LazyRow` (spacing 12dp, `key = { it.id }`) of `MenuItemCard(modifier = Modifier.width(176.dp), ...)` with the same `inCartQuantity` and click handling as grid cards. Grid item keys remain `item.id`.
- [ ] **Step 3: Verify** `./gradlew compileDebugKotlin` then `./gradlew testDebugUnitTest assembleDebug` — Expected: BUILD SUCCESSFUL, tests PASS.
- [ ] **Step 4: Commit** `feat: add Menu Unggulan row to the catalog`.

---

### Task 5: Login messages in Indonesian

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/data/auth/AuthRepository.kt` (`AuthRepositoryImpl.mapLoginError`; keep the existing branch order)
- Modify: `app/src/main/java/com/example/poswarnus/ui/login/LoginViewModel.kt` (blank-field message)
- Test: modify `app/src/test/java/com/example/poswarnus/data/auth/AuthRepositoryTest.kt`, `.../ui/login/LoginViewModelTest.kt`, `app/src/androidTest/java/com/example/poswarnus/ui/login/LoginScreenTest.kt`

**Interfaces:**
- Produces (exact strings): `Email atau kata sandi salah. Periksa lalu coba lagi.` (`AuthRestException`); `Gagal masuk. Silakan coba lagi.` (other `RestException` and `else`); `Tidak bisa terhubung. Periksa koneksi lalu coba lagi.` (`HttpRequestException`, `HttpRequestTimeoutException`, `IOException`); `Email dan kata sandi wajib diisi.` (blank fields).

- [ ] **Step 1: Update the existing assertions first** to the new strings in `AuthRepositoryTest` (3), `LoginViewModelTest` (1) and `LoginScreenTest` (1); add `login failure never exposes the raw exception message` in `AuthRepositoryTest` (exception message contains a URL; assert the result text equals one of the three mapped strings).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.auth.*" --tests "com.example.poswarnus.ui.login.*"` — Expected: FAIL (old English strings).
- [ ] **Step 3: Implement** the new strings in `mapLoginError` and `LoginViewModel`. Then `grep` the repo for the old English strings (`Invalid email or password`, `Login failed`, `Can't reach server`, `are required`) — Expected: no hits left in `src`.
- [ ] **Step 4: Run** the same command and `./gradlew compileDebugAndroidTestKotlin` — Expected: PASS / BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `fix: Indonesian login error messages`.

---

### Task 6: Launcher icon, system splash and splash status-bar icons

**Files:**
- Modify: `app/src/main/res/values/colors.xml` (add `<color name="brand_accent">#C2410C</color>`)
- Modify: `app/src/main/res/drawable/ic_launcher_background.xml` (solid `#C2410C`, 108x108)
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml` (white `BrandMark` glyph, geometry exactly as spec 4.2: ring r 20.736 stroke 5.376 centred 54,54; left stroke x 45.70, y 35.34 to 56.07; right stroke x 62.30, y 35.34 to 72.66, stroke width 4.57, round caps; `strokeColor="#FFFFFF"`, `fillColor="#00000000"`)
- Delete: the ten `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp` and `ic_launcher_round.webp`
- Create: `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml` (`<layer-list>` of background + foreground vector, for API 24-25)
- Modify: `app/src/main/res/values/themes.xml` (`android:windowBackground` = `@color/brand_accent`)
- Create: `app/src/main/res/values-v31/themes.xml` (same style name `Theme.POSWarNus`, parent `android:Theme.Material.Light.NoActionBar`, `android:windowBackground`, `android:windowSplashScreenBackground` = `@color/brand_accent`, `android:windowSplashScreenAnimatedIcon` = `@drawable/ic_launcher_foreground`)
- Modify: `app/src/main/java/com/example/poswarnus/ui/splash/SplashScreen.kt` (status-bar icon effect)

**Interfaces:**
- Consumes: existing `mipmap-anydpi-v26/ic_launcher*.xml` (unchanged).
- Produces: `SplashScreen` gains a `DisposableEffect(Unit)` that, through `WindowCompat.getInsetsController(window, view)` (window from the `Context` of `LocalView.current` unwrapped to `Activity`), sets `isAppearanceLightStatusBars = false` and `isAppearanceLightNavigationBars = false`, and in `onDispose` restores both to `true`. Public signature of `SplashScreen` is unchanged.

- [ ] **Step 1: Write the resources and layer-lists** as specified; remove the ten webp files with `git rm`.
- [ ] **Step 2: Implement the splash status-bar effect** (null-safe if the context has no `Activity`).
- [ ] **Step 3: Verify** `./gradlew assembleDebug` — Expected: BUILD SUCCESSFUL (resource merge succeeds; no missing `ic_launcher` reference in `AndroidManifest.xml`). Run `./gradlew lintDebug` and note any new resource errors; fix only errors caused by this task.
- [ ] **Step 4: Commit** `feat: brand launcher icon and system splash; light splash status bar`. API 24-25 rendering is **NOT verified** unless such an AVD exists (report it).

---

### Task 7: Responsive catalog header

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt` (`CatalogPane` header)

**Interfaces:**
- Consumes: existing `CatalogSearchField`, `BrandMark`, logout callback.
- Produces: header chosen by the catalog pane's own width: `BoxWithConstraints` inside `CatalogPane`; when `maxWidth < 600.dp` the header is two rows in the same white, bottom-bordered container (row 1: brand mark + outlet title/subtitle + "Keluar"; row 2: search field `fillMaxWidth`, no fixed 320dp); at >= 600dp it is unchanged. Extract a private `CatalogHeader(compact: Boolean, ...)` if it keeps `CatalogPane` readable.

- [ ] **Step 1: Implement** the width check (pane width, **not** window width) and the compact layout.
- [ ] **Step 2: Verify** `./gradlew compileDebugKotlin` then `./gradlew testDebugUnitTest assembleDebug` — Expected: BUILD SUCCESSFUL, tests PASS.
- [ ] **Step 3: Commit** `feat: compact two-row catalog header on narrow panes`.

---

### Task 8: Emulator verification and roadmap update

**Files:**
- Modify: `docs/roadmap.md` (Phase 3b status row + findings appended to the backlog)

**Interfaces:**
- Consumes: Tasks 1-7 on the branch. Tools: `adb` at `~/Library/Android/sdk/platform-tools/adb`, emulator at `~/Library/Android/sdk/emulator/emulator` (AVDs `Medium_Tablet`, `Pixel_Tablet`, `Pixel_10_Pro_XL`; `emulator-5554` is a 2560x1600 tablet that may already be running). Test cashier credentials: typed by the controller from the conversation, never stored.

- [ ] **Step 1: Build and install** `./gradlew assembleDebug`, then `adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk` on the tablet; start the phone AVD in the background (`emulator -avd Pixel_10_Pro_XL`), wait for `adb shell getprop sys.boot_completed` = 1, install there too.
- [ ] **Step 2: Run the checklist** on **tablet and phone** (screenshot with `adb exec-out screencap -p`, read each image; read back typed text before submitting):
  - Splash: orange, white status-bar icons, then login; system splash shows orange with the brand glyph (Android 12+) and the app drawer shows the new icon.
  - Login: error banners in Indonesian (blank submit; wrong password only if the owner allowed it); form stays above the keyboard.
  - Dashboard: header fits at phone width (two rows) and tablet (one row); catalog loads; **no featured row** while the live database has no `is_featured` column or no featured items (expected, confirms Review Focus 2).
  - Phase 3 on phone: bottom bar "N item · Rp..." and "Lihat Pesanan" sheet open, edit a row, and auto-close when the cart empties; dialog shown above the sheet.
  - Featured row visual (horizontal scroll, matches grid cards, hidden on search, kept on category) can only be checked end-to-end after the owner applies the SQL file and marks items: mark it **NOT verified** otherwise.
- [ ] **Step 3: Record results** — update the Phase 3b row in `docs/roadmap.md` (done / what is NOT verified / the pending owner action "apply `supabase/migrations/20261023100000_add_menu_items_is_featured.sql` and mark featured items") and append any non-trivial findings to its backlog.
- [ ] **Step 4: Final gate** `./gradlew testDebugUnitTest assembleDebug` — Expected: BUILD SUCCESSFUL, all tests PASS.
- [ ] **Step 5: Commit** `docs: record Phase 3b verification status`. Stop here: no merge, no push.

---

## Self-Review

- **Spec coverage:** SQL file and data layer (T2), schema export 2.json/3.json (T1, T2), ViewModel rules (T3), UI row and card modifier (T4), login copy (T5), icon/splash/status-bar (T6), responsive header (T7), emulator pass, phone check and roadmap (T8). Out-of-scope items (`featured_order`, admin UI, Phase 4+) are not planned. Spec 7 step 2 ("apply the SQL to live Supabase") is deliberately **not** a task: the owner has not consented, so the plan only writes the file.
- **Type consistency:** `isFeatured`, `MIGRATION_2_3`, `featuredItems`, `MenuItemCard(modifier, inCartQuantity, onClick)` and `CatalogHeader` use the same names wherever they are produced and consumed.
- **Proportion:** the plan holds signatures, test names, exact strings and geometry from the spec; the SQL and vector geometry stay in the spec and are copied verbatim by the tasks.
