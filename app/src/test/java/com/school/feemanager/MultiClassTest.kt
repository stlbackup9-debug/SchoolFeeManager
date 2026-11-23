package com.school.feemanager

import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.ClassRate
import com.school.feemanager.domain.EnrollmentRecord
import com.school.feemanager.domain.FeeCalculator
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.Ledger
import com.school.feemanager.domain.LedgerInput
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
import com.school.feemanager.domain.academicYearLabel
import com.school.feemanager.io.HtmlDocs
import com.school.feemanager.io.ImportModule
import com.school.feemanager.io.ImportPlanner
import com.school.feemanager.io.MatchCandidate
import com.school.feemanager.io.MatchResult
import com.school.feemanager.io.ModuleImporter
import com.school.feemanager.io.ReceiptLine
import com.school.feemanager.io.ReceiptView
import com.school.feemanager.io.StudentRef
import com.school.feemanager.io.TableReader
import com.school.feemanager.io.XlsxReader
import com.school.feemanager.io.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate

/** Multiple classes per student, per-class academic year / period, combined payments and Excel/CSV module imports. */
class MultiClassTest {
    private val card = FeeRateCard.workbookDefaults()
    private val june = LocalDate.of(2026, 6, 1)

    private fun enr(id: Long, sid: Long, cls: String, start: LocalDate, period: Int, term: PaymentTerm, type: StudentType = StudentType.DAY, status: StudentStatus = StudentStatus.OLD) =
        EnrollmentRecord(id, sid, cls, academicYearLabel(start, period), start, period, type, status, BookType.MYANMAR, term, 0, "", true, start)

    private fun ledgers(students: List<StudentRecord>, enrollments: List<EnrollmentRecord>, pays: List<PaymentRecord>, today: LocalDate) = Ledger.build(
        LedgerInput(
            students, enrollments, enrollments.associate { it.id to FeeCalculator.assess(card, it) },
            enrollments.associate { it.id to ScheduleCalculator.generate(it.term, FeeCalculator.assess(card, it).netTotal, it.startDate, it.periodMonths) },
            pays, card.classNames, today,
        ),
    )

    @Test fun studentWithTwoClassesHasSeparateSchedulesAndCombinedTotals() {
        val s = StudentRecord(1, "STU-0001", "EN-7", "Mya Mya", "", true, june)
        // G-3 from 1 June for 12 months (3-Inst) and G-5 from 1 Sep for 6 months (monthly)
        val a = enr(10, 1, "G-3", june, 12, PaymentTerm.THREE_INST)
        val b = enr(11, 1, "G-5", LocalDate.of(2026, 9, 1), 6, PaymentTerm.MONTHLY)
        val pays = listOf(
            // one combined receipt (id 5) paying Inst-1 of G-3 and M-1 of G-5
            PaymentRecord(1, 1, LocalDate.of(2026, 9, 1), 150000, 1, "G-3 Installment 1", "R-5", "", "c", enrollmentId = 10, receiptId = 5),
            PaymentRecord(2, 1, LocalDate.of(2026, 9, 1), 49333, 1, "G-5 Month 1", "R-5", "", "c", enrollmentId = 11, receiptId = 5),
        )
        val l = ledgers(listOf(s), listOf(a, b), pays, LocalDate.of(2026, 9, 2)).single()
        assertEquals(2, l.enrollments.size)
        val g3 = l.enrollment(10)!!; val g5 = l.enrollment(11)!!
        assertEquals(220000L + 200000 + 30000, g3.net)                // Old day student, Myanmar book
        assertEquals(150000L, g3.paid)
        assertEquals(listOf(june, june.plusMonths(4), june.plusMonths(8)), g3.lines.map { it.line.dueDate })
        assertEquals(6, g5.lines.size)                                // monthly over a 6-month period
        assertEquals(LocalDate.of(2027, 2, 1), g5.lines.last().line.dueDate)
        assertEquals(g5.net, g5.lines.sumOf { it.line.amount })
        assertEquals(49333L, g5.paid)
        assertEquals(g3.net + g5.net, l.net)
        assertEquals(199333L, l.paid)
        assertEquals(l.net - 199333L, l.balance)
        assertEquals("2026-2027", a.academicYear)
        assertEquals("2026-2027", b.academicYear)                     // Sep 2026 – Feb 2027

        // payables across both classes, earliest due first, keyed enrollment:seq
        val keys = l.payables.map { it.key }
        assertEquals("11:1", keys.first())                            // G-5 M-1 (due 1 Sep): 82,667 − 49,333 left
        assertEquals(33334L, l.payables.first().remaining)
        assertTrue("10:1" !in keys)                                   // G-3 Inst-1 = 150,000, fully paid
        assertTrue("10:2" in keys && "11:6" in keys)
        assertEquals(PayStatus.OVERDUE, l.payables.first().line.status)
        assertEquals(PayStatus.PARTIAL, l.paymentState)
        assertEquals(PayStatus.OVERDUE, l.status)

        // reports have one row per enrollment, with Enroll No. and academic year
        val enrollRep = Reports.build(ReportType.ENROLLMENT, listOf(l), pays, card.classNames, ReportFilter(), june)
        assertEquals(2, enrollRep.rows.size)
        assertEquals("EN-7", enrollRep.rows[0][0])
        val sched = Reports.build(ReportType.PAYMENT_SCHEDULE, listOf(l), pays, card.classNames, ReportFilter(className = "G-5"), june)
        assertEquals(6, sched.rows.size)
        // dashboard counts the student once but both enrollments per class
        val dash = Ledger.dashboard(listOf(l), pays, card.classNames, june)
        assertEquals(1, dash.totalStudents)
        assertEquals(2, dash.activeEnrollments)
        assertEquals(1, dash.receiptsIssued)                          // one combined receipt
        assertEquals(1, dash.classSummaries.first { it.className == "G-3" }.dayStudents)
        assertEquals(1, dash.classSummaries.first { it.className == "G-5" }.dayStudents)
        ReportType.entries.forEach { Reports.build(it, listOf(l), pays, card.classNames, ReportFilter(search = "EN-7"), june) }
    }

    @Test fun periodChangesLaundryAndScheduleSpacing() {
        val tenMonths = FeeCalculator.assess(card, "G-1", StudentType.HOSTEL, StudentStatus.OLD, BookType.MYANMAR, 0, 10)
        assertEquals(700000L, tenMonths.laundryFee)                   // 70,000 × 10
        val twelve = FeeCalculator.assess(card, "G-1", StudentType.HOSTEL, StudentStatus.OLD, BookType.MYANMAR, 0)
        assertEquals(840000L, twelve.laundryFee)                      // default stays 12 months – Excel equivalence unchanged
        val inst = ScheduleCalculator.generate(PaymentTerm.THREE_INST, 900, LocalDate.of(2027, 1, 15), 9)
        assertEquals(listOf(LocalDate.of(2027, 1, 15), LocalDate.of(2027, 4, 15), LocalDate.of(2027, 7, 15)), inst.map { it.dueDate })
        assertEquals(9, ScheduleCalculator.parsePaymentFor("M-9", PaymentTerm.MONTHLY, 9))
        assertNull(ScheduleCalculator.parsePaymentFor("M-10", PaymentTerm.MONTHLY, 9))
        assertEquals("2027", academicYearLabel(LocalDate.of(2027, 1, 1), 12))
        val rate = ClassRate("G-9", 0, 1, 1, 1, 1, 1, 1, startMonth = 1, startDay = 10, periodMonths = 11)
        assertEquals(LocalDate.of(2026, 1, 10), rate.defaultStart(LocalDate.of(2026, 11, 23)))
        assertEquals(LocalDate.of(2025, 1, 10), rate.defaultStart(LocalDate.of(2026, 1, 9)))
        assertEquals(LocalDate.of(2027, 2, 28), ClassRate("X", 0, 0, 0, 0, 0, 0, 0, 2, 31, 12).startIn(2027))
    }

    @Test fun combinedReceiptHtml() {
        fun v(no: String, lines: List<ReceiptLine>, voided: Boolean = false) =
            ReceiptView("School", "Addr", "", no, june, "Mya Mya, Ko Ko", lines, 2000000, 2000000 - lines.sumOf { it.amount }, "cashier", "", false, voided)
        val one = v("R-1", listOf(ReceiptLine("Mya Mya", "EN-7", "G-3 (2026-2027)", "G-3 Installment 1", 150000), ReceiptLine("Ko Ko", "EN-8", "G-5 (2026-2027)", "G-5 Month 1", 49333)))
        val html = HtmlDocs.receipt(one)
        listOf("(COMBINED)", "Mya Mya, Ko Ko", "G-3 Installment 1", "G-5 Month 1", "199,333 MMK", "EN-8").forEach { assertTrue("missing $it", html.contains(it)) }
        val all = HtmlDocs.combinedReceipts(listOf(one, v("R-2", listOf(ReceiptLine("Mya Mya", "EN-7", "G-3", "Inst 2", 1000)), voided = true)), "School", "Addr", "")
        listOf("COMBINED RECEIPT STATEMENT", "R-1", "R-2 (VOIDED)", "199,333 MMK", "excluding voided").forEach { assertTrue("missing $it", all.contains(it)) }
    }

    @Test fun csvImportOfAllModules() {
        val students = listOf(
            "Enroll No.,Student Name *,Class,Academic Year,Start Date,Period (Months),Student Type,New/Old,Book Type,Payment Term,Discount %,Remarks",
            "EN-1,Aung Aung,G-3,,01/06/2026,12,Day,New,Myanmar,3-Inst,10,\"likes music, piano\"",
            "EN-1,Aung Aung,G-5,2026-2027,,6,Hostel,Old,Intl,Monthly,,",
            "EN-2,\"Su \"\"Su\"\"\",,,,,,,,,,",
            ",,,,,,,,,,,",
            "EN-3,Bad Class,G-99,,,,,,,,,",
        ).joinToString("\n").toByteArray()
        val st = ModuleImporter.read(ImportModule.STUDENTS, students, card)
        assertEquals(listOf("Aung Aung", "Aung Aung", "Su \"Su\""), st.students.map { it.name })
        assertEquals(1000, st.students[0].enrollment!!.discountBp)
        assertEquals(LocalDate.of(2026, 6, 1), st.students[0].enrollment!!.startDate)
        assertEquals("likes music, piano", st.students[0].remarks)
        assertEquals(6, st.students[1].enrollment!!.periodMonths)
        assertEquals(PaymentTerm.MONTHLY, st.students[1].enrollment!!.term)
        assertNull(st.students[2].enrollment)
        assertEquals(1, st.errors.size)                               // G-99

        val enrollments = "Student ID;Class;Payment Term\nSTU-0009;G-1;Monthly\n;G-2;\n".toByteArray()
        val en = ModuleImporter.read(ImportModule.ENROLLMENTS, enrollments, card)
        assertEquals(1, en.enrollments.size)
        assertEquals("STU-0009", en.enrollments[0].ref.code)
        assertEquals(1, en.errors.size)                               // no student reference

        val payments = listOf(
            "Receipt No.,Date,Enroll No.,Class,Payment For,Amount",
            "R-100,05/06/2026,EN-1,G-3,Inst-1,\"150,000\"",
            "R-100,05/06/2026,EN-2,,M-1,10000",
            "R-100,06/06/2026,EN-1,,,5",
            ",2026-06-07,EN-1,,,-4",
            ",46180,EN-1,,,700",
        ).joinToString("\r\n").toByteArray()
        val pay = ModuleImporter.read(ImportModule.PAYMENTS, payments, card)
        assertEquals(listOf(150000L, 10000L, 700L), pay.payments.map { it.amount })
        assertEquals(LocalDate.of(2026, 6, 7), pay.payments[2].date)  // Excel serial as text
        assertEquals(2, pay.errors.size)                              // R-100 on another date, negative amount
        assertEquals("G-3", pay.payments[0].ref.className)

        val rates = "Class,Training Fee,Hostel Fee,Start Month,Start Day,Period (Months)\nPIANO-1,300000,0,9,1,10\nG-3,230000,,,,\n".toByteArray()
        val r = ModuleImporter.read(ImportModule.FEE_RATES, rates, card)
        assertEquals(listOf("PIANO-1", "G-3"), r.classRates.map { it.className })
        assertEquals(9, r.classRates[0].startMonth); assertEquals(10, r.classRates[0].periodMonths)
        // blank cells keep the current values of an existing class
        assertEquals(null, r.classRates[1].startMonth); assertEquals(null, r.classRates[1].hostelFee)
        val g3 = r.classRates[1].applyTo(card.rateFor("G-3"), 0, 12)
        assertEquals(230000L, g3.trainingFee); assertEquals(card.rateFor("G-3")!!.bookDayIntl, g3.bookDayIntl); assertEquals(950000L, g3.hostelFee)
        val piano = r.classRates[0].applyTo(null, 13, 12)
        assertEquals(0L, piano.bookDayMyanmar); assertEquals(1, piano.startDay)
    }

    @Test fun xlsxTemplatesRoundTripThroughTheImporter() {
        ImportModule.entries.forEach { m ->
            val x = ModuleImporter.templateXlsx(m)
            val sheets = XlsxReader.read(ByteArrayInputStream(x))
            assertEquals(listOf(m.sheetName, "Help"), sheets.keys.toList())
            val imp = ModuleImporter.read(m, x, card)
            assertEquals("${m.label}: ${imp.issues}", emptyList<Any>(), imp.errors)
            assertTrue("${m.label} example row imported", !imp.isEmpty)
            val csv = ModuleImporter.read(m, ModuleImporter.templateCsv(m), card)
            assertEquals("${m.label} csv: ${csv.issues}", emptyList<Any>(), csv.errors)
        }
        // header on row 3 after a title, aliases and extra columns are fine
        val w = XlsxWriter().sheet("Anything")
        w.set(1, 1, "My student list"); w.set(3, 1, "Name"); w.set(3, 2, "Enroll Number"); w.set(3, 3, "Notes"); w.set(4, 1, "Hla Hla"); w.set(4, 2, 12345.0)
        val imp = ModuleImporter.read(ImportModule.STUDENTS, w.toBytes(), card)
        assertEquals("Hla Hla", imp.students.single().name)
        assertEquals("12345", imp.students.single().enrollNo)
        assertEquals(listOf(listOf("a", "b,c", "d\"e"), listOf("1", "", "")), TableReader.parseCsv("a,\"b,c\",\"d\"\"e\"\r\n1,,\n"))
    }

    @Test fun matchingByEnrollNoIdAndName() {
        val c = listOf(
            MatchCandidate(1, "STU-0001", "EN-1", "Ko Ko", setOf("G-1")),
            MatchCandidate(2, "STU-0002", "", "Ko Ko", setOf("G-2", "G-3")),
            MatchCandidate(3, "STU-0003", "EN-3", "Ma Ma", setOf("G-1")),
        )
        fun key(r: StudentRef) = (ImportPlanner.match(r, c) as? MatchResult.Found)?.candidate?.key
        assertEquals(3L, key(StudentRef("en-3", null, "")))
        assertEquals(2L, key(StudentRef(null, "stu-0002", "")))
        assertEquals(3L, key(StudentRef(null, null, "Ma Ma")))
        assertEquals(2L, key(StudentRef(null, null, "Ko Ko", "G-3")))
        assertNull(key(StudentRef(null, null, "Ko Ko")))             // ambiguous without class
        assertNull(key(StudentRef("EN-404", null, "")))
    }
}
