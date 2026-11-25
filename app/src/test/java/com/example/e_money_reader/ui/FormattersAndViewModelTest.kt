package com.example.e_money_reader.ui

import com.example.e_money_reader.data.local.CardEntity
import com.example.e_money_reader.data.local.CardIssuer
import com.example.e_money_reader.data.local.TapSaldoDao
import com.example.e_money_reader.data.local.TransactionCategory
import com.example.e_money_reader.data.local.TransactionEntity
import com.example.e_money_reader.nfc.ApduTransceiver
import com.example.e_money_reader.nfc.EmoneyParser
import com.example.e_money_reader.nfc.EmoneyReaderManager
import com.example.e_money_reader.nfc.ParsedCardData
import com.example.e_money_reader.data.repository.CardRepository
import com.example.e_money_reader.ui.theme.formatRupiah
import com.example.e_money_reader.ui.theme.formatSignedRupiah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FormattersAndViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun formatsRupiahAndSignedAmountsInIndonesianNotation() {
        assertEquals("Rp 148.500", formatRupiah(148_500L))
        assertEquals("Rp 0", formatRupiah(0L))
        assertEquals("-Rp 7.500", formatSignedRupiah(-7_500L))
        assertEquals("+Rp 100.000", formatSignedRupiah(100_000L))
    }

    @Test
    fun scanningDifferentCardFlagsActiveConfirmationAndPausesAutoScanWhileSheetOpen() = runTest {
        val dao = MiniDao()
        val repo = CardRepository(dao)
        repo.saveScannedCard(
            ParsedCardData("UID_1", "603211114821", CardIssuer.MANDIRI, 148_500L, emptyList()),
            nowMillis = 1_000L
        )

        val stubManager = EmoneyReaderManager(
            listOf(
                object : EmoneyParser {
                    override val issuer = CardIssuer.BCA_FLAZZ
                    override fun tryParse(transceiver: ApduTransceiver): ParsedCardData? {
                        return if (transceiver.uidHex == "UID_2") {
                            ParsedCardData("UID_2", "109822227734", CardIssuer.BCA_FLAZZ, 72_000L, emptyList())
                        } else null
                    }
                }
            )
        )

        val vm = TapSaldoViewModel(
            repository = repo,
            readerManager = stubManager,
            ioDispatcher = testDispatcher
        )

        // Open edit sheet -> auto-scan should report modal open
        val firstCard = vm.uiState.value.cards.first()
        vm.openEditCardLabel(firstCard)
        assertTrue(vm.uiState.value.isModalSheetOpen)
        vm.dismissSheets()
        assertFalse(vm.uiState.value.isModalSheetOpen)

        // Scan second card -> requiresActiveConfirmation must be true
        vm.processTransceiver(
            object : ApduTransceiver {
                override val uidHex: String = "UID_2"
                override fun transceive(command: ByteArray): ByteArray = ByteArray(0)
            }
        )

        val stateAfterScan = vm.uiState.value.scanState
        assertTrue(stateAfterScan is ScanUiState.Success)
        val success = stateAfterScan as ScanUiState.Success
        assertEquals("UID_2", success.card.uid)
        assertTrue(success.requiresActiveConfirmation)

        // Confirm set as active -> UID_2 becomes active and scanState returns to Idle
        vm.confirmScannedCardAsActive()
        assertTrue(vm.uiState.value.scanState is ScanUiState.Idle)
        assertTrue(vm.uiState.value.cards.first { it.uid == "UID_2" }.isActive)
    }

    @Test
    fun blocksScanWhileModalOpenMarksCardsLoadedAndEmitsDistinctConsecutiveErrors() = runTest {
        val dao = MiniDao()
        val repo = CardRepository(dao)
        repo.saveScannedCard(
            ParsedCardData("UID_1", "603211114821", CardIssuer.MANDIRI, 148_500L, emptyList()),
            nowMillis = 1_000L
        )
        val vm = TapSaldoViewModel(
            repository = repo,
            readerManager = EmoneyReaderManager(emptyList()),
            ioDispatcher = testDispatcher
        )

        assertTrue(vm.uiState.value.isCardsLoaded)

        // While modal sheet is open, processTransceiver must be ignored
        vm.openEditCardLabel(vm.uiState.value.cards.first())
        vm.processTransceiver(
            object : ApduTransceiver {
                override val uidHex: String = "BAD_TAG"
                override fun transceive(command: ByteArray): ByteArray = ByteArray(0)
            }
        )
        assertTrue(vm.uiState.value.scanState is ScanUiState.Idle)

        vm.dismissSheets()

        // Two identical consecutive errors must carry distinct eventIds so auto-scan navigation always triggers
        vm.processTransceiver(
            object : ApduTransceiver {
                override val uidHex: String = "BAD_TAG"
                override fun transceive(command: ByteArray): ByteArray = ByteArray(0)
            }
        )
        val err1 = vm.uiState.value.scanState as ScanUiState.Error

        vm.processTransceiver(
            object : ApduTransceiver {
                override val uidHex: String = "BAD_TAG"
                override fun transceive(command: ByteArray): ByteArray = ByteArray(0)
            }
        )
        val err2 = vm.uiState.value.scanState as ScanUiState.Error
        assertNotEquals(err1.eventId, err2.eventId)
    }

    private class MiniDao : TapSaldoDao {
        private val cards = MutableStateFlow<List<CardEntity>>(emptyList())
        private val txs = MutableStateFlow<List<TransactionEntity>>(emptyList())
        private var idSeq = 1L

        override fun observeCards(): Flow<List<CardEntity>> =
            cards.map { it.sortedWith(compareByDescending<CardEntity> { c -> c.isActive }.thenByDescending { c -> c.lastScannedAt }) }

        override suspend fun getAllCards(): List<CardEntity> = cards.value
        override suspend fun getCardByUid(uid: String): CardEntity? = cards.value.firstOrNull { it.uid == uid }
        override fun observeTransactionsForCard(cardUid: String): Flow<List<TransactionEntity>> =
            txs.map { list -> list.filter { it.cardUid == cardUid } }

        override suspend fun getTransactionsForCard(cardUid: String): List<TransactionEntity> =
            txs.value.filter { it.cardUid == cardUid }

        override suspend fun upsertCard(card: CardEntity) {
            cards.value = cards.value.filterNot { it.uid == card.uid } + card
        }

        override suspend fun clearActiveFlags() {
            cards.value = cards.value.map { it.copy(isActive = false) }
        }

        override suspend fun setActiveCardFlag(uid: String) {
            cards.value = cards.value.map { if (it.uid == uid) it.copy(isActive = true) else it }
        }

        override suspend fun updateCardLabel(uid: String, label: String) {
            cards.value = cards.value.map { if (it.uid == uid) it.copy(customLabel = label) else it }
        }

        override suspend fun deleteCardByUid(uid: String) {
            cards.value = cards.value.filterNot { it.uid == uid }
            txs.value = txs.value.filterNot { it.cardUid == uid }
        }

        override suspend fun insertTransactionsIgnore(transactions: List<TransactionEntity>): List<Long> {
            val cur = txs.value.toMutableList()
            return transactions.map { tx ->
                if (cur.any { it.cardUid == tx.cardUid && it.dedupKey == tx.dedupKey }) -1L
                else {
                    val assigned = idSeq++
                    cur += tx.copy(id = assigned)
                    txs.value = cur
                    assigned
                }
            }
        }

        override suspend fun updateTransactionDetails(id: Long, customTitle: String?, category: TransactionCategory) {
            txs.value = txs.value.map { if (it.id == id) it.copy(customTitle = customTitle, category = category) else it }
        }

        override suspend fun getMaxSequenceNumber(cardUid: String): Int =
            txs.value.filter { it.cardUid == cardUid }.maxOfOrNull { it.sequenceNumber } ?: 0
    }
}
