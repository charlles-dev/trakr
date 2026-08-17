package app.trakr.core.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.trakr.R
import app.trakr.model.Tool
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ToolExportHelper {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun generateCsvReport(tools: List<Tool>): String {
        val sb = StringBuilder()
        sb.append("ID,Nome,Categoria,Tag_EPC,Status,Ultimo_RSSI_dBm,Ultima_Varredura\n")
        tools.forEach { tool ->
            val status = if (tool.present) "PRESENTE" else "AUSENTE"
            val lastSeen = tool.lastSeenAt?.let { dateFormat.format(Date(it)) } ?: "Nunca"
            val rssi = tool.rssi?.toString() ?: "N/A"
            sb.append("\"${tool.id}\",\"${tool.name}\",\"${tool.category}\",\"${tool.epc}\",\"$status\",$rssi,\"$lastSeen\"\n")
        }
        return sb.toString()
    }

    fun generateJsonReport(tools: List<Tool>): String {
        val root = JSONObject()
        val now = Date()
        root.put("generated_at", dateFormat.format(now))
        root.put("total_tools", tools.size)
        root.put("present_tools", tools.count { it.present })
        root.put("missing_tools", tools.count { !it.present })
        val arr = JSONArray()
        tools.forEach { tool ->
            val obj = JSONObject()
            obj.put("id", tool.id)
            obj.put("name", tool.name)
            obj.put("category", tool.category)
            obj.put("epc", tool.epc)
            obj.put("present", tool.present)
            obj.put("rssi", tool.rssi)
            obj.put("last_seen_at", tool.lastSeenAt)
            arr.put(obj)
        }
        root.put("tools", arr)
        return root.toString(2)
    }

    fun shareAuditReport(
        context: Context,
        tools: List<Tool>,
        format: String = "csv",
    ) {
        try {
            val timestamp = fileDateFormat.format(Date())
            val filename = "trakr_auditoria_$timestamp.$format"
            val content =
                if (format.lowercase() == "json") {
                    generateJsonReport(tools)
                } else {
                    generateCsvReport(tools)
                }

            val cacheDir = File(context.cacheDir, "reports")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, filename)
            file.writeText(content)

            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )

            val dateHuman = dateFormat.format(Date())
            val subject = context.getString(R.string.export_report_subject, dateHuman)
            val presentCount = tools.count { it.present }
            val missingCount = tools.count { !it.present }

            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = if (format == "json") "application/json" else "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Relatório Trakr em $dateHuman.\nTotal: ${tools.size} ($presentCount presentes, $missingCount ausentes).",
                    )
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            val chooser = Intent.createChooser(intent, context.getString(R.string.action_export_report))
            context.startActivity(chooser)
        } catch (e: Exception) {
            val dateHuman = dateFormat.format(Date())
            val summary = generateCsvReport(tools)
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_report_subject, dateHuman))
                    putExtra(Intent.EXTRA_TEXT, summary)
                }
            val chooser = Intent.createChooser(intent, context.getString(R.string.action_export_report))
            context.startActivity(chooser)
        }
    }
}
