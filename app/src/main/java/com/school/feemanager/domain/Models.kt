package com.school.feemanager.domain

import java.time.LocalDate

/*
 * Pure-Kotlin domain model. Nothing in this package depends on Android,
 * so every business rule here is unit-tested on a plain JVM.
 *
 * MONEY RULE: every MMK amount is a Long holding whole kyat.
 * Percentages are Ints in basis points (10.5 % = 1050). No Float/Double
 * is ever used for stored amounts, so there are no floating-point errors.
 */

enum class StudentType(val label: String) {
    DAY("Day"), HOSTEL("Hostel");

    companion object {
        fun parse(s: String?): StudentType? =
            entries.firstOrNull { it.label.equals(s?.trim(), ignoreCase = true) }
    }
}

enum class StudentStatus(val label: String) {
    NEW("New"), OLD("Old");

    companion object {
        fun parse(s: String?): StudentStatus? =
            entries.firstOrNull { it.label.equals(s?.trim(), ignoreCase = true) }
    }
}

enum class BookType(val label: String) {
    MYANMAR("Myanmar"), INTERNATIONAL("International");

    companion object {
        fun parse(s: String?): BookType? {
            val t = s?.trim()?.lowercase() ?: return null
            return when {
                t == "myanmar" || t == "mm" -> MYANMAR
                t == "international" || t == "intl" || t == "int" -> INTERNATIONAL
                else -> null
            }
        }
    }
}

enum class PaymentTerm(val label: String, val parts: Int) {
    THREE_INST("3-Inst", 3), MONTHLY("Monthly", 12);

    /** Number of schedule lines for an enrollment whose period is [periodMonths] months. */
    fun partsFor(periodMonths: Int): Int = if (this == THREE_INST) 3 else periodMonths.coerceIn(1, 24)

    companion object {
        fun parse(s: String?): PaymentTerm? {
            val t = s?.trim()?.lowercase()?.replace(" ", "")?.replace("_", "-") ?: return null
            return when (t) {
                "3-inst", "3inst", "3-installments", "3-instalments", "3-installment", "installment", "instalment" -> THREE_INST
                "monthly", "month" -> MONTHLY
                else -> null
            }
        }
    }
}

/** Payment status of one schedule line or one student. */
enum class PayStatus(val label: String) {
    UNPAID("Unpaid"), PARTIAL("Partially Paid"), PAID("Fully Paid"), OVERDUE("Overdue")
}

enum class Role(val label: String) {
    ADMIN("Admin"), CASHIER("Cashier");

    companion object {
        fun parse(s: String?): Role? = entries.firstOrNull { it.name.equals(s, true) || it.label.equals(s, true) }
    }
}

/** The fixed class list used by the workbook (Fee_Rates rows 6–18). */
val DEFAULT_CLASSES = listOf("KG") + (1..12).map { "G-$it" }

/** One row of the Fee_Rates sheet for a class. */
data class ClassRate(
    val className: String,
    val sortOrder: Int,
    val trainingFee: Long,
    val bookDayMyanmar: Long,
    val bookDayIntl: Long,
    val bookHostelMyanmar: Long,
    val bookHostelIntl: Long,
    val hostelFee: Long,
    /** Default academic-year start (month/day) and period for enrollments in this class. */
    val startMonth: Int = 6,
    val startDay: Int = 1,
    val periodMonths: Int = 12,
) {
    /** The start date of the academic year (for this class) that contains [today]. */
    fun defaultStart(today: LocalDate): LocalDate {
        val thisYear = startIn(today.year)
        return if (thisYear.isAfter(today)) startIn(today.year - 1) else thisYear
    }

    /** This class's start date in calendar year [year]. */
    fun startIn(year: Int): LocalDate {
        val ym = java.time.YearMonth.of(year, startMonth.coerceIn(1, 12))
        return ym.atDay(startDay.coerceIn(1, ym.lengthOfMonth()))
    }
}

/** "2026-2027" for a June 2026 start; "2026" for a January start with a period of 12 months or less. */
fun academicYearLabel(start: LocalDate, periodMonths: Int): String {
    val end = start.plusMonths(periodMonths.toLong()).minusDays(1)
    return if (end.year == start.year) "${start.year}" else "${start.year}-${end.year}"
}

/** Complete rate card = Fee_Rates worksheet. */
data class FeeRateCard(
    val classes: List<ClassRate>,
    val instrumentFee: Long,
    val laundryPerMonth: Long,
    val monthsPerYear: Int,
    val entranceFee: Long,
) {
    fun rateFor(className: String?): ClassRate? =
        classes.firstOrNull { it.className.equals(className?.trim(), ignoreCase = true) }

    val classNames: List<String> get() = classes.sortedBy { it.sortOrder }.map { it.className }

    companion object {
        /** Values exactly as found in School_Fee_Management.xlsx → Fee_Rates. */
        fun workbookDefaults(): FeeRateCard {
            val training = listOf(150000L, 180000, 200000, 220000, 240000, 260000, 280000, 300000, 320000, 340000, 380000, 420000, 450000)
            val book = listOf(
                longArrayOf(20000, 25000, 22000, 28000), longArrayOf(25000, 30000, 27000, 33000),
                longArrayOf(28000, 34000, 30000, 37000), longArrayOf(30000, 36000, 33000, 40000),
                longArrayOf(33000, 40000, 36000, 44000), longArrayOf(36000, 44000, 39000, 48000),
                longArrayOf(40000, 48000, 43000, 53000), longArrayOf(44000, 53000, 47000, 58000),
                longArrayOf(48000, 58000, 52000, 64000), longArrayOf(53000, 64000, 57000, 70000),
                longArrayOf(60000, 72000, 65000, 78000), longArrayOf(65000, 78000, 70000, 85000),
                longArrayOf(70000, 85000, 75000, 92000),
            )
            val hostel = (0..12).map { 800000L + it * 50000L }
            val classes = DEFAULT_CLASSES.mapIndexed { i, c ->
                ClassRate(c, i, training[i], book[i][0], book[i][1], book[i][2], book[i][3], hostel[i])
            }
            return FeeRateCard(classes, instrumentFee = 200000, laundryPerMonth = 70000, monthsPerYear = 12, entranceFee = 100000)
        }
    }
}

/** A student's approved fee assessment (columns G–P of Student_Register). */
data class FeeAssessment(
    val trainingFee: Long,
    val instrumentFee: Long,
    val bookFee: Long,
    val hostelFee: Long,
    val laundryFee: Long,
    val entranceFee: Long,
    val discountBp: Int,
) {
    val grossTotal: Long get() = trainingFee + instrumentFee + bookFee + hostelFee + laundryFee + entranceFee
    val discountAmount: Long get() = Money.percentOf(grossTotal, discountBp)
    val netTotal: Long get() = grossTotal - discountAmount

    companion object {
        val ZERO = FeeAssessment(0, 0, 0, 0, 0, 0, 0)
    }
}

/** A person. Class-related data lives in [EnrollmentRecord]; a student can have several. */
data class StudentRecord(
    val id: Long,
    val code: String,
    /** Enroll number typed by the school (optional, unique when given). */
    val enrollNo: String,
    val name: String,
    val remarks: String,
    val active: Boolean,
    val registeredOn: LocalDate,
) {
    /** "EN-15 · STU-0003" or just the code. */
    val idText: String get() = if (enrollNo.isBlank()) code else "$enrollNo · $code"
}

/** One class a student attends, with its own academic year, start date and period. */
data class EnrollmentRecord(
    val id: Long,
    val studentId: Long,
    val className: String,
    val academicYear: String,
    val startDate: LocalDate,
    val periodMonths: Int,
    val type: StudentType,
    val status: StudentStatus,
    val bookType: BookType,
    val term: PaymentTerm,
    val discountBp: Int,
    val remarks: String,
    val active: Boolean,
    val createdOn: LocalDate,
) {
    val title: String get() = "$className · $academicYear"
}

data class ScheduleLine(
    val seq: Int,
    val label: String,
    val dueDate: LocalDate,
    val amount: Long,
)

/** One line of a receipt: money applied to one enrollment (and optionally one schedule line). */
data class PaymentRecord(
    val id: Long,
    val studentId: Long,
    val date: LocalDate,
    val amount: Long,
    val scheduleSeq: Int?,
    val paymentFor: String,
    val receiptNo: String,
    val remarks: String,
    val receivedBy: String,
    val voided: Boolean = false,
    val createdAtMillis: Long = 0,
    val enrollmentId: Long = studentId,
    val receiptId: Long = id,
)

/** A receipt; [items] holds one or more payment lines (a combined receipt has several). */
data class ReceiptRecord(
    val id: Long,
    val receiptNo: String,
    val date: LocalDate,
    val payerName: String,
    val issuedAtMillis: Long,
    val previousBalance: Long,
    val remainingBalance: Long,
    val receivedBy: String,
    val remarks: String,
    val printCount: Int,
    val voided: Boolean,
    val voidReason: String,
    val items: List<PaymentRecord>,
) {
    val total: Long get() = items.sumOf { it.amount }
    val studentIds: List<Long> get() = items.map { it.studentId }.distinct()
}

data class LineStatus(
    val line: ScheduleLine,
    val paid: Long,
    val status: PayStatus,
) {
    val remaining: Long get() = (line.amount - paid).coerceAtLeast(0)
}

/** Fees, schedule and payments of one enrollment. */
data class EnrollmentLedger(
    val enrollment: EnrollmentRecord,
    val fee: FeeAssessment,
    val lines: List<LineStatus>,
    val paid: Long,
    val payments: List<PaymentRecord>,
) {
    val net: Long get() = fee.netTotal
    val balance: Long get() = net - paid
    val overdue: Boolean get() = lines.any { it.status == PayStatus.OVERDUE }
    val paymentState: PayStatus get() = payState(balance, paid)
    val status: PayStatus get() = if (paymentState != PayStatus.PAID && overdue) PayStatus.OVERDUE else paymentState
}

/** A student with all enrollments. Totals are the sum over every enrollment. */
data class StudentLedger(
    val student: StudentRecord,
    val enrollments: List<EnrollmentLedger>,
) {
    val net: Long get() = enrollments.sumOf { it.net }
    val paid: Long get() = enrollments.sumOf { it.paid }
    val balance: Long get() = net - paid
    val overdue: Boolean get() = enrollments.any { it.overdue }
    val payments: List<PaymentRecord> get() = enrollments.flatMap { it.payments }.sortedByDescending { it.date }
    val activeEnrollments: List<EnrollmentLedger> get() = enrollments.filter { it.enrollment.active }

    /** Class names of the active enrollments, e.g. "G-3, G-5". */
    val classesText: String
        get() = (activeEnrollments.ifEmpty { enrollments }).joinToString(", ") { it.enrollment.className }.ifEmpty { "–" }

    fun enrollment(id: Long): EnrollmentLedger? = enrollments.firstOrNull { it.enrollment.id == id }

    /** Unpaid / Partially Paid / Fully Paid – ignores due dates. */
    val paymentState: PayStatus get() = payState(balance, paid)

    /** Display status: Overdue wins over Unpaid / Partially Paid. */
    val status: PayStatus get() = if (paymentState != PayStatus.PAID && overdue) PayStatus.OVERDUE else paymentState

    /** Every schedule line that still has money to pay, across all classes, earliest due first. */
    val payables: List<Payable>
        get() = enrollments.filter { it.enrollment.active }.flatMap { e -> e.lines.filter { it.remaining > 0 }.map { Payable(student, e, it) } }
            .sortedWith(compareBy<Payable> { it.line.line.dueDate }.thenBy { it.enrollment.enrollment.id }.thenBy { it.line.line.seq })
}

/** One outstanding installment / month of one enrollment. */
data class Payable(val student: StudentRecord, val enrollment: EnrollmentLedger, val line: LineStatus) {
    val key: String get() = "${enrollment.enrollment.id}:${line.line.seq}"
    val remaining: Long get() = line.remaining
    val description: String
        get() = "${enrollment.enrollment.className} ${enrollment.enrollment.academicYear} – ${ScheduleCalculator.longLabelFor(enrollment.enrollment.term, line.line.seq)}"
}

private fun payState(balance: Long, paid: Long): PayStatus = when {
    balance <= 0 -> PayStatus.PAID
    paid <= 0 -> PayStatus.UNPAID
    else -> PayStatus.PARTIAL
}
