# POS Navigation Rail (Phase 7b) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move History, Settings and Logout out of the Dashboard header into a left navigation rail on tablets (>= 840dp window) and a drawer on phones, with a logout confirmation.

**Architecture:** A pure model (`MainDestination`, `showNavRail`) and a window-width helper drive one rail placed once in `PosNavGraph` beside the `NavHost`; on narrow windows the Dashboard hosts a menu button that opens a `ModalNavigationDrawer` wrapped around the `NavHost`. Switching destinations keeps `Dashboard` at the bottom of the back stack so its cart and payment ViewModels survive. `LogoutViewModel` moves to the navigation level and gains a confirmation state.

**Tech Stack:** Kotlin, Compose Material3 (`ModalNavigationDrawer`), Navigation Compose, Hilt, JUnit/coroutines-test. No new dependencies.

**Spec:** [docs/superpowers/specs/2026-10-24-navigation-rail-design.md](../specs/2026-10-24-navigation-rail-design.md) (section numbers below refer to it). Format precedent: `docs/superpowers/plans/2026-10-24-pos-transaction-history.md`.

## Global Constraints

- Package `com.example.poswarnus`; minSdk 24; Kotlin; Compose Material3; Hilt; single module `:app`. No new dependencies (in particular no `navigation-testing`).
- **No Room, sync, payment, Supabase or SQL change; never execute SQL on the live project; no push; no merge to `main`.** Work in a git worktree on branch `feature/phase7b-navigation-rail` (the harness may name it `worktree-...`); copy `local.properties` into it. Execution stops at a reviewed branch.
- Shell in the worktree: plain separate commands (no `&&`, `;`, subshells, or `cd` chains; the shell guard refuses compound commands). `git add` only the files a task lists. Commit messages end with the line `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Wide window = `LocalConfiguration.current.screenWidthDp >= 840` (`NavRailMinWidthDp`), used by Dashboard, History, Settings and the rail. The header's own 600dp compact rule inside the catalog pane stays pane-based.
- Exact copy: rail/drawer labels `Kasir`, `Riwayat`, `Pengaturan`, `Keluar`; menu button `contentDescription` `Menu`; logout dialog title `Keluar?`, body `Pesanan yang belum dibayar akan dihapus.`, buttons `Keluar` and `Batal`.
- Every rail/drawer item and the menu button: target >= 48dp, `contentDescription`/label present, selected item exposes `selected` semantics.
- Navigation rules (spec 4.3): `KASIR` = `popBackStack(DASHBOARD, inclusive = false)`; `RIWAYAT`/`PENGATURAN` when not already active = `navigate(route) { popUpTo(DASHBOARD) { inclusive = false }; launchSingleTop = true }`; the active destination does nothing; the drawer closes after any choice. Logout confirmation then `navigate(LOGIN) { popUpTo(DASHBOARD) { inclusive = true } }`.
- The training banner stays inside each screen (rail runs full height beside it). Dialogs and the payment dialog cover the rail as they cover everything.
- Reuse existing glyphs `HistoryGlyph`, `SettingsGlyph`, `BrandMark`; add `StorefrontGlyph` and `LogoutGlyph` to `ui/components/Glyphs.kt` drawn like the others.
- Verification gate for code tasks: `./gradlew testDebugUnitTest assembleDebug` green. `./gradlew lint` may show only the 2 known errors in androidTest `LoginScreenTest`. Anything not verified is reported as NOT verified.
- Test-cashier credentials never go into any repo file or commit message; the controller types them from the conversation. `adb`/`emulator` live at `~/Library/Android/sdk/platform-tools/adb` and `~/Library/Android/sdk/emulator/emulator`; target `emulator-5554` with `-s` and install with `adb -s emulator-5554 install -r` (never `installDebug`; a physical tablet may be attached). The emulator may have been re-created (empty database, stale clock): log in again if needed.

## Review Focus

1. **Back stack after switching:** Riwayat -> Pengaturan -> Kasir leaves `[dashboard]`; Back from Riwayat/Pengaturan returns to Dashboard; Back on Dashboard exits; the cart is intact throughout (emulator, Task 5).
2. **Window 840-911dp:** rail shown and Dashboard uses the tablet layout in the same window (rule tests in Task 1; emulator in Task 5).
3. **Logout:** only after confirming; from every screen it clears the stack, the cart and training mode; a second tap while confirming is harmless (Task 2 tests; emulator in Task 5).
4. **Drawer:** reachable only from Dashboard on phones, closes after a choice, no edge-swipe on History/Settings (Task 4).
5. **Layout:** no content under the rail, no double inset, banner beside the rail, Dashboard header uncluttered and the brand not ellipsized on tablets (Tasks 3-5).

---

### Task 1: Navigation model and rules

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/navigation/MainNavigation.kt`
- Test: `app/src/test/java/com/example/poswarnus/ui/navigation/MainNavigationTest.kt`

**Interfaces:**
- Consumes: `PosDestinations` (`DASHBOARD`, `HISTORY`, `SETTINGS`, `LOGIN`, `SPLASH`) from `PosNavGraph.kt`.
- Produces: `enum class MainDestination(val route: String, val label: String) { KASIR(PosDestinations.DASHBOARD, "Kasir"), RIWAYAT(PosDestinations.HISTORY, "Riwayat"), PENGATURAN(PosDestinations.SETTINGS, "Pengaturan") }` with `companion object { fun fromRoute(route: String?): MainDestination? }`; `const val NavRailMinWidthDp: Int = 840`; `fun showNavRail(windowWidthDp: Int, route: String?): Boolean`; `@Composable fun isWideWindow(): Boolean` (= `LocalConfiguration.current.screenWidthDp >= NavRailMinWidthDp`).

- [ ] **Step 1: Write the failing tests** in `MainNavigationTest`: `fromRoute maps the three main routes` (dashboard, history, settings); `fromRoute is null for login, splash, null and unknown routes`; `showNavRail is true on a tablet for each main route`; `showNavRail flips exactly at 840dp` (839 false, 840 true); `showNavRail is false for login and splash even on a tablet`; `showNavRail is false for a null route`.
- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*MainNavigationTest"`
Expected: compile failure (`MainDestination`, `showNavRail` do not exist).

- [ ] **Step 3: Implement** the enum, constant, `showNavRail` and `isWideWindow` exactly as listed.
- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, whole suite green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/navigation/MainNavigation.kt app/src/test/java/com/example/poswarnus/ui/navigation/MainNavigationTest.kt
git commit -m "feat: add the main navigation model and rail visibility rule"
```

### Task 2: LogoutViewModel with a confirmation state

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/navigation/LogoutViewModel.kt` (moved from `ui/dashboard/DashboardScreen.kt`, package `com.example.poswarnus.ui.navigation`)
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt` (delete the class and its now-unused imports; the screen still compiles because Task 4 removes its use of the ViewModel, so in this task keep `DashboardScreen` compiling by importing the moved class)
- Test: move `app/src/test/java/com/example/poswarnus/ui/dashboard/LogoutViewModelTest.kt` to `app/src/test/java/com/example/poswarnus/ui/navigation/LogoutViewModelTest.kt` (package and imports updated; existing tests unchanged)

**Interfaces:**
- Consumes: `AuthRepository`, `CartStore`, `TrainingModeStore` (constructor unchanged: `LogoutViewModel(authRepository, cartStore, trainingModeStore)`).
- Produces: in `LogoutViewModel`: `val confirming: StateFlow<Boolean>` (initially `false`), `fun requestLogout()`, `fun cancelLogout()`, and the existing `fun logout(onLoggedOut: () -> Unit)` which also sets `confirming` to `false`.

- [ ] **Step 1: Write the failing tests** (added to the moved test class): `requestLogout raises confirming without signing out or clearing the cart`; `cancelLogout lowers confirming and leaves the cart and training mode untouched`; `logout lowers confirming`; `requestLogout twice keeps a single confirming state`.
- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*LogoutViewModelTest"`
Expected: compile failure (`confirming`, `requestLogout`, `cancelLogout` do not exist).

- [ ] **Step 3: Move and extend** the class (a private `MutableStateFlow(false)` behind `asStateFlow()`); keep the existing logout order (sign out, clear cart, reset training, then call `onLoggedOut`).
- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/navigation/LogoutViewModel.kt app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt app/src/test/java/com/example/poswarnus/ui/navigation/LogoutViewModelTest.kt app/src/test/java/com/example/poswarnus/ui/dashboard/LogoutViewModelTest.kt
git commit -m "feat: move logout to the navigation level with a confirmation state"
```

### Task 3: Rail, drawer and logout dialog components

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/navigation/MainNavComponents.kt`
- Modify: `app/src/main/java/com/example/poswarnus/ui/components/Glyphs.kt` (add `StorefrontGlyph(tint, modifier)` and `LogoutGlyph(tint, modifier)`)

**Interfaces:**
- Consumes: `MainDestination` (Task 1), `BrandMark`, `HistoryGlyph`, `SettingsGlyph`, theme tokens.
- Produces: `@Composable fun MainNavRail(current: MainDestination?, onSelect: (MainDestination) -> Unit, onLogout: () -> Unit, modifier: Modifier = Modifier)`; `@Composable fun MainNavDrawerContent(current: MainDestination?, onSelect: (MainDestination) -> Unit, onLogout: () -> Unit, modifier: Modifier = Modifier)`; `@Composable fun LogoutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit)`.

- [ ] **Step 1: Implement the components** per spec 4.2: rail 72dp wide, white, divider on the right, `BrandMark` 32dp at the top, then three items (icon 24dp above a 12sp label, >= 48dp target, active item tinted with the accent tint and accent-dark icon/label, inactive grey, `selected` semantics, `contentDescription` = label), a flexible spacer, then `Keluar` at the bottom (`LogoutGlyph`); the rail pads itself with `WindowInsets.safeDrawing.only(Start + Vertical)`. Drawer content uses the same items as rows (icon + label, height >= 48dp) plus `Keluar`. `LogoutConfirmDialog` is an `AlertDialog` with the exact copy from Global Constraints (dismiss = `Batal`). Glyphs: a storefront (awning + door) and a logout (door with an arrow leaving), stroked like `SettingsGlyph`.
- [ ] **Step 2: Run to verify**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/navigation/MainNavComponents.kt app/src/main/java/com/example/poswarnus/ui/components/Glyphs.kt
git commit -m "feat: add the navigation rail, drawer content and logout dialog"
```

### Task 4: Wire the rail and drawer and tidy the screens

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/ui/navigation/PosNavGraph.kt`, `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt`, `app/src/main/java/com/example/poswarnus/ui/history/HistoryScreen.kt`, `app/src/main/java/com/example/poswarnus/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: Tasks 1-3 (`MainDestination`, `showNavRail`, `isWideWindow`, `LogoutViewModel.confirming/requestLogout/cancelLogout/logout`, `MainNavRail`, `MainNavDrawerContent`, `LogoutConfirmDialog`).
- Produces: `DashboardScreen(onOpenMenu: () -> Unit, ...)` (parameters `onLoggedOut`, `onOpenSettings`, `onOpenHistory` and the `logoutViewModel` default removed); `CatalogPane`/`CatalogHeader` without `onLogout`, `onOpenSettings`, `onOpenHistory`; `HistoryScreen(onBack)` and `SettingsScreen(onBack)` keep their signatures.

- [ ] **Step 1: `PosNavGraph`.** Hoist `LogoutViewModel` (`hiltViewModel()` at graph level) and a `DrawerState`. Compute `currentDestination = MainDestination.fromRoute(route)` and `showRail = showNavRail(screenWidthDp, route)`. Layout per spec 4.3: `Row { MainNavRail(...); NavHost(Modifier.weight(1f)) }` when `showRail`; otherwise, on a main route in a narrow window, wrap `NavHost` in `ModalNavigationDrawer` (`gesturesEnabled = route == DASHBOARD`, drawer content `MainNavDrawerContent`); no wrapper on Login and Splash. The content next to the rail consumes the start and vertical insets the rail already used (`consumeWindowInsets`) so screens do not pad twice. Add `NavHostController.navigateToMain(destination, current)` with the exact rules from Global Constraints, and a logout flow: rail/drawer `Keluar` calls `requestLogout()`; when `confirming` is true show `LogoutConfirmDialog`, whose confirm calls `logout { navigate(LOGIN) { popUpTo(DASHBOARD) { inclusive = true } } }` and whose dismiss calls `cancelLogout()`. The Dashboard composable passes `onOpenMenu = { scope.launch { drawerState.open() } }`.
- [ ] **Step 2: `DashboardScreen`.** `isTablet` from `isWideWindow()` (not the pane width). `CatalogHeader` keeps brand (`Modifier.weight(1f)`), sync pill and search only; on `!isWideWindow()` it also shows a 48dp menu button (`contentDescription` `Menu`, a hamburger glyph drawn inline or reused if one exists) before the brand that calls `onOpenMenu`. Remove the three buttons, their parameters and any now-unused imports and glyph usage.
- [ ] **Step 3: `HistoryScreen` and `SettingsScreen`.** Use `isWideWindow()` for the wide/narrow decision in History (replacing the `BoxWithConstraints` threshold); show the top-bar back arrow only when `!isWideWindow()` (the system Back and the narrow detail `BackHandler` behave as before).
- [ ] **Step 4: Run to verify**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/navigation/PosNavGraph.kt app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt app/src/main/java/com/example/poswarnus/ui/history/HistoryScreen.kt app/src/main/java/com/example/poswarnus/ui/settings/SettingsScreen.kt
git commit -m "feat: navigate with a rail on tablets and a drawer on phones"
```

### Task 5: Emulator verification and roadmap (controller)

**Files:**
- Modify: `docs/roadmap.md` (Phase 7b status)

**Interfaces:** none.

- [ ] **Step 1: Full gate.** `./gradlew testDebugUnitTest assembleDebug lint`: tests and build green; lint shows only the 2 known `LoginScreenTest` errors.
- [ ] **Step 2: Install on the tablet emulator** (`adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`), log in if needed, and verify spec section 6 items 1-6 and 8: rail on the three screens with the right active item and absent on Login; Dashboard header uncluttered and the brand not ellipsized; with a filled cart switch Riwayat -> Pengaturan -> Kasir and confirm the cart is intact, Back from Riwayat/Pengaturan returns to Dashboard and Back on Dashboard exits; `Keluar` shows the dialog, `Batal` does nothing, `Keluar` returns to Login with an empty cart from all three screens; the back arrow is gone while the rail shows; training banner beside the rail and a training sale absent from the history; `wm size 880x1600` with a density that gives a window width of about 880dp (e.g. `wm density 320`): rail shown and Dashboard on the tablet layout (reset with `wm size reset` and `wm density reset`).
- [ ] **Step 3: Phone size** (`wm size 1080x2400`, reset afterwards): the menu button opens the drawer with four items, choosing one navigates and closes it, History and Settings keep the back arrow and no edge swipe opens the drawer there, `Keluar` from the drawer confirms, the Dashboard header fits.
- [ ] **Step 4: Update `docs/roadmap.md`:** a "Phase 7b status" block (verified and NOT verified lists, deferred minors) and the table row. Commit `docs: record Phase 7b status`.
