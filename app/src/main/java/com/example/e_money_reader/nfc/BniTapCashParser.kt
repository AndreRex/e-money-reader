package com.example.e_money_reader.nfc

import com.example.e_money_reader.data.local.CardIssuer

class BniTapCashParser : EmoneyParser {
    override val issuer: CardIssuer = CardIssuer.BNI_TAPCASH

    override fun tryParse(transceiver: ApduTransceiver): ParsedCardData? {
        var purseResp = transceiver.transceive(READ_PURSE_3_CMD)
        if (!purseResp.isStatusOk()) {
            val selectResp = transceiver.transceive(SELECT_CEPAS_AID)
            if (!selectResp.isStatusOk()) return null
            purseResp = transceiver.transceive(READ_PURSE_3_CMD)
            if (!purseResp.isStatusOk()) return null
        }

        val payload = purseResp.payloadWithoutSw()
        if (payload.size < 16) return null
        val balance = payload.readInt24SignedBE(2).toLong()
        if (balance < 0L || balance > MAX_REASONABLE_BALANCE) return null

        val cardNumber = payload.copyOfRange(8, 16).toHex()
        val (logs, logFailed) = readCepasTransactions(transceiver)

        return ParsedCardData(
            uid = transceiver.uidHex,
            cardNumber = cardNumber,
            issuer = CardIssuer.BNI_TAPCASH,
            balance = balance,
            chipTransactions = logs,
            chipLogReadFailed = logFailed
        )
    }

    private fun readCepasTransactions(transceiver: ApduTransceiver): Pair<List<ChipTransaction>, Boolean> {
        val logResp = transceiver.transceive(READ_TX_LOG_CMD)
        if (!logResp.isStatusOk()) {
            return emptyList<ChipTransaction>() to !logResp.isUnsupportedLogStatus()
        }
        val payload = logResp.payloadWithoutSw()
        if (payload.size < 16) return emptyList<ChipTransaction>() to true

        val result = mutableListOf<ChipTransaction>()
        var offset = 0
        while (offset + 16 <= payload.size) {
            val txType = payload[offset].toInt() and 0xFF
            val amount = payload.readInt24SignedBE(offset + 1).toLong()
            val cepasSec = payload.readInt32BE(offset + 4).toLong() and 0xFFFFFFFFL
            if (amount != 0L) {
                val unixMillis = (cepasSec + CEPAS_EPOCH_OFFSET_SEC) * 1_000L
                result += ChipTransaction(
                    dedupKey = "BNI-$txType-$amount-$cepasSec",
                    amount = amount,
                    balanceAfter = null,
                    timestamp = unixMillis
                )
            }
            offset += 16
        }
        return result to false
    }

    companion object {
        private const val MAX_REASONABLE_BALANCE = 20_000_000L
        private const val CEPAS_EPOCH_OFFSET_SEC = 788_918_400L // 1995-01-01T00:00:00Z
        private val SELECT_CEPAS_AID = hexToBytes("00A4040008A000000652301001")
        private val READ_PURSE_3_CMD = hexToBytes("9032030000")
        private val READ_TX_LOG_CMD = hexToBytes("903B030000")
    }
}
