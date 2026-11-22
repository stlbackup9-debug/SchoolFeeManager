package com.school.feemanager.io

import com.school.feemanager.domain.DashboardSummary
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.PaymentRecord
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.ReportTable
import com.school.feemanager.domain.StudentLedger
import com.school.feemanager.domain.StudentType
import com.school.feemanager.io.XlsxWriter.Companion.STYLE_BOLD
import com.school.feemanager.io.XlsxWriter.Companion.STYLE_BOLD_NUMBER
import com.school.feemanager.io.XlsxWriter.Companion.STYLE_HEADER
import com.school.feemanager.io.XlsxWriter.Companion.STYLE_PERCENT
import com.school.feemanager.io.XlsxWriter.Companion.STYLE_TEXT
import com.school.feemanager.io.XlsxWriter.Companion.STYLE_TITLE
import java.time.format.DateTimeFormatter

/**
 * Writes the database back out in the same five-sheet structure as School_Fee_Management.xlsx
 * (values, not formulas). Student_Register and Cash_Collection get extra columns
 * (Student ID, Active / Received By) to the right of the original ones, so a file exported
 * here can be imported again without losing student IDs.
 */
object WorkbookExporter {
    private val D: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy")

    fun export(
        schoolName: String,
        ledgers: List<StudentLedger>,
        payments: List<PaymentRecord>,
        card: FeeRateCard,
        dash: DashboardSummary,
    ): ByteArray {
        val w = XlsxWriter()
        val students = ledgers.sortedWith(compareBy<StudentLedger> { card.classNames.indexOf(it.student.className).let { i -> if (i < 0) 999 else i } }.thenBy { it.student.code })
        dashboard(w, schoolName, dash)
        register(w, students)
        schedule(w, students)
        cash(w, ledgers, payments)
        rates(w, card)
        return w.toBytes()
    }

    private fun dashboard(w: XlsxWriter, school: String, d: DashboardSummary) {
        w.sheet(WorkbookImporter.DASHBOARD)
        w.set(1, 1, "FEE COLLECTION DASHBOARD – $school", STYLE_TITLE)
        val pairs = listOf(
            "Total Students Enrolled" to d.totalStudents.toLong(), "Total Fee Receivable (MMK)" to d.totalReceivable,
            "Total Cash Collected (MMK)" to d.totalCollected, "Total Balance Due (MMK)" to d.totalOutstanding,
            "Day Students" to d.dayStudents.toLong(), "Hostel Students" to d.hostelStudents.toLong(),
            "New Students" to d.newStudents.toLong(), "Old Students" to d.oldStudents.toLong(),
        )
        pairs.chunked(2).forEachIndexed { i, pair ->
            val r = 3 + i * 4
            w.set(r, 1, pair[0].first, STYLE_BOLD); w.set(r + 1, 1, pair[0].second, STYLE_BOLD_NUMBER)
            w.set(r, 6, pair[1].first, STYLE_BOLD); w.set(r + 1, 6, pair[1].second, STYLE_BOLD_NUMBER)
        }
        w.set(20, 1, "CLASS-WISE FEE RECEIVABLE SUMMARY", STYLE_TITLE)
        listOf("Class", "Day Students", "Hostel Students", "Total Students", "Day Receivable", "Hostel Receivable", "Total Receivable", "Total Collected", "Balance Due")
            .forEachIndexed { i, h -> w.set(21, i + 1, h, STYLE_HEADER) }
        var r = 22
        d.classSummaries.forEach { c ->
            w.set(r, 1, c.className); w.set(r, 2, c.dayStudents); w.set(r, 3, c.hostelStudents); w.set(r, 4, c.totalStudents)
            w.set(r, 5, c.dayReceivable); w.set(r, 6, c.hostelReceivable); w.set(r, 7, c.totalReceivable); w.set(r, 8, c.collected); w.set(r, 9, c.balance)
            r++
        }
        w.set(r, 1, "GRAND TOTAL", STYLE_BOLD)
        val cs = d.classSummaries
        listOf(cs.sumOf { it.dayStudents }.toLong(), cs.sumOf { it.hostelStudents }.toLong(), cs.sumOf { it.totalStudents }.toLong(),
            cs.sumOf { it.dayReceivable }, cs.sumOf { it.hostelReceivable }, cs.sumOf { it.totalReceivable }, cs.sumOf { it.collected }, cs.sumOf { it.balance })
            .forEachIndexed { i, v -> w.set(r, i + 2, v, STYLE_BOLD_NUMBER) }
        (1..9).forEach { w.width(it, if (it == 1) 26.0 else 17.0) }
    }

    private fun register(w: XlsxWriter, list: List<StudentLedger>) {
        w.sheet(WorkbookImporter.REGISTER)
        w.set(1, 1, "STUDENT REGISTER & FEE CALCULATION", STYLE_TITLE)
        val headers = listOf("No.", "Student Name", "Class", "Student Type\n(Day/Hostel)", "New/Old\nStudent", "Book Type\n(Myanmar/\nIntl)",
            "Training Fee", "Instrument Fee", "Book Fee", "Hostel Fee", "Laundry Fee\n(Annual)", "Entrance Fee", "Gross Total", "Discount %",
            "Discount Amt", "NET TOTAL\nRECEIVABLE", "Payment Term\n(3-inst/Monthly)", "Paid Amount\n(Cumulative)", "Balance Due", "Remarks", "Student ID", "Active")
        headers.forEachIndexed { i, h -> w.set(2, i + 1, h, STYLE_HEADER) }
        list.forEachIndexed { i, l ->
            val r = i + 3; val s = l.student; val f = l.fee
            w.set(r, 1, (i + 1).toLong()); w.set(r, 2, s.name); w.set(r, 3, s.className); w.set(r, 4, s.type.label); w.set(r, 5, s.status.label)
            w.set(r, 6, s.bookType.label); w.set(r, 7, f.trainingFee); w.set(r, 8, f.instrumentFee); w.set(r, 9, f.bookFee)
            if (s.type == StudentType.HOSTEL) { w.set(r, 10, f.hostelFee); w.set(r, 11, f.laundryFee) }
            if (f.entranceFee > 0) w.set(r, 12, f.entranceFee)
            w.set(r, 13, f.grossTotal); w.set(r, 14, f.discountBp / 10000.0, STYLE_PERCENT); w.set(r, 15, f.discountAmount)
            w.set(r, 16, f.netTotal, STYLE_BOLD_NUMBER); w.set(r, 17, s.term.label); w.set(r, 18, l.paid); w.set(r, 19, l.balance)
            w.set(r, 20, s.remarks.ifEmpty { null }); w.set(r, 21, s.code); w.set(r, 22, if (s.active) "Yes" else "No")
        }
        val tr = list.size + 3
        w.set(tr, 1, "TOTALS", STYLE_BOLD)
        listOf<Pair<Int, (StudentLedger) -> Long>>(7 to { l -> l.fee.trainingFee }, 8 to { l -> l.fee.instrumentFee }, 9 to { l -> l.fee.bookFee }, 10 to { l -> l.fee.hostelFee },
            11 to { l -> l.fee.laundryFee }, 12 to { l -> l.fee.entranceFee }, 13 to { l -> l.fee.grossTotal }, 15 to { l -> l.fee.discountAmount },
            16 to { l -> l.net }, 18 to { l -> l.paid }, 19 to { l -> l.balance })
            .forEach { (c, fn) -> w.set(tr, c, list.sumOf(fn), STYLE_BOLD_NUMBER) }
        w.width(1, 6.0); w.width(2, 26.0); (3..19).forEach { w.width(it, 14.0) }; w.width(20, 24.0); w.width(21, 12.0)
    }

    private fun schedule(w: XlsxWriter, list: List<StudentLedger>) {
        w.sheet(WorkbookImporter.SCHEDULE)
        w.set(1, 1, "PAYMENT SCHEDULE – 3 Instalments & Monthly", STYLE_TITLE)
        val headers = listOf("No.", "Student Name", "Class", "Payment Term", "Net Total", "Inst-1", "Inst-2", "Inst-3") + (1..12).map { "M-$it" } + listOf("Total Paid", "Remaining", "Status", "Student ID")
        headers.forEachIndexed { i, h -> w.set(2, i + 1, h, STYLE_HEADER) }
        list.forEachIndexed { i, l ->
            val r = i + 3
            w.set(r, 1, (i + 1).toLong()); w.set(r, 2, l.student.name); w.set(r, 3, l.student.className); w.set(r, 4, l.student.term.label); w.set(r, 5, l.net)
            val base = if (l.student.term == PaymentTerm.THREE_INST) 6 else 9
            l.lines.forEach { ls -> w.set(r, base + ls.line.seq - 1, ls.line.amount) }
            w.set(r, 21, l.paid); w.set(r, 22, l.balance); w.set(r, 23, l.status.label); w.set(r, 24, l.student.code)
        }
        val tr = list.size + 3
        w.set(tr, 1, "TOTALS", STYLE_BOLD)
        w.set(tr, 5, list.sumOf { it.net }, STYLE_BOLD_NUMBER)
        (1..3).forEach { seq -> w.set(tr, 5 + seq, list.filter { it.student.term == PaymentTerm.THREE_INST }.sumOf { l -> l.lines.firstOrNull { it.line.seq == seq }?.line?.amount ?: 0L }, STYLE_BOLD_NUMBER) }
        (1..12).forEach { seq -> w.set(tr, 8 + seq, list.filter { it.student.term == PaymentTerm.MONTHLY }.sumOf { l -> l.lines.firstOrNull { it.line.seq == seq }?.line?.amount ?: 0L }, STYLE_BOLD_NUMBER) }
        w.set(tr, 21, list.sumOf { it.paid }, STYLE_BOLD_NUMBER); w.set(tr, 22, list.sumOf { it.balance }, STYLE_BOLD_NUMBER)
        w.width(2, 26.0); (3..24).forEach { w.width(it, 12.0) }
    }

    private fun cash(w: XlsxWriter, ledgers: List<StudentLedger>, payments: List<PaymentRecord>) {
        w.sheet(WorkbookImporter.CASH)
        w.set(1, 1, "DAILY CASH COLLECTION REGISTER", STYLE_TITLE)
        listOf("No.", "Student Name", "Class", "Date", "Amount Received", "Receipt No.", "Payment For", "Remarks", "Student ID", "Received By")
            .forEachIndexed { i, h -> w.set(2, i + 1, h, STYLE_HEADER) }
        val byId = ledgers.associateBy { it.student.id }
        val list = payments.filter { !it.voided }.sortedWith(compareBy<PaymentRecord> { it.date }.thenBy { it.id })
        list.forEachIndexed { i, p ->
            val r = i + 3; val s = byId[p.studentId]?.student
            w.set(r, 1, (i + 1).toLong()); w.set(r, 2, s?.name ?: "?"); w.set(r, 3, s?.className ?: "")
            w.set(r, 4, p.date.format(D)); w.set(r, 5, p.amount); w.set(r, 6, p.receiptNo); w.set(r, 7, p.paymentFor)
            w.set(r, 8, p.remarks.ifEmpty { null }); w.set(r, 9, s?.code); w.set(r, 10, p.receivedBy)
        }
        w.set(list.size + 3, 1, "TOTALS", STYLE_BOLD); w.set(list.size + 3, 5, list.sumOf { it.amount }, STYLE_BOLD_NUMBER)
        w.width(2, 26.0); w.width(4, 13.0); w.width(5, 16.0); w.width(6, 18.0); w.width(7, 16.0); w.width(8, 24.0); w.width(10, 14.0)
    }

    /** Same cell positions as the original Fee_Rates sheet, so the file re-imports cleanly. */
    private fun rates(w: XlsxWriter, card: FeeRateCard) {
        w.sheet(WorkbookImporter.RATES)
        w.set(1, 1, "SCHOOL FEE RATE CARD – ASSUMPTION SHEET", STYLE_TITLE)
        w.set(2, 1, "All amounts in MMK", STYLE_TEXT)
        w.set(4, 1, "A)  TRAINING FEE  (per year, MMK)", STYLE_BOLD)
        w.set(4, 6, "B)  INSTRUMENT FEE  (All Students)", STYLE_BOLD)
        w.set(4, 11, "C)  BOOK FEE  (per year, MMK)", STYLE_BOLD)
        listOf(1 to "Class", 2 to "Training Fee (MMK)", 6 to "Description", 7 to "Amount (MMK)", 11 to "Class", 12 to "Day-Myanmar", 13 to "Day-International", 14 to "Hostel-Myanmar", 15 to "Hostel-International")
            .forEach { (c, h) -> w.set(5, c, h, STYLE_HEADER) }
        w.set(6, 6, "Instrument Fee (Fixed)"); w.set(6, 7, card.instrumentFee)
        val classes = card.classes.sortedBy { it.sortOrder }
        classes.forEachIndexed { i, c ->
            val r = 6 + i
            w.set(r, 1, c.className); w.set(r, 2, c.trainingFee)
            w.set(r, 11, c.className); w.set(r, 12, c.bookDayMyanmar); w.set(r, 13, c.bookDayIntl); w.set(r, 14, c.bookHostelMyanmar); w.set(r, 15, c.bookHostelIntl)
        }
        val hr = 6 + classes.size + 1
        w.set(hr, 1, "D)  HOSTEL FEE  (per year, MMK)", STYLE_BOLD)
        w.set(hr + 1, 1, "Class", STYLE_HEADER); w.set(hr + 1, 2, "Hostel Fee (MMK)", STYLE_HEADER)
        classes.forEachIndexed { i, c -> w.set(hr + 2 + i, 1, c.className); w.set(hr + 2 + i, 2, c.hostelFee) }
        val lr = hr + 2 + classes.size + 1
        w.set(lr, 1, "Laundry Fee (per month, all hostel students)", STYLE_BOLD)
        w.set(lr + 1, 1, "Laundry Fee / month"); w.set(lr + 1, 2, card.laundryPerMonth); w.set(lr + 1, 3, "Months/Year"); w.set(lr + 1, 4, card.monthsPerYear.toLong())
        w.set(lr + 3, 1, "Entrance Fee (New Students only)", STYLE_BOLD)
        w.set(lr + 4, 1, "Entrance Fee"); w.set(lr + 4, 2, card.entranceFee)
        w.width(1, 30.0); w.width(2, 18.0); w.width(6, 24.0); w.width(7, 16.0); (11..15).forEach { w.width(it, 18.0) }
    }

    /** Any report table as a one-sheet workbook. */
    fun exportReport(table: ReportTable, schoolName: String): ByteArray {
        val w = XlsxWriter().sheet(table.title.take(31))
        w.set(1, 1, schoolName, STYLE_TITLE)
        w.set(2, 1, table.title, STYLE_BOLD)
        w.set(3, 1, table.subtitle)
        table.headers.forEachIndexed { i, h -> w.set(5, i + 1, h, STYLE_HEADER); w.width(i + 1, if (i == 1) 26.0 else 15.0) }
        table.rows.forEachIndexed { r, row -> row.forEachIndexed { c, v -> w.set(6 + r, c + 1, v) } }
        table.totals?.forEachIndexed { c, v -> w.set(6 + table.rows.size, c + 1, v, if (v is Number) STYLE_BOLD_NUMBER else STYLE_BOLD) }
        return w.toBytes()
    }
}
