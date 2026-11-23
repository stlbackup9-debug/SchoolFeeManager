package com.school.feemanager.domain

import java.time.LocalDate
import java.time.YearMonth

/** Everything needed to compute balances, dashboard and reports. */
data class LedgerInput(
    val students: List<StudentRecord>,
    val enrollments: List<EnrollmentRecord>,
    /** keyed by enrollment id */
    val fees: Map<Long, FeeAssessment>,
    /** keyed by enrollment id */
    val schedules: Map<Long, List<ScheduleLine>>,
    val payments: List<PaymentRecord>,
    val classOrder: List<String>,
    val today: LocalDate,
)

data class ClassSummary(
    val className: String,
    val dayStudents: Int,
    val hostelStudents: Int,
    val dayReceivable: Long,
    val hostelReceivable: Long,
    val collected: Long,
    val balance: Long,
) {
    val totalStudents: Int get() = dayStudents + hostelStudents
    val totalReceivable: Long get() = dayReceivable + hostelReceivable
}

data class MonthTotal(val month: YearMonth, val amount: Long, val receipts: Int)

data class DashboardSummary(
    val totalStudents: Int,
    val activeEnrollments: Int,
    val dayStudents: Int,
    val hostelStudents: Int,
    val newStudents: Int,
    val oldStudents: Int,
    val totalReceivable: Long,
    val totalCollected: Long,
    val totalOutstanding: Long,
    val collectedToday: Long,
    val collectedThisMonth: Long,
    val receiptsIssued: Int,
    val studentsWithBalance: Int,
    val overdueStudents: Int,
    val classSummaries: List<ClassSummary>,
    val lastSixMonths: List<MonthTotal>,
    val recentPayments: List<PaymentRecord>,
    val payablesDue: Int = 0,
) {
    val collectionRatePct: Int
        get() = if (totalReceivable <= 0) 0 else ((totalCollected * 100) / totalReceivable).toInt()
}

object Ledger {

    fun build(input: LedgerInput): List<StudentLedger> {
        val payByEnrollment = input.payments.filter { !it.voided }.groupBy { it.enrollmentId }
        val byStudent = input.enrollments.groupBy { it.studentId }
        return input.students.map { s ->
            val enrollments = byStudent[s.id].orEmpty()
                .sortedWith(compareByDescending<EnrollmentRecord> { it.active }.thenByDescending { it.startDate }.thenBy { classIndex(input.classOrder, it.className) })
                .map { e ->
                    val fee = input.fees[e.id] ?: FeeAssessment.ZERO
                    val pays = payByEnrollment[e.id].orEmpty()
                    val lines = ScheduleCalculator.allocate(input.schedules[e.id].orEmpty(), pays, input.today)
                    EnrollmentLedger(e, fee, lines, pays.sumOf { it.amount }, pays.sortedByDescending { it.date })
                }
            StudentLedger(s, enrollments)
        }
    }

    private fun classIndex(order: List<String>, c: String): Int = order.indexOf(c).let { if (it < 0) 999 else it }

    /**
     * Dashboard sheet logic, per enrollment.
     * Counts (students, day, hostel, new, old) use ACTIVE students / enrollments.
     * Money totals include everything so deactivated records' unpaid balances are not lost.
     */
    fun dashboard(ledgers: List<StudentLedger>, payments: List<PaymentRecord>, classOrder: List<String>, today: LocalDate): DashboardSummary {
        val all = ledgers.flatMap { it.enrollments }
        val active = ledgers.filter { it.student.active }.flatMap { it.activeEnrollments }
        val valid = payments.filter { !it.voided }
        val thisMonth = YearMonth.from(today)
        val classes = (classOrder + all.map { it.enrollment.className }).distinct()
        val summaries = classes.map { c ->
            val inClass = all.filter { it.enrollment.className == c }
            val activeInClass = active.filter { it.enrollment.className == c }
            ClassSummary(
                className = c,
                dayStudents = activeInClass.count { it.enrollment.type == StudentType.DAY },
                hostelStudents = activeInClass.count { it.enrollment.type == StudentType.HOSTEL },
                dayReceivable = inClass.filter { it.enrollment.type == StudentType.DAY }.sumOf { it.net },
                hostelReceivable = inClass.filter { it.enrollment.type == StudentType.HOSTEL }.sumOf { it.net },
                collected = inClass.sumOf { it.paid },
                balance = inClass.sumOf { it.balance },
            )
        }
        val months = (5 downTo 0).map { thisMonth.minusMonths(it.toLong()) }.map { ym ->
            val inMonth = valid.filter { YearMonth.from(it.date) == ym }
            MonthTotal(ym, inMonth.sumOf { it.amount }, inMonth.map { it.receiptId }.distinct().size)
        }
        val receiptsLatest = valid.groupBy { it.receiptId }.values
            .map { items -> items.first().copy(amount = items.sumOf { it.amount }, paymentFor = items.joinToString(", ") { it.paymentFor }) }
            .sortedWith(compareByDescending<PaymentRecord> { it.date }.thenByDescending { it.receiptId })
        return DashboardSummary(
            totalStudents = ledgers.count { it.student.active && it.activeEnrollments.isNotEmpty() },
            activeEnrollments = active.size,
            dayStudents = active.count { it.enrollment.type == StudentType.DAY },
            hostelStudents = active.count { it.enrollment.type == StudentType.HOSTEL },
            newStudents = active.count { it.enrollment.status == StudentStatus.NEW },
            oldStudents = active.count { it.enrollment.status == StudentStatus.OLD },
            totalReceivable = all.sumOf { it.net },
            totalCollected = valid.sumOf { it.amount },
            totalOutstanding = all.sumOf { it.balance },
            collectedToday = valid.filter { it.date == today }.sumOf { it.amount },
            collectedThisMonth = valid.filter { YearMonth.from(it.date) == thisMonth }.sumOf { it.amount },
            receiptsIssued = valid.map { it.receiptId }.distinct().size,
            studentsWithBalance = ledgers.count { it.balance > 0 },
            overdueStudents = ledgers.count { it.status == PayStatus.OVERDUE },
            classSummaries = summaries,
            lastSixMonths = months,
            recentPayments = receiptsLatest.take(10),
            payablesDue = ledgers.filter { it.student.active }.sumOf { l -> l.payables.count { !it.line.line.dueDate.isAfter(today) } },
        )
    }
}
