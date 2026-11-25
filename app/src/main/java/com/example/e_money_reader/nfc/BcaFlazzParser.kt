package com.example.e_money_reader.nfc

import com.example.e_money_reader.data.local.CardIssuer

class BcaFlazzParser : EmoneyParser {
    override val issuer: CardIssuer = CardIssuer.BCA_FLAZZ

    override fun tryParse(transceiver: ApduTransceiver): ParsedCardData? {
        val selectResp = transceiver.transceive(SELECT_FLAZZ_AID)
        if (!selectResp.isStatusOk()) return null

        val balResp = transceiver.transceive(READ_BALANCE_CMD)
        if (!balResp.isStatusOk()) return null
        val balPayload = balResp.payloadWithoutSw()
        if (balPayload.size < 4) return null
        val balance = balPayload.readInt32BE(0).toLong() and 0xFFFFFFFFL
        if (balance > MAX_REASONABLE_BALANCE) return null

        val cardNumber = extractEmvPan(transceiver).orEmpty()

        return ParsedCardData(
            uid = transceiver.uidHex,
            cardNumber = cardNumber,
            issuer = CardIssuer.BCA_FLAZZ,
            balance = balance,
            chipTransactions = emptyList()
        )
    }

    private fun extractEmvPan(transceiver: ApduTransceiver): String? {
        val recResp = transceiver.transceive(READ_RECORD_1_CMD)
        if (!recResp.isStatusOk()) return null
        val payload = recResp.payloadWithoutSw()
        for (i in 0 until payload.size - 2) {
            val tag = payload[i].toInt() and 0xFF
            val len = payload[i + 1].toInt() and 0xFF
            if (tag == 0x5A && len in 4..10 && i + 2 + len <= payload.size) {
                return payload.copyOfRange(i + 2, i + 2 + len).toHex().trimEnd('F')
            }
        }
        return null
    }

    companion object {
        private const val MAX_REASONABLE_BALANCE = 20_000_000L
        private val SELECT_FLAZZ_AID = hexToBytes("00A4040007A0000006723010")
        private val READ_BALANCE_CMD = hexToBytes("805C000204")
        private val READ_RECORD_1_CMD = hexToBytes("00B2011400")
    }
}
