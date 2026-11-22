package com.school.feemanager.io

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Small, dependency-free .xlsx reader and writer (Office Open XML).
 * Uses only java.util.zip and the DOM parser that ship with both Android and the JVM,
 * which keeps the APK small and avoids Apache POI's Android incompatibilities.
 */

data class XCell(val text: String?, val number: Double?, val isFormula: Boolean) {
    val isBlank: Boolean get() = (text == null || text.isBlank()) && number == null
    fun asText(): String = text ?: number?.let { if (it == Math.floor(it) && !it.isInfinite()) it.toLong().toString() else it.toString() } ?: ""
}

class XSheet(val name: String, private val cells: Map<Long, XCell>) {
    val maxRow: Int = cells.keys.maxOfOrNull { (it shr 20).toInt() } ?: 0
    fun cell(row: Int, col: Int): XCell? = cells[key(row, col)]
    fun text(row: Int, col: Int): String = cell(row, col)?.asText()?.trim() ?: ""
    fun number(row: Int, col: Int): Double? = cell(row, col)?.let { it.number ?: it.text?.replace(",", "")?.trim()?.toDoubleOrNull() }
    fun allCells(): Map<Long, XCell> = cells

    companion object {
        fun key(row: Int, col: Int): Long = (row.toLong() shl 20) or col.toLong()
        fun rowOf(key: Long): Int = (key shr 20).toInt()
        fun colOf(key: Long): Int = (key and 0xFFFFF).toInt()
    }
}

object XlsxReader {

    fun read(input: InputStream): Map<String, XSheet> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var e: ZipEntry? = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory) files[e.name.trimStart('/')] = zip.readBytes()
                e = zip.nextEntry
            }
        }
        val wb = files["xl/workbook.xml"] ?: throw IllegalArgumentException("Not a valid .xlsx workbook (xl/workbook.xml missing)")
        val shared = files["xl/sharedStrings.xml"]?.let { parseShared(it) } ?: emptyList()
        val rels = files["xl/_rels/workbook.xml.rels"]?.let { parseRels(it) } ?: emptyMap()
        val result = LinkedHashMap<String, XSheet>()
        val doc = dom(wb)
        for (sheet in elements(doc.documentElement, "sheet")) {
            val name = sheet.getAttribute("name")
            val rid = attr(sheet, "id")
            var target = rels[rid] ?: continue
            target = if (target.startsWith("/")) target.trimStart('/') else "xl/" + target.removePrefix("./")
            val bytes = files[target] ?: continue
            result[name] = XSheet(name, parseSheet(bytes, shared))
        }
        return result
    }

    private fun parseShared(bytes: ByteArray): List<String> {
        val doc = dom(bytes)
        return children(doc.documentElement, "si").map { si -> textOfAll(si, "t") }
    }

    private fun parseRels(bytes: ByteArray): Map<String, String> {
        val doc = dom(bytes)
        return elements(doc.documentElement, "Relationship").associate { it.getAttribute("Id") to it.getAttribute("Target") }
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): Map<Long, XCell> {
        val doc = dom(bytes)
        val out = HashMap<Long, XCell>()
        for (c in elements(doc.documentElement, "c")) {
            val ref = c.getAttribute("r")
            if (ref.isNullOrEmpty()) continue
            val (row, col) = parseRef(ref) ?: continue
            val t = c.getAttribute("t")
            val v = children(c, "v").firstOrNull()?.textContent
            val isFormula = children(c, "f").isNotEmpty()
            val cell = when (t) {
                "s" -> XCell(v?.toIntOrNull()?.let { shared.getOrNull(it) }, null, isFormula)
                "inlineStr" -> XCell(children(c, "is").firstOrNull()?.let { textOfAll(it, "t") }, null, isFormula)
                "str", "e" -> XCell(v, null, isFormula)
                "b" -> XCell(if (v == "1") "TRUE" else "FALSE", null, isFormula)
                else -> XCell(null, v?.toDoubleOrNull(), isFormula)
            }
            out[XSheet.key(row, col)] = cell
        }
        return out
    }

    /** "B12" -> (12, 2). Rows and columns are 1-based. */
    fun parseRef(ref: String): Pair<Int, Int>? {
        var i = 0
        var col = 0
        while (i < ref.length && ref[i].isLetter()) { col = col * 26 + (ref[i].uppercaseChar() - 'A' + 1); i++ }
        val row = ref.substring(i).toIntOrNull() ?: return null
        return if (col == 0) null else row to col
    }

    private fun dom(bytes: ByteArray): org.w3c.dom.Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = false
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
        try { f.isExpandEntityReferences = false } catch (_: Exception) {}
        return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun local(n: Node): String = n.nodeName.substringAfter(':')

    private fun attr(e: Element, localName: String): String? {
        val attrs = e.attributes
        for (i in 0 until attrs.length) {
            val a = attrs.item(i)
            if (a.nodeName.substringAfter(':') == localName && a.nodeName.contains(':')) return a.nodeValue
        }
        return e.getAttribute(localName).ifEmpty { null }
    }

    private fun children(e: Element, name: String): List<Element> {
        val list = ArrayList<Element>()
        val nl = e.childNodes
        for (i in 0 until nl.length) {
            val n = nl.item(i)
            if (n is Element && local(n) == name) list += n
        }
        return list
    }

    /** All descendants with the given local name (document order). */
    private fun elements(root: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        fun walk(n: Node) {
            val nl = n.childNodes
            for (i in 0 until nl.length) {
                val c = nl.item(i)
                if (c is Element) {
                    if (local(c) == name) out += c
                    walk(c)
                }
            }
        }
        walk(root)
        return out
    }

    /** Concatenates all <t> descendants, skipping phonetic runs (<rPh>). */
    private fun textOfAll(e: Element, tName: String): String {
        val sb = StringBuilder()
        fun walk(n: Node) {
            val nl = n.childNodes
            for (i in 0 until nl.length) {
                val c = nl.item(i)
                if (c is Element) {
                    when (local(c)) {
                        "rPh" -> {}
                        tName -> sb.append(c.textContent)
                        else -> walk(c)
                    }
                }
            }
        }
        walk(e)
        return sb.toString()
    }
}

/** Builder for a simple multi-sheet workbook with a title row, bold headers and #,##0 numbers. */
class XlsxWriter {
    private class Sheet(val name: String) {
        val rows = sortedMapOf<Int, MutableMap<Int, Pair<Any, Int>>>()
        val widths = HashMap<Int, Double>()
    }

    companion object {
        const val STYLE_TEXT = 0
        const val STYLE_BOLD = 1
        const val STYLE_NUMBER = 2
        const val STYLE_TITLE = 3
        const val STYLE_HEADER = 4
        const val STYLE_BOLD_NUMBER = 5
        const val STYLE_PERCENT = 6
    }

    private val sheets = ArrayList<Sheet>()
    private var current: Sheet? = null

    fun sheet(name: String): XlsxWriter {
        val safe = name.replace(Regex("[\\[\\]:*?/\\\\]"), "_").take(31)
        current = Sheet(safe).also { sheets += it }
        return this
    }

    fun set(row: Int, col: Int, value: Any?, style: Int = -1): XlsxWriter {
        if (value == null) return this
        val s = current ?: error("call sheet() first")
        val st = if (style >= 0) style else if (value is Number) STYLE_NUMBER else STYLE_TEXT
        s.rows.getOrPut(row) { sortedMapOf() }[col] = value to st
        return this
    }

    fun width(col: Int, chars: Double): XlsxWriter { current!!.widths[col] = chars; return this }

    fun toBytes(): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(name: String, content: String) {
                z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            put("[Content_Types].xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
                sheets.indices.forEach { append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""") }
                append("</Types>")
            })
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
                sheets.forEachIndexed { i, s -> append("""<sheet name="${esc(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
                append("</sheets></workbook>")
            })
            put("xl/_rels/workbook.xml.rels", buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                sheets.indices.forEach { append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""") }
                append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            })
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
        return bos.toByteArray()
    }

    private fun sheetXml(s: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (s.widths.isNotEmpty()) {
            append("<cols>")
            s.widths.toSortedMap().forEach { (c, w) -> append("""<col min="$c" max="$c" width="$w" customWidth="1"/>""") }
            append("</cols>")
        }
        append("<sheetData>")
        s.rows.forEach { (r, cols) ->
            append("""<row r="$r">""")
            cols.forEach { (c, pair) ->
                val (v, st) = pair
                val ref = colName(c) + r
                when (v) {
                    is Number -> append("""<c r="$ref" s="$st"><v>$v</v></c>""")
                    else -> append("""<c r="$ref" s="$st" t="inlineStr"><is><t xml:space="preserve">${esc(v.toString())}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it == '\n' || it == '\r' || it >= ' ' }

    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0"/></numFmts><fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="14"/><color rgb="FF1F4E79"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFDDEBF7"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="7"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment wrapText="1" vertical="center"/></xf><xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/><xf numFmtId="10" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs></styleSheet>"""

    fun colName(c: Int): String {
        var n = c
        val sb = StringBuilder()
        while (n > 0) { val m = (n - 1) % 26; sb.insert(0, ('A' + m)); n = (n - 1) / 26 }
        return sb.toString()
    }
}
