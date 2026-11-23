package com.school.feemanager.io

import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.ClassRate
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.Money
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import java.io.InputStream
import java.time.LocalDate
import java.util.Locale

typealias WorkbookImport = ImportBundle

/**
 * Reads School_Fee_Management.xlsx. Only INPUT cells are imported; every formula column
 * (fees, totals, paid, balance, schedule amounts, dashboard) is ignored and recalculated in Kotlin.
 */
object WorkbookImporter {
    const val REGISTER = "Student_Register"
    const val SCHEDULE = "Payment_Schedule"
    const val CASH = "Cash_Collection"
    const val RATES = "Fee_Rates"
    const val DASHBOARD = "Dashboard"

    fun read(input: InputStream, currentRates: FeeRateCard): WorkbookImport =
        parse(XlsxReader.read(input), currentRates)

    fun parse(sheets: Map<String, XSheet>, currentRates: FeeRateCard): WorkbookImport {
        val issues = ArrayList<ImportIssue>()
        fun sheet(name: String): XSheet? = sheets.entries.firstOrNull { it.key.equals(name, true) }?.value

        listOf(DASHBOARD, REGISTER, SCHEDULE, CASH, RATES).forEach {
            if (sheet(it) == null) issues += ImportIssue(it, 0, "Worksheet not found in the file", it != DASHBOARD)
        }

        val rates = sheet(RATES)?.let { parseRates(it, issues) }
        val card = rates ?: currentRates
        val students = sheet(REGISTER)?.let { parseStudents(it, card, issues) } ?: emptyList()
        val payments = sheet(CASH)?.let { parsePayments(it, issues) } ?: emptyList()
        val scheduleRows = sheet(SCHEDULE)?.let { s ->
            (3..s.maxRow).count { r -> s.text(r, 2).isNotEmpty() && !s.text(r, 1).equals("TOTALS", true) }
        } ?: 0
        if (sheet(SCHEDULE) != null) {
            issues += ImportIssue(SCHEDULE, 0, "Payment_Schedule contains only formulas; schedules are regenerated from each student's Net Total ($scheduleRows rows seen)", false)
        }
        return ImportBundle("School_Fee_Management workbook", sheets.keys.toList(), rates, emptyList(), students, emptyList(), payments, issues)
    }

    // ---------------------------------------------------------------- Fee_Rates
    private fun parseRates(s: XSheet, issues: MutableList<ImportIssue>): FeeRateCard? {
        val classRe = Regex("^(KG|G-\\d{1,2})$", RegexOption.IGNORE_CASE)
        val cells = s.allCells()
        fun find(pred: (String) -> Boolean): Pair<Int, Int>? = cells.entries
            .filter { it.value.text?.let { t -> pred(t.trim()) } == true }
            .minByOrNull { it.key }
            ?.let { XSheet.rowOf(it.key) to XSheet.colOf(it.key) }

        val hostelHeader = find { it.startsWith("D)") && it.contains("HOSTEL", true) }?.first ?: 20
        val training = LinkedHashMap<String, Long>()
        val hostel = LinkedHashMap<String, Long>()
        val book = LinkedHashMap<String, LongArray>()
        for (r in 1..s.maxRow) {
            val a = s.text(r, 1)
            if (classRe.matches(a)) {
                val v = s.number(r, 2)?.toLong()
                if (v == null) issues += ImportIssue(RATES, r, "No amount for class $a", true)
                else if (r < hostelHeader) training[norm(a)] = v else hostel[norm(a)] = v
            }
            val k = s.text(r, 11)
            if (classRe.matches(k)) {
                val vals = (12..15).map { s.number(r, it)?.toLong() }
                if (vals.any { it == null }) issues += ImportIssue(RATES, r, "Book fee missing for class $k", true)
                else book[norm(k)] = vals.map { it!! }.toLongArray()
            }
        }
        fun valueRightOf(label: (String) -> Boolean): Double? = find(label)?.let { (r, c) ->
            (c + 1..c + 3).firstNotNullOfOrNull { s.number(r, it) }
        }
        val instrument = valueRightOf { it.startsWith("Instrument Fee", true) && !it.contains("ALL", true) }
        val laundry = valueRightOf { it.startsWith("Laundry Fee /", true) || it.equals("Laundry Fee per month", true) }
        val months = valueRightOf { it.startsWith("Months", true) }
        val entrance = valueRightOf { it.equals("Entrance Fee", true) }
        if (training.isEmpty()) { issues += ImportIssue(RATES, 0, "Training fee table not found", true); return null }
        if (instrument == null) issues += ImportIssue(RATES, 0, "Instrument fee not found – current value kept", false)
        if (laundry == null || months == null) issues += ImportIssue(RATES, 0, "Laundry fee / months not found – current values kept", false)
        if (entrance == null) issues += ImportIssue(RATES, 0, "Entrance fee not found – current value kept", false)

        // E) per-class academic year table written by this app (columns Q–T); absent in the original workbook
        val yearInfo = HashMap<String, Triple<Int, Int, Int>>()
        for (r in 1..s.maxRow) {
            val c = s.text(r, 17)
            if (!classRe.matches(c)) continue
            val m = s.number(r, 18)?.toInt(); val d = s.number(r, 19)?.toInt(); val p = s.number(r, 20)?.toInt()
            if (m in 1..12 && d in 1..31 && p in 1..24) yearInfo[norm(c)] = Triple(m!!, d!!, p!!)
        }
        val defaults = FeeRateCard.workbookDefaults()
        val classes = training.keys.mapIndexed { i, c ->
            val b = book[c]
            if (b == null) issues += ImportIssue(RATES, 0, "Book fees for $c not found – set to 0", false)
            if (hostel[c] == null) issues += ImportIssue(RATES, 0, "Hostel fee for $c not found – set to 0", false)
            val y = yearInfo[c]
            ClassRate(c, i, training[c]!!, b?.get(0) ?: 0, b?.get(1) ?: 0, b?.get(2) ?: 0, b?.get(3) ?: 0, hostel[c] ?: 0,
                y?.first ?: 6, y?.second ?: 1, y?.third ?: (months?.toInt()?.takeIf { it in 1..24 } ?: 12))
        }
        return FeeRateCard(
            classes = classes,
            instrumentFee = instrument?.toLong() ?: defaults.instrumentFee,
            laundryPerMonth = laundry?.toLong() ?: defaults.laundryPerMonth,
            monthsPerYear = months?.toInt() ?: defaults.monthsPerYear,
            entranceFee = entrance?.toLong() ?: defaults.entranceFee,
        )
    }

    private fun norm(c: String): String = c.trim().uppercase(Locale.US)

    // ---------------------------------------------------------- Student_Register
    /** Column of a header (row 2) added by this app's export, e.g. "Student ID". */
    private fun headerCol(s: XSheet, vararg names: String): Int? =
        (1..60).firstOrNull { c -> names.any { n -> Fields.norm(s.text(2, c)) == Fields.norm(n) } }

    private fun parseStudents(s: XSheet, card: FeeRateCard, issues: MutableList<ImportIssue>): List<ImportedStudent> {
        val out = ArrayList<ImportedStudent>()
        val seen = HashSet<String>()
        // Optional columns added by this app's own export.
        val codeCol = headerCol(s, "Student ID")
        val enrollCol = headerCol(s, "Enroll No.")
        val yearCol = headerCol(s, "Academic Year")
        val startCol = headerCol(s, "Start Date")
        val periodCol = headerCol(s, "Period (Months)")
        for (r in 3..s.maxRow) {
            if (s.text(r, 1).equals("TOTALS", true)) break
            val name = s.text(r, 2)
            if (name.isEmpty()) continue
            val cls = norm(s.text(r, 3))
            val sheetName = REGISTER
            if (cls.isEmpty()) { issues += ImportIssue(sheetName, r, "$name: class is blank – row skipped", true); continue }
            if (card.rateFor(cls) == null) { issues += ImportIssue(sheetName, r, "$name: class '$cls' is not in Fee_Rates – row skipped", true); continue }
            val type = StudentType.parse(s.text(r, 4))
            if (type == null) { issues += ImportIssue(sheetName, r, "$name: student type '${s.text(r, 4)}' must be Day or Hostel – row skipped", true); continue }
            var status = StudentStatus.parse(s.text(r, 5))
            if (status == null) {
                issues += ImportIssue(sheetName, r, "$name: New/Old is '${s.text(r, 5)}' – treated as Old (no entrance fee), as in Excel", false)
                status = StudentStatus.OLD
            }
            var book = BookType.parse(s.text(r, 6))
            if (book == null) {
                issues += ImportIssue(sheetName, r, "$name: book type '${s.text(r, 6)}' – treated as International, as in Excel", false)
                book = BookType.INTERNATIONAL
            }
            val bp = Fields.discountBp(s.cell(r, 14), name) { issues += ImportIssue(sheetName, r, it, false) }
            var term = PaymentTerm.parse(s.text(r, 17))
            if (term == null) {
                issues += ImportIssue(sheetName, r, "$name: payment term '${s.text(r, 17)}' – 3-Inst used", false)
                term = PaymentTerm.THREE_INST
            }
            val code = codeCol?.let { s.text(r, it) }?.takeIf { it.isNotEmpty() }
            val enrollNo = enrollCol?.let { s.text(r, it) }?.takeIf { it.isNotEmpty() }
            val year = yearCol?.let { s.text(r, it) }?.takeIf { it.isNotEmpty() }
            val start = startCol?.let { parseDate(s.cell(r, it)) }
            val period = periodCol?.let { s.number(r, it)?.toInt() }?.takeIf { it in 1..24 }
            val key = (code ?: enrollNo ?: name.lowercase(Locale.US)) + "|" + cls + "|" + (year ?: "")
            if (!seen.add(key)) { issues += ImportIssue(sheetName, r, "$name ($cls${year?.let { " $it" } ?: ""}) appears more than once – duplicate skipped", true); continue }
            out += ImportedStudent(
                sheetName, r, code, enrollNo, name, s.text(r, 20),
                ImportedEnrollment(cls, year, start, period, type, status, book, bp, term, ""),
            )
        }
        return out
    }

    // ----------------------------------------------------------- Cash_Collection
    private fun parsePayments(s: XSheet, issues: MutableList<ImportIssue>): List<ImportedPayment> {
        val out = ArrayList<ImportedPayment>()
        val codeCol = headerCol(s, "Student ID")
        val byCol = headerCol(s, "Received By")
        val enrollCol = headerCol(s, "Enroll No.")
        val yearCol = headerCol(s, "Academic Year")
        for (r in 3..s.maxRow) {
            if (s.text(r, 1).equals("TOTALS", true)) break
            val name = s.text(r, 2)
            val amountCell = s.cell(r, 5)
            if (name.isEmpty() && (amountCell == null || amountCell.isBlank)) continue
            if (name.isEmpty()) { issues += ImportIssue(CASH, r, "Student name is blank – row skipped", true); continue }
            val amount = s.number(r, 5)
            if (amount == null || amount <= 0) { issues += ImportIssue(CASH, r, "$name: amount '${s.text(r, 5)}' is not a positive number – row skipped", true); continue }
            if (amount != Math.floor(amount)) issues += ImportIssue(CASH, r, "$name: amount $amount rounded to whole kyat", false)
            val date = parseDate(s.cell(r, 4))
            if (date == null) { issues += ImportIssue(CASH, r, "$name: date '${s.text(r, 4)}' is not valid – row skipped", true); continue }
            out += ImportedPayment(
                sheet = CASH, row = r,
                ref = StudentRef(enrollCol?.let { s.text(r, it) }?.takeIf { it.isNotEmpty() }, codeCol?.let { s.text(r, it) }?.takeIf { it.isNotEmpty() }, name, norm(s.text(r, 3)).ifEmpty { null }),
                academicYear = yearCol?.let { s.text(r, it) }?.takeIf { it.isNotEmpty() },
                date = date, amount = Math.round(amount), receiptNo = s.text(r, 6),
                paymentFor = s.text(r, 7), remarks = s.text(r, 8),
                receivedBy = byCol?.let { s.text(r, it) }?.ifEmpty { null } ?: "Imported",
            )
        }
        Fields.checkReceiptGroups(out, CASH, issues)
        return out
    }

    /** Excel serial date (1900 system) or common text formats. */
    fun parseDate(c: XCell?): LocalDate? = Fields.date(c)
}
