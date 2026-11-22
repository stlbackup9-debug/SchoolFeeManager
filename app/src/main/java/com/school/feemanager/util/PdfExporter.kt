package com.school.feemanager.util

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.school.feemanager.domain.ReportTable
import java.io.OutputStream

/** Renders a report table to an A4-landscape PDF with Android's built-in PdfDocument. */
object PdfExporter {
    private const val W = 842
    private const val H = 595
    private const val M = 28f
    private const val ROW = 15f

    fun writeReport(t: ReportTable, school: String, out: OutputStream) {
        val doc = PdfDocument()
        val text = Paint().apply { textSize = 7.5f; isAntiAlias = true }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = Paint(bold).apply { textSize = 13f; color = 0xFF1F4E79.toInt() }
        val sub = Paint(text).apply { textSize = 8f; color = 0xFF555555.toInt() }
        val fill = Paint().apply { color = 0xFFDDEBF7.toInt() }
        val line = Paint().apply { color = 0xFFBBBBBB.toInt(); strokeWidth = 0.5f }

        val cols = t.headers.size
        val all = listOf(t.headers) + t.rows.map { r -> r.map { ReportTable.cellText(it) } } + listOfNotNull(t.totals?.map { ReportTable.cellText(it) })
        val natural = (0 until cols).map { c -> all.maxOf { r -> bold.measureText(r.getOrNull(c) ?: "") } + 8f }
        val avail = W - 2 * M
        val scale = if (natural.sum() > avail) avail / natural.sum() else 1f
        val widths = natural.map { it * scale }
        val numeric = (0 until cols).map { t.isNumeric(it) }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            val c = page!!.canvas
            y = M + 12
            c.drawText(school, M, y, title); y += 14
            c.drawText(t.title, M, y, bold.apply { textSize = 10f }); bold.textSize = 7.5f; y += 11
            c.drawText(t.subtitle, M, y, sub)
            c.drawText("Page $pageNo", W - M - sub.measureText("Page $pageNo"), y, sub); y += 10
            c.drawRect(M, y, W - M, y + ROW, fill)
            drawRow(c, t.headers, widths, numeric, y, bold, line)
            y += ROW
        }
        fun clip(s: String, w: Float, p: Paint): String {
            if (p.measureText(s) <= w - 4) return s
            var e = s
            while (e.isNotEmpty() && p.measureText("$e…") > w - 4) e = e.dropLast(1)
            return "$e…"
        }
        newPage()
        val body = t.rows.map { r -> r.map { ReportTable.cellText(it) } }
        if (body.isEmpty()) page!!.canvas.drawText("No records match the selected filters.", M + 4, y + 11, text)
        body.forEach { r ->
            if (y + ROW > H - M) newPage()
            drawRow(page!!.canvas, r.mapIndexed { i, s -> clip(s, widths[i], text) }, widths, numeric, y, text, line)
            y += ROW
        }
        t.totals?.let { tot ->
            if (y + ROW > H - M) newPage()
            page!!.canvas.drawRect(M, y, W - M, y + ROW, Paint().apply { color = 0xFFF2F2F2.toInt() })
            drawRow(page!!.canvas, tot.map { ReportTable.cellText(it) }, widths, numeric, y, bold, line)
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
    }

    private fun drawRow(c: android.graphics.Canvas, cells: List<String>, widths: List<Float>, numeric: List<Boolean>, y: Float, p: Paint, line: Paint) {
        var x = M
        cells.forEachIndexed { i, s ->
            val w = widths.getOrElse(i) { 40f }
            val tx = if (numeric.getOrElse(i) { false }) x + w - 3 - p.measureText(s) else x + 3
            c.drawText(s, tx, y + ROW - 4.5f, p)
            x += w
        }
        c.drawLine(M, y + ROW, W - M, y + ROW, line)
    }
}
