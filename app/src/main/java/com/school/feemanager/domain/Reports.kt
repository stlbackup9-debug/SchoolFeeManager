package com.school.feemanager.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class ReportType(val title: String, val usesDates: Boolean, val usesStatus: Boolean) {
    ENROLLMENT("Student Enrollment Report", false, true),
    FEE_ASSESSMENT("Student Fee Assessment Report", false, true),
    OUTSTANDING("Student Outstanding Balance Report", false, true),
    DAILY_COLLECTION("Daily Cash Collection Report", true, false),
    MONTHLY_COLLECTION("Monthly Cash Collection Report", true, false),
    CLASS_WISE("Class-wise Fee Collection Report", false, false),
    DAY_VS_HOSTEL("Day vs Hostel Student Report", false, false),
    PAYMENT_SCHEDULE("Payment Schedule Report", false, true),
    UNPAID("Unpaid Student Report", false, false),
    PARTIALLY_PAID("Partially Paid Student Report", false, false),
}

data class ReportFilter(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val className: String? = null,
    val search: String = "",
    val status: PayStatus? = null,
    val includeInactive: Boolean = false,
)

/** A report cell is a String, a Long (money/count, right-aligned) or null. */
data class ReportTable(
    val title: String,
    val subtitle: String,
    val headers: List<String>,
    val rows: List<List<Any?>>,
    val totals: List<Any?>? = null,
) {
    fun isNumeric(col: Int): Boolean = rows.any { it.getOrNull(col) is Long } || totals?.getOrNull(col) is Long

    companion object {
        fun cellText(v: Any?): String = when (v) {
            null -> ""
            is Long -> Money.format(v)
            is Int -> v.toString()
            else -> v.toString()
        }
    }
}

/** One enrollment row of a report, with its student. */
private data class ERow(val s: StudentRecord, val e: EnrollmentLedger) {
    val en: EnrollmentRecord get() = e.enrollment
}

object Reports {
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy")
    private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")

    fun build(
        type: ReportType,
        ledgers: List<StudentLedger>,
        payments: List<PaymentRecord>,
        classOrder: List<String>,
        f: ReportFilter,
        today: LocalDate,
    ): ReportTable {
        val byId = ledgers.associateBy { it.student.id }
        val enrollmentById = ledgers.flatMap { it.enrollments }.associateBy { it.enrollment.id }
        val rowsAll = ledgers
            .filter { f.includeInactive || it.student.active }
            .filter { matches(it.student, f.search) }
            .flatMap { l -> l.enrollments.map { ERow(l.student, it) } }
            .filter { f.includeInactive || it.en.active }
            .filter { f.className == null || it.en.className == f.className }
            .sortedWith(compareBy<ERow> { classIndex(classOrder, it.en.className) }.thenBy { it.s.name.lowercase() }.thenBy { it.en.startDate })
        val statusFiltered = if (f.status == null) rowsAll else rowsAll.filter { statusMatches(it.e, f.status) }

        val pays = payments.filter { !it.voided }
            .filter { f.from == null || !it.date.isBefore(f.from) }
            .filter { f.to == null || !it.date.isAfter(f.to) }
            .filter { p ->
                val l = byId[p.studentId]
                val e = enrollmentById[p.enrollmentId]?.enrollment
                (f.className == null || e?.className == f.className) &&
                    (f.search.isBlank() || (l != null && matches(l.student, f.search)) || p.receiptNo.contains(f.search.trim(), true))
            }
            .sortedWith(compareBy<PaymentRecord> { it.date }.thenBy { it.receiptId }.thenBy { it.id })

        val sub = subtitle(f, type, today)
        return when (type) {
            ReportType.ENROLLMENT -> ReportTable(
                type.title, sub,
                listOf("Enroll No.", "Student ID", "Student Name", "Class", "Academic Year", "Start Date", "Months", "Type", "New/Old", "Book Type", "Payment Term", "Status", "Active"),
                statusFiltered.map {
                    val s = it.s; val e = it.en
                    listOf(s.enrollNo, s.code, s.name, e.className, e.academicYear, e.startDate.format(DATE), e.periodMonths.toString(), e.type.label, e.status.label,
                        e.bookType.label, e.termText, it.e.status.label, if (s.active && e.active) "Yes" else "No")
                },
                listOf<Any?>("TOTAL", "${statusFiltered.map { it.s.id }.distinct().size} students", "${statusFiltered.size} enrollments") + List(10) { null },
            )

            ReportType.FEE_ASSESSMENT -> {
                val rows = statusFiltered.map {
                    val s = it.s; val fe = it.e.fee
                    listOf<Any?>(s.enrollNo, s.code, s.name, it.en.className, it.en.academicYear, it.en.type.label, fe.trainingFee, fe.instrumentFee, fe.bookFee, fe.hostelFee, fe.laundryFee, fe.entranceFee,
                        fe.grossTotal, Money.bpToPercentText(fe.discountBp), fe.discountAmount, fe.netTotal)
                }
                ReportTable(
                    type.title, sub,
                    listOf("Enroll No.", "Student ID", "Student Name", "Class", "Academic Year", "Type", "Training", "Instrument", "Book", "Hostel", "Laundry", "Entrance", "Gross Total", "Disc %", "Disc Amt", "Net Total"),
                    rows, totalsRow(rows, 16, setOf(6, 7, 8, 9, 10, 11, 12, 14, 15)),
                )
            }

            ReportType.OUTSTANDING -> {
                val rows = statusFiltered.filter { it.e.balance > 0 }.map {
                    val s = it.s
                    listOf<Any?>(s.enrollNo, s.code, s.name, it.en.className, it.en.academicYear, it.en.type.label, it.en.termText, it.e.net, it.e.paid, it.e.balance, it.e.status.label)
                }
                ReportTable(type.title, sub, listOf("Enroll No.", "Student ID", "Student Name", "Class", "Academic Year", "Type", "Term", "Net Total", "Paid", "Balance Due", "Status"), rows, totalsRow(rows, 11, setOf(7, 8, 9)))
            }

            ReportType.DAILY_COLLECTION -> {
                val rows = pays.map { p ->
                    val s = byId[p.studentId]?.student
                    val e = enrollmentById[p.enrollmentId]?.enrollment
                    listOf<Any?>(p.date.format(DATE), p.receiptNo, s?.enrollNo ?: "", s?.code ?: "", s?.name ?: "?", e?.className ?: "", e?.academicYear ?: "", p.paymentFor, p.receivedBy, p.amount)
                }
                ReportTable(type.title, sub, listOf("Date", "Receipt No.", "Enroll No.", "Student ID", "Student Name", "Class", "Academic Year", "Payment For", "Received By", "Amount (MMK)"), rows, totalsRow(rows, 10, setOf(9)))
            }

            ReportType.MONTHLY_COLLECTION -> {
                val rows = pays.groupBy { YearMonth.from(it.date) }.toSortedMap().map { (ym, list) ->
                    val days = list.map { it.date }.distinct().size
                    listOf<Any?>(ym.format(MONTH), list.map { it.receiptId }.distinct().size.toLong(), days.toLong(), list.sumOf { it.amount })
                }
                ReportTable(type.title, sub, listOf("Month", "Receipts", "Collection Days", "Amount (MMK)"), rows, totalsRow(rows, 4, setOf(1, 2, 3)))
            }

            ReportType.CLASS_WISE -> {
                val classes = (classOrder + rowsAll.map { it.en.className }).distinct()
                    .filter { f.className == null || it == f.className }
                val rows = classes.map { c ->
                    val l = rowsAll.filter { it.en.className == c }
                    val rec = l.sumOf { it.e.net }; val col = l.sumOf { it.e.paid }
                    listOf<Any?>(c, l.count { it.en.type == StudentType.DAY }.toLong(), l.count { it.en.type == StudentType.HOSTEL }.toLong(), l.size.toLong(), rec, col, rec - col, pct(col, rec))
                }
                val t = totalsRow(rows, 8, setOf(1, 2, 3, 4, 5, 6))!!.toMutableList()
                t[7] = pct(t[5] as Long, t[4] as Long)
                ReportTable(type.title, sub, listOf("Class", "Day", "Hostel", "Total Enrolled", "Receivable", "Collected", "Balance Due", "Collected %"), rows, t)
            }

            ReportType.DAY_VS_HOSTEL -> {
                val rows = StudentType.entries.map { ty ->
                    val l = rowsAll.filter { it.en.type == ty }
                    val rec = l.sumOf { it.e.net }; val col = l.sumOf { it.e.paid }
                    listOf<Any?>(ty.label, l.size.toLong(), l.count { it.en.status == StudentStatus.NEW }.toLong(), l.count { it.en.status == StudentStatus.OLD }.toLong(), rec, col, rec - col, pct(col, rec))
                }
                val t = totalsRow(rows, 8, setOf(1, 2, 3, 4, 5, 6))!!.toMutableList()
                t[7] = pct(t[5] as Long, t[4] as Long)
                ReportTable(type.title, sub, listOf("Student Type", "Enrollments", "New", "Old", "Receivable", "Collected", "Balance Due", "Collected %"), rows, t)
            }

            ReportType.PAYMENT_SCHEDULE -> {
                val rows = rowsAll.flatMap { r ->
                    r.e.lines.filter { f.status == null || it.status == f.status }.map { ls ->
                        listOf<Any?>(r.s.enrollNo, r.s.code, r.s.name, r.en.className, r.en.academicYear, r.en.termText, r.e.net, ls.line.label, ls.line.dueDate.format(DATE), ls.line.amount, ls.paid, ls.remaining, ls.status.label)
                    }
                }
                ReportTable(
                    type.title, sub,
                    listOf("Enroll No.", "Student ID", "Student Name", "Class", "Academic Year", "Payment Term", "Net Total", "Part", "Due Date", "Scheduled", "Actual Paid", "Remaining", "Status"),
                    rows, totalsRow(rows, 13, setOf(9, 10, 11)),
                )
            }

            ReportType.UNPAID, ReportType.PARTIALLY_PAID -> {
                val want = if (type == ReportType.UNPAID) PayStatus.UNPAID else PayStatus.PARTIAL
                val rows = rowsAll.filter { it.e.paymentState == want }.map {
                    val nextDue = it.e.lines.firstOrNull { ls -> ls.remaining > 0 }
                    listOf<Any?>(it.s.enrollNo, it.s.code, it.s.name, it.en.className, it.en.academicYear, it.en.type.label, it.en.termText, it.e.net, it.e.paid, it.e.balance,
                        nextDue?.line?.dueDate?.format(DATE) ?: "", if (it.e.overdue) "Yes" else "No")
                }
                ReportTable(type.title, sub, listOf("Enroll No.", "Student ID", "Student Name", "Class", "Academic Year", "Type", "Term", "Net Total", "Paid", "Balance Due", "Next Due", "Overdue"), rows, totalsRow(rows, 12, setOf(7, 8, 9)))
            }
        }
    }

    fun matches(s: StudentRecord, q: String): Boolean {
        val t = q.trim()
        return t.isEmpty() || s.name.contains(t, true) || s.code.contains(t, true) || s.enrollNo.contains(t, true)
    }

    fun statusMatches(l: StudentLedger, st: PayStatus): Boolean = when (st) {
        PayStatus.OVERDUE -> l.overdue && l.paymentState != PayStatus.PAID
        else -> l.paymentState == st
    }

    fun statusMatches(l: EnrollmentLedger, st: PayStatus): Boolean = when (st) {
        PayStatus.OVERDUE -> l.overdue && l.paymentState != PayStatus.PAID
        else -> l.paymentState == st
    }

    private fun classIndex(order: List<String>, c: String): Int = order.indexOf(c).let { if (it < 0) 999 else it }

    private fun pct(part: Long, whole: Long): String = if (whole <= 0) "0%" else "${(part * 100) / whole}%"

    private fun totalsRow(rows: List<List<Any?>>, width: Int, sumCols: Set<Int>): List<Any?>? {
        if (rows.isEmpty()) return null
        return (0 until width).map { c ->
            when {
                c == 0 -> "TOTAL"
                c in sumCols -> rows.sumOf { (it.getOrNull(c) as? Long) ?: 0L }
                else -> null
            }
        }
    }

    private fun subtitle(f: ReportFilter, type: ReportType, today: LocalDate): String {
        val parts = mutableListOf("Generated ${today.format(DATE)}")
        if (type.usesDates && (f.from != null || f.to != null)) {
            parts += "Period: ${f.from?.format(DATE) ?: "start"} to ${f.to?.format(DATE) ?: "today"}"
        }
        f.className?.let { parts += "Class: $it" }
        f.status?.let { parts += "Status: ${it.label}" }
        if (f.search.isNotBlank()) parts += "Search: \"${f.search.trim()}\""
        return parts.joinToString("  |  ")
    }
}
