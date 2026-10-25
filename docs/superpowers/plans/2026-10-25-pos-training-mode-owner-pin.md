# Fase 6c: PIN Pemilik untuk Mode Pelatihan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menyalakan Mode Pelatihan membutuhkan PIN pemilik yang diverifikasi di server (RPC `SECURITY DEFINER`), dan setiap transisi nyala/mati tercatat ke log audit Supabase.

**Architecture:** Satu RPC baru (`verify_training_pin`) dan satu tabel audit insert-only, dibungkus `TrainingPinRemoteDataSource` baru di lapisan `data/remote` (pola yang sama dengan `TaxRemoteDataSource`/`TransactionRemoteDataSource`). `SettingsViewModel` mengganti dialog konfirmasi ya/tidak yang ada dengan state dialog PIN; `TrainingModeStore.enable()/disable()` tidak berubah sama sekali.

**Tech Stack:** Kotlin, Hilt, Jetpack Compose (Material 3), Supabase (`postgrest-kt`), JUnit + `kotlinx-coroutines-test` + Ktor `MockEngine` untuk tes remote data source.

**Spec:** `docs/superpowers/specs/2026-10-25-training-mode-owner-pin-design.md`

## Global Constraints

- Migrasi SQL dibuat sebagai berkas di repo tapi **tidak diterapkan** ke Supabase hidup sebagai bagian dari eksekusi plan ini — menunggu persetujuan eksplisit pemilik, terpisah dari pekerjaan ini.
- Mematikan Mode Pelatihan tidak pernah butuh PIN atau koneksi; hanya menyalakan yang digerbang.
- PIN diverifikasi hanya online; tidak ada PIN atau hash tersimpan/di-cache di perangkat.
- `verify_training_pin` mengembalikan `boolean` murni — "PIN salah" bukan exception. Exception dari RPC berarti masalah koneksi/sesi/server, bukan PIN yang salah.
- Kegagalan menulis baris `training_mode_audit` tidak pernah membatalkan atau memblokir `enable()`/`disable()` yang sudah terjadi.
- **Ruling (celah di spec):** menonaktifkan toggle (`onTrainingToggle(false)`) saat dialog PIN masih terbuka (user membalik switch sebelum submit, atau saat `submitting` sedang menunggu RPC) langsung menutup dialog dan menonaktifkan Mode Pelatihan — mematikan selalu menang seketika, terlepas dari state dialog PIN. Mencegah RPC verifikasi yang sudah berjalan berhasil belakangan dan diam-diam menyalakan Mode Pelatihan setelah kasir mengira sudah membatalkannya.
- Subagent implementer dan reviewer: Sonnet 5.5 untuk semuanya (tidak ada task yang cukup kecil untuk model lebih murah — semua menyentuh integrasi Hilt/Supabase).

## Review Focus

- Menekan "Nyalakan" dua kali sebelum respons pertama datang tidak boleh memanggil `verifyPin` dua kali — Task 3.
- Mematikan toggle saat dialog PIN masih terbuka (menunggu respons atau belum disubmit) langsung menutup dialog dan menonaktifkan (lihat ruling di atas) — Task 3.
- Kegagalan RPC generik (bukan offline/no-session, mis. error 500 dari server) harus gagal tertutup sebagai "PIN salah", tidak melempar exception ke UI dan tidak diam-diam menyalakan Mode Pelatihan — Task 3.
- Kegagalan menulis baris audit (`recordTransition` melempar) tidak boleh mencegah `enable()`/`disable()` yang sudah terjadi dan tidak boleh melempar ke pemanggil `onPinSubmit`/`onTrainingToggle` — Task 3.
- Membuka kembali dialog PIN setelah percobaan salah sebelumnya (matikan lalu nyalakan lagi) dimulai dari state bersih: tidak ada pesan error atau `pinInput` lama yang tersisa — Task 3.

---

### Task 1: Migrasi Supabase (tidak diterapkan)

**Files:**
- Create: `supabase/migrations/20261025000000_add_training_mode_pin.sql`

**Interfaces:**
- Produces: tabel `public.training_mode_pin` (kolom `id`, `pin_hash`, `updated_at`), tabel `public.training_mode_audit` (kolom `id`, `cashier_id`, `action`, `occurred_at`), fungsi `public.verify_training_pin(p_pin text) returns boolean`. Task 2 memanggil fungsi ini lewat nama `verify_training_pin` dengan parameter JSON `{"pin": "<nilai>"}`, dan menulis ke tabel `training_mode_audit` lewat insert biasa (bukan RPC) dengan kolom `cashier_id` dan `action`.

- [ ] **Step 1: Tulis berkas migrasi**

Salin isi SQL ini persis (ikuti gaya komentar migrasi Fase 4/5: sebutkan belum diterapkan, tidak idempoten untuk `create table`/`create policy`, PIN awal sengaja tidak diisi):

```sql
-- Fase 6c: PIN pemilik untuk menyalakan Mode Pelatihan + log audit nyala/mati.
--
-- Diterapkan manual oleh pemilik proyek; BELUM dieksekusi oleh app atau langkah build apa pun, dan
-- BELUM diterapkan ke proyek hidup. TIDAK idempoten: menjalankan ulang `create table` / `create
-- policy` akan gagal. Sampai migrasi ini diterapkan dan baris PIN diisi (lihat di bawah), menyalakan
-- Mode Pelatihan selalu gagal dengan "PIN salah" di app — ini dianggap aman secara default.
--
-- Sumber: docs/superpowers/specs/2026-10-25-training-mode-owner-pin-design.md, bagian 4.1.

create extension if not exists pgcrypto;

create table public.training_mode_pin (
  id         int primary key default 1 check (id = 1),
  pin_hash   text not null,
  updated_at timestamptz not null default now()
);

alter table public.training_mode_pin enable row level security;
revoke all on public.training_mode_pin from anon, authenticated;
-- Tidak ada policy sama sekali: hanya fungsi SECURITY DEFINER di bawah yang bisa membacanya.
-- Baris PIN TIDAK diisi di sini. Isi setelah migrasi ini diterapkan, misalnya:
--   insert into public.training_mode_pin (id, pin_hash)
--   values (1, crypt('<PIN pilihan pemilik>', gen_salt('bf')))
--   on conflict (id) do update set pin_hash = excluded.pin_hash, updated_at = now();

create table public.training_mode_audit (
  id          uuid primary key default gen_random_uuid(),
  cashier_id  uuid not null references auth.users (id),
  action      text not null check (action in ('enable', 'disable')),
  occurred_at timestamptz not null default now()
);

create index training_mode_audit_occurred_at_idx on public.training_mode_audit (occurred_at desc);

alter table public.training_mode_audit enable row level security;

-- Tulis-saja untuk kasir: INSERT baris miliknya sendiri, tidak ada select/update/delete.
create policy "Cashiers insert own training_mode_audit rows"
  on public.training_mode_audit for insert to authenticated
  with check (cashier_id = (select auth.uid()));

revoke all on public.training_mode_audit from anon;

create or replace function public.verify_training_pin(p_pin text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_hash text;
begin
  if auth.uid() is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;

  select pin_hash into v_hash from public.training_mode_pin where id = 1;
  if v_hash is null then
    return false; -- belum diisi pemilik: anggap semua PIN salah
  end if;

  return v_hash = crypt(p_pin, v_hash);
end;
$$;

revoke all on function public.verify_training_pin(text) from public, anon;
grant execute on function public.verify_training_pin(text) to authenticated;
```

- [ ] **Step 2: Commit**

```bash
git add supabase/migrations/20261025000000_add_training_mode_pin.sql
git commit -m "feat: add Supabase migration for training-mode owner PIN + audit log (not applied)"
```

Tidak ada langkah verifikasi run/test untuk task ini — berkas SQL statis, tidak dieksekusi. Review task ini memeriksa isinya terhadap spec (bagian 4.1), bukan menjalankannya.

---

### Task 2: `TrainingPinRemoteDataSource` + modul DI

**Files:**
- Create: `app/src/main/java/com/example/poswarnus/data/remote/TrainingPinRemoteDataSource.kt`
- Create: `app/src/main/java/com/example/poswarnus/di/TrainingPinModule.kt`
- Test: `app/src/test/java/com/example/poswarnus/data/remote/SupabaseTrainingPinRemoteDataSourceTest.kt`

**Interfaces:**
- Consumes: `NoSessionException` dari `com.example.poswarnus.data.remote.SupabaseCatalogRemoteDataSource` (sudah ada, dipakai ulang — jangan buat exception baru).
- Produces (dipakai Task 3): `interface TrainingPinRemoteDataSource { suspend fun verifyPin(pin: String): Boolean; suspend fun recordTransition(cashierId: String, action: TrainingAuditAction) }`, `enum class TrainingAuditAction(val wireValue: String) { ENABLE("enable"), DISABLE("disable") }`. Keduanya di-resolve Hilt lewat binding di `TrainingPinModule` (tidak perlu disuntik manual).

- [ ] **Step 1: Tulis tes yang gagal untuk `verifyPin`**

Di `SupabaseTrainingPinRemoteDataSourceTest.kt`, ikuti pola `SupabaseTransactionRemoteDataSourceTest` yang sudah ada (buat `SupabaseClient` dengan `createSupabaseClient(...) { install(Postgrest); httpEngine = MockEngine { ... } }`, kumpulkan `requests`):

```kotlin
@Test
fun `verifyPin throws NoSessionException without a session and never hits the network`() = runTest {
    val source = SupabaseTrainingPinRemoteDataSource(client, sessionAvailable = { false })
    try {
        source.verifyPin("123456")
        fail("expected NoSessionException")
    } catch (e: NoSessionException) {
        // expected
    }
    assertTrue(requests.isEmpty())
}

@Test
fun `verifyPin posts to the verify_training_pin rpc with the pin and decodes the boolean result`() = runTest {
    responseBody = "true"
    val source = SupabaseTrainingPinRemoteDataSource(client, sessionAvailable = { true })

    val result = source.verifyPin("123456")

    assertTrue(result)
    assertEquals(1, requests.size)
    assertEquals("/rest/v1/rpc/verify_training_pin", requests[0].url.encodedPath)
    val body = (requests[0].body as TextContent).text
    assertEquals("123456", Json.parseToJsonElement(body).jsonObject.getValue("pin").jsonPrimitive.content)
}

@Test
fun `verifyPin returns false for a false result`() = runTest {
    responseBody = "false"
    val source = SupabaseTrainingPinRemoteDataSource(client, sessionAvailable = { true })

    assertEquals(false, source.verifyPin("000000"))
}

@Test
fun `verifyPin propagates a server error`() = runTest {
    status = HttpStatusCode.InternalServerError
    responseBody = """{"code":"XX000","message":"boom","details":null,"hint":null}"""
    val source = SupabaseTrainingPinRemoteDataSource(client, sessionAvailable = { true })
    try {
        source.verifyPin("123456")
        fail("expected RestException")
    } catch (e: RestException) {
        // expected
    }
}
```

Tambahkan juga dua tes serupa untuk `recordTransition`: tanpa sesi melempar `NoSessionException` tanpa memanggil jaringan; dengan sesi, `requests[0].url.encodedPath == "/rest/v1/training_mode_audit"` dan body JSON memuat `cashier_id` dan `action` (`"enable"` untuk `TrainingAuditAction.ENABLE`) yang dikirim.

- [ ] **Step 2: Jalankan tes, pastikan gagal karena `SupabaseTrainingPinRemoteDataSource` belum ada**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.remote.SupabaseTrainingPinRemoteDataSourceTest"`
Expected: FAIL — tidak ter-resolve / unresolved reference `SupabaseTrainingPinRemoteDataSource`.

- [ ] **Step 3: Implementasikan `TrainingPinRemoteDataSource.kt`**

Satu berkas, pola yang sama seperti `TaxRemoteDataSource.kt` (interface + implementasi Supabase dalam satu file):

```kotlin
interface TrainingPinRemoteDataSource {
    suspend fun verifyPin(pin: String): Boolean
    suspend fun recordTransition(cashierId: String, action: TrainingAuditAction)
}

enum class TrainingAuditAction(val wireValue: String) { ENABLE("enable"), DISABLE("disable") }

class SupabaseTrainingPinRemoteDataSource(
    private val client: SupabaseClient,
    private val sessionAvailable: () -> Boolean,
) : TrainingPinRemoteDataSource {
    override suspend fun verifyPin(pin: String): Boolean { ... }
    override suspend fun recordTransition(cashierId: String, action: TrainingAuditAction) { ... }
}
```

`verifyPin` memanggil `client.postgrest.rpc("verify_training_pin", buildJsonObject { put("pin", pin) }).decodeAs<Boolean>()` (pola `buildJsonObject`/`put` sama seperti `SupabaseTransactionRemoteDataSource.submit`). `recordTransition` memanggil `client.postgrest.from("training_mode_audit").insert(buildJsonObject { put("cashier_id", cashierId); put("action", action.wireValue) })`. Keduanya melempar `NoSessionException` lebih dulu bila `!sessionAvailable()`, sebelum menyentuh `client`.

- [ ] **Step 4: Jalankan tes, pastikan semuanya lulus**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.data.remote.SupabaseTrainingPinRemoteDataSourceTest"`
Expected: PASS, semua tes.

- [ ] **Step 5: Tulis modul DI**

`TrainingPinModule.kt`, pola identik `TaxModule.kt`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object TrainingPinModule {
    @Provides
    @Singleton
    fun provideTrainingPinRemoteDataSource(client: SupabaseClient): TrainingPinRemoteDataSource =
        SupabaseTrainingPinRemoteDataSource(
            client = client,
            sessionAvailable = { client.auth.currentSessionOrNull() != null },
        )
}
```

- [ ] **Step 6: Build untuk memastikan modul ter-resolve Hilt (belum ada consumer, cukup kompilasi)**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/data/remote/TrainingPinRemoteDataSource.kt \
        app/src/main/java/com/example/poswarnus/di/TrainingPinModule.kt \
        app/src/test/java/com/example/poswarnus/data/remote/SupabaseTrainingPinRemoteDataSourceTest.kt
git commit -m "feat: add TrainingPinRemoteDataSource for the verify_training_pin RPC and audit insert"
```

---

### Task 3: `SettingsViewModel` — state dialog PIN

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/example/poswarnus/data/sync/SyncErrors.kt:11` (ubah `private fun isOffline` menjadi `internal fun isOffline`, tidak ada perubahan lain pada berkas ini)
- Test: `app/src/test/java/com/example/poswarnus/ui/settings/SettingsViewModelTest.kt` (tulis ulang; tes `confirmingEnable` yang lama diganti seluruhnya karena state itu dihapus)

**Interfaces:**
- Consumes: `TrainingPinRemoteDataSource` dan `TrainingAuditAction` (Task 2); `AuthRepository.currentUserId(): String?` (sudah ada, `com.example.poswarnus.data.auth.AuthRepository`); `SyncErrors.isOffline(e: Throwable): Boolean` (di atas, sekarang `internal`); `TrainingModeStore.enable()`/`disable()`/`enabled: StateFlow<Boolean>` (sudah ada, tidak berubah).
- Produces (dipakai Task 4): `data class SettingsUiState(val trainingEnabled: Boolean, val pinDialog: PinDialogState?)`, `data class PinDialogState(val pinInput: String = "", val submitting: Boolean = false, val error: String? = null)`, fungsi `onTrainingToggle(checked: Boolean)`, `onPinInputChange(value: String)`, `onPinCancel()`, `onPinSubmit()` pada `SettingsViewModel`.

Buat sebuah fake kecil di awal berkas tes:

```kotlin
private class FakeTrainingPinRemoteDataSource(
    private val result: suspend () -> Boolean = { true },
) : TrainingPinRemoteDataSource {
    val recorded = mutableListOf<Pair<String, TrainingAuditAction>>()
    var recordTransitionThrows: Throwable? = null
    override suspend fun verifyPin(pin: String): Boolean = result()
    override suspend fun recordTransition(cashierId: String, action: TrainingAuditAction) {
        recordTransitionThrows?.let { throw it }
        recorded += cashierId to action
    }
}
```

`AuthRepository` dipalsukan dengan implementasi anonim yang `currentUserId()`-nya mengembalikan `"cashier-1"` tetap; metode lain `login`/`logout`/`isLoggedIn`/`ensureSessionForSync` tidak dipanggil ViewModel ini, lempar `error("not used")` di badannya.

Perbarui properti kelas tes dan `setUp()`:

```kotlin
private val cartStore = CartStore()
private val store = TrainingModeStore(cartStore)
private val fakeAuthRepository = /* AuthRepository anonim, currentUserId() -> "cashier-1" */
private var remote = FakeTrainingPinRemoteDataSource()
private lateinit var viewModel: SettingsViewModel

@Before
fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    cartStore.add(CartLine("l1", "m1", "Nasi Goreng", 25000, emptyList(), 2))
    viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
}
```

Sebuah tes yang butuh perilaku `verifyPin`/kegagalan `recordTransition` berbeda membuat `remote` dan `viewModel` barunya sendiri di awal tes (meneduhi punya `setUp()` untuk sisa tes itu saja), seperti ditunjukkan pada beberapa tes di bawah; tes yang tidak menyebutkan `remote`/`viewModel` baru (mis. tes "toggle off") memakai `remote`/`viewModel` dari `setUp()`.

- [ ] **Step 1: Tulis tes-tes yang gagal**

Ganti isi `SettingsViewModelTest.kt`. Pertahankan tes yang masih relevan (`uiState follows the store`), tulis ulang sisanya karena `confirmingEnable`/`confirmEnable`/`cancelEnable` dihapus:

```kotlin
@Test
fun `toggle on opens a fresh pin dialog without changing the store`() = runTest(UnconfinedTestDispatcher()) {
    backgroundScope.launch { viewModel.uiState.collect {} }

    viewModel.onTrainingToggle(true)

    assertFalse(store.enabled.value)
    assertEquals(1, cartStore.state.value.lines.size)
    assertEquals(PinDialogState(), viewModel.uiState.value.pinDialog)
}

@Test
fun `toggle off disables immediately, clears the cart and records a disable audit row`() = runTest(UnconfinedTestDispatcher()) {
    backgroundScope.launch { viewModel.uiState.collect {} }
    store.enable()
    cartStore.add(CartLine("l2", "m2", "Es Teh", 5000, emptyList(), 1))

    viewModel.onTrainingToggle(false)

    assertFalse(store.enabled.value)
    assertTrue(cartStore.state.value.lines.isEmpty())
    assertEquals(null, viewModel.uiState.value.pinDialog)
    assertEquals(listOf("cashier-1" to TrainingAuditAction.DISABLE), remote.recorded)
}

@Test
fun `toggle off while the pin dialog is open closes it and disables at once`() = runTest(UnconfinedTestDispatcher()) {
    backgroundScope.launch { viewModel.uiState.collect {} }
    viewModel.onTrainingToggle(true)

    viewModel.onTrainingToggle(false)

    assertFalse(store.enabled.value)
    assertEquals(null, viewModel.uiState.value.pinDialog)
}

@Test
fun `correct pin enables training, clears the cart and records an enable audit row`() = runTest(UnconfinedTestDispatcher()) {
    val remote = FakeTrainingPinRemoteDataSource(result = { true })
    viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
    backgroundScope.launch { viewModel.uiState.collect {} }
    viewModel.onTrainingToggle(true)
    viewModel.onPinInputChange("123456")

    viewModel.onPinSubmit()

    assertTrue(store.enabled.value)
    assertTrue(cartStore.state.value.lines.isEmpty())
    assertEquals(null, viewModel.uiState.value.pinDialog)
    assertEquals(listOf("cashier-1" to TrainingAuditAction.ENABLE), remote.recorded)
}

@Test
fun `wrong pin keeps training off and shows an error with the input cleared`() = runTest(UnconfinedTestDispatcher()) {
    val remote = FakeTrainingPinRemoteDataSource(result = { false })
    viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
    backgroundScope.launch { viewModel.uiState.collect {} }
    viewModel.onTrainingToggle(true)
    viewModel.onPinInputChange("000000")

    viewModel.onPinSubmit()

    assertFalse(store.enabled.value)
    assertEquals(PinDialogState(error = "PIN salah"), viewModel.uiState.value.pinDialog)
}

@Test
fun `offline during verification shows a connectivity message and leaves training off`() = runTest(UnconfinedTestDispatcher()) {
    val remote = FakeTrainingPinRemoteDataSource(result = { throw java.io.IOException("offline") })
    viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
    backgroundScope.launch { viewModel.uiState.collect {} }
    viewModel.onTrainingToggle(true)
    viewModel.onPinInputChange("123456")

    viewModel.onPinSubmit()

    assertFalse(store.enabled.value)
    assertEquals("Perlu koneksi untuk verifikasi PIN", viewModel.uiState.value.pinDialog?.error)
}

@Test
fun `a generic server error fails closed as wrong pin instead of throwing`() = runTest(UnconfinedTestDispatcher()) {
    val remote = FakeTrainingPinRemoteDataSource(result = { error("server exploded") })
    viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
    backgroundScope.launch { viewModel.uiState.collect {} }
    viewModel.onTrainingToggle(true)
    viewModel.onPinInputChange("123456")

    viewModel.onPinSubmit()

    assertFalse(store.enabled.value)
    assertEquals("PIN salah", viewModel.uiState.value.pinDialog?.error)
}

@Test
fun `a failed audit write does not block enabling`() = runTest(UnconfinedTestDispatcher()) {
    val remote = FakeTrainingPinRemoteDataSource(result = { true })
    remote.recordTransitionThrows = RuntimeException("insert failed")
    viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
    backgroundScope.launch { viewModel.uiState.collect {} }
    viewModel.onTrainingToggle(true)
    viewModel.onPinInputChange("123456")

    viewModel.onPinSubmit()

    assertTrue(store.enabled.value)
    assertEquals(null, viewModel.uiState.value.pinDialog)
}

@Test
fun `submitting twice before the first response only calls verifyPin once`() = runTest(UnconfinedTestDispatcher()) {
    var calls = 0
    val remote = FakeTrainingPinRemoteDataSource(result = { calls++; true })
    viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
    backgroundScope.launch { viewModel.uiState.collect {} }
    viewModel.onTrainingToggle(true)
    viewModel.onPinInputChange("123456")

    viewModel.onPinSubmit()
    viewModel.onPinSubmit()

    assertEquals(1, calls)
}

@Test
fun `reopening the dialog after a wrong attempt starts fresh`() = runTest(UnconfinedTestDispatcher()) {
    val remote = FakeTrainingPinRemoteDataSource(result = { false })
    viewModel = SettingsViewModel(store, remote, fakeAuthRepository)
    backgroundScope.launch { viewModel.uiState.collect {} }
    viewModel.onTrainingToggle(true)
    viewModel.onPinInputChange("000000")
    viewModel.onPinSubmit()
    viewModel.onTrainingToggle(false)

    viewModel.onTrainingToggle(true)

    assertEquals(PinDialogState(), viewModel.uiState.value.pinDialog)
}
```

(Catatan eksplisit: `submitting twice` memakai efek samping penghitung `calls` di dalam `result` untuk mengukur berapa kali `verifyPin` benar-benar terpanggil — bukan menebak dari `submitting`. `UnconfinedTestDispatcher` menjalankan coroutine secara eager, jadi panggilan `onPinSubmit()` kedua pada baris berikutnya terjadi sebelum `result()` pertama kembali hanya jika `submitting` sudah dicek dan diset `true` secara sinkron sebelum titik suspensi pertama di implementasi — ini mem-verifikasi urutan itu.)

- [ ] **Step 2: Jalankan tes, pastikan gagal**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.settings.SettingsViewModelTest"`
Expected: FAIL — kompilasi gagal (`PinDialogState`, `onPinInputChange`, dll. belum ada) atau tes gagal begitu dikompilasi manual terhadap stub kosong.

- [ ] **Step 3: Ubah visibilitas `SyncErrors.isOffline`**

Di `data/sync/SyncErrors.kt:11`, ganti `private fun isOffline(e: Throwable)` menjadi `internal fun isOffline(e: Throwable)`. Tidak ada perubahan lain pada file ini.

- [ ] **Step 4: Implementasikan `SettingsUiState`, `PinDialogState`, dan logika `SettingsViewModel`**

```kotlin
data class SettingsUiState(
    val trainingEnabled: Boolean = false,
    val pinDialog: PinDialogState? = null,
)

data class PinDialogState(
    val pinInput: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val trainingMode: TrainingModeStore,
    private val trainingPinRemote: TrainingPinRemoteDataSource,
    private val authRepository: AuthRepository,
) : ViewModel() {
    // pinDialog: MutableStateFlow<PinDialogState?>(null), digabung dengan trainingMode.enabled lewat combine,
    // sama seperti confirmingEnable sebelumnya.

    fun onTrainingToggle(checked: Boolean) { ... }
    fun onPinInputChange(value: String) { ... }
    fun onPinCancel() { ... }
    fun onPinSubmit() { ... }
}
```

Signature dan nama kelas/fungsi di atas final — badan fungsi ditulis sendiri oleh implementer mengikuti alur di bagian 4.3 spec:
- `onTrainingToggle(false)`: tutup `pinDialog` (set `null`) kalau sedang terbuka, panggil `trainingMode.disable()`, lalu di `viewModelScope` panggil `runCatching { trainingPinRemote.recordTransition(authRepository.currentUserId().orEmpty(), TrainingAuditAction.DISABLE) }` (abaikan hasilnya, jangan `await`/blokir UI thread — cukup diluncurkan, tidak perlu ditunggu sebelum fungsi ini kembali).
- `onTrainingToggle(true)`: set `pinDialog = PinDialogState()` (state bersih). Tidak menyentuh `trainingMode`.
- `onPinInputChange(value)`: `pinDialog = pinDialog?.copy(pinInput = value)` (tidak mengubah `error`/`submitting`).
- `onPinCancel()`: `pinDialog = null`.
- `onPinSubmit()`: abaikan bila `pinDialog == null || pinDialog.submitting`. Set `submitting = true` secara sinkron (sebelum titik suspensi pertama) lalu di `viewModelScope.launch`: panggil `trainingPinRemote.verifyPin(pinDialog.pinInput)`.
  - `true` → `trainingMode.enable()`, `runCatching { trainingPinRemote.recordTransition(authRepository.currentUserId().orEmpty(), TrainingAuditAction.ENABLE) }` (abaikan hasil), `pinDialog = null`.
  - `false` → `pinDialog = PinDialogState(error = "PIN salah")`.
  - exception dengan `SyncErrors.isOffline(e)` atau `e is NoSessionException` → `pinDialog = PinDialogState(error = "Perlu koneksi untuk verifikasi PIN")`.
  - exception lain apa pun → `pinDialog = PinDialogState(error = "PIN salah")` (gagal tertutup; jangan biarkan exception keluar dari coroutine ini).

- [ ] **Step 5: Jalankan tes, pastikan semuanya lulus**

Run: `./gradlew testDebugUnitTest --tests "com.example.poswarnus.ui.settings.SettingsViewModelTest"`
Expected: PASS, semua tes termasuk lima dari Review Focus.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/settings/SettingsViewModel.kt \
        app/src/main/java/com/example/poswarnus/data/sync/SyncErrors.kt \
        app/src/test/java/com/example/poswarnus/ui/settings/SettingsViewModelTest.kt
git commit -m "feat: gate enabling Training Mode behind an owner PIN verified server-side"
```

---

### Task 4: Dialog PIN di `SettingsScreen`

**Files:**
- Modify: `app/src/main/java/com/example/poswarnus/ui/settings/SettingsScreen.kt:141-160` (ganti `AlertDialog` yang ada)

**Interfaces:**
- Consumes: `SettingsUiState.pinDialog: PinDialogState?`, `viewModel::onPinInputChange`, `viewModel::onPinCancel`, `viewModel::onPinSubmit` (Task 3).

- [ ] **Step 1: Ganti blok `if (state.confirmingEnable) { AlertDialog(...) }` dengan dialog PIN**

```kotlin
state.pinDialog?.let { pinDialog ->
    AlertDialog(
        onDismissRequest = { if (!pinDialog.submitting) viewModel.onPinCancel() },
        shape = RoundedCornerShape(10.dp),
        containerColor = SurfaceWhite,
        title = { Text("Masukkan PIN Pemilik", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) },
        text = {
            Column {
                Text("Pesanan saat ini akan dikosongkan.", fontSize = 15.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pinDialog.pinInput,
                    onValueChange = viewModel::onPinInputChange,
                    singleLine = true,
                    enabled = !pinDialog.submitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = pinDialog.error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                pinDialog.error?.let {
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onPinCancel, enabled = !pinDialog.submitting, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Batal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::onPinSubmit,
                enabled = !pinDialog.submitting && pinDialog.pinInput.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Nyalakan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
            }
        },
    )
}
```

Tambahkan import `OutlinedTextField`, `KeyboardOptions`, `KeyboardType`, `Spacer`, `Column` height sesuai kebutuhan (`Spacer`/`Column` kemungkinan sudah ter-import; cek dulu sebelum menambah).

- [ ] **Step 2: Build untuk memastikan Compose mengompilasi**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Jalankan seluruh suite unit test modul (regresi)**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, semua tes lulus (termasuk yang tidak disentuh task ini).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/poswarnus/ui/settings/SettingsScreen.kt
git commit -m "feat: replace the training-mode confirmation dialog with an owner PIN prompt"
```

Tidak ada tes otomatis untuk `SettingsScreen.kt` (tidak ada tes Compose UI di proyek ini — layar Pengaturan sejak awal diverifikasi di emulator, bukan tes unit). Verifikasi emulator untuk task ini mengikuti bagian 6 spec, dijalankan controller setelah seluruh task selesai (lihat Urutan Kerja di spec).

---

## Setelah semua task selesai

Review akhir satu cabang (model paling mampu), lalu — mengikuti precedent Fase 4/5/6a/7/7b — controller (bukan task plan ini) yang:
1. Menjalankan `./gradlew testDebugUnitTest assembleDebug` di hasil akhir cabang.
2. Memperbarui `docs/roadmap.md` (baris Fase 6c).
3. Meminta persetujuan eksplisit pemilik sebelum menerapkan `20261025000000_add_training_mode_pin.sql` ke Supabase hidup — tanpa itu, verifikasi emulator end-to-end (PIN benar/salah sungguhan) tidak bisa dijalankan; yang bisa diverifikasi tanpa migrasi diterapkan adalah bahwa dialog PIN muncul, tombol nonaktif saat kosong/submitting, dan RPC yang gagal (fungsi tidak ditemukan di server) jatuh ke "PIN salah" sebagai gagal-tertutup (perilaku yang sama seperti pill sinkron Fase 5 saat tabel belum ada).
4. Meminta persetujuan eksplisit pemilik sebelum merge ke `main`.
