package app.trakr.core.nfc

import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.util.Log

object NfcPairingManager {
    private const val TAG = "NfcPairing"

    data class NfcData(val bleAddress: String?, val raw: String)

    fun parseIntent(intent: android.content.Intent): NfcData? {
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (rawMsgs != null) {
            for (msg in rawMsgs) {
                val ndef = msg as? NdefMessage ?: continue
                for (record in ndef.records) {
                    try {
                        val text = record.payload.toString(Charsets.UTF_8)
                        // Espera formato "trakr://<BLE_ADDRESS>" ou texto com MAC
                        val mac = extractMac(text)
                        return NfcData(mac, text)
                    } catch (e: Exception) {
                        Log.w(TAG, "Falha ao parse NDEF", e)
                    }
                }
            }
        }
        // fallback: tenta ler tag NDEF diretamente
        if (tag != null) {
            try {
                val ndef = Ndef.get(tag)
                ndef?.connect()
                val msg = ndef?.ndefMessage
                ndef?.close()
                msg?.records?.forEach { record ->
                    val text = record.payload.toString(Charsets.UTF_8)
                    val mac = extractMac(text)
                    return NfcData(mac, text)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao ler tag NDEF", e)
            }
        }
        return null
    }

    private fun extractMac(text: String): String? {
        // Procura padrão MAC XX:XX:XX:XX:XX:XX
        val regex = Regex("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}")
        return regex.find(text)?.value?.uppercase()
    }
}
