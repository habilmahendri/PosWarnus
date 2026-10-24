# Fase 7b: Rail Navigasi (Kasir, Riwayat, Pengaturan, Keluar) - Design

Tanggal: 2026-10-24. Tidak ada di PRD sebagai fitur sendiri; perapian navigasi setelah Fase 6a dan 7 menambah dua tujuan baru. Tidak ada perubahan data, Room, sinkron, pembayaran, atau SQL.

## 1. Tujuan

Mengeluarkan tiga tombol (riwayat, pengaturan, keluar) dari header Dashboard ke navigasi tetap: rail tipis di sisi kiri pada tablet dan drawer pada ponsel. Header Dashboard jadi longgar (merek tidak terpotong lagi), dan kasir berpindah layar tanpa kembali ke Dashboard.

## 2. Di luar cakupan

- Tujuan baru di rail (Fase 8 tidak menambah layar; Fase 9 hanya menambah isi layar Pengaturan).
- Penanda status printer pada item Pengaturan (Fase 9).
- Perubahan isi layar Dashboard, Riwayat, atau Pengaturan selain header dan bar atasnya.

## 3. Keputusan yang sudah diambil

Dari pemilik: **rail kiri pada tablet dan drawer pada ponsel**. Disetujui juga: dialog konfirmasi saat Keluar, dan panah kembali disembunyikan saat rail tampil.

Ruling saya di luar itu:
- **Batas lebar memakai lebar jendela**, bukan lebar area sisa. Dashboard sekarang menentukan `isTablet` dari lebarnya sendiri; dengan rail 72dp, jendela 840 sampai 911dp akan salah dianggap ponsel. Dashboard, Riwayat, Pengaturan, dan rail semuanya memakai `LocalConfiguration.screenWidthDp >= 840`. Batas 600dp di dalam header katalog (compact vs lebar) tetap memakai lebar panel karena itu soal ruang, bukan soal rail.
- **Banner pelatihan tetap di dalam tiap layar**, jadi rail berjalan setinggi layar di sampingnya.
- **Drawer hanya dari Dashboard di ponsel.** Riwayat dan Pengaturan di ponsel tetap memakai panah kembali.
- **Tanpa dependensi navigasi-testing baru.** Perilaku tumpukan navigasi diverifikasi di emulator; yang diuji unit hanya potongan murni (lihat 6).

## 4. Desain

### 4.1 Model

Berkas baru `ui/navigation/MainNavigation.kt`:

- `enum class MainDestination(val route: String, val label: String)` dengan `KASIR` (`PosDestinations.DASHBOARD`, "Kasir"), `RIWAYAT` (`PosDestinations.HISTORY`, "Riwayat"), `PENGATURAN` (`PosDestinations.SETTINGS`, "Pengaturan"); `companion fun fromRoute(route: String?): MainDestination?`.
- `const val NavRailMinWidthDp = 840` dan `fun showNavRail(windowWidthDp: Int, route: String?): Boolean` = `windowWidthDp >= NavRailMinWidthDp && MainDestination.fromRoute(route) != null`.
- `@Composable fun isWideWindow(): Boolean` = `LocalConfiguration.current.screenWidthDp >= NavRailMinWidthDp`, dipakai Dashboard, Riwayat, dan Pengaturan.

### 4.2 Komponen

- `MainNavRail(current: MainDestination, onSelect: (MainDestination) -> Unit, onLogout: () -> Unit)`: kolom selebar `72.dp * fontScale` (skala font sistem dibatasi 1,0 sampai 1,5, jadi tepat 72dp pada skala normal dan label "Pengaturan" tidak terpotong pada font besar; diverifikasi pada skala 1,3), latar putih, garis pemisah kanan; dari atas `BrandMark` 32dp, lalu tiga item (ikon 24dp di atas label 12sp, target >= 48dp, item aktif bertinta aksen dengan ikon dan label aksen gelap, tidak aktif abu), ruang lentur, lalu "Keluar" di dasar. Semantik `selected` pada item aktif; `contentDescription` memuat label.
- `MainNavDrawerContent(current, onSelect, onLogout)`: item yang sama berformat baris (ikon plus label, tinggi >= 48dp) di dalam `ModalNavigationDrawer`.
- Glyph baru `StorefrontGlyph` (Kasir) dan `LogoutGlyph` di `ui/components/Glyphs.kt`; `HistoryGlyph` dan `SettingsGlyph` dipakai ulang.

### 4.3 Perilaku di `PosNavGraph`

- Keadaan: rute sekarang dari `currentBackStackEntryAsState`, `showRail = showNavRail(screenWidthDp, route)`.
- Tata letak: bila `showRail`, `Row { MainNavRail(...); NavHost(weight 1f) }`; bila tidak, `NavHost` saja. Bila jendela sempit dan rute `DASHBOARD`, `NavHost` dibungkus `ModalNavigationDrawer` (`gesturesEnabled` hanya pada `DASHBOARD`), `DrawerState` diangkat ke grafik dan diteruskan ke `DashboardScreen` sebagai `onOpenMenu: () -> Unit`.
- Pindah tujuan (`navigateToMain(destination)`): `KASIR` = `popBackStack(DASHBOARD, inclusive = false)`; `RIWAYAT` atau `PENGATURAN` bila bukan tujuan aktif = `navigate(route) { popUpTo(DASHBOARD) { inclusive = false }; launchSingleTop = true }`. Hasilnya tumpukan selalu `[dashboard]` atau `[dashboard, tujuan]`: ViewModel Dashboard (keranjang, pembayaran) tidak hilang, dan Back dari tujuan kembali ke Dashboard seperti sekarang. Memilih tujuan yang sudah aktif tidak melakukan apa pun. Drawer ditutup setelah memilih.
- Keluar: `LogoutViewModel` (dipindah dari `DashboardScreen.kt` ke tingkat grafik, `hiltViewModel()`), mendapat `val confirming: StateFlow<Boolean>`, `fun requestLogout()`, `fun cancelLogout()`; `logout(onLoggedOut)` yang ada tetap menjadi aksi konfirmasi dan juga menurunkan `confirming`. Klik "Keluar" di rail atau drawer memanggil `requestLogout()` dan memunculkan `AlertDialog` ("Keluar?", isi "Pesanan yang belum dibayar akan dihapus.", tombol "Keluar" dan "Batal"). Konfirmasi menjalankan `logout`, lalu navigasi ke `LOGIN` dengan `popUpTo(DASHBOARD) { inclusive = true }`, yang membersihkan seluruh tumpukan dari tujuan mana pun. Perilaku `logout` yang ada (hapus keranjang, reset mode pelatihan) tidak berubah.

### 4.4 Layar

- **Dashboard:** `isTablet` dari `isWideWindow()`. `CatalogHeader` kehilangan tombol riwayat, roda gigi, dan Keluar serta parameternya (`onLogout`, `onOpenSettings`, `onOpenHistory`). Tersisa merek (`weight(1f)`), pill sinkron, dan cari. Di ponsel (`!isWideWindow()`) header mendapat tombol menu 48dp (`contentDescription` "Menu") di kiri merek yang memanggil `onOpenMenu`.
- **Riwayat dan Pengaturan:** panah kembali di bar atas hanya tampil bila `!isWideWindow()`; `BackHandler` dan perilaku Back sistem tidak berubah. Riwayat memakai `isWideWindow()` (bukan lebar sisa) untuk memilih dua panel atau satu panel.
- **Inset:** rail memberi padding pada dirinya sendiri (`windowInsetsPadding(WindowInsets.safeDrawing.only(Start + Vertical))`), sedangkan konten di sebelahnya hanya mengonsumsi inset **Start** (`consumeWindowInsets`) supaya `safeDrawing` di Dashboard, Riwayat, dan Pengaturan tidak menambah padding kiri ganda. Inset vertikal tidak dikonsumsi karena tiap layar masih membutuhkan padding status bar dan bilah navigasi sendiri (koreksi dari tulisan awal spec "Start + Vertical", diputuskan saat implementasi dan dikonfirmasi reviewer).
- **Pembayaran dan dialog layar penuh:** menutupi rail seperti menutupi layar lainnya; tidak ada perpindahan saat membayar.
- **Login dan Splash:** tanpa rail dan tanpa drawer.

## 5. Penanganan kondisi khusus

- Memilih tujuan yang sedang aktif: tidak ada navigasi, drawer tetap ditutup.
- Rotasi atau perubahan lebar melewati 840dp saat di Riwayat atau Pengaturan: rail muncul atau hilang, panah kembali menyesuaikan; tumpukan tidak berubah.
- Ketuk Keluar saat dialog konfirmasi sudah terbuka atau saat logout berjalan: diabaikan (satu `confirming`, `logout` memakai `viewModelScope` yang ada).
- Proses dibunuh dan dibuka ulang: tidak ada keadaan baru yang disimpan (drawer dan dialog tertutup).
- Mode pelatihan menyala: banner tampil di atas konten tiap layar, rail tetap setinggi layar.

## 6. Pengujian

Tes unit JVM (TDD, gagal dulu):
- `MainDestination.fromRoute`: tiga rute utama ke tujuan yang benar; `login`, `splash`, `null`, dan rute asing ke `null`.
- `showNavRail`: tablet pada tiga rute utama = true; lebar 839 = false dan 840 = true; Login dan Splash pada lebar tablet = false; `null` = false.
- `LogoutViewModel`: `requestLogout` menaikkan `confirming`, `cancelLogout` menurunkannya tanpa logout atau mengosongkan keranjang, `logout` menurunkannya, mengosongkan keranjang, dan mereset mode pelatihan (tes yang ada tetap hijau).

Verifikasi emulator (tablet, lalu ukuran ponsel):
1. Rail tampil di Dashboard, Riwayat, dan Pengaturan dengan item aktif yang benar; tidak ada di Login.
2. Header Dashboard longgar dan merek tidak terpotong; tombol lama hilang.
3. Dengan keranjang berisi, pindah Riwayat lalu Pengaturan lalu Kasir: keranjang utuh; Back dari Riwayat dan Pengaturan kembali ke Dashboard; Back di Dashboard keluar app.
4. Keluar menampilkan konfirmasi; Batal tidak melakukan apa pun; Keluar membersihkan keranjang dan kembali ke Login dari ketiga layar.
5. Panah kembali hilang di Riwayat dan Pengaturan saat rail tampil.
6. Banner pelatihan berdampingan dengan rail; penjualan latihan tetap tidak muncul di riwayat.
7. Ponsel (1080x2400): tombol menu membuka drawer dengan empat item; Riwayat dan Pengaturan memakai panah kembali; header Dashboard muat.
8. Jendela 840 sampai 911dp (mis. `wm size 880x1600` dengan density disesuaikan): rail tampil dan Dashboard memakai tata letak tablet (bukan ponsel).

Tidak diverifikasi di fase ini: TalkBack, perangkat fisik.

## 7. Urutan kerja

Satu plan, task kecil: model dan aturan murni, `LogoutViewModel` dan dialog, rail dan drawer (komponen dan glyph), pemasangan di `PosNavGraph` dan perapian Dashboard, Riwayat, dan Pengaturan, lalu verifikasi emulator, roadmap, dan tinjauan satu cabang di akhir. Subagent: Sonnet 5.5.
