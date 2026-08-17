package app.trakr.core.importexport

import android.content.Context
import android.net.Uri
import app.trakr.model.Tool
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

object ToolImportHelper {
    fun parseToolsFromUri(
        context: Context,
        uri: Uri,
    ): List<Tool> {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return emptyList()
        val reader = BufferedReader(InputStreamReader(inputStream))
        val rawContent = reader.readText().trim()
        reader.close()

        if (rawContent.startsWith("{")) {
            return parseJson(rawContent)
        } else {
            return parseCsv(rawContent)
        }
    }

    private fun parseJson(jsonStr: String): List<Tool> {
        val list = mutableListOf<Tool>()
        val root = JSONObject(jsonStr)
        val arr = root.optJSONArray("tools") ?: return emptyList()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name", "").trim()
            val epc = o.optString("epc", "").trim().uppercase()
            val category = o.optString("category", "manual").trim().lowercase()
            if (name.isNotEmpty() && epc.isNotEmpty()) {
                list.add(
                    Tool(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = name,
                        category = category,
                        epc = epc,
                        present = o.optBoolean("present", false),
                        rssi = if (o.has("rssi") && !o.isNull("rssi")) o.getInt("rssi") else null,
                        lastSeenAt = if (o.has("last_seen_at") && !o.isNull("last_seen_at")) o.getLong("last_seen_at") else null,
                    ),
                )
            }
        }
        return list
    }

    private fun parseCsv(csvStr: String): List<Tool> {
        val list = mutableListOf<Tool>()
        val lines = csvStr.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        // Pula o cabeçalho se houver
        val startIndex = if (lines[0].contains("Nome", ignoreCase = true) || lines[0].contains("EPC", ignoreCase = true)) 1 else 0

        for (i in startIndex until lines.size) {
            val line = lines[i]
            // Divide respeitando aspas básicas
            val cols = line.split(",").map { it.trim().removeSurrounding("\"") }
            if (cols.size >= 2) {
                // Formato padrão: ID, Nome, Categoria, EPC... ou Nome, EPC
                val (name, category, epc) =
                    when {
                        cols.size >= 4 -> Triple(cols[1], cols[2], cols[3])
                        cols.size >= 3 -> Triple(cols[0], cols[1], cols[2])
                        else -> Triple(cols[0], "manual", cols[1])
                    }
                if (name.isNotBlank() && epc.isNotBlank()) {
                    list.add(
                        Tool(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            category = category.lowercase(),
                            epc = epc.uppercase(),
                            present = false,
                        ),
                    )
                }
            }
        }
        return list
    }
}
