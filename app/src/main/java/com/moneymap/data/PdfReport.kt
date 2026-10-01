package com.moneymap.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.moneymap.core.ReportSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Draws a cycle report as an A4 PDF with Android's built-in PdfDocument (no extra libraries). */
object PdfReport {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 48f

    suspend fun write(context: Context, title: String, subtitle: String, sections: List<ReportSection>, fileName: String): Uri =
        withContext(Dispatchers.IO) {
            val doc = PdfDocument()
            val titlePaint = Paint().apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
            val headPaint = Paint().apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; color = 0xFF1B6B4A.toInt() }
            val textPaint = Paint().apply { textSize = 11f; isAntiAlias = true }
            val mutedPaint = Paint().apply { textSize = 10f; isAntiAlias = true; color = 0xFF666666.toInt() }
            val linePaint = Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 1f }

            var pageNo = 0
            var page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, ++pageNo).create())
            var y = MARGIN + 10

            fun newPageIfNeeded(space: Float) {
                if (y + space <= HEIGHT - MARGIN) return
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, ++pageNo).create())
                y = MARGIN + 10
            }

            page.canvas.drawText(title, MARGIN, y, titlePaint)
            y += 18
            page.canvas.drawText(subtitle, MARGIN, y, mutedPaint)
            y += 24
            for (section in sections) {
                newPageIfNeeded(40f)
                page.canvas.drawText(section.title, MARGIN, y, headPaint)
                y += 6
                page.canvas.drawLine(MARGIN, y, WIDTH - MARGIN, y, linePaint)
                y += 16
                for ((label, value) in section.rows) {
                    newPageIfNeeded(16f)
                    page.canvas.drawText(ellipsize(label, textPaint, WIDTH * 0.55f), MARGIN, y, textPaint)
                    val w = textPaint.measureText(value)
                    page.canvas.drawText(value, WIDTH - MARGIN - w, y, textPaint)
                    y += 16
                }
                y += 12
            }
            doc.finishPage(page)
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            file.outputStream().use { doc.writeTo(it) }
            doc.close()
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }

    private fun ellipsize(text: String, paint: Paint, max: Float): String {
        if (paint.measureText(text) <= max) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > max) end--
        return text.substring(0, end) + "…"
    }
}
