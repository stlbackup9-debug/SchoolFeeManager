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
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ImportIssue(val sheet: String, val row: Int, val message: String, val isError: Boolean) {
    override fun toString(): String = "${if (isError) "ERROR" else "Warning"} – $sheet row $row: $message"
}

data class ImportedStudent(
    val row: Int,
    val code: String?,
    val name: String,
    val className: String,
    val type: StudentType,
    val status: StudentStatus,
    val bookType: BookType,
    val discountBp: Int,
    val term: PaymentTerm,
    val remarks: String,
)

data class ImportedPayment(
    val row: Int,
    val studentName: String,
    val className: String,
    val studentCode: String?,
    val date: LocalDate,
    val amount: Long,
    val receiptNo: String,
    val paymentFor: String,
    val remarks: String,
    val receivedBy: String,
)

data class WorkbookImport(
    val sheetsFound: List<String>,
    val rates: FeeRateCard?,
    val students: List<ImportedStudent>,
    val payments: List<ImportedPayment>,
    val scheduleRows: Int,
    val issues: List<ImportIssue>,
) {
    val errors: List<ImportIssue> get() = issues.filter { it.isError }
    val warnings: List<ImportIssue> get() = issues.filter { !it.isError }
}

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
        return WorkbookImport(sheets.keys.toList(), rates, students, payments, scheduleRows, issues)
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

        val defaults = FeeRateCard.workbookDefaults()
        val classes = training.keys.mapIndexed { i, c ->
            val b = book[c]
            if (b == null) issues += ImportIssue(RATES, 0, "Book fees for $c not found – set to 0", false)
            if (hostel[c] == null) issues += ImportIssue(RATES, 0, "Hostel fee for $c not found – set to 0", false)
            ClassRate(c, i, training[c]!!, b?.get(0) ?: 0, b?.get(1) ?: 0, b?.get(2) ?: 0, b?.get(3) ?: 0, hostel[c] ?: 0)
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
    private fun parseStudents(s: XSheet, card: FeeRateCard, issues: MutableList<ImportIssue>): List<ImportedStudent> {
        val out = ArrayList<ImportedStudent>()
        val seen = HashSet<String>()
        // Optional "Student ID" column (added by this app's own export).
        val codeCol = (1..40).firstOrNull { s.text(2, it).equals("Student ID", true) }
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
            val discCell = s.cell(r, 14)
            var bp = 0
            if (discCell != null && !discCell.isBlank && !discCell.isFormula) {
                val d = discCell.number ?: discCell.text?.replace("%", "")?.trim()?.toDoubleOrNull()?.let { if (discCell.text!!.contains("%")) it / 100 else it }
                when {
                    d == null -> issues += ImportIssue(sheetName, r, "$name: discount '${discCell.asText()}' is not a number – 0% used", false)
                    d < 0 -> issues += ImportIssue(sheetName, r, "$name: negative discount – 0% used", false)
                    d <= 1.0 -> bp = Money.fractionToBp(d)
                    d <= 100.0 -> { bp = Money.fractionToBp(d / 100); issues += ImportIssue(sheetName, r, "$name: discount $d read as $d%", false) }
                    else -> issues += ImportIssue(sheetName, r, "$name: discount $d is above 100% – 0% used", false)
                }
            }
            var term = PaymentTerm.parse(s.text(r, 17))
            if (term == null) {
                issues += ImportIssue(sheetName, r, "$name: payment term '${s.text(r, 17)}' – 3-Inst used", false)
                term = PaymentTerm.THREE_INST
            }
            val key = name.lowercase(Locale.US) + "|" + cls
            if (!seen.add(key)) { issues += ImportIssue(sheetName, r, "$name ($cls) appears more than once – duplicate skipped", true); continue }
            val code = codeCol?.let { s.text(r, it) }?.takeIf { it.isNotEmpty() }
            out += ImportedStudent(r, code, name, cls, type, status, book, bp, term, s.text(r, 20))
        }
        return out
    }

    // ----------------------------------------------------------- Cash_Collection
    private fun parsePayments(s: XSheet, issues: MutableList<ImportIssue>): List<ImportedPayment> {
        val out = ArrayList<ImportedPayment>()
        val receipts = HashSet<String>()
        val codeCol = (1..40).firstOrNull { s.text(2, it).equals("Student ID", true) }
        val byCol = (1..40).firstOrNull { s.text(2, it).equals("Received By", true) }
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
            val receipt = s.text(r, 6)
            if (receipt.isNotEmpty() && !receipts.add(receipt.uppercase(Locale.US))) {
                issues += ImportIssue(CASH, r, "Receipt $receipt is duplicated in the file – row skipped", true); continue
            }
            out += ImportedPayment(
                row = r, studentName = name, className = norm(s.text(r, 3)),
                studentCode = codeCol?.let { s.text(r, it) }?.takeIf { it.isNotEmpty() },
                date = date, amount = Math.round(amount), receiptNo = receipt,
                paymentFor = s.text(r, 7), remarks = s.text(r, 8),
                receivedBy = byCol?.let { s.text(r, it) } ?: "Imported",
            )
        }
        return out
    }

    private val DATE_FORMATS = listOf("d/M/yyyy", "d-M-yyyy", "yyyy-M-d", "d-MMM-yyyy", "d MMM yyyy", "d-MMM-yy", "M/d/yyyy", "d.M.yyyy")
        .map { DateTimeFormatter.ofPattern(it, Locale.US) }

    /** Excel serial date (1900 system) or common text formats. */
    fun parseDate(c: XCell?): LocalDate? {
        if (c == null || c.isBlank) return null
        c.number?.let { n -> return if (n in 1.0..2958465.0) LocalDate.of(1899, 12, 30).plusDays(n.toLong()) else null }
        val t = c.text!!.trim().substringBefore('T').substringBefore(' ').let { if (it.length < 6) c.text.trim() else it }
        for (f in DATE_FORMATS) {
            try { return LocalDate.parse(t, f) } catch (_: Exception) {}
            try { return LocalDate.parse(c.text.trim(), f) } catch (_: Exception) {}
        }
        return null
    }
}
