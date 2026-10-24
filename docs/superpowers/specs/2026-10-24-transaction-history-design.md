# Fase 7: Riwayat Transaksi - Design

Tanggal: 2026-10-24. PRD: 4.11 (Transaction History). Acuan UI: desain pemilik, artboard "Riwayat" (`History-Default`, `History-Yesterday`, `History-Empty`, scene `history*` di `Main.dc.html`) pada kanvas "RestoPOS Tablet" (claude.ai/artifact/JjFZB66sa11r7k1Cbetivz). Tidak ada migrasi Room dan tidak ada SQL Supabase.

## 1. Tujuan

Kasir bisa membuka daftar penjualannya di perangkat ini (terbaru di atas), mencari satu penjualan, dan melihat rinciannya, termasuk penjualan yang belum terunggah. Hanya membaca: tanpa edit, refund, atau void (non-goal PRD).

## 2. Di luar cakupan

- Cetak ulang struk dan status printer (Fase 9). Tombol hanya ditampilkan nonaktif.
- Nomor urut pesanan "ORD-001" dari desain (aplikasi belum punya nomor pesanan; lihat 3).
- Nama kasir (tidak tersimpan; lihat 3).
- Transaksi lebih lama dari kemarin (lihat 3; masuk backlog).
- Perubahan skema Room, `submit_transaction`, atau alur simpan/sinkron.

## 3. Keputusan yang sudah diambil

Jawaban pemilik atas empat pertanyaan, ditambah kesimpulan dari kode:

- **Cakupan data:** hanya penjualan kasir yang sedang login, dan tidak pernah transaksi latihan. Sama seperti pill sinkron; badge "Menunggu sinkron" jadi selalu benar karena penjualan kasir lain baru terunggah saat kasir itu login. Desain menampilkan dua kasir di satu daftar; itu tidak diikuti.
- **Nomor pesanan = kode pendek dari UUID:** `"#" + id.replace("-", "").take(6).uppercase()` (mis. `#A3F9C1`). Tanpa migrasi, stabil, dan sama di perangkat dan di server. Tidak berurutan. Tampil di baris daftar dan judul detail, dan bisa dicari.
- **Baris "Kasir:" dihapus:** daftar hanya milik kasir yang login, dan nama memang tidak tersimpan.
- **Periode:** hanya tab "Hari ini" dan "Kemarin", sesuai desain. Penjualan yang lebih lama tetap tersimpan dan tersinkron tetapi belum bisa dibuka (backlog: tab "Semua" atau pemilih tanggal).
- **Cetak ulang:** footer detail ada dengan tombol nonaktif berlabel "Cetak Ulang Struk (segera hadir)", seperti tombol "Cetak struk" di layar sukses. Teks status printer dan tombol "Buka Pengaturan" dari desain tidak dibuat (Fase 9).

Rulings saya di luar jawaban itu:

- **Badge sinkron ditambahkan** (PRD 4.11 mewajibkannya, desain hanya punya "Selesai"): baris `PENDING` mendapat chip amber "Menunggu sinkron" di daftar dan di detail, di samping chip hijau "Selesai". `SYNCED` tidak diberi chip tambahan.
- **Subtotal dan pajak ditambahkan di detail** (PRD 4.11 menyebut keduanya, desain tidak): baris "Subtotal" dan "<label> <persen>%" (mis. "Pajak 10%") di atas "Jumlah item".
- **Ponsel:** desain hanya untuk tablet. Di lebar sempit daftar tampil penuh; mengetuk baris menampilkan detail menggantikan daftar, Back kembali ke daftar.

## 4. Desain

### 4.1 Data

`TransactionDao` mendapat satu query:

```kotlin
data class TransactionWithLines(
    @Embedded val header: TransactionEntity,
    @Relation(parentColumn = "id", entityColumn = "transactionId") val lines: List<TransactionLineEntity>,
)

@Transaction
@Query("SELECT * FROM transactions WHERE cashierId = :cashierId AND isTraining = 0 AND createdAt >= :fromMillis ORDER BY createdAt DESC, id")
abstract fun observeHistory(cashierId: String, fromMillis: Long): Flow<List<TransactionWithLines>>
```

`@Relation` tidak menjamin urutan, jadi baris diurutkan di kode menurut angka di akhir `id` (`"<saleId>:<index>"`).

`TransactionRepository` mendapat `fun observeHistory(fromMillis: Long): Flow<List<HistoryEntry>>` (kasir diambil dari `currentCashierId`, seperti `syncSummary`; tanpa kasir mengembalikan daftar kosong) dengan model:

```kotlin
data class HistoryLine(val name: String, val meta: String, val quantity: Int, val unitPrice: Long, val subtotal: Long)
data class HistoryEntry(
    val id: String, val code: String, val createdAt: Long,
    val lines: List<HistoryLine>, val itemCount: Int,
    val subtotal: Long, val taxLabel: String, val taxBasisPoints: Int, val taxAmount: Long,
    val grandTotal: Long, val cashReceived: Long, val changeAmount: Long,
    val isPending: Boolean,
)
```

Pemetaan: `unitPrice = basePrice + selections.sumOf(priceDelta)`; `meta` = nama opsi terpilih digabung " · " (kosong jika tanpa opsi); `itemCount` = jumlah `quantity`; `isPending = syncStatus == PENDING`. Jika `selectionsJson` rusak, baris tetap tampil dengan `meta` kosong (tidak crash).

### 4.2 `HistoryViewModel`

`@HiltViewModel` dengan `TransactionRepository` dan sumber waktu `now: () -> Long` serta `TimeZone` yang dapat disuntik (default `System.currentTimeMillis`, `TimeZone.getDefault()`), supaya batas hari bisa dites.

- `uiState`: `day` (`TODAY` | `YESTERDAY`), `query`, `selectedId`, daftar baris tampil, ringkasan, detail terpilih.
- Batas hari dihitung dari `now()` dan zona waktu perangkat: awal hari ini 00:00, awal kemarin 00:00 satu hari sebelumnya. Repository dipanggil dengan `fromMillis = awal kemarin`; `Hari ini` = `createdAt >= awal hari ini`, `Kemarin` = antara awal kemarin (inklusif) dan awal hari ini (eksklusif). Dihitung ulang pada setiap emisi Room dan setiap perubahan tab atau pencarian; layar yang dibiarkan terbuka melewati tengah malam tanpa penjualan baru tetap berlabel lama sampai ada interaksi (dapat diterima, dicatat).
- Pencarian: huruf besar-kecil diabaikan, di-trim, cocok dengan kode pesanan (dengan atau tanpa "#") atau nama item di baris mana pun. Mengubah pencarian mengosongkan `selectedId`.
- Pilihan: jika `selectedId` kosong atau tidak ada di daftar tampil, baris pertama dianggap terpilih (seperti desain); `null` hanya jika daftar kosong.
- Ringkasan header: `"<N> transaksi · <Rp total>"` untuk tab terpilih sebelum pencarian (jumlah `grandTotal`), persis format desain.
- `Flow` Room membuat badge berubah otomatis saat sinkron selesai.

### 4.3 UI

- Rute `PosDestinations.HISTORY = "history"`. Entri: ikon riwayat (panah putar dengan jarum jam, 48dp, `contentDescription` "Riwayat transaksi") di header katalog, tepat sebelum roda gigi, di layout lebar dan sempit; brand tetap `weight(1f)`.
- Layar penuh: bar atas 56dp (tombol kembali "Kembali ke kasir", judul "Riwayat transaksi", ringkasan di kanan), isi dua panel pada lebar tablet:
  - **Kiri (520dp, putih):** tab "Hari ini" / "Kemarin" dan kolom cari "No. pesanan atau menu" (240dp); baris: kode dan total (tebal) di atas, `"<HH.mm> · <N> item"`, nama item dipotong satu baris, chip "Menunggu sinkron" bila perlu; baris terpilih ditandai warna aksen muda. Format jam dengan `SimpleDateFormat("HH.mm", Locale.US)` (tanpa `java.time`, aturan minSdk 24).
  - **Kanan:** kartu detail: kode, `"<Hari ini|Kemarin> · <HH.mm>"`, chip "Selesai" (dan "Menunggu sinkron"); daftar baris (nama, meta, `<qty> × <harga satuan>`, subtotal); lalu Subtotal, Pajak, Jumlah item, Total (besar), Tunai diterima, Kembalian; footer dengan tombol nonaktif "Cetak Ulang Struk (segera hadir)".
- Keadaan kosong (teks persis): tab tanpa transaksi: "Belum ada transaksi" + "Penjualan <hari ini|kemarin> akan muncul di sini."; pencarian tanpa hasil: "Transaksi tidak ditemukan" + "Coba nomor pesanan atau nama menu lain."; panel kanan tanpa pilihan: "Pilih transaksi untuk melihat rincian."
- Lebar sempit: hanya daftar; ketuk baris membuka detail menggantikan daftar (`BackHandler` kembali ke daftar, tombol kembali di bar atas mengikuti).
- Warna dan tipografi mengikuti token tema yang sudah ada (aksen, `SurfaceWhite`, garis, chip hijau "Selesai", amber pill sinkron). Kontras teks chip >= 4.5:1.

## 5. Penanganan kondisi khusus

- Tanpa kasir login: daftar kosong, tanpa crash.
- Kasir lain login di perangkat yang sama: tidak melihat data kasir sebelumnya (query dibatasi `cashierId`); `HistoryViewModel` dibuat per entri rute sehingga id kasir diambil saat layar dibuka.
- Penjualan baru atau perubahan status sinkron saat layar terbuka: daftar dan badge ikut berubah; pilihan tetap pada id yang sama.
- `selectionsJson` rusak: baris tampil tanpa meta.
- Transaksi latihan: tidak muncul (tidak disimpan sejak 6a, dan query memfilter `isTraining = 0` sebagai lapisan kedua).
- Dua penjualan dengan kode pendek sama (peluang sangat kecil, 1 banding 16,7 juta): keduanya tampil dengan kode yang sama; `id` penuh tetap kunci pilihan, jadi tidak ada salah buka.

## 6. Pengujian

Tes unit JVM (TDD, gagal dulu):
- DAO (Robolectric): urutan terbaru di atas dengan tie-break `id`; hanya kasir yang diminta; `isTraining = 1` tidak ikut; `fromMillis` inklusif; baris diurutkan menurut indeks.
- Pemapar: `unitPrice` dengan delta opsi, `meta` digabung " · ", `itemCount`, `isPending`, kode pendek (huruf besar, tanpa tanda hubung, 6 karakter), `selectionsJson` rusak tidak crash.
- `HistoryViewModel` (repository palsu, jam dan zona waktu disuntik): pembagian Hari ini/Kemarin di batas 23:59:59.999 / 00:00:00.000, ringkasan "N transaksi · Rp...", pencarian per kode (dengan dan tanpa "#") dan per nama item, baris pertama terpilih otomatis dan kembali otomatis saat pilihan hilang karena pencarian, tab tanpa data versus pencarian tanpa hasil, status `isPending` ikut.

Verifikasi emulator (tablet, lalu ukuran ponsel):
1. Ikon riwayat membuka layar; daftar "Hari ini" memuat penjualan hari ini terbaru di atas dengan kode, jam, item, total.
2. Penjualan baru muncul tanpa membuka ulang; badge "Menunggu sinkron" berubah hilang setelah unggah.
3. Pencarian per kode dan per menu; tidak ada hasil menampilkan teks yang benar.
4. Detail menampilkan item, opsi, subtotal, pajak, total, tunai, kembalian dan tombol cetak nonaktif.
5. Mode Pelatihan: penjualan latihan tidak muncul.
6. Ganti kasir (logout dan login lain): tidak ada data kasir sebelumnya (diverifikasi dengan data uji, bila ada akun kedua; jika tidak, dinyatakan tidak diverifikasi).
7. Lebar ponsel: header dengan dua ikon muat, daftar lalu detail, Back kembali ke daftar.

Tidak diverifikasi di fase ini: TalkBack, perangkat fisik.

## 7. Urutan kerja

Satu plan, task kecil: data (DAO, model, repository), `HistoryViewModel`, layar dan navigasi (panel tablet dan alur ponsel), verifikasi emulator dan roadmap, tinjauan satu cabang di akhir. Subagent: Sonnet 5.5.
