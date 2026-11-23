package com.school.feemanager

import com.school.feemanager.domain.EnrollmentRecord
import com.school.feemanager.domain.FeeCalculator
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.Ledger
import com.school.feemanager.domain.LedgerInput
import com.school.feemanager.domain.PaymentRecord
import com.school.feemanager.domain.ReportFilter
import com.school.feemanager.domain.ReportType
import com.school.feemanager.domain.Reports
import com.school.feemanager.domain.ScheduleCalculator
import com.school.feemanager.domain.StudentRecord
import com.school.feemanager.io.ImportPlanner
import com.school.feemanager.io.MatchCandidate
import com.school.feemanager.io.MatchResult
import com.school.feemanager.io.WorkbookExporter
import com.school.feemanager.io.WorkbookImporter
import com.school.feemanager.io.XlsxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate

/** Scenarios 14 (import the real workbook) and 15 (export back to Excel and re-import). */
class WorkbookTest {
    private val defaults = FeeRateCard.workbookDefaults()

    private fun original() = requireNotNull(javaClass.getResourceAsStream("/School_Fee_Management.xlsx")) {
        "School_Fee_Management.xlsx must be in app/src/test/resources"
    }

    @Test fun scenario14_importOriginalWorkbook() {
        val imp = WorkbookImporter.read(original(), defaults)
        assertTrue(imp.sheetsFound.containsAll(listOf("Dashboard", "Student_Register", "Payment_Schedule", "Cash_Collection", "Fee_Rates")))
        assertEquals(emptyList<Any>(), imp.errors)
        assertNotNull(imp.rates)
        val r = imp.rates!!
        assertEquals(defaults, r)                       // every rate read from the sheet matches the rate card
        assertEquals(200000L, r.instrumentFee)          // read from G6 (the formula wrongly pointed at G7)
        assertEquals(800000L, r.rateFor("KG")!!.hostelFee)
        assertEquals(0, imp.students.size)              // the supplied workbook is an empty template
        assertEquals(0, imp.payments.size)
    }

    @Test fun scenario15_exportAndReimportRoundTrip() {
        val start = LocalDate.of(2026, 6, 1)
        val rows = listOf(
            arrayOf("Aung Aung", "G-3", "Day", "New", "Myanmar", "0", "3-Inst"),
            arrayOf("Su Su", "KG", "Hostel", "New", "International", "1000", "Monthly"),
            arrayOf("Mg Mg", "G-12", "Hostel", "Old", "Myanmar", "250", "3-Inst"),
            arrayOf("Hla Hla", "G-7", "Day", "Old", "International", "0", "Monthly"),
        )
        val enrollments = rows.mapIndexed { i, a ->
            EnrollmentRecord(i + 1L, i + 1L, a[1], "2026-2027", start, 12,
                com.school.feemanager.domain.StudentType.parse(a[2])!!, com.school.feemanager.domain.StudentStatus.parse(a[3])!!,
                com.school.feemanager.domain.BookType.parse(a[4])!!, com.school.feemanager.domain.PaymentTerm.parse(a[6])!!,
                a[5].toInt(), "", true, start)
        } + EnrollmentRecord(5, 1, "G-4", "2027-2028", start.plusYears(1), 10, com.school.feemanager.domain.StudentType.DAY, com.school.feemanager.domain.StudentStatus.OLD,
            com.school.feemanager.domain.BookType.MYANMAR, com.school.feemanager.domain.PaymentTerm.MONTHLY, 0, "", true, start)  // Aung Aung's second class
        val students = rows.mapIndexed { i, a -> StudentRecord(i + 1L, "STU-%04d".format(i + 1), if (i == 0) "EN-1" else "", a[0], "row $i", true, start) }
        val fees = enrollments.associate { it.id to FeeCalculator.assess(defaults, it) }
        val pays = listOf(
            PaymentRecord(1, 1, start, 200000, 1, "Installment 1", "RCP-2026-000001", "", "admin"),
            PaymentRecord(2, 2, start.plusDays(2), 300000, 1, "Month 1", "RCP-2026-000002", "cash", "cashier"),
            PaymentRecord(3, 3, start.plusDays(40), 999999, null, "Installment 1", "OLD-77", "", "admin"),
            // combined receipt: two lines, two classes of the same student
            PaymentRecord(4, 1, start.plusDays(41), 5000, 2, "G-3 Installment 2", "RCP-2026-000004", "", "admin", enrollmentId = 1, receiptId = 4),
            PaymentRecord(5, 1, start.plusDays(41), 7000, 1, "G-4 Month 1", "RCP-2026-000004", "", "admin", enrollmentId = 5, receiptId = 4),
        )
        val ledgers = Ledger.build(LedgerInput(students, enrollments, fees, enrollments.associate { it.id to ScheduleCalculator.generate(it.term, fees[it.id]!!.netTotal, it.startDate, it.periodMonths) }, pays, defaults.classNames, start))
        val dash = Ledger.dashboard(ledgers, pays, defaults.classNames, start)
        val bytes = WorkbookExporter.export("Test School", ledgers, pays, defaults, dash)

        val sheets = XlsxReader.read(ByteArrayInputStream(bytes))
        assertEquals(listOf("Dashboard", "Student_Register", "Payment_Schedule", "Cash_Collection", "Fee_Rates"), sheets.keys.toList())
        val reg = sheets["Student_Register"]!!
        assertEquals("Student Name", reg.text(2, 2))
        val aungRow = (3..reg.maxRow).first { reg.text(it, 2) == "Aung Aung" && reg.text(it, 3) == "G-3" }
        assertEquals(fees[1]!!.netTotal.toDouble(), reg.number(aungRow, 16)!!, 0.0)
        assertEquals("KG", reg.text(3, 3)) // export is sorted by class order
        assertEquals("EN-1", reg.text(aungRow, 23))

        val back = WorkbookImporter.parse(sheets, defaults)
        assertEquals(emptyList<Any>(), back.errors)
        assertEquals(defaults, back.rates)
        assertEquals(5, back.students.size)                          // one row per enrollment
        enrollments.forEach { e ->
            val s = students.single { it.id == e.studentId }
            val b = back.students.single { it.code == s.code && it.className == e.className }
            val be = b.enrollment!!
            assertEquals(s.name, b.name); assertEquals(s.enrollNo.ifEmpty { null }, b.enrollNo); assertEquals(e.type, be.type)
            assertEquals(e.status, be.status); assertEquals(e.bookType, be.bookType); assertEquals(e.discountBp, be.discountBp); assertEquals(e.term, be.term)
            assertEquals(e.academicYear, be.academicYear); assertEquals(e.startDate, be.startDate); assertEquals(e.periodMonths, be.periodMonths)
            assertEquals(fees[e.id]!!.netTotal, FeeCalculator.assess(back.rates!!, be.className, be.type, be.status, be.bookType, be.discountBp, be.periodMonths!!).netTotal)
        }
        assertEquals(5, back.payments.size)
        assertEquals(pays.map { it.receiptNo }, back.payments.map { it.receiptNo })
        assertEquals(pays.map { it.date }, back.payments.map { it.date })
        assertEquals(pays.map { it.amount }, back.payments.map { it.amount })
        assertEquals(listOf("G-3", "G-4"), back.payments.filter { it.receiptNo == "RCP-2026-000004" }.map { it.className })
        val cands = students.map { s -> MatchCandidate(s.id, s.code, s.enrollNo, s.name, enrollments.filter { it.studentId == s.id }.map { it.className }.toSet()) }
        back.payments.forEachIndexed { i, p -> assertEquals(pays[i].studentId, (ImportPlanner.match(p, cands) as MatchResult.Found).candidate.key) }

        // every report exports
        ReportType.entries.forEach { t ->
            val table = Reports.build(t, ledgers, pays, defaults.classNames, ReportFilter(), start)
            val x = XlsxReader.read(ByteArrayInputStream(WorkbookExporter.exportReport(table, "Test School")))
            assertEquals(table.headers[0], x.values.first().text(5, 1))
        }
    }

    @Test fun importValidationAndDuplicates() {
        val w = com.school.feemanager.io.XlsxWriter().sheet("Student_Register")
        w.set(2, 2, "Student Name")
        listOf(
            arrayOf("Ko Ko", "G-1", "Day", "New", "Myanmar"),
            arrayOf("Ko Ko", "G-1", "Day", "New", "Myanmar"),      // duplicate
            arrayOf("Bad Class", "G-15", "Day", "New", "Myanmar"), // invalid class
            arrayOf("Bad Type", "G-2", "Boarder", "New", "Myanmar"),
            arrayOf("Blank Book", "G-2", "Day", "Old", ""),
        ).forEachIndexed { i, a -> a.forEachIndexed { c, v -> w.set(3 + i, 2 + c, v) }; w.set(3 + i, 14, 0.1); w.set(3 + i, 17, "Monthly") }
        w.sheet("Cash_Collection")
        w.set(3, 2, "Ko Ko"); w.set(3, 4, 46174.0); w.set(3, 5, 50000.0); w.set(3, 6, "R1")
        w.set(4, 2, "Ko Ko"); w.set(4, 4, "02/06/2026"); w.set(4, 5, 60000.0); w.set(4, 6, "R1")   // same receipt no. on another date
        w.set(5, 2, "Ko Ko"); w.set(5, 4, "yesterday"); w.set(5, 5, 1.0)                           // bad date
        w.set(6, 2, "Ko Ko"); w.set(6, 4, "2026-06-03"); w.set(6, 5, -5.0)                         // bad amount
        val imp = WorkbookImporter.parse(XlsxReader.read(ByteArrayInputStream(w.toBytes())), defaults)
        assertEquals(listOf("Ko Ko", "Blank Book"), imp.students.map { it.name })
        assertEquals(1000, imp.students[0].discountBp)
        assertEquals(com.school.feemanager.domain.BookType.INTERNATIONAL, imp.students[1].bookType)
        assertEquals(1, imp.payments.size)
        assertEquals(LocalDate.of(2026, 6, 1), imp.payments[0].date)   // Excel serial 46174
        assertTrue(imp.errors.size >= 6)
    }
}
