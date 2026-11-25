package com.example.e_money_reader.nfc

import com.example.e_money_reader.data.local.CardIssuer

interface EmoneyParser {
    val issuer: CardIssuer
    fun tryParse(transceiver: ApduTransceiver): ParsedCardData?
}
