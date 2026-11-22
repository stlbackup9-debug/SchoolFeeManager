package com.school.feemanager

import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.FeeCalculator
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.Ledger
import com.school.feemanager.domain.LedgerInput
import com.school.feemanager.domain.Money
import com.school.feemanager.domain.PasswordHasher
import com.school.feemanager.domain.PayStatus
import com.school.feemanager.domain.PaymentRecord
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.ReportFilter
import com.school.feemanager.domain.ReportType
import com.school.feemanager.domain.Reports
import com.school.feemanager.domain.ScheduleCalculator
import com.school.feemanager.domain.StudentRecord
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import com.school.feemanager.io.HtmlDocs
import com.school.feemanager.io.ReceiptView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Test scenarios 1–13 from the specification (14–15 are in WorkbookTest). */
class BusinessRulesTest {
    private val card = FeeRateCard.workbookDefaults()
    private val start = LocalDate.of(2026, 6, 1)

    private fun student(id: Long, cls: String, type: StudentType, st: StudentStatus, book: BookType, bp: Int = 0, term: PaymentTerm = PaymentTerm.THREE_INST) =
        StudentRecord(id, "STU-%04d".format(id), "Student $id", cls, type, st, book, term, bp, "", true, start)

    private fun pay(id: Long, sid: Long, date: LocalDate, amt: Long, seq: Int? = null) =
        PaymentRecord(id, sid, date, amt, seq, "", "RCP-$id", "", "tester")

    @Test fun scenario1_newDayStudent() {
        val f = FeeCalculator.assess(card, "G-3", StudentType.DAY, StudentStatus.NEW, BookType.MYANMAR, 0)
        assertEquals(220000L, f.trainingFee)
        assertEquals(200000L, f.instrumentFee)
        assertEquals(30000L, f.bookFee)
        assertEquals(0L, f.hostelFee)
        assertEquals(0L, f.laundryFee)
        assertEquals(100000L, f.entranceFee)
        assertEquals(550000L, f.grossTotal)
        assertEquals(550000L, f.netTotal)
    }

    @Test fun scenario2_newHostelStudent_includingKgHostelFix() {
        val f = FeeCalculator.assess(card, "KG", StudentType.HOSTEL, StudentStatus.NEW, BookType.INTERNATIONAL, 0)
        assertEquals(150000L, f.trainingFee)
        assertEquals(28000L, f.bookFee)
        assertEquals(800000L, f.hostelFee)          // Excel range bug skipped KG; fixed
        assertEquals(840000L, f.laundryFee)         // 70,000 × 12
        assertEquals(100000L, f.entranceFee)
        assertEquals(2118000L, f.grossTotal)
        val g12 = FeeCalculator.assess(card, "G-12", StudentType.HOSTEL, StudentStatus.NEW, BookType.MYANMAR, 0)
        assertEquals(1400000L, g12.hostelFee)
        assertEquals(75000L, g12.bookFee)
    }

    @Test fun scenario3_oldStudentHasNoEntranceFee() {
        val f = FeeCalculator.assess(card, "G-12", StudentType.DAY, StudentStatus.OLD, BookType.INTERNATIONAL, 0)
        assertEquals(0L, f.entranceFee)
        assertEquals(450000L + 200000L + 85000L, f.netTotal)
    }

    @Test fun scenario4_bookTypeChange() {
        val mm = FeeCalculator.assess(card, "G-5", StudentType.DAY, StudentStatus.OLD, BookType.MYANMAR, 0)
        val intl = FeeCalculator.assess(card, "G-5", StudentType.DAY, StudentStatus.OLD, BookType.INTERNATIONAL, 0)
        assertEquals(36000L, mm.bookFee)
        assertEquals(44000L, intl.bookFee)
        assertEquals(8000L, intl.netTotal - mm.netTotal)
        val hmm = FeeCalculator.assess(card, "G-5", StudentType.HOSTEL, StudentStatus.OLD, BookType.MYANMAR, 0)
        val hint = FeeCalculator.assess(card, "G-5", StudentType.HOSTEL, StudentStatus.OLD, BookType.INTERNATIONAL, 0)
        assertEquals(39000L, hmm.bookFee)
        assertEquals(48000L, hint.bookFee)
    }

    @Test fun scenario5_discounts() {
        val f10 = FeeCalculator.assess(card, "G-3", StudentType.DAY, StudentStatus.NEW, BookType.MYANMAR, 1000)
        assertEquals(55000L, f10.discountAmount)
        assertEquals(495000L, f10.netTotal)
        val f125 = FeeCalculator.assess(card, "G-3", StudentType.DAY, StudentStatus.NEW, BookType.MYANMAR, 1250)
        assertEquals(68750L, f125.discountAmount)
        val f100 = FeeCalculator.assess(card, "G-3", StudentType.DAY, StudentStatus.NEW, BookType.MYANMAR, 10000)
        assertEquals(0L, f100.netTotal)
        assertEquals(1050, Money.parsePercentToBp("10.5"))
        assertEquals("10.5%", Money.bpToPercentText(1050))
        assertEquals(null, Money.parsePercentToBp("101"))
    }

    @Test fun scenario6_everyClass() {
        val training = listOf(150000L, 180000, 200000, 220000, 240000, 260000, 280000, 300000, 320000, 340000, 380000, 420000, 450000)
        card.classNames.forEachIndexed { i, c ->
            val f = FeeCalculator.assess(card, c, StudentType.DAY, StudentStatus.OLD, BookType.MYANMAR, 0)
            assertEquals(training[i], f.trainingFee)
            assertEquals(f.trainingFee + 200000 + f.bookFee, f.netTotal)
        }
        assertEquals(13, card.classNames.size)
        assertFalse(FeeCalculator.isClassKnown(card, "G-13"))
    }

    @Test fun scenario7_threeInstallments() {
        val lines = ScheduleCalculator.generate(PaymentTerm.THREE_INST, 1000000, start)
        assertEquals(listOf(333333L, 333333L, 333334L), lines.map { it.amount })
        assertEquals(listOf("Inst-1", "Inst-2", "Inst-3"), lines.map { it.label })
        assertEquals(listOf(start, start.plusMonths(4), start.plusMonths(8)), lines.map { it.dueDate })
        val even = ScheduleCalculator.generate(PaymentTerm.THREE_INST, 495000, start)
        assertEquals(listOf(165000L, 165000L, 165000L), even.map { it.amount })
    }

    @Test fun scenario8_monthlySchedule() {
        val lines = ScheduleCalculator.generate(PaymentTerm.MONTHLY, 1000000, start)
        assertEquals(12, lines.size)
        assertTrue(lines.take(11).all { it.amount == 83333L })
        assertEquals(83337L, lines[11].amount)
        assertEquals(1000000L, lines.sumOf { it.amount })
        assertEquals(start.plusMonths(11), lines[11].dueDate)
        // tiny amounts never go negative
        listOf(1L, 5L, 7L, 11L, 65L, 66L, 67L).forEach { n ->
            val l = ScheduleCalculator.generate(PaymentTerm.MONTHLY, n, start)
            assertEquals(n, l.sumOf { it.amount }); assertTrue(l.all { it.amount >= 0 })
        }
    }

    private fun ledgerOf(s: StudentRecord, pays: List<PaymentRecord>, today: LocalDate) =
        Ledger.build(
            LedgerInput(
                listOf(s), mapOf(s.id to FeeCalculator.assess(card, s)),
                mapOf(s.id to ScheduleCalculator.generate(s.term, FeeCalculator.assess(card, s).netTotal, start)),
                pays, card.classNames, today,
            ),
        ).single()

    @Test fun scenario9_partialPayments() {
        val s = student(1, "G-3", StudentType.DAY, StudentStatus.NEW, BookType.MYANMAR) // net 550,000
        val l = ledgerOf(s, listOf(pay(1, 1, start, 100000, 1), pay(2, 1, start.plusDays(3), 50000, 1)), start.plusDays(10))
        assertEquals(150000L, l.paid)
        assertEquals(400000L, l.balance)
        assertEquals(PayStatus.PARTIAL, l.paymentState)
        assertEquals(PayStatus.OVERDUE, l.lines[0].status) // Inst-1 was due on the start date
        assertEquals(PayStatus.UNPAID, l.lines[1].status)
        assertEquals(PayStatus.OVERDUE, l.status)
        val onTime = ledgerOf(s, listOf(pay(1, 1, start, 100000, 1)), start)
        assertEquals(PayStatus.PARTIAL, onTime.lines[0].status)
    }

    @Test fun scenario10_fullPaymentAndSpillOver() {
        val s = student(2, "G-3", StudentType.DAY, StudentStatus.NEW, BookType.MYANMAR, term = PaymentTerm.THREE_INST)
        val partsBefore = ledgerOf(s, listOf(pay(1, 2, start, 300000, 1)), start)
        assertEquals(PayStatus.PAID, partsBefore.lines[0].status)        // 183,333 to Inst-1
        assertEquals(116667L, partsBefore.lines[1].paid)                  // excess spills to Inst-2
        val full = ledgerOf(s, listOf(pay(1, 2, start, 550000, 1)), start.plusYears(1))
        assertEquals(0L, full.balance)
        assertEquals(PayStatus.PAID, full.status)
        assertTrue(full.lines.all { it.status == PayStatus.PAID })
    }

    @Test fun scenario11_outstandingBalancesAndVoids() {
        val s = student(3, "G-1", StudentType.HOSTEL, StudentStatus.OLD, BookType.MYANMAR, 500, PaymentTerm.MONTHLY)
        val f = FeeCalculator.assess(card, s)
        // 180,000 + 200,000 + 27,000 + 850,000 + 840,000 = 2,097,000; 5% = 104,850; net 1,992,150
        assertEquals(2097000L, f.grossTotal)
        assertEquals(104850L, f.discountAmount)
        assertEquals(1992150L, f.netTotal)
        val voided = pay(9, 3, start, 500000).copy(voided = true)
        val l = ledgerOf(s, listOf(pay(1, 3, start, 166013), voided), start)
        assertEquals(166013L, l.paid)
        assertEquals(1992150L - 166013L, l.balance)
    }

    @Test fun scenario12_receipt() {
        val html = HtmlDocs.receipt(ReceiptView("Shwe Pyi School", "Yangon", "09-123", "RCP-2026-000001", start, "STU-0001", "Aung <Aung>", "G-3",
            100000, "Installment 1", 550000, 450000, "cashier1", "", reprint = true, voided = false))
        listOf("Shwe Pyi School", "RCP-2026-000001", "01-Jun-2026", "Aung &lt;Aung&gt;", "G-3", "100,000 MMK", "Installment 1", "550,000 MMK", "450,000 MMK", "cashier1", "Signature", "DUPLICATE COPY")
            .forEach { assertTrue("receipt missing $it", html.contains(it)) }
    }

    @Test fun scenario13_dailyCollectionReport() {
        val s1 = student(1, "G-3", StudentType.DAY, StudentStatus.NEW, BookType.MYANMAR)
        val s2 = student(2, "KG", StudentType.HOSTEL, StudentStatus.NEW, BookType.MYANMAR, term = PaymentTerm.MONTHLY)
        val pays = listOf(pay(1, 1, start, 100000), pay(2, 2, start, 200000), pay(3, 2, start.plusDays(1), 70000), pay(4, 1, start, 5000).copy(voided = true))
        val input = LedgerInput(
            listOf(s1, s2), listOf(s1, s2).associate { it.id to FeeCalculator.assess(card, it) },
            listOf(s1, s2).associate { it.id to ScheduleCalculator.generate(it.term, FeeCalculator.assess(card, it).netTotal, start) },
            pays, card.classNames, start,
        )
        val ledgers = Ledger.build(input)
        val daily = Reports.build(ReportType.DAILY_COLLECTION, ledgers, pays, card.classNames, ReportFilter(from = start, to = start), start)
        assertEquals(2, daily.rows.size)
        assertEquals(300000L, daily.totals!![7])
        val monthly = Reports.build(ReportType.MONTHLY_COLLECTION, ledgers, pays, card.classNames, ReportFilter(), start)
        assertEquals(370000L, monthly.rows.single()[3])
        val dash = Ledger.dashboard(ledgers, pays, card.classNames, start)
        assertEquals(300000L, dash.collectedToday)
        assertEquals(370000L, dash.totalCollected)
        assertEquals(3, dash.receiptsIssued)
        assertEquals(1, dash.hostelStudents)
        assertEquals(dash.totalReceivable - 370000L, dash.totalOutstanding)
        val unpaid = Reports.build(ReportType.UNPAID, ledgers, pays, card.classNames, ReportFilter(), start)
        assertEquals(0, unpaid.rows.size)
        val partial = Reports.build(ReportType.PARTIALLY_PAID, ledgers, pays, card.classNames, ReportFilter(), start)
        assertEquals(2, partial.rows.size)
        ReportType.entries.forEach { Reports.build(it, ledgers, pays, card.classNames, ReportFilter(className = "KG", search = "2"), start) }
    }

    @Test fun passwordsAreSaltedAndVerified() {
        val salt = PasswordHasher.newSalt()
        val h = PasswordHasher.hash("admin123", salt)
        assertTrue(PasswordHasher.verify("admin123", salt, h))
        assertFalse(PasswordHasher.verify("admin124", salt, h))
        assertFalse(h == PasswordHasher.hash("admin123", PasswordHasher.newSalt()))
    }

    @Test fun paymentForTextMapsToScheduleLines() {
        assertEquals(2, ScheduleCalculator.parsePaymentFor("Inst-2", PaymentTerm.THREE_INST))
        assertEquals(3, ScheduleCalculator.parsePaymentFor("Installment 3", PaymentTerm.THREE_INST))
        assertEquals(5, ScheduleCalculator.parsePaymentFor("M-5", PaymentTerm.MONTHLY))
        assertEquals(11, ScheduleCalculator.parsePaymentFor("Month 11", PaymentTerm.MONTHLY))
        assertEquals(null, ScheduleCalculator.parsePaymentFor("M-5", PaymentTerm.THREE_INST))
        assertEquals(null, ScheduleCalculator.parsePaymentFor("Admission", PaymentTerm.MONTHLY))
    }
}
