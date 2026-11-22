package com.school.feemanager.domain

import java.time.LocalDate
import java.time.YearMonth

/** Everything needed to compute balances, dashboard and reports. */
data class LedgerInput(
    val students: List<StudentRecord>,
    val fees: Map<Long, FeeAssessment>,
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
) {
    val collectionRatePct: Int
        get() = if (totalReceivable <= 0) 0 else ((totalCollected * 100) / totalReceivable).toInt()
}

object Ledger {

    fun build(input: LedgerInput): List<StudentLedger> {
        val payBy = input.payments.filter { !it.voided }.groupBy { it.studentId }
        return input.students.map { s ->
            val fee = input.fees[s.id] ?: FeeAssessment.ZERO
            val pays = payBy[s.id].orEmpty()
            val lines = ScheduleCalculator.allocate(input.schedules[s.id].orEmpty(), pays, input.today)
            StudentLedger(s, fee, lines, pays.sumOf { it.amount }, pays.sortedByDescending { it.date })
        }
    }

    /**
     * Dashboard sheet logic.
     * Counts (enrolled, day, hostel, new, old) use ACTIVE students.
     * Money totals include every student so deactivated students' unpaid balances are not lost.
     */
    fun dashboard(ledgers: List<StudentLedger>, payments: List<PaymentRecord>, classOrder: List<String>, today: LocalDate): DashboardSummary {
        val active = ledgers.filter { it.student.active }
        val valid = payments.filter { !it.voided }
        val thisMonth = YearMonth.from(today)
        val classes = (classOrder + ledgers.map { it.student.className }).distinct()
        val summaries = classes.map { c ->
            val inClass = ledgers.filter { it.student.className == c }
            val activeInClass = inClass.filter { it.student.active }
            ClassSummary(
                className = c,
                dayStudents = activeInClass.count { it.student.type == StudentType.DAY },
                hostelStudents = activeInClass.count { it.student.type == StudentType.HOSTEL },
                dayReceivable = inClass.filter { it.student.type == StudentType.DAY }.sumOf { it.net },
                hostelReceivable = inClass.filter { it.student.type == StudentType.HOSTEL }.sumOf { it.net },
                collected = inClass.sumOf { it.paid },
                balance = inClass.sumOf { it.balance },
            )
        }
        val months = (5 downTo 0).map { thisMonth.minusMonths(it.toLong()) }.map { ym ->
            val inMonth = valid.filter { YearMonth.from(it.date) == ym }
            MonthTotal(ym, inMonth.sumOf { it.amount }, inMonth.size)
        }
        return DashboardSummary(
            totalStudents = active.size,
            dayStudents = active.count { it.student.type == StudentType.DAY },
            hostelStudents = active.count { it.student.type == StudentType.HOSTEL },
            newStudents = active.count { it.student.status == StudentStatus.NEW },
            oldStudents = active.count { it.student.status == StudentStatus.OLD },
            totalReceivable = ledgers.sumOf { it.net },
            totalCollected = valid.sumOf { it.amount },
            totalOutstanding = ledgers.sumOf { it.balance },
            collectedToday = valid.filter { it.date == today }.sumOf { it.amount },
            collectedThisMonth = valid.filter { YearMonth.from(it.date) == thisMonth }.sumOf { it.amount },
            receiptsIssued = valid.size,
            studentsWithBalance = ledgers.count { it.balance > 0 },
            overdueStudents = ledgers.count { it.status == PayStatus.OVERDUE },
            classSummaries = summaries,
            lastSixMonths = months,
            recentPayments = valid.sortedWith(compareByDescending<PaymentRecord> { it.date }.thenByDescending { it.id }).take(10),
        )
    }
}
