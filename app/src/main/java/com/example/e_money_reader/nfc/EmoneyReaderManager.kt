package com.example.e_money_reader.nfc

import java.io.IOException

class EmoneyReaderManager(
    private val parsers: List<EmoneyParser>
) {
    fun readCard(transceiver: ApduTransceiver): ReadResult {
        if (transceiver.uidHex.isBlank()) {
            return ReadResult.Error("UID kartu NFC tidak terbaca. Coba tempelkan ulang kartu.")
        }
        return try {
            for (parser in parsers) {
                val parsed = parser.tryParse(transceiver)
                if (parsed != null) {
                    return ReadResult.Success(parsed)
                }
            }
            ReadResult.Error(
                "Kartu tidak didukung atau saldo chip terenkripsi. Pastikan menggunakan Mandiri e-Money, BCA Flazz Gen 2, BNI TapCash, atau BRI Brizzi yang kompatibel."
            )
        } catch (_: IOException) {
            ReadResult.Error("Koneksi NFC terputus. Tempel dan tahan kartu sebentar di belakang HP.")
        } catch (e: Exception) {
            ReadResult.Error("Gagal membaca chip kartu: ${e.message ?: "kesalahan tidak dikenal"}")
        }
    }

    companion object {
        fun default(): EmoneyReaderManager = EmoneyReaderManager(
            listOf(
                MandiriEmoneyParser(),
                BniTapCashParser(),
                BcaFlazzParser(),
                BriBrizziParser()
            )
        )
    }
}
