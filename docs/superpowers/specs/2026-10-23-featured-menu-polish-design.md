# Phase 3b — Featured Menu + Polish of Phases 1–3 (Design)

Date: 2026-10-23 · Scope: [PRD 4.12](../../PRD-pos.md), [roadmap](../../roadmap.md) "Phase 3b". Builds on the merged Phase 3 code (`main`).

## 1. Intent and success criteria

**Intent.** A cashier sees, at a glance, the menu items the outlet wants to push. The admin marks items in Supabase (`menu_items.is_featured`); this app only reads the flag. Alongside it, close the rough edges found while testing Phases 1–3 on the emulator.

**Success criteria**
1. Setting `is_featured = true` on a menu item in Supabase makes it appear in a "Menu Unggulan" row at the top of the catalog on the next refresh; setting it back removes it.
2. The row never changes search, category filtering, the cart, or the normal grid. Featured items also remain in their category grid.
3. An app upgrade from DB v2 keeps the cached catalog and options (no re-download needed to see the old data).
4. Order of items in the row = order of the normal catalog list (decision A: no `featured_order` column).
5. Login errors are in Indonesian; launcher icon and system splash are on-brand; Room schema JSON is exported; the splash status-bar icons are readable; the catalog header fits a phone.

## 2. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | Featured list is **derived in `DashboardViewModel`** from the existing `repository.menuItems` flow (`isFeatured && isAvailable`). No extra DAO query. | One source of truth; refresh and filters can't drift apart. |
| D2 | Row order = order of the `menuItems` list. `MenuItemDao.observeAll()` has no `ORDER BY`, so this is Room's natural (insertion) order; it is **not changed** in this phase. | Choice A: no `featured_order`; the same order the grid already uses. |
| D3 | An unavailable featured item is **excluded** from the row (PRD allows "excluded or dimmed"). | Simpler; the grid still shows it dimmed with the "Habis" tag. |
| D4 | The row is a full-span first item of the catalog grid (scrolls with the grid), not a pinned bar. | Pinned would permanently eat ~200dp of vertical space. |
| D5 | If the selected category has no items, the existing empty state is shown and the row is **not** shown (degenerate case; the row lives inside the grid branch). | Avoids a second layout path for an edge case. |
| D6 | No `core-splashscreen` dependency; system splash is configured with theme attributes. | Roadmap/PRD: no new dependencies unless needed. |
| D7 | Compact catalog header is decided by the **catalog pane's own width** (< 600dp), not the window width. | On a 840–1040dp tablet window the catalog pane is only 400–600dp wide. |

## 3. Part 1 — Featured Menu

### 3.1 Supabase

New file `supabase/migrations/20261023100000_add_menu_items_is_featured.sql` (same record-of-change style as the Phase 3 file; applied manually through the Supabase MCP **only with the project owner's consent** at execution time; not idempotent):

```sql
-- Phase 3b: admin-curated featured flag (read-only for the app).
alter table public.menu_items
  add column is_featured boolean not null default false;

-- Optional example for marking items (NOT executed by the migration):
-- update public.menu_items set is_featured = true
--   where name in ('Nasi Goreng Spesial', 'Ayam Katsu');
```

RLS is unchanged (existing `menu_items` select policies cover the new column). Existing rows become `false`.

### 3.2 Data layer

- `MenuItemDto`: `@SerialName("is_featured") val isFeatured: Boolean = false`. The default makes a new app work against a Supabase that has not yet received the migration (the key is missing → `false`).
- `MenuItemEntity`: `@ColumnInfo(defaultValue = "0") val isFeatured: Boolean = false`. **The `defaultValue` must be exactly `"0"`**, identical to the DDL in `MIGRATION_2_3`, otherwise Room's schema validation fails when opening an upgraded database.
- `MenuItem` (domain, `CatalogRepository.kt`): `val isFeatured: Boolean = false` (default keeps existing callers/tests compiling). `CatalogRepositoryImpl` maps DTO → entity → domain with the new field.
- `AppDatabase`: `version = 3`; new

```kotlin
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE menu_items ADD COLUMN isFeatured INTEGER NOT NULL DEFAULT 0")
    }
}
```

- `DatabaseModule`: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`. No destructive fallback.

### 3.3 ViewModel

`DashboardUiState` gains `val featuredItems: List<MenuItem> = emptyList()`. Inside the existing `combine`, with `items` = the unfiltered list from the repository:

```kotlin
featuredItems = if (query.isBlank()) items.filter { it.isFeatured && it.isAvailable } else emptyList()
```

- Independent of `selectedCategoryId`.
- Hidden whenever `searchQuery` is not blank.
- Does not affect `visibleItems`, `isOffline`, `loadError`, `isLoading`.

### 3.4 UI

- `MenuItemCard` gets a leading `modifier: Modifier = Modifier` parameter; the card's own modifier chain becomes `modifier.fillMaxWidth()...` so a caller can fix the width.
- In `CatalogPane`'s grid branch, before the items: when `state.featuredItems` is not empty, `item(key = "featured", span = { GridItemSpan(maxLineSpan) })` containing a `Column`: label "Menu Unggulan" (`labelLarge`, `TextSecondary`, bottom padding 8dp) and a `LazyRow` (horizontal spacing 12dp, `key = { it.id }`) of `MenuItemCard(modifier = Modifier.width(176.dp), …)`.
- Same behavior as grid cards: `inCartQuantity = cartState.quantityByMenuItem[item.id] ?: 0` (badge + accent border), click → `cartViewModel.onMenuItemClicked(item)`.
- Grid item keys are unchanged (`item.id`); featured cards live in a nested `LazyRow`, so there is no key clash with the same item in the grid.

### 3.5 Edge cases

| Case | Behavior |
|---|---|
| No featured items | Row absent (no label, no placeholder). |
| All featured items unavailable | Row absent (list empty after the availability filter). |
| Search active | Row hidden; results come from the grid only. |
| Category selected | Row stays (featured items from all categories). |
| Selected category has no items | Empty state only, row not shown (D5). |
| Refresh fails offline | Cached `isFeatured` values are used; no special handling. |
| App updated before the Supabase migration ran | DTO default → no featured items; nothing breaks. |
| Admin un-features an item | Gone from the row after the next successful refresh (full-replace sync). |

## 4. Part 2 — Polish

### 4.1 Login messages in Indonesian

`AuthRepositoryImpl.mapLoginError` keeps its type-based mapping (never shows raw Supabase text, which embeds URL/headers) with new copy:

| Condition (existing branch order kept) | Old text | New text |
|---|---|---|
| `AuthRestException` | Invalid email or password | `Email atau kata sandi salah. Periksa lalu coba lagi.` |
| other `RestException`, and `else` | Login failed, please try again | `Gagal masuk. Silakan coba lagi.` |
| `HttpRequestException`, `HttpRequestTimeoutException`, `IOException` | Can't reach server, check your connection | `Tidak bisa terhubung. Periksa koneksi lalu coba lagi.` |

`LoginViewModel` blank-field message: `Email and password are required` → `Email dan kata sandi wajib diisi.`

Tests updated to the new strings: `AuthRepositoryTest` (3 assertions), `LoginViewModelTest` (1), `app/src/androidTest/.../LoginScreenTest` (1).

### 4.2 Launcher icon and system splash (no new dependency)

- Palette: add `<color name="brand_accent">#C2410C</color>` to `values/colors.xml`.
- `drawable/ic_launcher_background.xml`: replace the default green grid with a single solid `#C2410C` 108×108 rect.
- `drawable/ic_launcher_foreground.xml`: replace the Android robot with a white glyph imitating `BrandMark` (circle ring plus two vertical strokes), viewport 108×108, glyph kept inside the 66dp safe zone. Geometry (BrandMark proportions scaled ×2.4, centred at 54,54): ring r = 20.736, stroke 5.376; left stroke x = 45.70 from y 35.34 to 56.07; right stroke x = 62.30 from y 35.34 to 72.66; stroke width 4.57, round caps. Paths use `strokeColor="#FFFFFF"`, `fillColor="#00000000"`.
- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` already reference these two drawables (background/foreground/monochrome) — unchanged.
- Pre-API 26 (minSdk 24): delete the ten `mipmap-*/ic_launcher*.webp` (they are the default robot) and add `mipmap-anydpi/ic_launcher.xml` + `ic_launcher_round.xml` as a `<layer-list>` of the orange background and the foreground vector. Risk: vector `mipmap` icons on API 24–25 launchers; if no API 24/25 AVD is available this is reported as **not verified**.
- System splash:
  - `values/themes.xml`: `Theme.POSWarNus` adds `<item name="android:windowBackground">@color/brand_accent</item>` (pre-Android 12 cold start shows orange, then Compose Splash is also orange — seamless).
  - New `values-v31/themes.xml`: same style name, parent `android:Theme.Material.Light.NoActionBar`, adding `android:windowSplashScreenBackground` = `@color/brand_accent` and `android:windowSplashScreenAnimatedIcon` = `@drawable/ic_launcher_foreground`.
  - Manifest unchanged (it already uses `@style/Theme.POSWarNus`).

### 4.3 Room schema export

- `@Database(…, version = 3, exportSchema = true)`.
- `app/build.gradle.kts`: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.
- Commit the generated JSON under `app/schemas/com.example.poswarnus.data.local.AppDatabase/`. **v1 does not exist** (the app was built without export, and v1 can't be regenerated retroactively); the existing hand-built v1 file in `AppDatabaseMigrationTest` is the v1 reference. **`2.json` and `3.json` are produced** by building once at v2 (before the version bump, see §7) and once at v3.
- No `MigrationTestHelper` / new test dependency is introduced.

### 4.4 Splash status-bar icons

`SplashScreen` gets a `DisposableEffect` that, via `WindowCompat.getInsetsController(activity.window, view)`, sets `isAppearanceLightStatusBars = false` and `isAppearanceLightNavigationBars = false` (white icons on orange) and, in `onDispose`, restores both to `true` (the app-wide dark icons set in `MainActivity`). `activity` = the `Context` of `LocalView.current` unwrapped to `Activity`.

### 4.5 Responsive catalog header and phone check

- `CatalogPane` measures its own width (`BoxWithConstraints`). When `maxWidth < 600.dp` the header becomes two rows inside the same white, bottom-bordered container: row 1 = brand mark + outlet title/subtitle + "Keluar" button; row 2 = the search field at full width (`fillMaxWidth`, no fixed 320dp). At ≥ 600dp the header is unchanged.
- Verification on the `Pixel_10_Pro_XL` AVD (`~/Library/Android/sdk/emulator/emulator -avd Pixel_10_Pro_XL`, `adb` in `~/Library/Android/sdk/platform-tools`), logged in with the project owner's test cashier account (**credentials are never written to the repo or this spec**). Checklist: header fits without clipping; Menu Unggulan row scrolls horizontally and matches grid cards; tap opens the dialog; bottom bar "N item · Rp…" and "Lihat Pesanan" sheet from Phase 3 (open, edit a row, empty cart auto-closes); keyboard does not hide the Login form; splash and icons. Findings that are not trivial are recorded in `docs/roadmap.md` backlog rather than fixed silently.

## 5. Testing strategy

JVM-only (no emulator in CI), plus a manual emulator pass.

- `DashboardViewModelTest`: featured appears for `isFeatured && isAvailable`; unavailable excluded; empty with no featured; hidden when search is non-blank; shown while a category is selected; order equals source list order; `visibleItems` unaffected.
- `CatalogRepositoryTest`: `is_featured = true/false` maps through to the domain; DTO decoded without the field yields `false` (`Json` decode test next to the DTO).
- Room: `MenuItemDao` round-trip of `isFeatured`; `AppDatabaseMigrationTest` extended — hand-built v1 file opened with `MIGRATION_1_2` + `MIGRATION_2_3` (old rows survive, `isFeatured == false`, Room schema validation passes), and a hand-built v2 file opened with `MIGRATION_2_3` only.
- Auth: updated strings (§4.1).
- Not unit-testable (verified by reading + emulator checklist): icons, splash, header layout, status-bar icons, featured row visuals.
- Gate: `./gradlew testDebugUnitTest assembleDebug` green; `app/schemas` contains `2.json` and `3.json`.

## 6. Out of scope

`featured_order` / any admin-controlled ordering; admin UI for setting the flag; final brand design of icon/splash (this is a faithful stand-in of the existing `BrandMark`); dimmed-but-visible unavailable featured items; image loading (`image_url` is null in the data); Phase 4+ (tax, payment, transactions, printer, training mode, suggestions); pre-API 26 icon polish beyond what §4.2 states.

## 7. Suggested order of work

1. **Room schema export first**: add `exportSchema = true` + `room.schemaLocation` while the DB is still v2, build, commit `2.json`.
2. **Supabase**: write the migration SQL file; apply it to the live project (with owner consent) and verify `is_featured` exists.
3. **Featured Menu**: DTO/entity/domain → `MIGRATION_2_3` + version 3 (build → commit `3.json`) → ViewModel → UI → tests.
4. **Polish**: Login messages + tests, icon/splash resources, splash status-bar effect, responsive header.
5. **Phone check last** on the Pixel AVD; update `docs/roadmap.md` status for 3b.
