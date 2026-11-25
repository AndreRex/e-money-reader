# Desain Aplikasi Android: TapSaldo (NFC E-Money Card Reader)

**Tanggal:** 2026-11-25  
**Proyek:** `emoneyreader` (`com.example.e_money_reader`)  
**Status:** Siap Review Final (Menunggu Persetujuan)

---

## 1. Tujuan & Latar Belakang (Intent & Why)
Membangun aplikasi Android pembaca kartu e-money berbasis NFC bernama **TapSaldo** yang tidak hanya membaca sisa saldo sekali lewat, tetapi juga:
1. **Mengelola banyak kartu** dalam satu dompet digital lokal.
2. **Menamai kartu secara custom** (*custom card label*) agar kartu pribadi, tol, KRL, atau keluarga mudah dibedakan.
3. **Mengingat dan menggabungkan riwayat transaksi** di penyimpanan lokal HP, karena chip kartu e-money fisik umumnya hanya menyimpan saldo terakhir dan sekitar 10–15 transaksi terbaru sebelum tertimpa.

---

## 2. Goals, Non-Goals, dan Anti-Goals

### Goals
- Mengadopsi penuh UI/UX prototipe **TapSaldo** (tema gelap matte, *floating pill bottom navigation*, 5 layar/modal utama).
- Membaca kartu e-money via NFC Android (`IsoDep` / `NfcA`) baik melalui layar **Scan NFC Card** maupun **auto-detect** saat aplikasi sedang terbuka.
- Menyimpan daftar kartu, status kartu aktif, nama custom, saldo terakhir, dan gabungan riwayat transaksi secara lokal menggunakan **Room** (100% *offline-first*).
- Melakukan deduplikasi transaksi setiap kali kartu yang sama di-scan ulang agar riwayat lama tetap tersimpan tanpa duplikasi.

### Non-Goals (Fase 1)
- Fitur top-up saldo online atau integrasi API perbankan resmi.
- Sinkronisasi cloud, login akun, atau backup server.
- Widget home screen, notifikasi pengingat saldo minimum, atau grafik analitik pengeluaran bulanan (akan dipertimbangkan pada fase berikutnya).

### Anti-Goals
- Menghapus atau menimpa riwayat transaksi lama di HP setiap kali kartu di-scan ulang.
- Aplikasi bergantung pada koneksi internet untuk menampilkan saldo terakhir atau riwayat tersimpan.

---

## 3. Batasan Teknis (Constraints)
- **Platform:** Android Kotlin, `minSdk = 24`, `targetSdk = 37`, AGP `9.5.2`.
- **Variasi Chip E-Money Indonesia:** Kartu seperti Mandiri e-Money, BCA Flazz, BNI TapCash, dan BRI Brizzi memiliki struktur APDU dan ketersediaan log chip yang berbeda-beda (sebagian menyediakan log transaksi mentah, sebagian lain hanya mengekspos UID dan saldo atau memerlukan perintah khusus). Arsitektur parser harus modular per penerbit, dengan mekanisme *fallback* mencatat selisih saldo (*balance delta*) sebagai entri transaksi bila chip tidak menyediakan log transaksi rinci.
- **Perangkat Tanpa NFC / NFC Nonaktif:** Aplikasi harus tetap bisa dibuka untuk melihat data tersimpan, dan menampilkan status/arahan mengaktifkan NFC di pengaturan perangkat saat pengguna mencoba melakukan scan.

---

## 4. Pendekatan Implementasi (Approaches)

### Pendekatan yang Dipertimbangkan
1. **XML Views + Material 3 + Room Database:** Mempertahankan sistem XML bawaan template proyek saat ini. *Trade-off:* Minim perubahan konfigurasi Gradle, tetapi mereplikasi komponen modern TapSaldo (*floating glassmorphic pill nav*, kartu gradien, animasi gelombang NFC, filter reaktif) membutuhkan banyak boilerplate XML dan *adapter*.
2. **Jetpack Compose + Material 3 + Room Database + Modular NFC Reader (Dipilih):** Mengaktifkan Jetpack Compose pada `app/build.gradle.kts` untuk membangun seluruh UI TapSaldo secara deklaratif, dipadukan dengan Room dan modul parser NFC APDU. *Trade-off:* Membutuhkan setup Compose di Gradle di awal, namun jauh lebih presisi terhadap prototipe TapSaldo, lebih ringkas, dan mudah dikembangkan untuk fitur-fitur lanjutan.

---

## 5. Spesifikasi UI/UX (Blueprint TapSaldo)

### 5.1 Design System & Tema
- **Latar Utama:** Matte dark (`#0B0B0E` / `#121215`)
- **Surface / Card / Sheet:** Dark charcoal (`#1A1A1F` – `#1F1F26`) dengan border tipis (`#2A2A32`)
- **Primary CTA:** Putih kontras tinggi (`#FFFFFF`) dengan teks gelap
- **Aksen:** Emerald green (`#22C55E`) untuk status aktif/NFC/top-up; Lavender (`#9188F2`) untuk aksi sekunder & ikon edit; Merah lembut (`#F87171`) untuk pengeluaran
- **Artwork Kartu:** Gradien navy biru + ikon chip emas (Mandiri e-Money), gradien teal gelap (BCA Flazz), aksen coklat hangat (BNI TapCash), dan biru/oranye netral untuk penerbit lain/tak dikenal
- **Navigasi Utama:** Tanpa *bottom navigation bar* (`Home` / `My Cards` dihapus). Navigasi antar kartu dilakukan langsung dengan **swipe horizontal** pada kartu di halaman utama (dilengkapi indikator *pager* di atas), yang secara otomatis memperbarui saldo & daftar transaksi pada panel di bawahnya.

### 5.2 Layar & Alur Utama
1. **Home (Swipeable Multi-Card Pocket)**
   - *Top Bar (Sticky & Minimalis)*: Kosong tanpa tombol di kiri/kanan—hanya menampilkan **indikator pager kartu** (`▬ • •`) di tengah agar tampilan tetap bersih dan minimalis (aksi tambah/scan kartu dilakukan lewat tombol `Scan NFC Card` di Bubble 1 atau *auto-scan*).
   - *Active Card (Horizontal Pager)*: Kartu visual **vertikal/portrait** di tengah yang bagian bawahnya **terbelah/terselip setengah** di balik bubble pertama saat scroll berada di posisi paling atas. Bagian atas kartu menampilkan penerbit, nama custom, chip emas, 4 digit terakhir, dan tombol `Detail` di pojok kanan atas. Menggeser kartu ke kiri/kanan berpindah ke kartu lain secara langsung.
   - *Separate Foreground Bubbles (Scrollable Up)*: Konten di bawah kartu tersusun atas **bubble-bubble terpisah per fungsi** (dengan jarak antar-bubble), bukan satu panel menyatu:
     - *Bubble Saldo & Scan*: Menampilkan **saldo kartu aktif** (`Rp 148.500`), pill `• Scanned ... ago`, dan tombol putih `Scan NFC Card`.
     - *Bubble Aksi Kartu*: Baris cepat untuk mengubah label kartu (`Edit card name & details >`).
     - *Bubble Riwayat Transaksi*: Dikelompokkan per hari (`Today`, `Yesterday`, dst.) menampilkan daftar transaksi milik kartu tersebut (tap transaksi mana saja untuk ubah nama custom & kategori).
   - *Perilaku Scroll*: Saat pengguna melakukan scroll ke bawah untuk membaca riwayat transaksi, tumpukan bubble bergerak ke atas **menutupi dan menyembunyikan kartu** hingga hanya menyisakan *top bar* pager di paling atas (seperti referensi Gambar 3). Jika belum ada kartu tersimpan, tampilkan *empty state* yang mengajak scan kartu pertama.
2. **Card Details (Ekspansi dari tombol `Detail` di Kartu)**
   - *Header*: Tombol kembali / `Close`, judul `Card Details`, ikon tag di kanan atas, dan ikon hapus kartu (dengan dialog konfirmasi).
   - *Ringkasan Saldo*: Visual kartu penuh di atas + panel `Current Stored Balance` dengan pill `• Saved`, kontrol `Set as Active / Primary`, dan waktu scan terakhir.
   - *Baris Card Label*: Menampilkan nama custom kartu saat ini; **baik ikon tag di kanan atas maupun baris `Edit >` membuka modal Edit Card Label**.
   - *Card Tap History*: Filter chip kategori (`All`, `Transit`, `Toll`, `Top-up`) dan daftar riwayat transaksi kartu tersebut beserta catatan footer tentang penyimpanan chip offline. Secara default transaksi baru bernama `Transaksi #N` (berurutan per kartu), dan **pengguna dapat men-tap transaksi mana saja untuk mengubah nama custom transaksi serta kategorinya**.
3. **Edit Card Label & Edit Transaction Modal (Bottom Sheet)**
   - *Edit Card Label*: Preview kartu mini, input teks `Custom Card Label`, chip saran cepat (`Primary Commute`, `Daily Commute`, `Tol Trans Jawa`, `Family Backup`, `Office Transit`), catatan privasi penyimpanan lokal, dan tombol putih `Save Label`.
   - *Edit Transaction*: Input teks untuk nama custom transaksi (menggantikan default `Transaksi #N`) dan pilihan chip kategori (`Transit`, `Toll`, `Top-up`, `Other`) agar filter di *Card Details* tetap akurat.
5. **Scan NFC Card & Konfirmasi**
   - *Layar Tunggu Scan*: Animasi gelombang NFC konsentris hijau, teks `"Hold card against phone back"`, daftar kompatibilitas penerbit, pill status antena (`● Antenna Active · 13.56 MHz` atau status NFC mati), kartu intipan di bawah, dan tombol `Cancel`.
   - *Auto-Scan*: Saat aplikasi terbuka di *foreground* (dijeda sementara ketika modal edit sedang terbuka agar tidak mengganggu input), Android NFC Foreground Dispatch aktif sehingga menempelkan kartu langsung memicu pembacaan.
   - *Layar Sukses & Konfirmasi Kartu Aktif*: Ikon centang hijau konsentris, `"Read successfully"`, nama kartu + saldo terbaru. Jika kartu yang di-scan berbeda dari kartu aktif saat ini, tersedia tombol **Confirm & Set as Active** untuk memastikan kartu tersebut menjadi kartu aktif di *Home*, serta tombol **Done / Simpan Saja** untuk kembali tanpa mengganti kartu aktif.
   - *Penanganan Kartu Tidak Terbaca / Terenkripsi (Keputusan Teknis)*: Jika saldo kartu gagal dibaca secara offline (mis. kartu non-e-money atau varian terenkripsi yang tidak didukung), tampilkan pesan error yang jelas beserta tombol coba lagi—kartu tidak disimpan ke daftar agar dompet tetap bersih.

---

## 6. Arsitektur Data & Pembacaan NFC

### 6.1 Skema Penyimpanan Lokal (Room)
- **`CardEntity`**: `uid` (Primary Key), `cardNumber`, `issuer`, `customLabel`, `lastBalance`, `lastScannedAt`, `isActive`.
- **`TransactionEntity`**: `id` (Primary Key), `cardUid` (Foreign Key dengan `onDelete = CASCADE` untuk mendukung penghapusan kartu), `dedupKey` (unik per kartu untuk mencegah duplikasi), `sequenceNumber` (urutan transaksi per kartu untuk nama default `Transaksi #N`), `customTitle` (nullable, diisi jika pengguna mengubah nama transaksi), `amount` (bertanda +/-), `balanceAfter`, `category` (`TRANSIT`, `TOLL`, `TOPUP`, `OTHER`), `timestamp`, `source` (`CHIP_LOG`, `BALANCE_DELTA`).

### 6.2 Alur Scan & Deduplikasi
1. Saat tag NFC (`IsoDep` / `NfcA`) terdeteksi, baca `uid` dan identifikasi penerbit kartu menggunakan parser modular per penerbit. Jika saldo gagal dibaca, hentikan proses dan tampilkan pesan error di layar scan.
2. Jika `uid` baru, buat `CardEntity` baru dengan nama default (mis. nama penerbit) dan jadikan aktif jika belum ada kartu aktif.
3. Jika `uid` sudah tersimpan, perbarui `lastBalance` dan `lastScannedAt` (perubahan `isActive` hanya terjadi jika pengguna menekan **Confirm & Set as Active** pada layar hasil scan atau `Set as Active` di *My Cards*).
4. Gabungkan log transaksi dari chip ke `TransactionEntity` menggunakan `dedupKey` agar entri lama tidak hilang, nama custom yang sudah diubah pengguna tidak tertimpa, dan entri yang sama tidak tercatat ganda. Transaksi baru diberi `sequenceNumber` berurutan (`Transaksi #N`) dengan kategori default `TOPUP` (jika `amount > 0`) atau `OTHER` (jika `amount <= 0`). Jika chip tidak menyediakan log tetapi `lastBalance` berubah dari scan sebelumnya, buat satu entri `BALANCE_DELTA` otomatis.

---

## 7. Yang Diserahkan ke Implementer (Left to the Builder)
- Pemilihan versi library spesifik (Compose BOM, Room, KSP, Navigation Compose) yang kompatibel dengan AGP `9.5.2` dan Kotlin di proyek ini.
- Implementasi detail perintah APDU (`SELECT AID`, `READ RECORD` / perintah saldo & log) untuk masing-masing penerbit kartu e-money Indonesia, disertai unit test dengan payload hex tiruan serta mode simulasi/mock NFC opsional untuk pengujian di emulator.
