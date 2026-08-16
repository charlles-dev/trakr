package app.trakr.core.ble

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import app.trakr.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Resultado de uma atualização de firmware via BLE. */
sealed interface OtaResult {
    data object Ok : OtaResult

    data class Error(
        @StringRes val messageRes: Int,
    ) : OtaResult
}

/**
 * Transfere um firmware (.bin) para o rastreador via OTA over GATT.
 *
 * Protocolo (espelhado no firmware):
 *   1. {"cmd":"ota_begin","size":N}   — abre a sessão (Control)
 *   2. chunks binários na característica OTA
 *   3. {"cmd":"ota_end"}              — valida, define boot e reinicia
 */
object OtaManager {
    const val CHUNK_SIZE = 200

    suspend fun update(
        context: Context,
        uri: Uri,
        onProgress: (Int) -> Unit,
    ): OtaResult =
        withContext(Dispatchers.IO) {
            if (BleManager.connectedCount == 0) {
                return@withContext OtaResult.Error(R.string.ota_no_device)
            }

            val bytes =
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext OtaResult.Error(R.string.ota_err_read)
            if (bytes.size < 1024) {
                return@withContext OtaResult.Error(R.string.ota_err_too_small)
            }

            try {
                if (!beginOta(bytes.size.toLong())) {
                    return@withContext OtaResult.Error(R.string.ota_err_begin)
                }

                var sent = 0
                while (sent < bytes.size) {
                    val end = minOf(sent + CHUNK_SIZE, bytes.size)
                    val chunk = bytes.copyOfRange(sent, end)
                    if (!sendChunk(chunk)) {
                        BleManager.abortOta()
                        return@withContext OtaResult.Error(R.string.ota_err_chunk)
                    }
                    sent = end
                    onProgress(sent * 100 / bytes.size)
                    // Pequena pausa para o firmware processar cada gravação.
                    delay(15)
                }

                if (!endOta()) {
                    return@withContext OtaResult.Error(R.string.ota_err_end)
                }
                OtaResult.Ok
            } catch (e: Exception) {
                BleManager.abortOta()
                OtaResult.Error(R.string.ota_err_unknown)
            }
        }

    // ---------------- Primitivas GATT (via BleManager) ----------------

    private suspend fun beginOta(size: Long): Boolean {
        BleManager.beginOta(size)
        return awaitReply("ota_begin")
    }

    private suspend fun endOta(): Boolean {
        BleManager.endOta()
        return awaitReply("ota_end")
    }

    private suspend fun sendChunk(chunk: ByteArray): Boolean = BleManager.sendOtaChunk(chunk)

    private suspend fun awaitReply(cmd: String): Boolean {
        // O firmware responde via Event notify (cmd_reply) em até ~3 s.
        repeat(30) {
            delay(100)
            val reply = BleManager.lastReply.value
            if (reply != null && reply.cmd == cmd) {
                return reply.status == "ok"
            }
        }
        return false
    }
}
