package com.example.e_money_reader.data.repository

import com.example.e_money_reader.data.local.CardEntity
import com.example.e_money_reader.data.local.CardIssuer
import com.example.e_money_reader.data.local.TapSaldoDao
import com.example.e_money_reader.data.local.TransactionCategory
import com.example.e_money_reader.data.local.TransactionEntity
import com.example.e_money_reader.data.local.TransactionSource
import com.example.e_money_reader.nfc.ChipTransaction
import com.example.e_money_reader.nfc.ParsedCardData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CardRepositoryTest {

    private lateinit var fakeDao: InMemoryTapSaldoDao
    private lateinit var repository: CardRepository

    @Before
    fun setUp() {
        fakeDao = InMemoryTapSaldoDao()
        repository = CardRepository(fakeDao)
    }

    @Test
    fun firstCardBecomesActiveAndSecondCardStaysInactiveUntilConfirmed() = runTest {
        val cardA = ParsedCardData(
            uid = "UID_A",
            cardNumber = "6032000011114821",
            issuer = CardIssuer.MANDIRI,
            balance = 148_500L,
            chipTransactions = emptyList()
        )
        val cardB = ParsedCardData(
            uid = "UID_B",
            cardNumber = "1098000022227734",
            issuer = CardIssuer.BCA_FLAZZ,
            balance = 72_000L,
            chipTransactions = emptyList()
        )

        val savedA = repository.saveScannedCard(cardA, nowMillis = 1_000L)
        assertTrue(savedA.isActive)

        val savedB = repository.saveScannedCard(cardB, nowMillis = 2_000L)
        assertFalse(savedB.isActive)

        repository.setActiveCard("UID_B")
        val allCards = repository.observeCards().first()
        assertTrue(allCards.first { it.uid == "UID_B" }.isActive)
        assertFalse(allCards.first { it.uid == "UID_A" }.isActive)
    }

    @Test
    fun deduplicatesRescannedTransactionsAndPreservesCustomNamesAndCategories() = runTest {
        val scan1 = ParsedCardData(
            uid = "UID_A",
            cardNumber = "6032000011114821",
            issuer = CardIssuer.MANDIRI,
            balance = 148_500L,
            chipTransactions = listOf(
                ChipTransaction("K1", -7_500L, 48_500L, 1_000L),
                ChipTransaction("K2", 100_000L, 148_500L, 2_000L)
            )
        )
        repository.saveScannedCard(scan1, nowMillis = 2_500L)

        val initialTxs = repository.observeTransactions("UID_A").first()
        assertEquals(2, initialTxs.size)
        val tx1 = initialTxs.first { it.dedupKey == "K1" }
        assertEquals("Transaksi #1", tx1.displayTitle)
        assertEquals(TransactionCategory.OTHER, tx1.category)

        val tx2 = initialTxs.first { it.dedupKey == "K2" }
        assertEquals(TransactionCategory.TOPUP, tx2.category)

        // User renames tx1 to custom name + TOLL category
        repository.updateTransaction(tx1.id, "Tol Jagorawi Gate 3", TransactionCategory.TOLL)

        // Rescan with overlapping K2 + new K3 (K1 already evicted from physical chip memory)
        val scan2 = scan1.copy(
            balance = 145_000L,
            chipTransactions = listOf(
                ChipTransaction("K2", 100_000L, 148_500L, 2_000L),
                ChipTransaction("K3", -3_500L, 145_000L, 3_000L)
            )
        )
        repository.saveScannedCard(scan2, nowMillis = 3_500L)

        val mergedTxs = repository.observeTransactions("UID_A").first()
        assertEquals(3, mergedTxs.size)

        val preservedTx1 = mergedTxs.first { it.dedupKey == "K1" }
        assertEquals("Tol Jagorawi Gate 3", preservedTx1.displayTitle)
        assertEquals(TransactionCategory.TOLL, preservedTx1.category)

        val newTx3 = mergedTxs.first { it.dedupKey == "K3" }
        assertEquals(3, newTx3.sequenceNumber)
        assertEquals("Transaksi #3", newTx3.displayTitle)
    }

    @Test
    fun createsBalanceDeltaTransactionWhenChipLogsEmptyAndBalanceChanges() = runTest {
        val firstScan = ParsedCardData(
            uid = "UID_FLAZZ",
            cardNumber = "1098000022227734",
            issuer = CardIssuer.BCA_FLAZZ,
            balance = 100_000L,
            chipTransactions = emptyList()
        )
        repository.saveScannedCard(firstScan, nowMillis = 1_000L)
        assertTrue(repository.observeTransactions("UID_FLAZZ").first().isEmpty())

        // Balance drops to 85.000 -> 1 BALANCE_DELTA (-15.000)
        repository.saveScannedCard(firstScan.copy(balance = 85_000L), nowMillis = 2_000L)
        val afterSpend = repository.observeTransactions("UID_FLAZZ").first()
        assertEquals(1, afterSpend.size)
        assertEquals(-15_000L, afterSpend.first().amount)
        assertEquals(TransactionSource.BALANCE_DELTA, afterSpend.first().source)
        assertEquals("Transaksi #1", afterSpend.first().displayTitle)

        // Same balance scanned again -> no duplicate delta entry
        repository.saveScannedCard(firstScan.copy(balance = 85_000L), nowMillis = 3_000L)
        assertEquals(1, repository.observeTransactions("UID_FLAZZ").first().size)
    }

    @Test
    fun deletingActiveCardCascadesTransactionsAndPromotesAnotherCard() = runTest {
        repository.saveScannedCard(
            ParsedCardData("UID_A", "60321111", CardIssuer.MANDIRI, 50_000L, emptyList()),
            nowMillis = 1_000L
        )
        repository.saveScannedCard(
            ParsedCardData(
                "UID_B",
                "10982222",
                CardIssuer.BCA_FLAZZ,
                70_000L,
                listOf(ChipTransaction("B1", -5_000L, 70_000L, 1_500L))
            ),
            nowMillis = 2_000L
        )
        repository.setActiveCard("UID_B")

        repository.deleteCard("UID_B")

        val remainingCards = repository.observeCards().first()
        assertEquals(1, remainingCards.size)
        assertEquals("UID_A", remainingCards.first().uid)
        assertTrue(remainingCards.first().isActive)
        assertTrue(repository.observeTransactions("UID_B").first().isEmpty())
    }

    private class InMemoryTapSaldoDao : TapSaldoDao {
        private val cardsState = MutableStateFlow<List<CardEntity>>(emptyList())
        private val txsState = MutableStateFlow<List<TransactionEntity>>(emptyList())
        private var nextTxId = 1L

        override fun observeCards(): Flow<List<CardEntity>> =
            cardsState.map { list ->
                list.sortedWith(compareByDescending<CardEntity> { it.isActive }.thenByDescending { it.lastScannedAt })
            }

        override suspend fun getAllCards(): List<CardEntity> =
            cardsState.value.sortedWith(compareByDescending<CardEntity> { it.isActive }.thenByDescending { it.lastScannedAt })

        override suspend fun getCardByUid(uid: String): CardEntity? =
            cardsState.value.firstOrNull { it.uid == uid }

        override fun observeTransactionsForCard(cardUid: String): Flow<List<TransactionEntity>> =
            txsState.map { list ->
                list.filter { it.cardUid == cardUid }
                    .sortedWith(compareByDescending<TransactionEntity> { it.timestamp }.thenByDescending { it.sequenceNumber })
            }

        override suspend fun getTransactionsForCard(cardUid: String): List<TransactionEntity> =
            txsState.value.filter { it.cardUid == cardUid }.sortedBy { it.sequenceNumber }

        override suspend fun upsertCard(card: CardEntity) {
            cardsState.value = cardsState.value.filterNot { it.uid == card.uid } + card
        }

        override suspend fun clearActiveFlags() {
            cardsState.value = cardsState.value.map { it.copy(isActive = false) }
        }

        override suspend fun setActiveCardFlag(uid: String) {
            cardsState.value = cardsState.value.map { if (it.uid == uid) it.copy(isActive = true) else it }
        }

        override suspend fun updateCardLabel(uid: String, label: String) {
            cardsState.value = cardsState.value.map { if (it.uid == uid) it.copy(customLabel = label) else it }
        }

        override suspend fun deleteCardByUid(uid: String) {
            cardsState.value = cardsState.value.filterNot { it.uid == uid }
            txsState.value = txsState.value.filterNot { it.cardUid == uid }
        }

        override suspend fun insertTransactionsIgnore(transactions: List<TransactionEntity>): List<Long> {
            val current = txsState.value.toMutableList()
            val ids = mutableListOf<Long>()
            for (tx in transactions) {
                val exists = current.any { it.cardUid == tx.cardUid && it.dedupKey == tx.dedupKey }
                if (exists) {
                    ids += -1L
                } else {
                    val assignedId = if (tx.id == 0L) nextTxId++ else tx.id
                    current += tx.copy(id = assignedId)
                    ids += assignedId
                }
            }
            txsState.value = current
            return ids
        }

        override suspend fun updateTransactionDetails(
            id: Long,
            customTitle: String?,
            category: TransactionCategory
        ) {
            txsState.value = txsState.value.map {
                if (it.id == id) it.copy(customTitle = customTitle, category = category) else it
            }
        }

        override suspend fun getMaxSequenceNumber(cardUid: String): Int =
            txsState.value.filter { it.cardUid == cardUid }.maxOfOrNull { it.sequenceNumber } ?: 0
    }
}
