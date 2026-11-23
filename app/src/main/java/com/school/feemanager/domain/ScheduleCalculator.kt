package com.school.feemanager.domain

import java.time.LocalDate

/**
 * Payment_Schedule logic.
 *
 * Excel: each part = ROUND(Net/3,0) or ROUND(Net/12,0).
 * Approved change: the LAST part absorbs the rounding difference so the
 * schedule always totals exactly Net Total.
 *
 * Due dates (approved, not in Excel): based on the enrollment's start date and period.
 *   3-Inst : start, start + period/3, start + 2·period/3   (12 months: +0, +4, +8)
 *   Monthly: start + (m-1) months, m = 1..period
 */
object ScheduleCalculator {

    fun labelFor(term: PaymentTerm, seq: Int): String =
        if (term == PaymentTerm.THREE_INST) "Inst-$seq" else "M-$seq"

    fun longLabelFor(term: PaymentTerm, seq: Int): String =
        if (term == PaymentTerm.THREE_INST) "Installment $seq" else "Month $seq"

    fun splitAmounts(net: Long, parts: Int): List<Long> {
        if (net <= 0) return List(parts) { 0L }
        var base = Money.roundDiv(net, parts.toLong())
        if (base * (parts - 1) > net) base = net / parts // tiny nets only
        val list = MutableList(parts) { base }
        list[parts - 1] = net - base * (parts - 1)
        return list
    }

    fun generate(term: PaymentTerm, net: Long, yearStart: LocalDate, periodMonths: Int = 12): List<ScheduleLine> {
        val period = periodMonths.coerceIn(1, 24)
        val amounts = splitAmounts(net, term.partsFor(period))
        return amounts.mapIndexed { i, amt ->
            val seq = i + 1
            val offset = if (term == PaymentTerm.THREE_INST) i * period / 3 else i
            ScheduleLine(seq, labelFor(term, seq), yearStart.plusMonths(offset.toLong()), amt)
        }
    }

    /**
     * Allocates payments to schedule lines.
     * Each payment goes first to the line it was recorded against (scheduleSeq),
     * any excess spills into the earliest lines that still have a balance.
     */
    fun allocate(lines: List<ScheduleLine>, payments: List<PaymentRecord>, today: LocalDate): List<LineStatus> {
        val sorted = lines.sortedBy { it.seq }
        val paid = LongArray(sorted.size)
        val idxBySeq = sorted.withIndex().associate { it.value.seq to it.index }
        payments.filter { !it.voided }
            .sortedWith(compareBy<PaymentRecord> { it.date }.thenBy { it.id })
            .forEach { p ->
                var left = p.amount
                val target = p.scheduleSeq?.let { idxBySeq[it] }
                if (target != null) {
                    val take = minOf(left, (sorted[target].amount - paid[target]).coerceAtLeast(0))
                    paid[target] += take
                    left -= take
                }
                var i = 0
                while (left > 0 && i < sorted.size) {
                    val room = (sorted[i].amount - paid[i]).coerceAtLeast(0)
                    val take = minOf(left, room)
                    paid[i] += take
                    left -= take
                    i++
                }
            }
        return sorted.mapIndexed { i, line ->
            LineStatus(line, paid[i], lineStatus(line, paid[i], today))
        }
    }

    fun lineStatus(line: ScheduleLine, paid: Long, today: LocalDate): PayStatus = when {
        line.amount <= 0 || paid >= line.amount -> PayStatus.PAID
        today.isAfter(line.dueDate) -> PayStatus.OVERDUE
        paid > 0 -> PayStatus.PARTIAL
        else -> PayStatus.UNPAID
    }

    /** Maps Excel "Payment For" text (e.g. "Inst-2", "Installment 2", "M-3", "Month 3") to a line number. */
    fun parsePaymentFor(text: String?, term: PaymentTerm, periodMonths: Int = 12): Int? {
        val t = text?.trim()?.lowercase() ?: return null
        val n = Regex("(\\d+)").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val isInst = t.startsWith("inst") || t.contains("install") || t.contains("instal")
        val isMonth = t.startsWith("m-") || t.startsWith("m ") || t.startsWith("month") || Regex("^m\\d").containsMatchIn(t)
        return when {
            isInst && term == PaymentTerm.THREE_INST && n in 1..3 -> n
            isMonth && term == PaymentTerm.MONTHLY && n in 1..term.partsFor(periodMonths) -> n
            else -> null
        }
    }
}
