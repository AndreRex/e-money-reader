package com.example.e_money_reader.nfc

import android.nfc.tech.IsoDep

interface ApduTransceiver {
    val uidHex: String
    fun transceive(command: ByteArray): ByteArray
}

class IsoDepApduTransceiver(
    private val isoDep: IsoDep
) : ApduTransceiver {
    override val uidHex: String
        get() = isoDep.tag?.id?.toHex().orEmpty()

    override fun transceive(command: ByteArray): ByteArray {
        if (!isoDep.isConnected) {
            isoDep.connect()
            isoDep.timeout = 3_000
        }
        return isoDep.transceive(command)
    }
}
