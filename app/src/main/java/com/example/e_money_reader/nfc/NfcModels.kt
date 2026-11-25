package com.example.e_money_reader.nfc

import com.example.e_money_reader.data.local.CardIssuer

data class ChipTransaction(
    val dedupKey: String,
    val amount: Long,
    val balanceAfter: Long?,
    val timestamp: Long
)

data class ParsedCardData(
    val uid: String,
    val cardNumber: String,
    val issuer: CardIssuer,
    val balance: Long,
    val chipTransactions: List<ChipTransaction>,
    val chipLogReadFailed: Boolean = false
)

sealed interface ReadResult {
    data class Success(val data: ParsedCardData) : ReadResult
    data class Error(val message: String) : ReadResult
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

internal fun hexToBytes(hex: String): ByteArray {
    val clean = hex.replace(" ", "")
    return ByteArray(clean.length / 2) { idx ->
        clean.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
    }
}

internal fun ByteArray.isStatusOk(): Boolean {
    if (size < 2) return false
    val sw1 = this[size - 2].toInt() and 0xFF
    val sw2 = this[size - 1].toInt() and 0xFF
    return (sw1 == 0x90 && sw2 == 0x00) || (sw1 == 0x91 && sw2 == 0x00)
}

internal fun ByteArray.isUnsupportedLogStatus(): Boolean {
    if (size < 2) return false
    val sw1 = this[size - 2].toInt() and 0xFF
    val sw2 = this[size - 1].toInt() and 0xFF
    return (sw1 == 0x6A && (sw2 == 0x81 || sw2 == 0x82)) || sw1 == 0x6D || sw1 == 0x6E
}

internal fun ByteArray.payloadWithoutSw(): ByteArray =
    if (size >= 2) copyOfRange(0, size - 2) else ByteArray(0)

internal fun ByteArray.readInt32LE(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)
}

internal fun ByteArray.readUInt32LE(offset: Int): Long =
    readInt32LE(offset).toLong() and 0xFFFFFFFFL

internal fun ByteArray.readInt32BE(offset: Int): Int {
    return ((this[offset].toInt() and 0xFF) shl 24) or
        ((this[offset + 1].toInt() and 0xFF) shl 16) or
        ((this[offset + 2].toInt() and 0xFF) shl 8) or
        (this[offset + 3].toInt() and 0xFF)
}

internal fun ByteArray.readInt24SignedBE(offset: Int): Int {
    val raw = ((this[offset].toInt() and 0xFF) shl 16) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        (this[offset + 2].toInt() and 0xFF)
    return if ((raw and 0x800000) != 0) raw or -0x1000000 else raw
}
