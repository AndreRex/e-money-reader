package com.example.e_money_reader.data.repository

import com.example.e_money_reader.data.local.CardEntity
import com.example.e_money_reader.data.local.TapSaldoDao
import com.example.e_money_reader.data.local.TransactionCategory
import com.example.e_money_reader.data.local.TransactionEntity
import com.example.e_money_reader.data.local.TransactionSource
import com.example.e_money_reader.nfc.ParsedCardData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CardRepository(
    private val dao: TapSaldoDao
) {
    private val writeMutex = Mutex()

    fun observeCards(): Flow<List<CardEntity>> = dao.observeCards()

    fun observeTransactions(cardUid: String): Flow<List<TransactionEntity>> =
        dao.observeTransactionsForCard(cardUid)

    suspend fun saveScannedCard(
        data: ParsedCardData,
        nowMillis: Long = System.currentTimeMillis()
    ): CardEntity = writeMutex.withLock {
        val existingCard = dao.getCardByUid(data.uid)
        val allCards = dao.getAllCards()
        val shouldBeActive = existingCard?.isActive ?: allCards.none { it.isActive }
        val cardLabel = existingCard?.customLabel ?: data.issuer.displayName

        val updatedCard = CardEntity(
            uid = data.uid,
            cardNumber = data.cardNumber.ifBlank { existingCard?.cardNumber ?: data.uid },
            issuer = data.issuer,
            customLabel = cardLabel,
            lastBalance = data.balance,
            lastScannedAt = nowMillis,
            isActive = shouldBeActive
        )

        val newTransactions = buildMergedTransactions(existingCard, data, nowMillis)
        dao.saveCardWithTransactions(updatedCard, newTransactions)
        updatedCard
    }

    private suspend fun buildMergedTransactions(
        existingCard: CardEntity?,
        data: ParsedCardData,
        nowMillis: Long
    ): List<TransactionEntity> {
        val existingKeys = dao.getTransactionsForCard(data.uid).map { it.dedupKey }.toHashSet()
        var nextSequence = dao.getMaxSequenceNumber(data.uid)

        if (data.chipTransactions.isNotEmpty()) {
            val newEntities = mutableListOf<TransactionEntity>()
            for (chipTx in data.chipTransactions.sortedBy { it.timestamp }) {
                if (existingKeys.add(chipTx.dedupKey)) {
                    nextSequence += 1
                    newEntities += TransactionEntity(
                        cardUid = data.uid,
                        dedupKey = chipTx.dedupKey,
                        sequenceNumber = nextSequence,
                        customTitle = null,
                        amount = chipTx.amount,
                        balanceAfter = chipTx.balanceAfter,
                        category = defaultCategoryForAmount(chipTx.amount),
                        timestamp = chipTx.timestamp,
                        source = TransactionSource.CHIP_LOG
                    )
                }
            }
            return newEntities
        } else if (!data.chipLogReadFailed && existingCard != null && data.balance != existingCard.lastBalance) {
            val delta = data.balance - existingCard.lastBalance
            nextSequence += 1
            return listOf(
                TransactionEntity(
                    cardUid = data.uid,
                    dedupKey = "DELTA-${existingCard.lastBalance}->${data.balance}@$nowMillis",
                    sequenceNumber = nextSequence,
                    customTitle = null,
                    amount = delta,
                    balanceAfter = data.balance,
                    category = defaultCategoryForAmount(delta),
                    timestamp = nowMillis,
                    source = TransactionSource.BALANCE_DELTA
                )
            )
        }
        return emptyList()
    }

    private fun defaultCategoryForAmount(amount: Long): TransactionCategory =
        if (amount > 0L) TransactionCategory.TOPUP else TransactionCategory.OTHER

    suspend fun setActiveCard(uid: String) = writeMutex.withLock {
        dao.setActiveCard(uid)
    }

    suspend fun updateCardLabel(uid: String, newLabel: String) = writeMutex.withLock {
        val trimmed = newLabel.trim()
        if (trimmed.isNotEmpty()) {
            dao.updateCardLabel(uid, trimmed)
        }
    }

    suspend fun updateTransaction(
        id: Long,
        customTitle: String?,
        category: TransactionCategory
    ) = writeMutex.withLock {
        dao.updateTransactionDetails(
            id = id,
            customTitle = customTitle?.trim()?.takeIf { it.isNotEmpty() },
            category = category
        )
    }

    suspend fun deleteCard(uid: String) = writeMutex.withLock {
        dao.deleteCardAndPromoteActive(uid)
    }
}
