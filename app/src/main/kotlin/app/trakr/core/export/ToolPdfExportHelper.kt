package app.trakr.core.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import app.trakr.R
import app.trakr.model.Tool
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ToolPdfExportHelper {
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun generateAndSharePdfReport(
        context: Context,
        tools: List<Tool>,
    ) {
        val timestamp = fileDateFormat.format(Date())
        val dateHuman = dateFormat.format(Date())
        val filename = "trakr_auditoria_$timestamp.pdf"

        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas

        val paint =
            Paint().apply {
                isAntiAlias = true
            }

        // Fundo Superior Escuro / Tático
        paint.color = Color.rgb(18, 26, 22)
        canvas.drawRect(0f, 0f, 595f, 100f, paint)

        // Logo / Título
        paint.color = Color.rgb(0, 230, 118) // Neon Green
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        canvas.drawText("TRAKR // AUDITORIA TÁTICA", 30f, 45f, paint)

        paint.color = Color.WHITE
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Relatório Oficial de Conformidade e Inventário RFID", 30f, 65f, paint)
        canvas.drawText("Emissão: $dateHuman", 30f, 80f, paint)

        // Linha divisória
        paint.color = Color.rgb(0, 230, 118)
        paint.strokeWidth = 2f
        canvas.drawLine(0f, 100f, 595f, 100f, paint)

        // Resumo de Telemetria
        var y = 130f
        paint.strokeWidth = 1f
        paint.color = Color.rgb(30, 30, 30)
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("RESUMO EXECUTIVO", 30f, y, paint)

        y += 25f
        val presentCount = tools.count { it.present }
        val missingCount = tools.count { !it.present }
        val complianceRate = if (tools.isNotEmpty()) (presentCount * 100) / tools.size else 100

        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.DKGRAY
        canvas.drawText("Total de Itens Auditados: ${tools.size}", 30f, y, paint)
        canvas.drawText("Itens Presentes: $presentCount", 220f, y, paint)
        canvas.drawText("Itens Ausentes: $missingCount", 380f, y, paint)

        y += 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = if (complianceRate >= 90) Color.rgb(0, 150, 50) else Color.rgb(200, 30, 30)
        canvas.drawText("Taxa de Conformidade: $complianceRate%", 30f, y, paint)

        // Tabela de Ferramentas
        y += 35f
        paint.color = Color.rgb(240, 240, 240)
        canvas.drawRect(30f, y - 15f, 565f, y + 10f, paint)

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FERRAMENTA", 35f, y, paint)
        canvas.drawText("CATEGORIA", 220f, y, paint)
        canvas.drawText("TAG EPC", 330f, y, paint)
        canvas.drawText("STATUS", 480f, y, paint)

        y += 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        tools.take(25).forEach { tool ->
            paint.color = Color.BLACK
            canvas.drawText(tool.name.take(25), 35f, y, paint)
            canvas.drawText(tool.category.uppercase(), 220f, y, paint)
            canvas.drawText(tool.epc.takeLast(12), 330f, y, paint)

            if (tool.present) {
                paint.color = Color.rgb(0, 150, 50)
                canvas.drawText("[ OK ] PRESENTE", 480f, y, paint)
            } else {
                paint.color = Color.rgb(200, 30, 30)
                canvas.drawText("[ ! ] AUSENTE", 480f, y, paint)
            }
            y += 18f
        }

        // Rodapé / Assinatura
        y = 750f
        paint.color = Color.LTGRAY
        canvas.drawLine(50f, y, 250f, y, paint)
        canvas.drawLine(345f, y, 545f, y, paint)

        paint.color = Color.DKGRAY
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Assinatura do Técnico / Inspetor", 70f, y + 15f, paint)
        canvas.drawText("Assinatura do Responsável / Supervisor", 355f, y + 15f, paint)

        pdfDoc.finishPage(page)

        val cacheDir = File(context.cacheDir, "reports")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val file = File(cacheDir, filename)
        val fos = FileOutputStream(file)
        pdfDoc.writeTo(fos)
        fos.close()
        pdfDoc.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_report_subject, dateHuman))
                putExtra(Intent.EXTRA_TEXT, "Relatório Tático de Auditoria Trakr gerado em $dateHuman.")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(intent, "Compartilhar Relatório PDF"))
    }
}
