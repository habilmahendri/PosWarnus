# Fase 6a: Pengaturan dan Mode Pelatihan - Design

Tanggal: 2026-10-24. PRD: 4.13 (Training Mode); bagian layar Pengaturan dari 4.10. Cetak Bluetooth (sisa 4.10) adalah Fase 6b dan punya spec sendiri.

## 1. Tujuan

Kasir baru atau orang yang sedang demo bisa menjalankan seluruh alur pesan sampai bayar tanpa membuat transaksi asli. Katalog, modifier, pajak, dan hitungan kembalian sama persis dengan mode normal; hanya hasilnya dibuang.

## 2. Di luar cakupan

- Cetak struk dan Bluetooth (Fase 6b). Tombol "Cetak struk" tetap nonaktif.
- Penanda "LATIHAN - BUKAN TRANSAKSI" di struk (6b).
- Riwayat atau penilaian sesi latihan (non-goal PRD).
- Migrasi Room dan SQL Supabase: tidak ada.

## 3. Keputusan yang sudah diambil

- Menyalakan Mode Pelatihan **mengosongkan keranjang**; mematikan juga mengosongkan (PRD). Pesanan asli tidak bisa berubah jadi latihan, dan sebaliknya.
- Status mode hanya di memori, tidak pernah ditulis ke disk. Itu yang membuat mode mati sendiri saat app dibuka ulang.
- Pengaturan adalah layar baru, dibuka dari ikon roda gigi di header katalog. Isinya hanya Mode Pelatihan sampai 6b.

## 4. Desain

### 4.1 `TrainingModeStore`

Singleton Hilt (`data/training/TrainingModeStore.kt`), bergantung pada `CartStore`.

- `val enabled: StateFlow<Boolean>`, awal `false`.
- `fun enable()`: `cartStore.clear()` lalu `enabled = true`.
- `fun disable()`: `cartStore.clear()` lalu `enabled = false`.
- `fun reset()`: `enabled = false` tanpa menyentuh keranjang (dipakai logout, yang sudah mengosongkan keranjang sendiri).

`LogoutViewModel` memanggil `reset()` di tempat yang sama dengan `cartStore.clear()`. Login juga memanggil `reset()` (jaga-jaga bila sesi sebelumnya berakhir tanpa logout bersih).

### 4.2 Pembayaran tidak menulis apa pun saat latihan

- `PaymentViewModel.Frozen` mendapat `isTraining: Boolean`, diisi dari `TrainingModeStore.enabled.value` di `open()`. Status dibekukan bersama total dan UUID, jadi mode tidak bisa berganti di tengah pembayaran.
- `confirm()`: jika `frozen.isTraining`, **tidak memanggil** `transactionRepository.save` dan tidak membaca id kasir; langsung hasilkan `Success`. Tidak ada baris Room, tidak ada `requestSync()`, tidak ada panggilan Supabase.
- `PaymentUiState.Paying` dan `Success` mendapat `isTraining: Boolean` supaya `PaymentDialog` bisa menampilkan banner tanpa membaca store.
- `TransactionRepositoryImpl` tidak berubah (tetap `isTraining = false`). Training tidak pernah sampai ke repository. Filter `isTraining = 0` di DAO tetap sebagai lapisan kedua.
- `finish()` ("Selesai") tetap mengosongkan keranjang; mode pelatihan tetap menyala untuk pesanan berikutnya.

### 4.3 UI

- Rute baru `PosDestinations.SETTINGS = "settings"` di `PosNavGraph`. Tombol sistem Back dan tombol Kembali di layar kembali ke Dashboard.
- Ikon roda gigi (glyph baru di `ui/components/Glyphs.kt`) di `CatalogHeader`, di samping pill sinkron dan "Keluar", layout lebar dan sempit. Dengan aturan header yang ada: judul merek tetap `weight(1f)` dan mengecil lebih dulu; target sentuh minimal 48dp; `contentDescription` "Pengaturan".
- `SettingsScreen` + `SettingsViewModel`: satu baris "Mode Pelatihan" dengan teks bantu dan `Switch`. Menyalakan membuka `AlertDialog` ("Nyalakan Mode Pelatihan?", isi: keranjang akan dikosongkan dan transaksi tidak disimpan; tombol "Nyalakan" / "Batal"). Switch baru berubah setelah "Nyalakan". Mematikan langsung berlaku tanpa dialog.
- `TrainingBanner` (`ui/components/TrainingBanner.kt`), teks "MODE PELATIHAN - transaksi tidak disimpan", warna kontras tinggi (latar amber tua, teks gelap, rasio kontras >= 4.5:1 diperiksa), satu baris, tidak bisa ditutup. Tampil di: atas Dashboard (di atas header), layar Pengaturan, dan di dalam `PaymentDialog` pada state Paying dan Success (dialog layar penuh menutupi Dashboard, jadi banner harus ada di dalamnya).
- Pill sinkron tidak berubah. Training tidak menambah apa pun ke antrean, jadi pill tidak terpengaruh.

## 5. Penanganan kondisi khusus

- Logout atau login ulang: mode mati, keranjang kosong.
- Proses app dimatikan lalu dibuka: mode mati (status memori), tidak perlu kode tambahan.
- Rotasi layar dan perubahan konfigurasi: status ada di singleton, bukan di ViewModel layar, jadi bertahan; dialog konfirmasi memakai state yang disimpan.
- Menyalakan mode saat dialog pembayaran terbuka: tidak mungkin, dialog layar penuh menutupi akses ke Pengaturan.

## 6. Pengujian

Tes unit JVM (TDD, gagal dulu):
- `TrainingModeStore`: awal mati; `enable()` mengosongkan keranjang dan menyalakan; `disable()` mengosongkan dan mematikan; `reset()` mematikan tanpa menyentuh keranjang.
- `PaymentViewModel` saat latihan: `save` tidak pernah dipanggil, hasil tetap Success dengan total, uang, dan kembalian yang benar; `isTraining` ikut di Paying dan Success; status dibekukan saat `open()` (mengganti mode setelahnya tidak mengubah sale yang sedang berjalan).
- `PaymentViewModel` mode normal: perilaku Fase 4 tidak berubah (suite yang ada tetap hijau).
- `LogoutViewModel`: logout mematikan mode.
- `SettingsViewModel`: nyalakan butuh konfirmasi (state dialog), batal tidak mengubah apa pun, matikan langsung berlaku.

Verifikasi emulator (tablet, lalu ukuran ponsel):
1. Banner tampil di Dashboard, Pengaturan, Paying, dan Success.
2. Bayar saat latihan: baris `transactions` di Room tidak bertambah, tidak ada panggilan `submit_transaction`, pill tidak berubah.
3. Menyalakan dan mematikan mengosongkan keranjang.
4. Mode mati setelah logout, dan setelah app dibunuh lalu dibuka lagi.
5. Mode normal setelahnya: sale tetap tersimpan dan tersinkron seperti biasa.
6. Header muat di ponsel dengan ikon roda gigi (judul merek mengecil lebih dulu).

Tidak diverifikasi di fase ini: TalkBack, perangkat fisik.

## 7. Urutan kerja

Satu plan, task kecil: store, pembayaran, layar Pengaturan dan navigasi, banner, verifikasi emulator, tinjauan satu cabang di akhir. Subagent: Sonnet 5.5.
