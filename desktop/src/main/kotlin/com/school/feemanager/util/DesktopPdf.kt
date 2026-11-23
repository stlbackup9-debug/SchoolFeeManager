package com.school.feemanager.util

import com.school.feemanager.domain.ReportTable
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Desktop version of the report PDF (same layout as the Android one): each A4-landscape page is
 * drawn with Java2D – which shapes and falls back fonts for Myanmar text – and embedded as an image
 * in a small PDF written here, so no PDF library is needed.
 */
object PdfExporter {
    private const val W = 842f
    private const val H = 595f
    private const val M = 28f
    private const val ROW = 15f
    private const val SCALE = 2.5f // pixels per point (≈180 dpi)

    fun writeReport(t: ReportTable, school: String, out: OutputStream) {
        val pages = render(t, school)
        writePdf(pages.map { jpeg(it) }, out)
    }

    private val base = Font(Font.SANS_SERIF, Font.PLAIN, 1)

    fun render(t: ReportTable, school: String): List<BufferedImage> {
        val text = base.deriveFont(7.5f)
        val bold = base.deriveFont(Font.BOLD, 7.5f)
        val title = base.deriveFont(Font.BOLD, 13f)
        val titleSmall = base.deriveFont(Font.BOLD, 10f)
        val sub = base.deriveFont(8f)

        val measure = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics()
        fun width(s: String, f: Font) = measure.getFontMetrics(f).stringWidth(s).toFloat()

        val cols = t.headers.size
        val all = listOf(t.headers) + t.rows.map { r -> r.map { ReportTable.cellText(it) } } + listOfNotNull(t.totals?.map { ReportTable.cellText(it) })
        val natural = (0 until cols).map { c -> all.maxOf { r -> width(r.getOrNull(c) ?: "", bold) } + 8f }
        val avail = W - 2 * M
        val scale = if (natural.sum() > avail) avail / natural.sum() else 1f
        val widths = natural.map { it * scale }
        val numeric = (0 until cols).map { t.isNumeric(it) }

        val pages = ArrayList<BufferedImage>()
        lateinit var g: Graphics2D
        var y = 0f

        fun drawRow(cells: List<String>, f: Font) {
            g.font = f
            var x = M
            cells.forEachIndexed { i, s ->
                val w = widths.getOrElse(i) { 40f }
                val tx = if (numeric.getOrElse(i) { false }) x + w - 3 - width(s, f) else x + 3
                g.color = Color.BLACK
                g.drawString(s, tx, y + ROW - 4.5f)
                x += w
            }
            g.color = Color(0xBB, 0xBB, 0xBB)
            g.draw(java.awt.geom.Line2D.Float(M, y + ROW, W - M, y + ROW))
        }

        fun newPage() {
            val img = BufferedImage((W * SCALE).toInt(), (H * SCALE).toInt(), BufferedImage.TYPE_INT_RGB)
            pages += img
            g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            g.color = Color.WHITE
            g.fillRect(0, 0, img.width, img.height)
            g.scale(SCALE.toDouble(), SCALE.toDouble())
            g.stroke = java.awt.BasicStroke(0.5f)
            y = M + 12
            g.color = Color(0x1F, 0x4E, 0x79); g.font = title; g.drawString(school, M, y); y += 14
            g.color = Color.BLACK; g.font = titleSmall; g.drawString(t.title, M, y); y += 11
            g.color = Color(0x55, 0x55, 0x55); g.font = sub; g.drawString(t.subtitle, M, y)
            val pn = "Page ${pages.size}"
            g.drawString(pn, W - M - width(pn, sub), y); y += 10
            g.color = Color(0xDD, 0xEB, 0xF7); g.fill(java.awt.geom.Rectangle2D.Float(M, y, W - 2 * M, ROW))
            drawRow(t.headers, bold)
            y += ROW
        }

        fun clip(s: String, w: Float, f: Font): String {
            if (width(s, f) <= w - 4) return s
            var e = s
            while (e.isNotEmpty() && width("$e…", f) > w - 4) e = e.dropLast(1)
            return "$e…"
        }

        newPage()
        val body = t.rows.map { r -> r.map { ReportTable.cellText(it) } }
        if (body.isEmpty()) { g.font = text; g.color = Color.BLACK; g.drawString("No records match the selected filters.", M + 4, y + 11) }
        body.forEach { r ->
            if (y + ROW > H - M) newPage()
            drawRow(r.mapIndexed { i, s -> clip(s, widths[i], text) }, text)
            y += ROW
        }
        t.totals?.let { tot ->
            if (y + ROW > H - M) newPage()
            g.color = Color(0xF2, 0xF2, 0xF2); g.fill(java.awt.geom.Rectangle2D.Float(M, y, W - 2 * M, ROW))
            drawRow(tot.map { ReportTable.cellText(it) }, bold)
        }
        measure.dispose()
        return pages
    }

    private class Page(val width: Int, val height: Int, val jpeg: ByteArray)

    private fun jpeg(img: BufferedImage): Page {
        val bos = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val param = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = 0.9f }
        ImageIO.createImageOutputStream(bos).use { ios -> writer.output = ios; writer.write(null, IIOImage(img, null, null), param) }
        writer.dispose()
        return Page(img.width, img.height, bos.toByteArray())
    }

    /** Minimal PDF 1.4: one page per image, each image scaled to A4 landscape. */
    private fun writePdf(pages: List<Page>, out: OutputStream) {
        val buf = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()
        fun ascii(s: String) = buf.write(s.toByteArray(Charsets.ISO_8859_1))
        fun obj(n: Int, body: () -> Unit) { while (offsets.size < n) offsets += 0; offsets[n - 1] = buf.size(); ascii("$n 0 obj\n"); body(); ascii("\nendobj\n") }

        ascii("%PDF-1.4\n%âãÏÓ\n")
        val n = pages.size
        // objects: 1 catalog, 2 pages, then per page: page, contents, image
        obj(1) { ascii("<< /Type /Catalog /Pages 2 0 R >>") }
        obj(2) { ascii("<< /Type /Pages /Count $n /Kids [${(0 until n).joinToString(" ") { "${3 + it * 3} 0 R" }}] >>") }
        pages.forEachIndexed { i, p ->
            val pageNo = 3 + i * 3
            obj(pageNo) {
                ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${W.toInt()} ${H.toInt()}] /Resources << /XObject << /Im0 ${pageNo + 2} 0 R >> >> /Contents ${pageNo + 1} 0 R >>")
            }
            val content = "q ${W.toInt()} 0 0 ${H.toInt()} 0 0 cm /Im0 Do Q"
            obj(pageNo + 1) { ascii("<< /Length ${content.length} >>\nstream\n$content\nendstream") }
            obj(pageNo + 2) {
                ascii("<< /Type /XObject /Subtype /Image /Width ${p.width} /Height ${p.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpeg.size} >>\nstream\n")
                buf.write(p.jpeg)
                ascii("\nendstream")
            }
        }
        val xref = buf.size()
        ascii("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { ascii("%010d 00000 n \n".format(it)) }
        ascii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        buf.writeTo(out)
        out.flush()
    }
}
