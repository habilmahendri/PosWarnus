# POS Settings and Training Mode (Phase 6a) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Settings screen with a "Mode Pelatihan" toggle; while it is on, payments complete exactly as normal but write nothing (no Room row, no sync, no Supabase), and a high-contrast banner is visible from Dashboard through Payment Success.

**Architecture:** A singleton in-memory `TrainingModeStore` holds the flag (so a cold start is automatically off) and clears the cart when toggled. `PaymentViewModel` freezes the flag when the payment screen opens and skips `TransactionRepository.save` entirely when it is set, so training can never reach the repository, DAO or sync queue. A new `settings` route, a gear icon in the catalog header and a shared `TrainingBanner` composable make up the UI.

**Tech Stack:** Kotlin, Hilt, Compose Material3, Navigation Compose, JUnit/coroutines-test. No Room, WorkManager or Supabase changes.

**Spec:** [docs/superpowers/specs/2026-10-24-settings-training-mode-design.md](../specs/2026-10-24-settings-training-mode-design.md) (section numbers below refer to it). Upstream: [PRD 4.13](../../PRD-pos.md), [roadmap](../../roadmap.md) "Phase 6". Format precedent: `docs/superpowers/plans/2026-10-24-pos-offline-sync.md`.

## Global Constraints

- Package `com.example.poswarnus`; minSdk 24; Kotlin; Compose Material3; Hilt; single module `:app`. No new dependencies.
- **Never execute SQL against the live Supabase project, no Room schema change, no push, no merge to `main`.** Work in a git worktree on branch `feature/phase6a-training-mode`; copy `local.properties` into it. Execution stops at a reviewed branch.
- Shell in the worktree: plain separate commands (no `&&`, `;`, subshells, or `cd` chains; use `git -C <worktree>` and `./gradlew` from the worktree root). `git add` only the files a task lists. Commit messages end with the line `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- The training flag lives only in memory; it must never be written to disk, Room or DataStore.
- Enabling training **clears the cart**; disabling also clears it; logout and login reset the flag (spec 3, 4.1).
- Banner text (exact): `MODE PELATIHAN - transaksi tidak disimpan`. Dialog texts: title `Nyalakan Mode Pelatihan?`, buttons `Nyalakan` / `Batal`; settings row label `Mode Pelatihan`; gear `contentDescription` `Pengaturan`.
- `TransactionRepositoryImpl` is not modified; `isTraining` stays `false` there.
- Header rule (existing): the brand title keeps `Modifier.weight(1f)` and ellipsizes first; every new header control is at least 48dp touch target.
- Verification gate for code tasks: `./gradlew testDebugUnitTest assembleDebug` green. `./gradlew lint` may show only the 2 known errors in androidTest `LoginScreenTest`. Anything not verified is reported as NOT verified.
- Test-cashier credentials never go into any repo file or commit message; the controller types them from the conversation. `adb`/`emulator` live at `~/Library/Android/sdk/platform-tools/adb` and `~/Library/Android/sdk/emulator/emulator` (not on PATH).

## Review Focus

1. **Flag flips between open and confirm:** a sale opened in normal mode is saved even if training is enabled before confirm, and a sale opened in training is never saved even if training is disabled before confirm (Task 2).
2. **Training sale then a normal sale:** the retry/UUID bookkeeping and `finish()` leave the next normal sale persisted and synced as usual (Task 2).
3. **Logout, re-login and process death** all end with the flag off and an empty cart (Tasks 1, 5 verification).
4. **Banner contrast and placement:** readable at 4.5:1, visible on Dashboard, Settings, Paying and Success, never inside a normal sale (Task 4).
5. **Settings confirm dialog:** cancelling leaves flag and cart untouched; only "Nyalakan" changes them (Task 3).

---

### Task 1: TrainingModeStore, logout and login reset

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/training/TrainingModeStore.kt`
- Modify: `app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt` (`LogoutViewModel`), `app/src/main/java/com/example/poswarnus/ui/login/LoginViewModel.kt`
- Test: `app/src/test/java/com/example/poswarnus/data/training/TrainingModeStoreTest.kt`; update `ui/dashboard/LogoutViewModelTest.kt` and `ui/login/LoginViewModelTest.kt` for the new constructor parameter

**Interfaces:**
- Consumes: `CartStore.clear()`, `CartStore.state`.
- Produces: `@Singleton class TrainingModeStore @Inject constructor(private val cartStore: CartStore)` with `val enabled: StateFlow<Boolean>` (initially `false`), `fun enable()` (clear cart, then `true`), `fun disable()` (clear cart, then `false`), `fun reset()` (`false`, cart untouched). `LogoutViewModel(authRepository, cartStore, trainingModeStore)` and `LoginViewModel(authRepository, cartStore, trainingModeStore)`: `trainingModeStore` is the **last** parameter in both.

- [ ] **Step 1: Write the failing tests** in `TrainingModeStoreTest`: `starts disabled`; `enable clears the cart and turns training on` (cart with one line first); `disable clears the cart and turns training off`; `reset turns training off and keeps the cart`. In `LogoutViewModelTest` add `logout turns training mode off` (enable first, assert `enabled.value == false` after logout). In `LoginViewModelTest` add `successful login turns training mode off`. Update every existing constructor call in both test files to pass a `TrainingModeStore(cartStore)`.
- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*TrainingModeStoreTest" --tests "*LogoutViewModelTest" --tests "*LoginViewModelTest"`
Expected: compile failure / FAIL (`TrainingModeStore` and the new parameter do not exist).

- [ ] **Step 3: Implement `TrainingModeStore`** with the signature above (a `MutableStateFlow(false)` behind `asStateFlow()`), and call `trainingModeStore.reset()` next to `cartStore.clear()` in `LogoutViewModel.logout` and in the successful-login branch of `LoginViewModel.onLoginClicked`. Hilt needs no module (constructor injection on a `@Singleton`).
- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, whole suite green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/training/TrainingModeStore.kt app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt app/src/main/java/com/example/poswarnus/ui/login/LoginViewModel.kt app/src/test/java/com/example/poswarnus/data/training/TrainingModeStoreTest.kt app/src/test/java/com/example/poswarnus/ui/dashboard/LogoutViewModelTest.kt app/src/test/java/com/example/poswarnus/ui/login/LoginViewModelTest.kt
git commit -m "feat: add in-memory training mode store reset on logout and login"
```

### Task 2: Payment writes nothing in training

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/ui/payment/PaymentViewModel.kt`
- Test: `app/src/test/java/com/example/poswarnus/ui/payment/PaymentViewModelTest.kt`

**Interfaces:**
- Consumes: `TrainingModeStore.enabled` (Task 1).
- Produces: `PaymentViewModel(cartStore, taxRepository, transactionRepository, authRepository, trainingMode: TrainingModeStore)` (new **last** parameter). `PaymentUiState.Paying` and `PaymentUiState.Success` each gain `val isTraining: Boolean = false` as the **last** property. `Frozen` gains `isTraining`, set from `trainingMode.enabled.value` in `open()`.

- [ ] **Step 1: Write the failing tests** (the existing `viewModel()` helper passes a `TrainingModeStore(cartStore)` held in a field so tests can toggle it; `fillCart()` is the existing helper):
  - `training sale reaches Success without saving`: enable training, open, "Uang pas", confirm, advance; assert `transactions.saved.isEmpty()`, state is `Success` with `isTraining == true`, correct `cash` and `change`.
  - `training Paying state is flagged`: enable training, open; `(state as Paying).isTraining == true`.
  - `enabling training after open does not stop the sale being saved` (Review Focus 1): open in normal mode, then `trainingStore.enable()` (this empties the cart; the frozen lines still sell), confirm; `saved.size == 1`, `Success.isTraining == false`.
  - `disabling training after open still does not save` : enable, open, `trainingStore.disable()`, confirm; `saved.isEmpty()`, `Success.isTraining == true`.
  - `normal sale after a training sale is saved` (Review Focus 2): training sale, finish, `trainingStore.disable()`, fill cart, open, pay, confirm; `saved.size == 1`.
  - Training confirm must not require a cashier id: `auth.userId = null`, training sale still reaches Success.
- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*PaymentViewModelTest"`
Expected: compile failure / FAIL (new constructor parameter and `isTraining` do not exist).

- [ ] **Step 3: Implement.** In `confirm()`: after the existing guard, if `sale.isTraining`, skip the cashier lookup and `transactionRepository.save` and treat the result as success; otherwise the current path. Thread `isTraining` through `paying(...)`, `Paying`, and `Success`. `dismiss()`/`finish()` unchanged.
- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, whole suite green (all pre-existing payment tests unchanged in behavior).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/payment/PaymentViewModel.kt app/src/test/java/com/example/poswarnus/ui/payment/PaymentViewModelTest.kt
git commit -m "feat: skip persistence and sync for training-mode payments"
```

### Task 3: Settings screen, route and header gear

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/settings/SettingsViewModel.kt`, `app/src/main/java/com/example/poswarnus/ui/settings/SettingsScreen.kt`
- Modify: `ui/components/Glyphs.kt` (add `SettingsGlyph(tint, modifier)`), `ui/navigation/PosNavGraph.kt` (`PosDestinations.SETTINGS = "settings"`), `ui/dashboard/DashboardScreen.kt` (`DashboardScreen` gets `onOpenSettings: () -> Unit`; `CatalogPane` and `CatalogHeader` pass it down)
- Test: `app/src/test/java/com/example/poswarnus/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `TrainingModeStore` (Task 1).
- Produces: `data class SettingsUiState(val trainingEnabled: Boolean, val confirmingEnable: Boolean)`; `@HiltViewModel class SettingsViewModel @Inject constructor(trainingMode: TrainingModeStore)` with `val uiState: StateFlow<SettingsUiState>`, `fun onTrainingToggle(checked: Boolean)` (checked: set `confirmingEnable = true`, change nothing else; unchecked: `disable()` immediately), `fun confirmEnable()` (`enable()`, `confirmingEnable = false`), `fun cancelEnable()` (`confirmingEnable = false`). `@Composable fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel())`.

- [ ] **Step 1: Write the failing tests** in `SettingsViewModelTest` (store built from a real `CartStore` with one line): `toggle on asks for confirmation without changing anything` (flag still off, cart still full, `confirmingEnable == true`); `cancel leaves flag and cart untouched` (Review Focus 5); `confirm enables training and clears the cart`; `toggle off disables immediately and clears the cart`; `uiState follows the store` (store enabled elsewhere shows `trainingEnabled == true`).
- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*SettingsViewModelTest"`
Expected: compile failure (`SettingsViewModel` does not exist).

- [ ] **Step 3: Implement the ViewModel** (combine the store's `enabled` with a private `MutableStateFlow` for `confirmingEnable`, `stateIn` `WhileSubscribed(5000)` like the other ViewModels).
- [ ] **Step 4: Implement the UI.** `SettingsScreen`: top bar with `ArrowBackGlyph` button (48dp, `contentDescription` "Kembali") and title "Pengaturan"; one row with label `Mode Pelatihan`, helper text (e.g. "Latihan tanpa menyimpan transaksi"), and a `Switch` bound to `trainingEnabled` and `onTrainingToggle`; when `confirmingEnable`, an `AlertDialog` with the fixed title/buttons whose body says the cart will be emptied and sales are not saved. `SettingsGlyph`: a simple gear drawn like the other glyphs. `PosNavGraph`: `composable(SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }` and Dashboard's `onOpenSettings = { navController.navigate(PosDestinations.SETTINGS) }`. `CatalogHeader`: an icon button (48dp, `SettingsGlyph`, `contentDescription` "Pengaturan") placed between the sync pill and the logout button in both compact and wide layouts, brand keeps `weight(1f)`.
- [ ] **Step 5: Run to verify**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/settings app/src/main/java/com/example/poswarnus/ui/components/Glyphs.kt app/src/main/java/com/example/poswarnus/ui/navigation/PosNavGraph.kt app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt app/src/test/java/com/example/poswarnus/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: add the settings screen with a training mode toggle"
```

### Task 4: TrainingBanner on Dashboard, Settings and payment

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/ui/components/TrainingBanner.kt`, `app/src/main/java/com/example/poswarnus/ui/training/TrainingBannerViewModel.kt`
- Modify: `ui/dashboard/DashboardScreen.kt` (banner above the header row/panes), `ui/settings/SettingsScreen.kt` (banner under the top bar when enabled), `ui/payment/PaymentDialog.kt` (banner at the top of `PayingScreen` and `SuccessScreen` when `state.isTraining`)
- Test: `app/src/test/java/com/example/poswarnus/ui/components/TrainingBannerColorsTest.kt`, `app/src/test/java/com/example/poswarnus/ui/training/TrainingBannerViewModelTest.kt`

**Interfaces:**
- Consumes: `TrainingModeStore.enabled`; `PaymentUiState.Paying.isTraining` / `Success.isTraining` (Task 2).
- Produces: `object TrainingBannerColors { val Background: Color; val Content: Color }` and `fun contrastRatio(a: Color, b: Color): Double` (WCAG relative-luminance formula, internal to the file or the object); `@Composable fun TrainingBanner(modifier: Modifier = Modifier)`; `@HiltViewModel class TrainingBannerViewModel @Inject constructor(trainingMode: TrainingModeStore)` with `val enabled: StateFlow<Boolean>`. `DashboardScreen` gets `trainingBannerViewModel: TrainingBannerViewModel = hiltViewModel()` as an extra defaulted parameter.

- [ ] **Step 1: Write the failing tests:** `banner colours meet 4.5:1` (`contrastRatio(Background, Content) >= 4.5`), and `banner viewmodel follows the store` (enable/disable on a real store changes `enabled.value`).
- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*TrainingBannerColorsTest" --tests "*TrainingBannerViewModelTest"`
Expected: compile failure.

- [ ] **Step 3: Implement.** Banner: full-width, single line, `maxLines = 1` with ellipsis, text `MODE PELATIHAN - transaksi tidak disimpan`, bold, amber-dark background with near-black content (pick values that pass the test), no dismiss control, a polite `liveRegion`-free static semantics (plain text). Dashboard: banner on top of the whole screen above both panes (inside the existing `BoxWithConstraints`, respecting safe-drawing insets so it sits below the status bar) only when `trainingBannerViewModel.enabled` is true. Settings: shown when `trainingEnabled`. `PaymentDialog`: shown at the top of Paying and Success only when `state.isTraining`; the one-column phone layout must still fit and scroll as before.
- [ ] **Step 4: Run to verify**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/components/TrainingBanner.kt app/src/main/java/com/example/poswarnus/ui/training app/src/main/java/com/example/poswarnus/ui/dashboard/DashboardScreen.kt app/src/main/java/com/example/poswarnus/ui/settings/SettingsScreen.kt app/src/main/java/com/example/poswarnus/ui/payment/PaymentDialog.kt app/src/test/java/com/example/poswarnus/ui/components/TrainingBannerColorsTest.kt app/src/test/java/com/example/poswarnus/ui/training/TrainingBannerViewModelTest.kt
git commit -m "feat: show a high-contrast training banner on dashboard, settings and payment"
```

### Task 5: Emulator verification and roadmap (controller)

**Files:**
- Modify: `docs/roadmap.md` (mark 6a, record verified / NOT verified)

**Interfaces:** none.

- [ ] **Step 1: Full gate.** Run `./gradlew testDebugUnitTest assembleDebug lint`. Expected: tests and build green; lint shows only the 2 known `LoginScreenTest` errors.
- [ ] **Step 2: Install on the tablet emulator** (`emulator-5554`) with `./gradlew installDebug`, log in (controller types the credentials), and verify spec section 6 items 1-5 with screenshots/`uiautomator` text and `adb shell run-as com.example.poswarnus` copies of the database: banner on Dashboard, Settings, Paying and Success; a training payment leaves `SELECT count(*) FROM transactions` unchanged; the pill is unchanged; enabling and disabling empties the cart; logout, and kill-then-reopen, leave the mode off; a normal sale afterwards is stored and synced.
- [ ] **Step 3: Phone size** (`wm size 1080x2400`, reset afterwards with `wm size reset`): the header with the gear and the banner fit, brand title ellipsizes first, payment single column fits.
- [ ] **Step 4: Update `docs/roadmap.md`:** a "Phase 6a status" block (done, verified list, NOT verified list: TalkBack, physical device) and keep 6b as the remaining Phase 6 work. Commit `docs: record Phase 6a status`.
