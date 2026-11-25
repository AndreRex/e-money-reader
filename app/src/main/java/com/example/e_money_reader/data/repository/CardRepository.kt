package com.example.e_money_reader.data.repository

import com.example.e_money_reader.data.local.CardEntity
import com.example.e_money_reader.data.local.TapSaldoDao
import com.example.e_money_reader.data.local.TransactionCategory
import com.example.e_money_reader.data.local.TransactionEntity
import com.example.e_money_reader.data.local.TransactionSource
import com.example.e_money_reader.nfc.ParsedCardData
import kotlinx.coroutines.flow.Flow

class CardRepository(
    private val dao: TapSaldoDao
) {
    fun observeCards(): Flow<List<CardEntity>> = dao.observeCards()

    fun observeTransactions(cardUid: String): Flow<List<TransactionEntity>> =
        dao.observeTransactionsForCard(cardUid)

    suspend fun saveScannedCard(
        data: ParsedCardData,
        nowMillis: Long = System.currentTimeMillis()
    ): CardEntity {
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
        dao.upsertCard(updatedCard)

        mergeTransactions(existingCard, data, nowMillis)
        return updatedCard
    }

    private suspend fun mergeTransactions(
        existingCard: CardEntity?,
        data: ParsedCardData,
        nowMillis: Long
    ) {
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
            if (newEntities.isNotEmpty()) {
                dao.insertTransactionsIgnore(newEntities)
            }
        } else if (existingCard != null && data.balance != existingCard.lastBalance) {
            val delta = data.balance - existingCard.lastBalance
            nextSequence += 1
            val deltaEntity = TransactionEntity(
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
            dao.insertTransactionsIgnore(listOf(deltaEntity))
        }
    }

    private fun defaultCategoryForAmount(amount: Long): TransactionCategory =
        if (amount > 0L) TransactionCategory.TOPUP else TransactionCategory.OTHER

    suspend fun setActiveCard(uid: String) {
        dao.setActiveCard(uid)
    }

    suspend fun updateCardLabel(uid: String, newLabel: String) {
        val trimmed = newLabel.trim()
        if (trimmed.isNotEmpty()) {
            dao.updateCardLabel(uid, trimmed)
        }
    }

    suspend fun updateTransaction(
        id: Long,
        customTitle: String?,
        category: TransactionCategory
    ) {
        dao.updateTransactionDetails(
            id = id,
            customTitle = customTitle?.trim()?.takeIf { it.isNotEmpty() },
            category = category
        )
    }

    suspend fun deleteCard(uid: String) {
        val wasActive = dao.getCardByUid(uid)?.isActive == true
        dao.deleteCardByUid(uid)
        if (wasActive) {
            val nextCandidate = dao.getAllCards().firstOrNull()
            if (nextCandidate != null) {
                dao.setActiveCard(nextCandidate.uid)
            }
        }
    }
}
