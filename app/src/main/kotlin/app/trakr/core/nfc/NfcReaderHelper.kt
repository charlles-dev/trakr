package app.trakr.core.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NfcReaderHelper {
    private val _isReading = MutableStateFlow(false)
    val isReading: StateFlow<Boolean> = _isReading.asStateFlow()

    private val _scannedTag = MutableStateFlow<String?>(null)
    val scannedTag: StateFlow<String?> = _scannedTag.asStateFlow()

    private var activeCallback: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isNfcAvailable(activity: Activity): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        return adapter != null && adapter.isEnabled
    }

    fun startListening(
        activity: Activity,
        onTagRead: (String) -> Unit,
    ) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        if (!adapter.isEnabled) return

        activeCallback = onTagRead
        _isReading.value = true
        _scannedTag.value = null

        val flags =
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        val options = Bundle()
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)

        adapter.enableReaderMode(
            activity,
            { tag ->
                val epc = extractTagCode(tag)
                mainHandler.post {
                    _scannedTag.value = epc
                    _isReading.value = false
                    activeCallback?.invoke(epc)
                    stopListening(activity)
                }
            },
            flags,
            options,
        )
    }

    fun stopListening(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        adapter?.disableReaderMode(activity)
        _isReading.value = false
        activeCallback = null
    }

    fun clearScannedTag() {
        _scannedTag.value = null
    }

    private fun extractTagCode(tag: Tag): String {
        // 1. Tenta ler payload NDEF se houver texto gravado
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                val msg = ndef.ndefMessage
                if (msg != null && msg.records.isNotEmpty()) {
                    val record = msg.records[0]
                    val payload = record.payload
                    if (payload.isNotEmpty()) {
                        // Formato de texto NDEF padrão
                        val languageCodeLength = payload[0].toInt() and 0x3F
                        val text = String(payload, 1 + languageCodeLength, payload.size - 1 - languageCodeLength, Charsets.UTF_8)
                        if (text.isNotBlank()) {
                            ndef.close()
                            return text.trim().uppercase()
                        }
                    }
                }
                ndef.close()
            }
        } catch (_: Exception) {
            // Fallback para UID da tag
        }

        // 2. Fallback para o UID físico da tag em hexadecimal (Ex: 04A1B2C3D4E5)
        val uid = tag.id
        return uid.joinToString("") { "%02X".format(it) }
    }
}
