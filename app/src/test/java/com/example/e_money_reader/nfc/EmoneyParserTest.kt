package com.example.e_money_reader.nfc

import com.example.e_money_reader.data.local.CardIssuer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmoneyParserTest {

    private class FakeTransceiver(
        override val uidHex: String,
        private val responses: Map<String, ByteArray>
    ) : ApduTransceiver {
        override fun transceive(command: ByteArray): ByteArray {
            val key = command.joinToString("") { "%02X".format(it) }
            return responses[key] ?: hexToBytes("6A82")
        }
    }

    @Test
    fun readsMandiriEmoneyBalanceCardNumberAndLogs() {
        // Balance Rp 148.500 = 0x00024414 -> LE: 14 44 02 00 + 9000
        // Card number 6032982012344821 at start of info response + 9000
        // Log record (16 bytes): seq=0x0C, debit (-7500 = 0xFFFFE2B4 LE -> B4 E2 FF FF), balAfter=148500 (14 44 02 00), epochSec=1732500000 (80 65 44 67)
        val logRecord = "0C" + "B4E2FFFF" + "14440200" + "80654467" + "000000"
        val transceiver = FakeTransceiver(
            uidHex = "04A1B2C3D4E580",
            responses = mapOf(
                "00A40400080000000000000001" to hexToBytes("9000"),
                "00B300003F" to hexToBytes("6032982012344821" + "00".repeat(55) + "9000"),
                "00B500000A" to hexToBytes("144402000000000000009000"),
                "00B7000010" to hexToBytes(logRecord + "9000")
            )
        )

        val manager = EmoneyReaderManager.default()
        val result = manager.readCard(transceiver)

        assertTrue("Expected ReadResult.Success but got $result", result is ReadResult.Success)
        val data = (result as ReadResult.Success).data
        assertEquals(CardIssuer.MANDIRI, data.issuer)
        assertEquals("6032982012344821", data.cardNumber)
        assertEquals(148_500L, data.balance)
        assertEquals(1, data.chipTransactions.size)
        assertEquals(-7_500L, data.chipTransactions.first().amount)
        assertEquals(148_500L, data.chipTransactions.first().balanceAfter)
    }

    @Test
    fun readsBniTapCashCepasPurseAndTransactions() {
        // CEPAS Purse 3: [0]=02, [1]=00, [2..4]=balance 24500 (0x005FB4), [5..7]=000000, [8..15]=CAN 7546120000001109
        val purseData = "0200" + "005FB4" + "000000" + "7546120000001109" + "00".repeat(16)
        // CEPAS Tx log (16 bytes): type=0x30 (debit), amount=-3500 (3-byte signed BE: FFF254), cepasTime=946684800 (386D4380), userdata=8 bytes
        val txRecord = "30" + "FFF254" + "386D4380" + "4B524C3031000000"
        val transceiver = FakeTransceiver(
            uidHex = "04112233445566",
            responses = mapOf(
                "9032030000" to hexToBytes(purseData + "9100"),
                "903B030001000100" to hexToBytes(txRecord + "9100")
            )
        )

        val result = EmoneyReaderManager.default().readCard(transceiver)
        assertTrue(result is ReadResult.Success)
        val data = (result as ReadResult.Success).data
        assertEquals(CardIssuer.BNI_TAPCASH, data.issuer)
        assertEquals("7546120000001109", data.cardNumber)
        assertEquals(24_500L, data.balance)
        assertEquals(1, data.chipTransactions.size)
        assertEquals(-3_500L, data.chipTransactions.first().amount)
    }

    @Test
    fun readsBalanceOnlyCardWithEmptyChipLogs() {
        // BCA Flazz Gen 2: SELECT AID succeeds, balance Rp 72.000 (0x011940), no log command supported
        val transceiver = FakeTransceiver(
            uidHex = "04998877665544",
            responses = mapOf(
                "00A4040007A0000006723010" to hexToBytes("6F0E8407A00000067230109000"),
                "805C000204" to hexToBytes("000119409000"),
                "00B2011400" to hexToBytes("5A0810980000123477349000")
            )
        )

        val result = EmoneyReaderManager.default().readCard(transceiver)
        assertTrue(result is ReadResult.Success)
        val data = (result as ReadResult.Success).data
        assertEquals(CardIssuer.BCA_FLAZZ, data.issuer)
        assertEquals(72_000L, data.balance)
        assertTrue(data.chipTransactions.isEmpty())
    }

    @Test
    fun rejectsEncryptedOrUnsupportedTagWithReadError() {
        val transceiver = FakeTransceiver(
            uidHex = "04DEADBEEF0001",
            responses = mapOf(
                "00A40400080000000000000001" to hexToBytes("6982") // Security status not satisfied
            )
        )

        val result = EmoneyReaderManager.default().readCard(transceiver)
        assertTrue(result is ReadResult.Error)
    }

    companion object {
        private fun hexToBytes(hex: String): ByteArray {
            val clean = hex.replace(" ", "")
            return ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
    }
}
