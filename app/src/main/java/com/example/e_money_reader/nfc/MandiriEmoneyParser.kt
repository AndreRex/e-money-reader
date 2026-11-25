package com.example.e_money_reader.nfc

import com.example.e_money_reader.data.local.CardIssuer

class MandiriEmoneyParser : EmoneyParser {
    override val issuer: CardIssuer = CardIssuer.MANDIRI

    override fun tryParse(transceiver: ApduTransceiver): ParsedCardData? {
        val selectResp = transceiver.transceive(SELECT_MANDIRI_AID)
        if (!selectResp.isStatusOk()) return null

        val balanceResp = transceiver.transceive(READ_BALANCE_CMD)
        if (!balanceResp.isStatusOk()) return null
        val balancePayload = balanceResp.payloadWithoutSw()
        if (balancePayload.size < 4) return null
        val balance = balancePayload.readUInt32LE(0)
        if (balance > MAX_REASONABLE_BALANCE) return null

        val cardNumber = readCardNumber(transceiver) ?: transceiver.uidHex
        val logs = readChipLogs(transceiver)

        return ParsedCardData(
            uid = transceiver.uidHex,
            cardNumber = cardNumber,
            issuer = CardIssuer.MANDIRI,
            balance = balance,
            chipTransactions = logs
        )
    }

    private fun readCardNumber(transceiver: ApduTransceiver): String? {
        val infoResp = transceiver.transceive(READ_INFO_CMD)
        if (!infoResp.isStatusOk()) return null
        val payload = infoResp.payloadWithoutSw()
        if (payload.size < 8) return null
        val candidate = payload.copyOfRange(0, 8).toHex()
        return candidate.takeIf { it.all(Char::isDigit) } ?: candidate
    }

    private fun readChipLogs(transceiver: ApduTransceiver): List<ChipTransaction> {
        val logResp = transceiver.transceive(READ_LOG_CMD)
        if (!logResp.isStatusOk()) return emptyList()
        val payload = logResp.payloadWithoutSw()
        if (payload.size < 16) return emptyList()

        val result = mutableListOf<ChipTransaction>()
        var offset = 0
        while (offset + 16 <= payload.size) {
            val seq = payload[offset].toInt() and 0xFF
            val amount = payload.readInt32LE(offset + 1).toLong()
            val balAfter = payload.readUInt32LE(offset + 5)
            val epochSec = payload.readUInt32LE(offset + 9)
            if (amount != 0L) {
                val tsMillis = if (epochSec > 946_684_800L) epochSec * 1_000L else System.currentTimeMillis()
                result += ChipTransaction(
                    dedupKey = "MDR-$seq-$amount-$balAfter-$epochSec",
                    amount = amount,
                    balanceAfter = balAfter,
                    timestamp = tsMillis
                )
            }
            offset += 16
        }
        return result
    }

    companion object {
        private const val MAX_REASONABLE_BALANCE = 20_000_000L
        private val SELECT_MANDIRI_AID = hexToBytes("00A40400080000000000000001")
        private val READ_INFO_CMD = hexToBytes("00B300003F")
        private val READ_BALANCE_CMD = hexToBytes("00B500000A")
        private val READ_LOG_CMD = hexToBytes("00B7000010")
    }
}
