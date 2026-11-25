package com.example.e_money_reader.nfc

import com.example.e_money_reader.data.local.CardIssuer

class BriBrizziParser : EmoneyParser {
    override val issuer: CardIssuer = CardIssuer.BRI_BRIZZI

    override fun tryParse(transceiver: ApduTransceiver): ParsedCardData? {
        val selectResp = transceiver.transceive(SELECT_BRIZZI_AID)
        if (!selectResp.isStatusOk()) return null

        val balResp = transceiver.transceive(READ_BALANCE_CMD)
        if (!balResp.isStatusOk()) return null
        val balPayload = balResp.payloadWithoutSw()
        if (balPayload.size < 4) return null
        val balance = balPayload.readInt32BE(0).toLong() and 0xFFFFFFFFL
        if (balance > MAX_REASONABLE_BALANCE) return null

        return ParsedCardData(
            uid = transceiver.uidHex,
            cardNumber = transceiver.uidHex,
            issuer = CardIssuer.BRI_BRIZZI,
            balance = balance,
            chipTransactions = emptyList()
        )
    }

    companion object {
        private const val MAX_REASONABLE_BALANCE = 20_000_000L
        private val SELECT_BRIZZI_AID = hexToBytes("00A4040007A0000006021010")
        private val READ_BALANCE_CMD = hexToBytes("805C000204")
    }
}
