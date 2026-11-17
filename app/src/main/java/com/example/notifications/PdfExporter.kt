package com.example.notifications

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

object PdfExporter {
    fun exportStudyMaterialToUri(
        context: Context,
        uri: Uri,
        title: String,
        subtitle: String,
        body: String
    ): Result<Unit> {
        return try {
            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 48
            val contentWidth = pageWidth - margin * 2

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8C141E")
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#6B4F4B")
                textSize = 11f
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#201514")
                textSize = 12f
            }

            val fullLayout = StaticLayout.Builder
                .obtain(body, 0, body.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, 1.15f)
                .build()

            var lineIndex = 0
            var pageNum = 1
            while (lineIndex < fullLayout.lineCount || pageNum == 1) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                var y = margin.toFloat()
                if (pageNum == 1) {
                    canvas.drawText(title.take(60), margin.toFloat(), y + 20f, titlePaint)
                    y += 30f
                    canvas.drawText(subtitle.take(80), margin.toFloat(), y + 12f, subPaint)
                    y += 28f
                    val rulePaint = Paint().apply { color = Color.parseColor("#DCC7C3"); strokeWidth = 1.5f }
                    canvas.drawLine(margin.toFloat(), y, (pageWidth - margin).toFloat(), y, rulePaint)
                    y += 18f
                }

                val maxY = (pageHeight - margin).toFloat()
                while (lineIndex < fullLayout.lineCount) {
                    val lineHeight = (fullLayout.getLineBottom(lineIndex) - fullLayout.getLineTop(lineIndex)).toFloat()
                    if (y + lineHeight > maxY) break
                    val start = fullLayout.getLineStart(lineIndex)
                    val end = fullLayout.getLineEnd(lineIndex)
                    val lineText = body.substring(start, end).trimEnd('\n')
                    canvas.drawText(lineText, margin.toFloat(), y - fullLayout.getLineAscent(lineIndex), bodyPaint)
                    y += lineHeight
                    lineIndex++
                }

                document.finishPage(page)
                pageNum++
                if (fullLayout.lineCount == 0) break
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                document.writeTo(out)
            } ?: throw IllegalStateException("Unable to open output stream for PDF export.")
            document.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
