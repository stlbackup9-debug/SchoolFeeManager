package com.school.feemanager.io

import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.ClassRate
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.Money
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import com.school.feemanager.io.XlsxWriter.Companion.STYLE_HEADER
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Field parsing shared by the workbook import and the module imports. */
object Fields {
    /** "Enroll No." → "enrollno" – used to match column headers loosely. */
    fun norm(s: String): String = s.lowercase(Locale.US).filter { it.isLetterOrDigit() }

    private val DATE_FORMATS = listOf("d/M/yyyy", "d-M-yyyy", "yyyy-M-d", "yyyy/M/d", "d-MMM-yyyy", "d MMM yyyy", "d-MMM-yy", "M/d/yyyy", "d.M.yyyy")
        .map { DateTimeFormatter.ofPattern(it, Locale.US) }

    /** Excel serial date (1900 system) or common text formats. */
    fun date(c: XCell?): LocalDate? {
        if (c == null || c.isBlank) return null
        c.number?.let { n -> return if (n in 1.0..2958465.0) LocalDate.of(1899, 12, 30).plusDays(n.toLong()) else null }
        val raw = c.text!!.trim()
        val t = raw.substringBefore('T').substringBefore(' ').let { if (it.length < 6) raw else it }
        for (f in DATE_FORMATS) {
            try { return LocalDate.parse(t, f) } catch (_: Exception) {}
            try { return LocalDate.parse(raw, f) } catch (_: Exception) {}
        }
        // a CSV made from Excel may contain the serial number as text
        raw.toDoubleOrNull()?.let { n -> if (n in 20000.0..80000.0) return LocalDate.of(1899, 12, 30).plusDays(n.toLong()) }
        return null
    }

    /** Discount as 0.1, "10%", or 10 (read as 10 %). Problems are reported through [warn]; 0 is used. */
    fun discountBp(cell: XCell?, name: String, warn: (String) -> Unit): Int {
        if (cell == null || cell.isBlank || cell.isFormula) return 0
        val text = cell.text?.trim()
        val d = cell.number ?: text?.replace("%", "")?.trim()?.toDoubleOrNull()?.let { if (text.contains("%")) it / 100 else it }
        return when {
            d == null -> { warn("$name: discount '${cell.asText()}' is not a number – 0% used"); 0 }
            d < 0 -> { warn("$name: negative discount – 0% used"); 0 }
            d <= 1.0 -> Money.fractionToBp(d)
            d <= 100.0 -> { warn("$name: discount $d read as $d%"); Money.fractionToBp(d / 100) }
            else -> { warn("$name: discount $d is above 100% – 0% used"); 0 }
        }
    }

    /** Rows sharing a receipt number form one combined receipt; they must have the same date. */
    fun checkReceiptGroups(list: MutableList<ImportedPayment>, sheet: String, issues: MutableList<ImportIssue>) {
        val firstDate = HashMap<String, LocalDate>()
        val it = list.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (p.receiptNo.isBlank()) continue
            val key = p.receiptNo.uppercase(Locale.US)
            val d = firstDate.getOrPut(key) { p.date }
            if (d != p.date) {
                issues += ImportIssue(sheet, p.row, "Receipt ${p.receiptNo} already used on another date in this file – row skipped", true)
                it.remove()
            }
        }
    }
}

/** A sheet or CSV file as header → cell maps. */
data class RawTable(val sheetName: String, val headerRow: Int, val headers: List<String>, val rows: List<RawRow>)

data class RawRow(val line: Int, val cells: Map<String, XCell>) {
    fun cell(key: String): XCell? = cells[key]
    fun text(key: String): String = cells[key]?.asText()?.trim() ?: ""
    fun number(key: String): Double? = cells[key]?.let { it.number ?: it.text?.replace(",", "")?.replace("MMK", "", true)?.trim()?.toDoubleOrNull() }
    val isBlank: Boolean get() = cells.values.all { it.isBlank }
}

/** Reads an .xlsx (first matching sheet) or a .csv file (comma, semicolon or tab separated, UTF-8). */
object TableReader {
    fun isXlsx(bytes: ByteArray): Boolean = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /**
     * [knownHeaders] are normalized header names of the module; the header row is the first of the
     * first 10 rows that contains at least two of them.
     */
    fun read(bytes: ByteArray, knownHeaders: Set<String>, preferredSheets: List<String>): RawTable {
        val grids: List<Pair<String, (Int, Int) -> XCell?>> = if (isXlsx(bytes)) {
            val sheets = XlsxReader.read(ByteArrayInputStream(bytes))
            val ordered = preferredSheets.mapNotNull { p -> sheets.entries.firstOrNull { it.key.equals(p, true) } } + sheets.entries
            ordered.distinct().map { (n, s) -> n to { r: Int, c: Int -> s.cell(r, c) } }
        } else {
            val rows = parseCsv(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"))
            listOf("CSV" to { r: Int, c: Int -> rows.getOrNull(r - 1)?.getOrNull(c - 1)?.let { XCell(it, null, false) } })
        }
        for ((name, grid) in grids) {
            val maxRow = if (isXlsx(bytes)) 100000 else Int.MAX_VALUE
            for (hr in 1..10) {
                val headers = (1..60).map { c -> grid(hr, c)?.asText()?.trim() ?: "" }
                if (headers.count { Fields.norm(it) in knownHeaders } < 2) continue
                val rows = ArrayList<RawRow>()
                var r = hr + 1
                var blanks = 0
                while (r < maxRow && blanks < 50) {
                    val cells = HashMap<String, XCell>()
                    headers.forEachIndexed { i, h -> if (h.isNotEmpty()) grid(r, i + 1)?.let { cells[Fields.norm(h)] = it } }
                    val row = RawRow(r, cells)
                    if (row.isBlank) blanks++ else { blanks = 0; rows += row }
                    r++
                }
                return RawTable(name, hr, headers.filter { it.isNotEmpty() }, rows)
            }
        }
        throw IllegalArgumentException("No header row found. The first rows must contain the column names of the template (e.g. ${knownHeaders.take(3).joinToString()}).")
    }

    fun parseCsv(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val sep = listOf(',', ';', '\t').maxByOrNull { c -> firstLine.count { it == c } } ?: ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
                !quoted && ch == sep -> { row += cell.toString(); cell.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.clear(); rows += row; row = ArrayList()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }
}

/** A column of an import template. */
data class ImportColumn(val header: String, val required: Boolean, val help: String, val example: String, val aliases: List<String> = emptyList()) {
    val keys: Set<String> get() = (listOf(header) + aliases).map { Fields.norm(it) }.toSet()
}

/** The modules that can be imported from Excel (.xlsx) or CSV. */
enum class ImportModule(val label: String, val description: String, val sheetName: String, val columns: List<ImportColumn>) {
    STUDENTS(
        "Students",
        "New students, each optionally with a first class. Existing students (same Enroll No. or Student ID) are not changed, but a class in the row is added to them.",
        "Students",
        listOf(
            ImportColumn("Enroll No.", false, "Your enroll number (unique)", "EN-2026-001", listOf("Enroll No", "Enroll Number", "Enrollment No", "Enrollment Number", "Roll No")),
            ImportColumn("Student ID", false, "Leave blank – generated by the app", "", listOf("Student Code")),
            ImportColumn("Student Name", true, "Full name", "Aung Aung", listOf("Name")),
            ImportColumn("Class", false, "Class from Fee Rates (blank = no class yet)", "G-3"),
            ImportColumn("Academic Year", false, "Blank = from the start date", "2026-2027", listOf("Year")),
            ImportColumn("Start Date", false, "Blank = the class's default start", "01/06/2026", listOf("Academic Year Start", "Start")),
            ImportColumn("Period (Months)", false, "Blank = the class's default period", "12", listOf("Period", "Months")),
            ImportColumn("Student Type", false, "Day or Hostel (blank = Day)", "Day", listOf("Type", "Day/Hostel")),
            ImportColumn("New/Old", false, "New or Old (blank = Old)", "New", listOf("New/Old Student", "Status")),
            ImportColumn("Book Type", false, "Myanmar or International (blank = International)", "Myanmar", listOf("Book")),
            ImportColumn("Payment Term", false, "3-Inst or Monthly (blank = 3-Inst)", "3-Inst", listOf("Term")),
            ImportColumn("Discount %", false, "0 – 100", "0", listOf("Discount")),
            ImportColumn("Remarks", false, "", ""),
        ),
    ),
    ENROLLMENTS(
        "Class enrollments",
        "Adds classes to students who are already in the app (or in the same file). Identify the student by Enroll No., Student ID or name.",
        "Enrollments",
        listOf(
            ImportColumn("Enroll No.", false, "Enroll No. OR Student ID OR name is required", "EN-2026-001", listOf("Enroll No", "Enroll Number", "Enrollment No", "Enrollment Number", "Roll No")),
            ImportColumn("Student ID", false, "", "", listOf("Student Code")),
            ImportColumn("Student Name", false, "", "Aung Aung", listOf("Name")),
            ImportColumn("Class", true, "Class from Fee Rates", "G-4"),
            ImportColumn("Academic Year", false, "Blank = from the start date", "2026-2027", listOf("Year")),
            ImportColumn("Start Date", false, "Blank = the class's default start", "01/06/2026", listOf("Academic Year Start", "Start")),
            ImportColumn("Period (Months)", false, "Blank = the class's default period", "12", listOf("Period", "Months")),
            ImportColumn("Student Type", false, "Day or Hostel (blank = Day)", "Day", listOf("Type", "Day/Hostel")),
            ImportColumn("New/Old", false, "New or Old (blank = Old)", "Old", listOf("New/Old Student", "Status")),
            ImportColumn("Book Type", false, "Myanmar or International (blank = International)", "Myanmar", listOf("Book")),
            ImportColumn("Payment Term", false, "3-Inst or Monthly (blank = 3-Inst)", "Monthly", listOf("Term")),
            ImportColumn("Discount %", false, "0 – 100", "10", listOf("Discount")),
            ImportColumn("Remarks", false, "", ""),
        ),
    ),
    PAYMENTS(
        "Payments",
        "Cash received. Rows with the same Receipt No. become one combined receipt. Receipt numbers already in the app are skipped.",
        "Payments",
        listOf(
            ImportColumn("Receipt No.", false, "Blank = a new number is generated", "RCP-2026-000101", listOf("Receipt No", "Receipt", "Receipt Number")),
            ImportColumn("Date", true, "Payment date", "05/06/2026", listOf("Payment Date")),
            ImportColumn("Enroll No.", false, "Enroll No. OR Student ID OR name is required", "EN-2026-001", listOf("Enroll No", "Enroll Number", "Enrollment No", "Enrollment Number", "Roll No")),
            ImportColumn("Student ID", false, "", "", listOf("Student Code")),
            ImportColumn("Student Name", false, "", "Aung Aung", listOf("Name")),
            ImportColumn("Class", false, "Needed when the student has more than one class", "G-3"),
            ImportColumn("Academic Year", false, "Needed when the same class is in several years", "2026-2027", listOf("Year")),
            ImportColumn("Payment For", false, "Inst-1…Inst-3, M-1…M-12 or text", "Inst-1"),
            ImportColumn("Amount", true, "MMK, whole kyat", "183333", listOf("Amount Received", "Amount (MMK)")),
            ImportColumn("Remarks", false, "", ""),
            ImportColumn("Received By", false, "Blank = Imported", "cashier1"),
        ),
    ),
    FEE_RATES(
        "Fee rates (classes)",
        "One row per class. Existing classes are updated, new classes are added. Existing students keep their approved fees.",
        "Fee_Rates",
        listOf(
            ImportColumn("Class", true, "Class name", "G-3"),
            ImportColumn("Training Fee", true, "MMK per period", "220000", listOf("Training")),
            ImportColumn("Book Day-Myanmar", false, "MMK", "30000", listOf("Day-Myanmar", "Book Day MM")),
            ImportColumn("Book Day-Intl", false, "MMK", "36000", listOf("Day-International", "Book Day International")),
            ImportColumn("Book Hostel-Myanmar", false, "MMK", "33000", listOf("Hostel-Myanmar", "Book Hostel MM")),
            ImportColumn("Book Hostel-Intl", false, "MMK", "40000", listOf("Hostel-International", "Book Hostel International")),
            ImportColumn("Hostel Fee", false, "MMK (hostel students)", "950000", listOf("Hostel")),
            ImportColumn("Start Month", false, "1–12 (blank = 6, June)", "6"),
            ImportColumn("Start Day", false, "1–31 (blank = 1)", "1"),
            ImportColumn("Period (Months)", false, "1–24 (blank = 12)", "12", listOf("Period", "Months")),
        ),
    );

    val knownHeaders: Set<String> get() = columns.flatMap { it.keys }.toSet()

    fun key(header: String): String = Fields.norm(header)
}

/** Parses module files and builds templates. */
object ModuleImporter {

    fun read(module: ImportModule, bytes: ByteArray, card: FeeRateCard): ImportBundle {
        val table = TableReader.read(bytes, module.knownHeaders, listOf(module.sheetName))
        val issues = ArrayList<ImportIssue>()
        val sheet = table.sheetName
        // map every alias column to the template header key
        val rows = table.rows.map { r ->
            val m = HashMap<String, XCell>()
            module.columns.forEach { col -> col.keys.firstNotNullOfOrNull { r.cells[it] }?.let { m[Fields.norm(col.header)] = it } }
            RawRow(r.line, m)
        }
        val missing = module.columns.filter { it.required && table.headers.none { h -> Fields.norm(h) in it.keys } }
        missing.forEach { issues += ImportIssue(sheet, table.headerRow, "Required column '${it.header}' not found", true) }
        if (missing.isNotEmpty()) return ImportBundle(module.label, listOf(sheet), null, emptyList(), emptyList(), emptyList(), emptyList(), issues)

        return when (module) {
            ImportModule.STUDENTS -> {
                val list = rows.mapNotNull { r -> student(r, sheet, card, issues) }
                val seen = HashSet<String>()
                val dedup = list.filter { s ->
                    val k = (s.enrollNo?.let { "e:" + it.lowercase() } ?: s.code?.let { "c:" + it.lowercase() } ?: ("n:" + s.name.lowercase())) + "|" + (s.enrollment?.className ?: "") + "|" + (s.enrollment?.academicYear ?: "")
                    if (seen.add(k)) true else { issues += ImportIssue(sheet, s.row, "${s.name} appears more than once – duplicate skipped", true); false }
                }
                ImportBundle(module.label, listOf(sheet), null, emptyList(), dedup, emptyList(), emptyList(), issues)
            }
            ImportModule.ENROLLMENTS -> {
                val list = rows.mapNotNull { r ->
                    val ref = ref(r) ?: run { issues += ImportIssue(sheet, r.line, "Enroll No., Student ID or Student Name is required – row skipped", true); return@mapNotNull null }
                    val e = enrollment(r, sheet, card, ref.display, issues, classRequired = true) ?: return@mapNotNull null
                    ImportedEnrollmentRow(sheet, r.line, ref.copy(className = null), e)
                }
                ImportBundle(module.label, listOf(sheet), null, emptyList(), emptyList(), list, emptyList(), issues)
            }
            ImportModule.PAYMENTS -> {
                val list = rows.mapNotNull { r -> payment(r, sheet, issues) }.toMutableList()
                Fields.checkReceiptGroups(list, sheet, issues)
                ImportBundle(module.label, listOf(sheet), null, emptyList(), emptyList(), emptyList(), list, issues)
            }
            ImportModule.FEE_RATES -> {
                val list = rows.mapNotNull { r -> rate(r, sheet, issues) }
                val dupes = list.groupBy { it.className }.filter { it.value.size > 1 }.keys
                dupes.forEach { issues += ImportIssue(sheet, 0, "Class $it appears more than once – the last row is used", false) }
                ImportBundle(module.label, listOf(sheet), null, list.associateBy { it.className }.values.toList(), emptyList(), emptyList(), emptyList(), issues)
            }
        }
    }

    private fun k(h: String) = Fields.norm(h)

    private fun ref(r: RawRow): StudentRef? {
        val en = r.text(k("Enroll No.")).ifEmpty { null }
        val code = r.text(k("Student ID")).ifEmpty { null }
        val name = r.text(k("Student Name")).replace(Regex("\\s+"), " ")
        if (en == null && code == null && name.isEmpty()) return null
        return StudentRef(en, code, name, r.text(k("Class")).uppercase(Locale.US).ifEmpty { null })
    }

    private fun student(r: RawRow, sheet: String, card: FeeRateCard, issues: MutableList<ImportIssue>): ImportedStudent? {
        val name = r.text(k("Student Name")).replace(Regex("\\s+"), " ")
        if (name.isEmpty()) {
            issues += ImportIssue(sheet, r.line, "Student Name is blank – row skipped", true); return null
        }
        if (name.length > 100) { issues += ImportIssue(sheet, r.line, "$name: name is longer than 100 characters – row skipped", true); return null }
        val hasClass = r.text(k("Class")).isNotEmpty()
        val e = if (hasClass) (enrollment(r, sheet, card, name, issues, classRequired = false) ?: return null).copy(remarks = "") else null
        return ImportedStudent(sheet, r.line, r.text(k("Student ID")).ifEmpty { null }, r.text(k("Enroll No.")).ifEmpty { null }, name, r.text(k("Remarks")), e)
    }

    private fun enrollment(r: RawRow, sheet: String, card: FeeRateCard, who: String, issues: MutableList<ImportIssue>, classRequired: Boolean): ImportedEnrollment? {
        val cls = r.text(k("Class")).uppercase(Locale.US)
        if (cls.isEmpty()) { if (classRequired) issues += ImportIssue(sheet, r.line, "$who: Class is blank – row skipped", true); return null }
        if (card.rateFor(cls) == null) { issues += ImportIssue(sheet, r.line, "$who: class '$cls' is not in Fee Rates – row skipped", true); return null }
        val typeText = r.text(k("Student Type"))
        val type = if (typeText.isEmpty()) StudentType.DAY else StudentType.parse(typeText)
            ?: run { issues += ImportIssue(sheet, r.line, "$who: student type '$typeText' must be Day or Hostel – row skipped", true); return null }
        val stText = r.text(k("New/Old"))
        val status = if (stText.isEmpty()) StudentStatus.OLD else StudentStatus.parse(stText)
            ?: run { issues += ImportIssue(sheet, r.line, "$who: New/Old '$stText' – Old used", false); StudentStatus.OLD }
        val bookText = r.text(k("Book Type"))
        val book = if (bookText.isEmpty()) BookType.INTERNATIONAL else BookType.parse(bookText)
            ?: run { issues += ImportIssue(sheet, r.line, "$who: book type '$bookText' – International used", false); BookType.INTERNATIONAL }
        val termText = r.text(k("Payment Term"))
        val term = if (termText.isEmpty()) PaymentTerm.THREE_INST else PaymentTerm.parse(termText)
            ?: run { issues += ImportIssue(sheet, r.line, "$who: payment term '$termText' – 3-Inst used", false); PaymentTerm.THREE_INST }
        val bp = Fields.discountBp(r.cell(k("Discount %")), who) { issues += ImportIssue(sheet, r.line, it, false) }
        val startCell = r.cell(k("Start Date"))
        val start = Fields.date(startCell)
        if (start == null && startCell != null && !startCell.isBlank) issues += ImportIssue(sheet, r.line, "$who: start date '${startCell.asText()}' not understood – class default used", false)
        val periodText = r.text(k("Period (Months)"))
        val period = r.number(k("Period (Months)"))?.toInt()?.takeIf { it in 1..24 }
        if (period == null && periodText.isNotEmpty()) issues += ImportIssue(sheet, r.line, "$who: period '$periodText' must be 1–24 months – class default used", false)
        return ImportedEnrollment(cls, r.text(k("Academic Year")).ifEmpty { null }, start, period, type, status, book, bp, term, r.text(k("Remarks")))
    }

    private fun payment(r: RawRow, sheet: String, issues: MutableList<ImportIssue>): ImportedPayment? {
        val ref = ref(r) ?: run {
            if (r.text(k("Amount")).isNotEmpty()) issues += ImportIssue(sheet, r.line, "Enroll No., Student ID or Student Name is required – row skipped", true)
            return null
        }
        val amount = r.number(k("Amount"))
        if (amount == null || amount <= 0) { issues += ImportIssue(sheet, r.line, "${ref.display}: amount '${r.text(k("Amount"))}' is not a positive number – row skipped", true); return null }
        if (amount != Math.floor(amount)) issues += ImportIssue(sheet, r.line, "${ref.display}: amount $amount rounded to whole kyat", false)
        val date = Fields.date(r.cell(k("Date")))
            ?: run { issues += ImportIssue(sheet, r.line, "${ref.display}: date '${r.text(k("Date"))}' is not valid – row skipped", true); return null }
        return ImportedPayment(
            sheet, r.line, ref, r.text(k("Academic Year")).ifEmpty { null }, date, Math.round(amount),
            r.text(k("Receipt No.")), r.text(k("Payment For")), r.text(k("Remarks")), r.text(k("Received By")).ifEmpty { "Imported" },
        )
    }

    private fun rate(r: RawRow, sheet: String, issues: MutableList<ImportIssue>): ClassRate? {
        val cls = r.text(k("Class")).uppercase(Locale.US)
        if (cls.isEmpty()) return null
        if (cls.length > 10) { issues += ImportIssue(sheet, r.line, "Class name '$cls' is longer than 10 characters – row skipped", true); return null }
        fun money(h: String, required: Boolean): Long? {
            val t = r.text(k(h))
            if (t.isEmpty()) { if (required) issues += ImportIssue(sheet, r.line, "$cls: $h is blank – row skipped", true); return if (required) null else 0 }
            val v = r.number(k(h))
            if (v == null || v < 0) { issues += ImportIssue(sheet, r.line, "$cls: $h '$t' is not a valid amount – row skipped", true); return null }
            return Math.round(v)
        }
        fun int(h: String, range: IntRange, default: Int): Int {
            val t = r.text(k(h))
            if (t.isEmpty()) return default
            val v = r.number(k(h))?.toInt()
            return if (v != null && v in range) v else { issues += ImportIssue(sheet, r.line, "$cls: $h '$t' must be ${range.first}–${range.last} – $default used", false); default }
        }
        val training = money("Training Fee", true) ?: return null
        val dm = money("Book Day-Myanmar", false) ?: return null
        val di = money("Book Day-Intl", false) ?: return null
        val hm = money("Book Hostel-Myanmar", false) ?: return null
        val hi = money("Book Hostel-Intl", false) ?: return null
        val hostel = money("Hostel Fee", false) ?: return null
        return ClassRate(cls, 0, training, dm, di, hm, hi, hostel, int("Start Month", 1..12, 6), int("Start Day", 1..31, 1), int("Period (Months)", 1..24, 12))
    }

    // ------------------------------------------------------------- templates
    /** An .xlsx template: header row, one example row, and a Help sheet. */
    fun templateXlsx(module: ImportModule): ByteArray {
        val w = XlsxWriter().sheet(module.sheetName)
        module.columns.forEachIndexed { i, c ->
            w.set(1, i + 1, c.header + if (c.required) " *" else "", STYLE_HEADER)
            if (c.example.isNotEmpty()) w.set(2, i + 1, c.example.toLongOrNull()?.takeIf { !c.example.startsWith("0") } ?: c.example)
            w.width(i + 1, maxOf(14.0, c.header.length + 4.0))
        }
        w.sheet("Help")
        w.set(1, 1, module.label, XlsxWriter.STYLE_TITLE)
        w.set(2, 1, module.description)
        w.set(3, 1, "Replace the example row (row 2) with your data. Columns marked * are required. You can also save this sheet as CSV.")
        w.set(5, 1, "Column", STYLE_HEADER); w.set(5, 2, "Required", STYLE_HEADER); w.set(5, 3, "How to fill", STYLE_HEADER)
        module.columns.forEachIndexed { i, c -> w.set(6 + i, 1, c.header); w.set(6 + i, 2, if (c.required) "Yes" else ""); w.set(6 + i, 3, c.help) }
        w.width(1, 22.0); w.width(2, 10.0); w.width(3, 60.0)
        return w.toBytes()
    }

    fun templateCsv(module: ImportModule): ByteArray {
        fun q(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        val text = module.columns.joinToString(",") { q(it.header) } + "\r\n" + module.columns.joinToString(",") { q(it.example) } + "\r\n"
        return ("\uFEFF" + text).toByteArray(Charsets.UTF_8)
    }
}
