# TapSaldo E-Money NFC Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Membangun aplikasi Android **TapSaldo** (pembaca kartu e-money NFC offline-first) dengan Jetpack Compose, Room Database, dan parser APDU modular sesuai desain Figma & dokumen spesifikasi.

**Architecture:** Aplikasi menggunakan arsitektur single-activity Jetpack Compose dengan `TapSaldoViewModel` yang mengelola state dari `CardRepository` (Room) dan `EmoneyReaderManager` (NFC `IsoDep` APDU). Layar utama menggunakan `HorizontalPager` kartu vertikal yang bagian bawahnya tertutup oleh *separate foreground bubbles* yang dapat di-scroll ke atas hingga menutupi kartu, ditambah layar `CardDetails`, modal `EditCardLabel` & `EditTransaction`, serta alur `ScanNfc` dengan konfirmasi *Set as Active*.

**Tech Stack:** Kotlin, AGP `9.5.2`, `minSdk = 24`, `targetSdk = 37`, Jetpack Compose (Material 3), Navigation Compose, Room + KSP, Coroutines/Flow, Android NFC (`NfcAdapter`, `IsoDep`), JUnit4.

**Spec:** [2026-11-25-tapsaldo-emoney-reader-design.md](file:///c:/My%20Code/android_studio/emoneyreader/docs/superpowers/specs/2026-11-25-tapsaldo-emoney-reader-design.md)

## Global Constraints
- `minSdk = 24`, `targetSdk = 37`, Kotlin, AGP `9.5.2`, package `com.example.e_money_reader`.
- 100% *offline-first*: tidak ada panggilan jaringan; semua kartu dan riwayat tersimpan di Room lokal.
- Tanpa *bottom navigation bar* (`Home` / `My Cards`); bagian atas layar utama minimalis tanpa tombol kiri/kanan (hanya indikator pager di tengah).
- Transaksi baru bernama default `Transaksi #N` berurutan per kartu, dapat di-rename secara custom oleh pengguna, dan nama custom tidak boleh tertimpa saat scan ulang.
- Kartu yang saldonya gagal dibaca secara offline ditolak dengan pesan error dan tidak disimpan ke database.

## Review Focus
- **Kartu tidak terbaca / non-e-money / terenkripsi:** `EmoneyReaderManager` harus mengembalikan `ReadResult.Error` tanpa menulis ke Room.
- **Scan ulang kartu yang sama dengan log chip bertumpuk:** `CardRepository.mergeScanResult` harus melakukan deduplikasi berdasarkan `dedupKey` dan mempertahankan `customTitle` serta `category` yang sudah diedit pengguna.
- **Kartu tanpa log chip (hanya saldo):** Jika `lastBalance` berubah dibanding scan sebelumnya dan chip tidak mengembalikan log, `CardRepository` harus membuat tepat satu transaksi `BALANCE_DELTA`.
- **Auto-scan saat modal edit terbuka:** `MainActivity` harus menjeda `enableForegroundDispatch` saat modal edit label/transaksi aktif agar input teks tidak terpotong.
- **Menghapus kartu aktif:** `CardRepository.deleteCard` harus menghapus transaksi via `CASCADE` dan otomatis mempromosikan kartu lain sebagai aktif (atau kembali ke *empty state* bila kosong).

---

### Task 1: Setup Gradle Dependencies (Compose, Room, KSP, Navigation) & NFC Manifest

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: Konfigurasi build Compose + Room + KSP + Navigation + izin `<uses-permission android:name="android.permission.NFC" />`.

- [ ] **Step 1: Update `gradle/libs.versions.toml` dan `build.gradle.kts`**
  Tambahkan versi & library untuk Compose BOM, `material3`, `material-icons-extended`, `navigation-compose`, `lifecycle-viewmodel-compose`, `room-runtime`, `room-ktx`, `room-compiler`, serta plugin `kotlin-compose` dan `ksp` yang kompatibel dengan AGP `9.5.2`.

- [ ] **Step 2: Update `app/build.gradle.kts`**
  Aktifkan `buildFeatures { compose = true; buildConfig = true }`, terapkan plugin Compose & KSP, dan tambahkan semua dependensi Compose, Navigation, Room, serta `kotlinx-coroutines-test` / `junit` untuk unit test.

- [ ] **Step 3: Update `AndroidManifest.xml` & `strings.xml`**
  Tambahkan `<uses-permission android:name="android.permission.NFC" />`, `<uses-feature android:name="android.hardware.nfc" android:required="false" />`, dan ubah `app_name` menjadi `TapSaldo`.

- [ ] **Step 4: Verifikasi build awal**
  Run: `.\gradlew.bat assembleDebug`
  Expected: `BUILD SUCCESSFUL`

---

### Task 2: Domain Enums, Room Entities & DAO

**Files:**
- Create: `app/src/main/java/com/example/e_money_reader/data/local/CardModels.kt`
- Create: `app/src/main/java/com/example/e_money_reader/data/local/TapSaldoDao.kt`
- Create: `app/src/main/java/com/example/e_money_reader/data/local/TapSaldoDatabase.kt`

**Interfaces:**
- Produces:
  - `enum class CardIssuer { MANDIRI, BCA_FLAZZ, BNI_TAPCASH, BRI_BRIZZI, UNKNOWN }`
  - `enum class TransactionCategory { TRANSIT, TOLL, TOPUP, OTHER }`
  - `enum class TransactionSource { CHIP_LOG, BALANCE_DELTA }`
  - `@Entity data class CardEntity(uid: String, cardNumber: String, issuer: CardIssuer, customLabel: String, lastBalance: Long, lastScannedAt: Long, isActive: Boolean)`
  - `@Entity data class TransactionEntity(id: Long = 0, cardUid: String, dedupKey: String, sequenceNumber: Int, customTitle: String?, amount: Long, balanceAfter: Long?, category: TransactionCategory, timestamp: Long, source: TransactionSource)`
  - `interface TapSaldoDao` dengan Flow queries (`observeCards()`, `observeTransactionsForCard(cardUid: String)`), suspend CRUD (`upsertCard`, `clearActiveFlags`, `setActiveCard`, `deleteCardByUid`, `insertTransactionsIgnore`, `updateTransaction`, `getMaxSequenceNumber`).

- [ ] **Step 1: Buat `CardModels.kt` dengan semua enum dan Room `@Entity`**
  Pastikan `TransactionEntity` memiliki `ForeignKey` ke `CardEntity` dengan `onDelete = ForeignKey.CASCADE` dan index unik `(cardUid, dedupKey)`.

- [ ] **Step 2: Buat `TapSaldoDao.kt` dan `TapSaldoDatabase.kt`**
  Tambahkan `TypeConverters` untuk enum `CardIssuer`, `TransactionCategory`, dan `TransactionSource`.

- [ ] **Step 3: Verifikasi kompilasi Room & KSP**
  Run: `.\gradlew.bat compileDebugKotlin`
  Expected: `BUILD SUCCESSFUL`

---

### Task 3: Modular NFC APDU Parsers & Unit Tests

**Files:**
- Create: `app/src/main/java/com/example/e_money_reader/nfc/NfcModels.kt`
- Create: `app/src/main/java/com/example/e_money_reader/nfc/ApduTransceiver.kt`
- Create: `app/src/main/java/com/example/e_money_reader/nfc/EmoneyParser.kt`
- Create: `app/src/main/java/com/example/e_money_reader/nfc/MandiriEmoneyParser.kt`
- Create: `app/src/main/java/com/example/e_money_reader/nfc/BcaFlazzParser.kt`
- Create: `app/src/main/java/com/example/e_money_reader/nfc/BniTapCashParser.kt`
- Create: `app/src/main/java/com/example/e_money_reader/nfc/BriBrizziParser.kt`
- Create: `app/src/main/java/com/example/e_money_reader/nfc/EmoneyReaderManager.kt`
- Test: `app/src/test/java/com/example/e_money_reader/nfc/EmoneyParserTest.kt`

**Interfaces:**
- Consumes: `CardIssuer`, `TransactionSource` (Task 2)
- Produces:
  - `interface ApduTransceiver { val uidHex: String; fun transceive(command: ByteArray): ByteArray }`
  - `data class ChipTransaction(val dedupKey: String, val amount: Long, val balanceAfter: Long?, val timestamp: Long)`
  - `data class ParsedCardData(val uid: String, val cardNumber: String, val issuer: CardIssuer, val balance: Long, val chipTransactions: List<ChipTransaction>)`
  - `sealed interface ReadResult { data class Success(val data: ParsedCardData) : ReadResult; data class Error(val message: String) : ReadResult }`
  - `class EmoneyReaderManager(parsers: List<EmoneyParser>) { fun readCard(transceiver: ApduTransceiver): ReadResult }`

- [ ] **Step 1: Tulis failing unit tests di `EmoneyParserTest.kt`**
  Uji kasus: (a) Mandiri e-Money APDU valid mengembalikan saldo + nomor kartu + log transaksi; (b) kartu tidak dikenal/terenkripsi mengembalikan `ReadResult.Error`; (c) parser mengembalikan saldo tanpa log transaksi chip (`chipTransactions.isEmpty()`).

- [ ] **Step 2: Jalankan test untuk memastikan gagal**
  Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.e_money_reader.nfc.EmoneyParserTest"`
  Expected: FAIL (class belum ada)

- [ ] **Step 3: Implementasi `NfcModels.kt`, `ApduTransceiver.kt`, `EmoneyParser.kt`, keempat parser penerbit, dan `EmoneyReaderManager.kt`**
  Implementasikan `IsoDepApduTransceiver` untuk runtime Android dan parser APDU modular untuk Mandiri, BCA Flazz, BNI TapCash, serta BRI Brizzi.

- [ ] **Step 4: Jalankan unit test untuk verifikasi lulus**
  Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.e_money_reader.nfc.EmoneyParserTest"`
  Expected: `BUILD SUCCESSFUL` / all tests PASS

---

### Task 4: CardRepository, Deduplication & `Transaksi #N` Sequencing

**Files:**
- Create: `app/src/main/java/com/example/e_money_reader/data/repository/CardRepository.kt`
- Test: `app/src/test/java/com/example/e_money_reader/data/repository/CardRepositoryTest.kt`

**Interfaces:**
- Consumes: `TapSaldoDao`, `CardEntity`, `TransactionEntity` (Task 2), `ParsedCardData` (Task 3)
- Produces:
  - `class CardRepository(private val dao: TapSaldoDao)`
  - `fun observeCards(): Flow<List<CardEntity>>`
  - `fun observeTransactions(cardUid: String): Flow<List<TransactionEntity>>`
  - `suspend fun saveScannedCard(data: ParsedCardData): CardEntity`
  - `suspend fun setActiveCard(uid: String)`
  - `suspend fun updateCardLabel(uid: String, newLabel: String)`
  - `suspend fun updateTransaction(id: Long, customTitle: String?, category: TransactionCategory)`
  - `suspend fun deleteCard(uid: String)`

- [ ] **Step 1: Tulis failing unit tests di `CardRepositoryTest.kt`**
  Uji kasus: (a) kartu pertama otomatis jadi aktif, kartu kedua tidak menimpa aktif sampai `setActiveCard`; (b) deduplikasi scan ulang tidak menggandakan transaksi dan mempertahankan `customTitle` yang sudah diedit; (c) fallback `BALANCE_DELTA` membuat 1 transaksi saat saldo berubah tanpa log chip; (d) `deleteCard` pada kartu aktif mempromosikan kartu tersisa.

- [ ] **Step 2: Jalankan test untuk memastikan gagal**
  Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.e_money_reader.data.repository.CardRepositoryTest"`
  Expected: FAIL

- [ ] **Step 3: Implementasi `CardRepository.kt`**
  Gunakan fake `TapSaldoDao` di unit test agar pengujian cepat di JVM.

- [ ] **Step 4: Jalankan test untuk verifikasi lulus**
  Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.e_money_reader.data.repository.CardRepositoryTest"`
  Expected: PASS

---

### Task 5: TapSaldo Compose Theme, Shared Components & ViewModel

**Files:**
- Create: `app/src/main/java/com/example/e_money_reader/ui/theme/TapSaldoTheme.kt`
- Create: `app/src/main/java/com/example/e_money_reader/ui/components/CardVisual.kt`
- Create: `app/src/main/java/com/example/e_money_reader/ui/components/TransactionRow.kt`
- Create: `app/src/main/java/com/example/e_money_reader/ui/TapSaldoViewModel.kt`

**Interfaces:**
- Consumes: `CardRepository` (Task 4), `EmoneyReaderManager` (Task 3)
- Produces:
  - `TapSaldoTheme` (warna `#0B0B0E`, `#18181D`, `#262630`, `#22C55E`, `#9188F2`, `#F87171`)
  - `EmoneyCardVisual(card: CardEntity, showDetailPill: Boolean, onDetailClick: () -> Unit, modifier: Modifier)`
  - `TransactionRow(tx: TransactionEntity, onClick: () -> Unit)`
  - `TapSaldoViewModel` dengan `uiState: StateFlow<TapSaldoUiState>`

- [ ] **Step 1: Buat `TapSaldoTheme.kt` dan formatter Rupiah / waktu relatif (`formatRupiah`, `formatRelativeTime`)**
- [ ] **Step 2: Buat `CardVisual.kt` dan `TransactionRow.kt`**
  Pastikan kartu vertikal mendukung gradien per `CardIssuer`, chip emas, masked PAN, dan tombol `Detail`.
- [ ] **Step 3: Buat `TapSaldoViewModel.kt`**
  Kelola daftar kartu, `scanState` (`Idle`, `Scanning`, `Success(card, isDifferentFromActive)`, `Error(msg)`), serta aksi CRUD label & transaksi.
- [ ] **Step 4: Verifikasi kompilasi**
  Run: `.\gradlew.bat compileDebugKotlin`
  Expected: `BUILD SUCCESSFUL`

---

### Task 6: Home Screen (Minimalist Pager, Vertical Card in Pocket & Separate Bubbles)

**Files:**
- Create: `app/src/main/java/com/example/e_money_reader/ui/screens/HomeScreen.kt`

**Interfaces:**
- Consumes: `TapSaldoViewModel`, `EmoneyCardVisual`, `TransactionRow` (Task 5)
- Produces: `HomeScreen(cards, transactionsByCard, onOpenDetail, onOpenScan, onEditCardLabel, onEditTransaction)`

- [ ] **Step 1: Implementasi `HomeScreen.kt`**
  - Top bar minimalis kosong tanpa tombol (hanya indikator pager `▬ • •` di tengah).
  - `HorizontalPager` kartu vertikal di bagian atas yang bagian bawahnya terbelah oleh Bubble 1 (`Bubble_BalanceAndScan`).
  - `LazyColumn` berisi **bubble terpisah per fungsi** (Bubble Saldo & Scan NFC, Bubble Edit Card Label, dan Bubble Daftar Transaksi per hari) yang saat di-scroll ke bawah akan naik menutupi kartu hingga kartu tersembunyi.
  - *Empty state* ramah ketika belum ada kartu tersimpan.
- [ ] **Step 2: Verifikasi kompilasi**
  Run: `.\gradlew.bat compileDebugKotlin`
  Expected: `BUILD SUCCESSFUL`

---

### Task 7: Card Details & Edit Modal Bottom Sheets

**Files:**
- Create: `app/src/main/java/com/example/e_money_reader/ui/screens/CardDetailsScreen.kt`
- Create: `app/src/main/java/com/example/e_money_reader/ui/components/EditSheets.kt`

**Interfaces:**
- Consumes: `TapSaldoViewModel`, `EmoneyCardVisual`, `TransactionRow` (Task 5)
- Produces: `CardDetailsScreen`, `EditCardLabelSheet`, `EditTransactionSheet`

- [ ] **Step 1: Implementasi `CardDetailsScreen.kt`**
  Header (`<`, `Card Details`, ikon tag + ikon hapus dengan dialog konfirmasi), visual kartu penuh, panel `Current Stored Balance` + tombol `Set as Active`, baris `Card Label` (`Edit >`), filter chip (`All`, `Transit`, `Toll`, `Top-up`), daftar transaksi kartu (tap untuk edit), dan catatan footer.
- [ ] **Step 2: Implementasi `EditSheets.kt`**
  `EditCardLabelSheet` (preview kartu mini, input label, chip saran cepat, `Save Label`) dan `EditTransactionSheet` (referensi `Transaksi #N`, input nama custom, chip kategori `Transit`/`Toll`/`Top-up`/`Other`, `Save Transaction`).
- [ ] **Step 3: Verifikasi kompilasi**
  Run: `.\gradlew.bat compileDebugKotlin`
  Expected: `BUILD SUCCESSFUL`

---

### Task 8: Scan NFC Flow, Foreground Dispatch & MainActivity Wiring

**Files:**
- Create: `app/src/main/java/com/example/e_money_reader/ui/screens/ScanNfcScreen.kt`
- Modify: `app/src/main/java/com/example/e_money_reader/MainActivity.kt`

**Interfaces:**
- Consumes: `HomeScreen` (Task 6), `CardDetailsScreen`, `EditSheets` (Task 7), `TapSaldoViewModel` (Task 5)
- Produces: Navigasi penuh aplikasi + integrasi `NfcAdapter.enableForegroundDispatch` (dijeda saat modal edit terbuka).

- [ ] **Step 1: Implementasi `ScanNfcScreen.kt`**
  Animasi cincin gelombang NFC hijau, status antena `13.56 MHz` / arahan buka Pengaturan NFC jika mati, state error + coba lagi, dan layar sukses dengan tombol **Confirm & Set as Active** serta **Done (Keep Current Active)** (plus tombol simulasi di `BuildConfig.DEBUG` agar mudah diuji di emulator).
- [ ] **Step 2: Wiring `MainActivity.kt`**
  Pasang `TapSaldoTheme`, `NavHost` (`home`, `details/{uid}`, `scan`), `NfcAdapter` foreground dispatch (dijeda saat sheet edit terbuka), dan `onNewIntent` -> `viewModel.onNfcTagDiscovered(tag)`.
- [ ] **Step 3: Jalankan seluruh unit test & debug APK build**
  Run: `.\gradlew.bat testDebugUnitTest assembleDebug`
  Expected: `BUILD SUCCESSFUL` dan semua unit test PASS.
