package com.example.e_money_reader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TapSaldoDao {
    @Query("SELECT * FROM cards ORDER BY isActive DESC, lastScannedAt DESC")
    fun observeCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards ORDER BY isActive DESC, lastScannedAt DESC")
    suspend fun getAllCards(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE uid = :uid LIMIT 1")
    suspend fun getCardByUid(uid: String): CardEntity?

    @Query("SELECT * FROM transactions WHERE cardUid = :cardUid ORDER BY timestamp DESC, sequenceNumber DESC")
    fun observeTransactionsForCard(cardUid: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE cardUid = :cardUid ORDER BY sequenceNumber ASC")
    suspend fun getTransactionsForCard(cardUid: String): List<TransactionEntity>

    @Upsert
    suspend fun upsertCard(card: CardEntity)

    @Query("UPDATE cards SET isActive = 0")
    suspend fun clearActiveFlags()

    @Query("UPDATE cards SET isActive = 1 WHERE uid = :uid")
    suspend fun setActiveCardFlag(uid: String)

    @Transaction
    suspend fun setActiveCard(uid: String) {
        clearActiveFlags()
        setActiveCardFlag(uid)
    }

    @Query("UPDATE cards SET customLabel = :label WHERE uid = :uid")
    suspend fun updateCardLabel(uid: String, label: String)

    @Query("DELETE FROM cards WHERE uid = :uid")
    suspend fun deleteCardByUid(uid: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactionsIgnore(transactions: List<TransactionEntity>): List<Long>

    @Query("UPDATE transactions SET customTitle = :customTitle, category = :category WHERE id = :id")
    suspend fun updateTransactionDetails(
        id: Long,
        customTitle: String?,
        category: TransactionCategory
    )

    @Query("SELECT COALESCE(MAX(sequenceNumber), 0) FROM transactions WHERE cardUid = :cardUid")
    suspend fun getMaxSequenceNumber(cardUid: String): Int
}
