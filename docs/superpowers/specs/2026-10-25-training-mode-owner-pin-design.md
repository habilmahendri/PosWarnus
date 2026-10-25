# Fase 6c: PIN Pemilik untuk Mode Pelatihan — Design

Tanggal: 2026-10-25. Bukan bagian asli PRD 4.13; ditambahkan sebagai mitigasi fraud setelah Fase 6a berjalan. ("6c" dipakai karena label "6b" sudah pernah dipakai untuk cetak struk sebelum dipindah jadi Fase 9 — lihat `docs/roadmap.md`.) Tidak ada perubahan pada alur pembayaran, histori, atau navigasi.

## 1. Tujuan

Mode Pelatihan (4.13) tidak pernah menyimpan transaksinya ke mana pun. Tanpa penghalang, kasir nakal bisa menyalakan Mode Pelatihan sendiri, melayani pembeli asli, menerima uang tunai, lalu mematikannya lagi — tidak ada jejak penjualan itu pernah terjadi. Fase ini menutup celah itu: menyalakan Mode Pelatihan membutuhkan PIN pemilik yang diverifikasi di server, dan setiap kali mode dinyalakan/dimatikan tercatat ke Supabase untuk ditinjau pemilik.

## 2. Di luar cakupan

- Rate-limit/lockout setelah PIN salah berulang kali.
- Mengubah PIN dari dalam app (pemilik mengatur lewat SQL langsung di Supabase).
- Notifikasi push/SMS real-time saat Mode Pelatihan dinyalakan.
- Peran/akun pemilik sebagai entitas login — PIN ini satu rahasia bersama yang dicek di server, bukan tingkat login kedua (lihat PRD 5, catatan di item Roles/permissions).
- Mencatat percobaan PIN yang salah (hanya transisi nyala/mati yang berhasil yang dicatat).

## 3. Keputusan yang sudah diambil

Dari pemilik (lihat percakapan): PIN pemilik untuk menyalakan + log audit, keduanya wajib online (tidak ada PIN tersimpan/cache di perangkat), PIN diatur langsung di Supabase (bukan lewat layar app).

Ruling saya di luar itu:
- **Verifikasi lewat RPC `SECURITY DEFINER`**, pola yang sama dengan `submit_transaction` (Fase 5, `20261024100000_fix_submit_transaction_security_definer.sql`): kasir tidak pernah mendapat hak `SELECT` ke tabel PIN; RPC yang membaca hash atas nama definer dan hanya mengembalikan `boolean`.
- **PIN disimpan sebagai hash** (`pgcrypto` `crypt()`/`gen_salt('bf')`), bukan teks biasa, mengikuti praktik standar — walau ancamannya di sini kasir lewat app, bukan pembobolan database, hash tetap baris pertahanan murah.
- **Migrasi hanya membuat struktur tabel, bukan mengisi PIN awal.** Mengikuti pola `tax_settings` ("edit nanti dengan `update ...`"): file migrasi di-commit ke git, jadi PIN sungguhan tidak boleh ada di dalamnya. Sebelum pemilik mengisi baris PIN, RPC mengembalikan `false` untuk PIN apa pun — Mode Pelatihan praktis terkunci sampai PIN diisi, dianggap aman secara default.
- **Mematikan Mode Pelatihan tidak pernah butuh PIN atau koneksi** — sudah berlaku sekarang (lihat `TrainingModeStore.disable()`) dan tetap begitu; hanya transisi nyala yang digerbang.
- **Deteksi offline memakai ulang `SyncErrors.isOffline`** (Fase 5, `data/sync/SyncErrors.kt`) alih-alih menulis klasifikasi baru: ubah visibilitasnya dari `private` menjadi `internal` (keduanya ada di modul `:app` yang sama) supaya `SettingsViewModel` memanggil `SyncErrors.isOffline(e)` langsung. Konsisten dengan sinyal "offline" yang sudah dipakai pill sinkron, dan tidak menduplikasi daftar tipe exception.
- **Gagal menulis baris audit tidak membatalkan Mode Pelatihan yang sudah diaktifkan/dimatikan.** PIN benar atau logout/disable tetap berlaku meski baris audit gagal tersimpan (mis. race jaringan tepat setelah verifikasi); audit adalah pencatatan tambahan, bukan syarat.

## 4. Desain

### 4.1 Migrasi Supabase baru

Berkas baru `supabase/migrations/20261025000000_add_training_mode_pin.sql` (pola komentar sama seperti migrasi Fase 4/5: belum diterapkan ke proyek hidup, diterapkan manual oleh pemilik, tidak idempoten untuk `create table`/`create policy`).

```sql
create extension if not exists pgcrypto;

create table public.training_mode_pin (
  id         int primary key default 1 check (id = 1),
  pin_hash   text not null,
  updated_at timestamptz not null default now()
);

alter table public.training_mode_pin enable row level security;
revoke all on public.training_mode_pin from anon, authenticated;
-- Tidak ada policy sama sekali: hanya fungsi SECURITY DEFINER di bawah yang bisa membacanya.
-- Baris PIN TIDAK diisi di sini (lihat ruling 3) — pemilik mengisinya setelah migrasi ini
-- diterapkan, misalnya:
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

Pemilik meninjau log lewat SQL Editor Supabase (berjalan sebagai `postgres`, melewati RLS), mis. `select * from training_mode_audit order by occurred_at desc limit 50;` — tidak ada UI baru untuk ini di app maupun di PRD.

### 4.2 Kotlin: remote data source

Berkas baru `data/remote/TrainingPinRemoteDataSource.kt`, pola sama dengan `TaxRemoteDataSource`/`TransactionRemoteDataSource`:

```kotlin
interface TrainingPinRemoteDataSource {
    suspend fun verifyPin(pin: String): Boolean
    suspend fun recordTransition(cashierId: String, action: TrainingAuditAction)
}

enum class TrainingAuditAction(val wireValue: String) { ENABLE("enable"), DISABLE("disable") }
```

`SupabaseTrainingPinRemoteDataSource(client, sessionAvailable)`:
- `verifyPin`: lempar `NoSessionException` bila `!sessionAvailable()`; selain itu panggil `client.postgrest.rpc("verify_training_pin", buildJsonObject { put("pin", pin) })` dan decode hasilnya sebagai `Boolean`.
- `recordTransition`: `client.postgrest.from("training_mode_audit").insert(mapOf("cashier_id" to cashierId, "action" to action.wireValue))`. Dipanggil "fire-and-forget" dari pemanggilnya (lihat 4.3) — fungsi ini sendiri tetap melempar error apa adanya; pemanggil yang memutuskan untuk mengabaikannya.

Modul DI baru `di/TrainingPinModule.kt`, pola identik `TaxModule`:
```kotlin
@Provides @Singleton
fun provideTrainingPinRemoteDataSource(client: SupabaseClient): TrainingPinRemoteDataSource =
    SupabaseTrainingPinRemoteDataSource(client, sessionAvailable = { client.auth.currentSessionOrNull() != null })
```

### 4.3 Kotlin: perilaku `SettingsViewModel`

`TrainingModeStore` dan `TrainingModeStore.enable()/disable()` **tidak berubah**. `SettingsViewModel` mendapat dua constructor param baru: `trainingPinRemote: TrainingPinRemoteDataSource` dan `authRepository: AuthRepository` (sumber `currentUserId()`, pola yang sama dipakai `TransactionSyncer`).

`SettingsUiState` mendapat state dialog PIN menggantikan `confirmingEnable`:

```kotlin
data class SettingsUiState(
    val trainingEnabled: Boolean = false,
    val pinDialog: PinDialogState? = null,
)

data class PinDialogState(
    val pinInput: String = "",
    val submitting: Boolean = false,
    val error: String? = null, // null = tidak ada error
)
```

- `onTrainingToggle(checked: Boolean)`: `checked = false` → langsung `trainingMode.disable()`, lalu catat audit `DISABLE` fire-and-forget di `viewModelScope` (`runCatching { trainingPinRemote.recordTransition(...) }`, abaikan hasilnya — lihat ruling 3). `checked = true` → buka `pinDialog = PinDialogState()`, tidak menyentuh `TrainingModeStore`.
- `onPinInputChange(value: String)`: perbarui `pinDialog.pinInput` (dialog tetap terbuka, `error` dan `submitting` tidak berubah oleh pengetikan).
- `onPinCancel()`: `pinDialog = null`.
- `onPinSubmit()`: set `submitting = true`, panggil `trainingPinRemote.verifyPin(pinInput)` di `viewModelScope`:
  - Sukses `true` → `trainingMode.enable()`, catat audit `ENABLE` fire-and-forget, `pinDialog = null`.
  - Sukses `false` → `pinDialog = pinDialog.copy(submitting = false, error = "PIN salah", pinInput = "")`.
  - Exception yang menandakan offline (`SyncErrors.isOffline(e)`, lihat ruling di bagian 3) atau `NoSessionException` → `pinDialog = pinDialog.copy(submitting = false, error = "Perlu koneksi untuk verifikasi PIN", pinInput = "")`.
  - Exception lain → `pinDialog = pinDialog.copy(submitting = false, error = "PIN salah", pinInput = "")` (gagal tertutup aman: dianggap bukan PIN yang valid, bukan bocoran detail server ke kasir).

### 4.4 UI: dialog PIN

Mengganti `AlertDialog` "Nyalakan Mode Pelatihan?" yang ada sekarang ([SettingsScreen.kt:141-160](../../../app/src/main/java/com/example/poswarnus/ui/settings/SettingsScreen.kt)) dengan dialog PIN, bentuk visual tetap mengikuti dialog yang ada (shape, warna, tombol):

- Judul "Masukkan PIN Pemilik".
- Teks bantu "Pesanan saat ini akan dikosongkan." (kalimat lama tentang pengosongan keranjang tetap relevan, dipindah ke sini).
- `OutlinedTextField` angka (`KeyboardType.NumberPassword` agar PIN tersamar seperti input kata sandi), terhubung ke `pinInput`/`onPinInputChange`.
- Baris error di bawah field bila `pinDialog.error != null`, warna galat tema yang sudah ada.
- Tombol "Batal" (`onPinCancel`) dan "Nyalakan" (`onPinSubmit`, dinonaktifkan saat `submitting` atau `pinInput` kosong; menampilkan indikator kecil saat `submitting`).
- Dialog tidak bisa ditutup dengan tap di luar saat `submitting` (`onDismissRequest` memanggil `onPinCancel` hanya bila tidak sedang submit).

Tidak ada numpad khusus seperti di `PaymentDialog` — PIN pemilik beda konteks (diketik sendiri oleh kasir yang menyampaikan ke pemilik, bukan diketik berulang tiap transaksi), `OutlinedTextField` dengan keyboard sistem cukup.

## 5. Penanganan kondisi khusus

- PIN belum pernah diisi pemilik di Supabase: `verify_training_pin` selalu `false` → kasir selalu melihat "PIN salah"; Mode Pelatihan terkunci sampai pemilik mengisi baris pertama.
- PIN benar tapi penulisan baris audit gagal (mis. koneksi putus tepat setelah RPC sukses): Mode Pelatihan tetap menyala (ruling 3); tidak ada retry otomatis untuk audit yang gagal (log celah ini "diketahui", tidak ditangani — di luar cakupan rate-limit/retry).
- Mematikan rail/drawer, berpindah layar, atau rotasi saat dialog PIN terbuka: dialog adalah state `SettingsViewModel`, bertahan selama ViewModel hidup (sama seperti `confirmingEnable` sebelumnya); berpindah ke Kasir/Riwayat lewat navigasi mem-pop balik ke Dashboard sehingga Settings (dan dialognya) tidak lagi di back stack — perilaku ini sudah ada untuk layar Pengaturan secara umum, tidak berubah di sini.
- Logout saat dialog PIN terbuka: `LogoutViewModel` tidak tahu soal dialog ini; logout tetap jalan seperti sekarang (Dashboard/Settings dibersihkan lewat `popUpTo`), dialog PIN ikut hilang karena Settings tidak lagi tampil.
- Kasir menekan "Nyalakan" berkali-kali sebelum respons pertama datang: `submitting = true` menonaktifkan tombol, jadi tidak ada RPC ganda.

## 6. Pengujian

Tes unit JVM (TDD, gagal dulu), dengan fake `TrainingPinRemoteDataSource`:
- `onTrainingToggle(true)` membuka `pinDialog` dengan state kosong tanpa menyentuh `TrainingModeStore`.
- `onTrainingToggle(false)` langsung memanggil `TrainingModeStore.disable()` dan memanggil `recordTransition(cashierId, DISABLE)`.
- `onPinSubmit()` dengan fake yang mengembalikan `true`: `TrainingModeStore.enable()` terpanggil, `recordTransition(cashierId, ENABLE)` terpanggil, `pinDialog` menjadi `null`.
- `onPinSubmit()` dengan fake yang mengembalikan `false`: `TrainingModeStore` tidak tersentuh, `pinDialog.error == "PIN salah"`, `pinInput` dikosongkan.
- `onPinSubmit()` dengan fake yang melempar exception offline (mis. `IOException`) atau `NoSessionException`: `pinDialog.error == "Perlu koneksi untuk verifikasi PIN"`, `TrainingModeStore` tidak tersentuh.
- Kegagalan `recordTransition` (exception apa pun) tidak membatalkan `enable()`/`disable()` yang sudah terjadi (ruling 3) dan tidak melempar ke pemanggil `onPinSubmit`/`onTrainingToggle`.
- `SupabaseTrainingPinRemoteDataSource.verifyPin`/`recordTransition`: tes gaya `SupabaseTransactionRemoteDataSourceTest` (session tersedia → panggil RPC/insert yang benar; tanpa session → lempar `NoSessionException` tanpa pernah memanggil jaringan).

Verifikasi emulator (setelah migrasi diterapkan pemilik dan baris PIN diisi):
1. Menyalakan toggle membuka dialog PIN, bukan dialog konfirmasi lama.
2. PIN benar → Mode Pelatihan menyala, banner tampil, keranjang kosong.
3. PIN salah → tetap mati, pesan "PIN salah" tampil, bisa dicoba lagi.
4. Wi-Fi mati saat submit → pesan "Perlu koneksi untuk verifikasi PIN".
5. Setelah menyalakan dan mematikan, baris di `training_mode_audit` bertambah dua (`enable` lalu `disable`) dengan `cashier_id` kasir yang login — dicek lewat SQL Editor atau MCP.
6. Mematikan Mode Pelatihan tidak pernah menampilkan dialog PIN, termasuk saat Wi-Fi mati.

Tidak diverifikasi di fase ini: TalkBack, perangkat fisik, rate-limit (karena memang di luar cakupan).

## 7. Urutan kerja

Satu plan, task kecil: migrasi SQL (tidak diterapkan sampai pemilik setuju), remote data source + modul DI, perubahan `SettingsViewModel`/`SettingsUiState`, dialog PIN di `SettingsScreen`, lalu verifikasi emulator setelah migrasi diterapkan dan roadmap. Subagent: Sonnet 5.5.

Migrasi SQL **tidak diterapkan ke Supabase hidup** sebagai bagian dari eksekusi plan ini — menunggu persetujuan eksplisit pemilik per-aksi, sama seperti migrasi Fase 4/5.
