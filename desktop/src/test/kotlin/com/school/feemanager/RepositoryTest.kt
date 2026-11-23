package com.school.feemanager

import com.school.feemanager.data.AppSnapshot
import com.school.feemanager.data.EnrollmentForm
import com.school.feemanager.data.FeeException
import com.school.feemanager.data.FeeRepository
import com.school.feemanager.data.JdbcFeeStore
import com.school.feemanager.data.PayItem
import com.school.feemanager.data.PaymentRequest
import com.school.feemanager.data.StudentForm
import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.PayStatus
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.ReportFilter
import com.school.feemanager.domain.ReportType
import com.school.feemanager.domain.Reports
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import com.school.feemanager.domain.academicYearLabel
import com.school.feemanager.io.ImportModule
import com.school.feemanager.io.ModuleImporter
import com.school.feemanager.io.WorkbookExporter
import com.school.feemanager.io.WorkbookImporter
import com.school.feemanager.util.PdfExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.sql.DriverManager
import java.time.LocalDate

/** The shared FeeRepository end-to-end on the desktop SQLite store (the same code the Android app runs on Room). */
class RepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var store: JdbcFeeStore
    private lateinit var repo: FeeRepository
    private val today = LocalDate.now()
    private val june = if (today.monthValue >= 6) LocalDate.of(today.year, 6, 1) else LocalDate.of(today.year - 1, 6, 1)

    @Before fun setUp() = runBlocking {
        store = JdbcFeeStore(File(tmp.root, "school_fees.db"))
        repo = FeeRepository(store)
        repo.ensureSeeded()
    }

    @After fun tearDown() { store.close() }

    private suspend fun snap(): AppSnapshot = repo.snapshot().first()

    private fun form(cls: String, start: LocalDate, period: Int, term: PaymentTerm, status: StudentStatus = StudentStatus.NEW) =
        EnrollmentForm(cls, academicYearLabel(start, period), start, period, StudentType.DAY, status, BookType.MYANMAR, 0, term, "")

    private fun fails(block: suspend () -> Unit) {
        try { runBlocking { block() }; fail("expected FeeException") } catch (_: FeeException) {}
    }

    @Test fun seedAndLogin() = runBlocking {
        val s = snap()
        assertEquals(13, s.card.classes.size)
        assertEquals(1, s.users.size)
        assertTrue(repo.login("admin", "admin123") != null)
        assertTrue(repo.login("admin", "wrong") == null)
    }

    @Test fun multiClassStudentAndCombinedReceipt() = runBlocking {
        val a = repo.saveStudent(null, StudentForm("EN-100", "Mya  Mya", ""), form("G-3", june, 12, PaymentTerm.THREE_INST), "admin")
        val sep = june.plusMonths(3)
        val g5 = repo.saveEnrollment(null, a, form("G-5", sep, 6, PaymentTerm.MONTHLY, StudentStatus.OLD), "admin")
        val b = repo.saveStudent(null, StudentForm("", "Ko Ko", ""), form("KG", june, 12, PaymentTerm.MONTHLY), "admin")

        fails { repo.saveStudent(null, StudentForm("en-100", "Other", ""), null, "admin") }            // enroll no. is unique
        fails { repo.saveEnrollment(null, a, form("G-5", sep, 6, PaymentTerm.MONTHLY), "admin") }      // same class + year
        fails { repo.saveStudent(null, StudentForm("", "mya mya", ""), form("G-3", june, 12, PaymentTerm.THREE_INST), "admin") } // same name, class, year

        var s = snap()
        val la = s.ledger(a)!!
        assertEquals("Mya Mya", la.student.name)
        assertEquals("EN-100", la.student.enrollNo)
        assertEquals(2, la.enrollments.size)
        val g3 = la.enrollments.first { it.enrollment.className == "G-3" }
        assertEquals(550000L, g3.net)
        assertEquals(3, g3.lines.size)
        assertEquals(6, la.enrollment(g5)!!.lines.size)
        assertEquals(sep, la.enrollment(g5)!!.lines.first().line.dueDate)

        // one combined receipt: G-3 Inst-1 and G-5 M-1 for Mya Mya and KG M-1 for Ko Ko
        val kg = s.ledger(b)!!.enrollments.single()
        val items = listOf(
            PayItem(a, g3.enrollment.id, 1, g3.lines[0].line.amount, "G-3 Installment 1"),
            PayItem(a, g5, 1, 10000, "G-5 Month 1"),
            PayItem(b, kg.enrollment.id, 1, kg.lines[0].line.amount, "KG Month 1"),
        )
        val before = la.balance + s.ledger(b)!!.balance
        val rid = repo.recordPayment(PaymentRequest(today, items, "family payment"), "cashier")
        s = snap()
        val r = s.receiptById[rid]!!
        assertEquals(3, r.items.size)
        assertEquals(items.sumOf { it.amount }, r.total)
        assertEquals("Mya Mya, Ko Ko", r.payerName)
        assertEquals(before, r.previousBalance)
        assertEquals(before - r.total, r.remainingBalance)
        assertTrue(r.receiptNo.startsWith("RCP-${today.year}-"))
        assertEquals(1, s.dashboard.receiptsIssued)
        assertEquals(PayStatus.PAID, s.ledger(a)!!.enrollment(g3.enrollment.id)!!.lines[0].status)
        assertEquals(10000L, s.ledger(a)!!.enrollment(g5)!!.paid)

        // a second receipt gets the next number
        val rid2 = repo.recordPayment(PaymentRequest(today, listOf(PayItem(b, kg.enrollment.id, 2, 5, "KG Month 2")), ""), "cashier")
        assertTrue(snap().receiptById[rid2]!!.receiptNo > r.receiptNo)

        // void the whole combined receipt
        repo.voidReceipt(rid, "wrong family", "admin")
        s = snap()
        assertTrue(s.receiptById[rid]!!.voided)
        assertEquals(5L, s.ledger(a)!!.paid + s.ledger(b)!!.paid)
        fails { repo.voidReceipt(rid, "again", "admin") }

        // a deactivated class cannot be paid and leaves the payables
        repo.setEnrollmentActive(g5, false, "admin")
        fails { repo.recordPayment(PaymentRequest(today, listOf(PayItem(a, g5, null, 5, "x")), ""), "cashier") }
        assertTrue(snap().ledger(a)!!.payables.none { it.enrollment.enrollment.id == g5 })
        fails { repo.recordPayment(PaymentRequest(today.plusDays(1), listOf(PayItem(b, kg.enrollment.id, null, 5, "x")), ""), "cashier") } // future date

        // editing a class re-assesses with a new period; payments stay
        repo.saveEnrollment(g3.enrollment.id, a, form("G-3", june, 10, PaymentTerm.MONTHLY), "admin")
        val g3b = snap().ledger(a)!!.enrollment(g3.enrollment.id)!!
        assertEquals(10, g3b.lines.size)
        assertEquals(g3b.net, g3b.lines.sumOf { it.line.amount })

        // every report runs on the real data; a report PDF can be written
        val ledgers = snap().ledgers
        ReportType.entries.forEach { t ->
            val table = Reports.build(t, ledgers, snap().payments, snap().card.classNames, ReportFilter(includeInactive = true), today)
            val pdf = ByteArrayOutputStream().also { PdfExporter.writeReport(table, "Test School", it) }.toByteArray()
            assertTrue(String(pdf, 0, 8, Charsets.ISO_8859_1).startsWith("%PDF-1.4"))
            assertTrue(String(pdf, pdf.size - 6, 6, Charsets.ISO_8859_1).contains("%%EOF"))
        }
    }

    @Test fun moduleImportsWithPreviewAndCombinedReceipts() = runBlocking {
        val card = snap().card
        val students = ModuleImporter.read(ImportModule.STUDENTS, listOf(
            "Enroll No.,Student Name,Class,Start Date,Period (Months),Payment Term",
            "EN-1,Aung Aung,G-3,${june.dayOfMonth}/${june.monthValue}/${june.year},12,3-Inst",
            "EN-1,Aung Aung,G-5,,6,Monthly",
            "EN-2,Su Su,KG,,,",
            "EN-3,Hla Hla,,,,",
        ).joinToString("\n").toByteArray(), card)
        val preview = repo.applyImport(students, false, dryRun = true, by = "admin")
        assertEquals(3, preview.studentsImported)
        assertEquals(3, preview.enrollmentsImported)
        assertEquals(0, snap().ledgers.size)                          // the preview saved nothing

        val done = repo.applyImport(students, false, dryRun = false, by = "admin")
        assertEquals(preview.copy(dryRun = false), done)
        var s = snap()
        assertEquals(3, s.ledgers.size)
        val aung = s.ledgers.single { it.student.enrollNo == "EN-1" }
        assertEquals(listOf("G-3", "G-5"), aung.enrollments.map { it.enrollment.className }.sorted())
        assertEquals(6, aung.enrollments.single { it.enrollment.className == "G-5" }.enrollment.periodMonths)

        // the same file again: nothing new
        val again = repo.applyImport(students, false, dryRun = false, by = "admin")
        assertEquals(0, again.studentsImported); assertEquals(3, again.enrollmentsSkippedDuplicate); assertEquals(3, again.studentsExisting)

        // enrollments by student name; the academic year gives the start date
        val en = ModuleImporter.read(ImportModule.ENROLLMENTS, "Student Name,Class,Academic Year\nHla Hla,G-1,2030-2031\nNobody,G-1,\n".toByteArray(), card)
        val enr = repo.applyImport(en, false, false, "admin")
        assertEquals(1, enr.enrollmentsImported)
        assertEquals(1, enr.problems.size)
        assertEquals(LocalDate.of(2030, 6, 1), snap().ledgers.single { it.student.name == "Hla Hla" }.enrollments.single().enrollment.startDate)

        // payments: R-1 = two lines for two classes → one receipt; R-2 is ambiguous (no class) → skipped as a whole
        val d = "${today.dayOfMonth}/${today.monthValue}/${today.year}"
        val pays = ModuleImporter.read(ImportModule.PAYMENTS, listOf(
            "Receipt No.,Date,Enroll No.,Class,Payment For,Amount",
            "R-1,$d,EN-1,G-3,Inst-1,100000",
            "R-1,$d,EN-1,G-5,M-2,20000",
            "R-2,$d,EN-1,,,500",
            "R-2,$d,EN-2,,,700",
            ",$d,EN-2,,M-1,1000",
        ).joinToString("\n").toByteArray(), card)
        val p = repo.applyImport(pays, false, false, "admin")
        assertEquals(2, p.receiptsImported)
        assertEquals(3, p.paymentsImported)
        assertEquals(1, p.problems.size)
        s = snap()
        val r1 = s.receipts.single { it.receiptNo == "R-1" }
        assertEquals(listOf(100000L, 20000L), r1.items.map { it.amount })
        assertEquals(2, r1.items.map { it.enrollmentId }.distinct().size)
        assertEquals(2, r1.items.single { it.amount == 20000L }.scheduleSeq)
        assertEquals(2, repo.applyImport(pays, false, false, "admin").paymentsSkippedDuplicate)

        // fee rates: update a class and add one with its own academic year
        val rates = ModuleImporter.read(ImportModule.FEE_RATES, "Class,Training Fee,Start Month,Start Day,Period (Months)\nG-3,999,6,1,12\nPIANO,5000,1,15,10\n".toByteArray(), card)
        val rr = repo.applyImport(rates, false, false, "admin")
        assertEquals(1, rr.classesAdded); assertEquals(1, rr.classesUpdated)
        val c2 = snap().card
        assertEquals(999L, c2.rateFor("G-3")!!.trainingFee)
        assertEquals(36000L, c2.rateFor("G-3")!!.bookDayIntl)       // not in the file → kept
        assertEquals(456000L, snap().ledgers.single { it.student.enrollNo == "EN-1" }.enrollments.single { it.enrollment.className == "G-3" }.net) // approved fee kept
        assertEquals(10, c2.rateFor("PIANO")!!.periodMonths)
        assertEquals(14, c2.classNames.size)          // 13 standard classes + PIANO

        // export the workbook and import it into a NEW database: same students, classes, receipts and balances
        s = snap()
        val bytes = WorkbookExporter.export("School", s.ledgers, s.payments, s.card, s.dashboard)
        val other = JdbcFeeStore(File(tmp.root, "other.db"))
        val repo2 = FeeRepository(other)
        repo2.ensureSeeded()
        val imp = WorkbookImporter.read(ByteArrayInputStream(bytes), repo2.snapshot().first().card)
        assertEquals(emptyList<Any>(), imp.errors)
        repo2.applyImport(imp, true, false, "admin")
        val s2 = repo2.snapshot().first()
        assertEquals(s.ledgers.map { it.student.code to it.balance }.sortedBy { it.first }, s2.ledgers.map { it.student.code to it.balance }.sortedBy { it.first })
        assertEquals(s.ledgers.sumOf { it.enrollments.size }, s2.ledgers.sumOf { it.enrollments.size })
        assertEquals(s.receipts.filter { !it.voided }.map { it.receiptNo to it.total }.toSet(), s2.receipts.map { it.receiptNo to it.total }.toSet())
        other.close()
    }

    @Test fun originalWorkbookTwiceDoesNotDuplicate() = runBlocking {
        val card = snap().card
        // a workbook in the original layout (no IDs): Ko Ko in two classes, Ma Ma once, payments by name
        val w = com.school.feemanager.io.XlsxWriter().sheet("Student_Register")
        w.set(2, 2, "Student Name")
        listOf(arrayOf("Ko Ko", "G-1"), arrayOf("Ko Ko", "G-2"), arrayOf("Ma Ma", "KG")).forEachIndexed { i, a ->
            w.set(3 + i, 2, a[0]); w.set(3 + i, 3, a[1]); w.set(3 + i, 4, "Day"); w.set(3 + i, 5, "New"); w.set(3 + i, 6, "Myanmar"); w.set(3 + i, 17, "3-Inst")
        }
        w.sheet("Cash_Collection")
        w.set(3, 2, "Ko Ko"); w.set(3, 3, "G-2"); w.set(3, 4, "01/06/2026"); w.set(3, 5, 50000.0); w.set(3, 6, "OLD-1")
        val imp = WorkbookImporter.parse(com.school.feemanager.io.XlsxReader.read(ByteArrayInputStream(w.toBytes())), card)
        val first = repo.applyImport(imp, false, false, "admin")
        assertEquals(2, first.studentsImported); assertEquals(3, first.enrollmentsImported); assertEquals(1, first.paymentsImported)
        val second = repo.applyImport(imp, false, false, "admin")
        assertEquals(0, second.studentsImported); assertEquals(0, second.enrollmentsImported); assertEquals(1, second.paymentsSkippedDuplicate)
        val koko = snap().ledgers.single { it.student.name == "Ko Ko" }
        assertEquals(2, koko.enrollments.size)
        assertEquals(50000L, koko.enrollments.single { it.enrollment.className == "G-2" }.paid)
    }

    @Test fun backupRestoreAndPhoneBackupFromVersion1() = runBlocking {
        repo.saveStudent(null, StudentForm("EN-9", "Backup Kid", ""), form("G-1", june, 12, PaymentTerm.MONTHLY), "admin")
        val backup = ByteArrayOutputStream().also { repo.backupTo(it) }.toByteArray()
        assertTrue(String(backup, 0, 15, Charsets.US_ASCII) == "SQLite format 3")
        // the backup has Room's version and identity, so the Android app accepts it
        val bf = File(tmp.root, "b.db").apply { writeBytes(backup) }
        DriverManager.getConnection("jdbc:sqlite:" + bf.absolutePath).use { c ->
            c.createStatement().executeQuery("PRAGMA user_version").use { it.next(); assertEquals(2, it.getInt(1)) }
            c.createStatement().executeQuery("SELECT identity_hash FROM room_master_table").use { assertTrue(it.next()) }
        }
        repo.saveStudent(null, StudentForm("", "After Backup", ""), null, "admin")
        assertEquals(2, snap().ledgers.size)
        repo.restoreFrom(ByteArrayInputStream(backup))
        assertEquals(listOf("Backup Kid"), snap().ledgers.map { it.student.name })
        try { repo.restoreFrom(ByteArrayInputStream("not a db".toByteArray())); fail() } catch (_: FeeException) {}

        // a version-1.0 phone backup (built from Room's v1 schema) is upgraded when restored
        val v1 = File(tmp.root, "v1.db")
        DriverManager.getConnection("jdbc:sqlite:" + v1.absolutePath).use { c ->
            val json = File(System.getProperty("schemaDir"), "1.json").readText()
            json.split("\"tableName\": \"").drop(1).forEach { chunk ->
                val table = chunk.substringBefore('"')
                Regex("\"createSql\": \"((?:[^\"\\\\]|\\\\.)*)\"").findAll(chunk).forEach { m -> c.createStatement().execute(m.groupValues[1].replace("\${TABLE_NAME}", table)) }
            }
            val st = c.createStatement()
            st.execute("INSERT INTO school_settings VALUES (1, 'Phone School', '', '', 200000, 70000, 12, 100000, ${june.toEpochDay()}, 'RCP', 2, 2, 0)")
            st.execute("INSERT INTO fee_rates (className, sortOrder, trainingFee, bookDayMyanmar, bookDayIntl, bookHostelMyanmar, bookHostelIntl, hostelFee) VALUES ('G-3', 0, 220000, 30000, 36000, 33000, 40000, 950000)")
            st.execute("INSERT INTO students VALUES (1, 'STU-0001', 'Phone Kid', 'phone kid', 'G-3', 'DAY', 'NEW', 'MYANMAR', 'THREE_INST', 0, '', 1, ${june.toEpochDay()}, 0)")
            st.execute("INSERT INTO student_fees VALUES (1, 220000, 200000, 30000, 0, 0, 100000, 550000, 0, 0, 550000, 0)")
            (1..3).forEach { st.execute("INSERT INTO payment_schedule (studentId, seq, label, dueDate, amount) VALUES (1, $it, 'Inst-$it', ${june.toEpochDay()}, 183333)") }
            st.execute("INSERT INTO payment_transactions VALUES (1, 1, ${june.toEpochDay()}, 100000, 1, 'Installment 1', '', 'admin', 0, 0, '', '', 0)")
            st.execute("INSERT INTO receipts VALUES (1, 'RCP-2026-000001', 1, 0, 550000, 450000, 'admin', 0)")
            st.execute("INSERT INTO users (username, fullName, role, passwordHash, salt, active, mustChangePassword) VALUES ('admin', 'A', 'ADMIN', 'x', 'y', 1, 0)")
            st.execute("PRAGMA user_version = 1")
        }
        repo.restoreFrom(v1.inputStream())
        val s = snap()
        assertEquals("Phone School", s.settings.schoolName)
        val kid = s.ledgers.single()
        assertEquals(550000L, kid.net); assertEquals(100000L, kid.paid)
        assertEquals("RCP-2026-000001", s.receipts.single().receiptNo)
        assertEquals("2026-2027".takeIf { june.year == 2026 } ?: academicYearLabel(june, 12), kid.enrollments.single().enrollment.academicYear)
    }
}
