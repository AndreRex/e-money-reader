package com.example.e_money_reader.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class CardIssuer(val displayName: String) {
    MANDIRI("Mandiri e-Money"),
    BCA_FLAZZ("BCA Flazz"),
    BNI_TAPCASH("BNI TapCash"),
    BRI_BRIZZI("BRI Brizzi"),
    UNKNOWN("E-Money Card")
}

enum class TransactionCategory(val displayName: String) {
    TRANSIT("Transit"),
    TOLL("Toll"),
    TOPUP("Top-up"),
    OTHER("Other")
}

enum class TransactionSource {
    CHIP_LOG,
    BALANCE_DELTA
}

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val uid: String,
    val cardNumber: String,
    val issuer: CardIssuer,
    val customLabel: String,
    val lastBalance: Long,
    val lastScannedAt: Long,
    val isActive: Boolean
) {
    val maskedCardNumber: String
        get() {
            val clean = cardNumber.filter { it.isLetterOrDigit() }
            return when {
                clean.length >= 8 -> "${clean.take(4)} •••• ${clean.takeLast(4)}"
                clean.length > 4 -> "•••• ${clean.takeLast(4)}"
                clean.isNotEmpty() -> clean
                else -> "UID ${uid.takeLast(4).uppercase()}"
            }
        }
}

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["uid"],
            childColumns = ["cardUid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cardUid", "dedupKey"], unique = true),
        Index(value = ["cardUid"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardUid: String,
    val dedupKey: String,
    val sequenceNumber: Int,
    val customTitle: String?,
    val amount: Long,
    val balanceAfter: Long?,
    val category: TransactionCategory,
    val timestamp: Long,
    val source: TransactionSource
) {
    val displayTitle: String
        get() = customTitle?.trim()?.takeIf { it.isNotEmpty() } ?: "Transaksi #$sequenceNumber"
}

class TapSaldoConverters {
    @TypeConverter
    fun fromIssuer(value: CardIssuer): String = value.name

    @TypeConverter
    fun toIssuer(value: String): CardIssuer =
        runCatching { CardIssuer.valueOf(value) }.getOrDefault(CardIssuer.UNKNOWN)

    @TypeConverter
    fun fromCategory(value: TransactionCategory): String = value.name

    @TypeConverter
    fun toCategory(value: String): TransactionCategory =
        runCatching { TransactionCategory.valueOf(value) }.getOrDefault(TransactionCategory.OTHER)

    @TypeConverter
    fun fromSource(value: TransactionSource): String = value.name

    @TypeConverter
    fun toSource(value: String): TransactionSource =
        runCatching { TransactionSource.valueOf(value) }.getOrDefault(TransactionSource.CHIP_LOG)
}
