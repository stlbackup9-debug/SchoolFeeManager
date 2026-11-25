package com.school.feemanager

import com.school.feemanager.data.MigrationSql
import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.FeeCalculator
import com.school.feemanager.domain.FeeChoice
import com.school.feemanager.domain.FeeKind
import com.school.feemanager.domain.FeeOption
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.PaymentPlan
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.ScheduleCalculator
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import com.school.feemanager.io.ImportModule
import com.school.feemanager.io.ModuleImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate

/** Version 1.3: Full / custom payment terms, "Fees for all students" per class, training and book choices. */
class FeeOptionsTest {
    private val base = FeeRateCard.workbookDefaults()
    private val piano = base.classes.first { it.className == "G-3" }.copy(
        className = "PIANO", commonFees = false,
        options = listOf(FeeOption(FeeKind.TRAINING, "Advanced", 300000), FeeOption(FeeKind.TRAINING, "Private", 450000),
            FeeOption(FeeKind.BOOK, "Workbook", 15000), FeeOption(FeeKind.BOOK, "Dictionary", 8000)),
    )
    private val card = base.copy(classes = base.classes + piano.copy(sortOrder = 99))

    @Test fun feesForAllStudentsCanBeSwitchedOffPerClass() {
        val g3 = FeeCalculator.assess(card, "G-3", StudentType.DAY, StudentStatus.NEW, BookType.MYANMAR, 0, 12)
        assertEquals(200000, g3.instrumentFee); assertEquals(100000, g3.entranceFee)
        val p = FeeCalculator.assess(card, "PIANO", StudentType.DAY, StudentStatus.NEW, BookType.MYANMAR, 0, 12)
        assertEquals(0, p.instrumentFee); assertEquals(0, p.entranceFee)
        assertEquals(220000 + 30000, p.netTotal)   // standard training + standard book only
    }

    @Test fun studentChoosesTrainingAndBooks() {
        fun net(c: FeeChoice) = FeeCalculator.assess(card, "PIANO", StudentType.DAY, StudentStatus.OLD, BookType.MYANMAR, 0, 12, c).let { it.trainingFee to it.bookFee }
        assertEquals(220000L to 30000L, net(FeeChoice.DEFAULT))
        assertEquals(300000L to 30000L + 15000, net(FeeChoice("Advanced", true, listOf("Workbook"))))
        assertEquals(450000L to 15000L + 8000, net(FeeChoice("private", false, listOf("Workbook", "Dictionary"))))   // names ignore case
        assertEquals(0L to 0L, net(FeeChoice(FeeChoice.NONE, false)))
        assertEquals(listOf("training option 'Gold'", "book option 'Atlas'"), FeeCalculator.unknownChoices(card, "PIANO", FeeChoice("Gold", true, listOf("Atlas"))))
        // stored text and export text round-trip
        val c = FeeChoice("Advanced", false, listOf("Workbook", "Dictionary"))
        assertEquals(c, FeeChoice.decode(c.encode()))
        assertEquals("", FeeChoice.DEFAULT.encode())
        assertEquals(c, FeeChoice.fromTexts(c.trainingText, c.booksText))
        assertEquals("Workbook + Dictionary", c.booksText)
        assertEquals(FeeChoice.DEFAULT, FeeChoice.fromTexts("", ""))
        assertEquals(FeeChoice(FeeChoice.NONE, false), FeeChoice.fromTexts("None", "None"))
        // class options round-trip through the database column
        assertEquals(piano.options, FeeOption.decode(FeeOption.encode(piano.options)))
        assertEquals(listOf(FeeOption(FeeKind.BOOK, "Work book", 15000)), FeeOption.parseList(FeeKind.BOOK, "Work;book = 15,000".replace(";", " ")))
        assertNull(FeeOption.parseList(FeeKind.BOOK, "Workbook 15000"))
    }

    @Test fun fullAndCustomPaymentTerms() {
        val start = LocalDate.of(2026, 6, 1)
        val full = ScheduleCalculator.generate(PaymentPlan(PaymentTerm.FULL), 1_000_000, start, 12)
        assertEquals(listOf(1_000_000L), full.map { it.amount }); assertEquals(start, full.single().dueDate); assertEquals("Full", full.single().label)
        // 4 installments spread evenly over 12 months: +0, +3, +6, +9
        val even = ScheduleCalculator.generate(PaymentPlan(PaymentTerm.CUSTOM, 4), 1_000_001, start, 12)
        assertEquals(listOf(250000L, 250000, 250000, 250001), even.map { it.amount })
        assertEquals(listOf(0L, 3, 6, 9), even.map { java.time.temporal.ChronoUnit.MONTHS.between(start, it.dueDate) })
        assertEquals(listOf("Inst-1", "Inst-2", "Inst-3", "Inst-4"), even.map { it.label })
        // 6 installments every 2 months
        val every = ScheduleCalculator.generate(PaymentPlan(PaymentTerm.CUSTOM, 6, 2), 600_000, start, 12)
        assertEquals(listOf(0L, 2, 4, 6, 8, 10), every.map { java.time.temporal.ChronoUnit.MONTHS.between(start, it.dueDate) })
        assertEquals(600_000L, every.sumOf { it.amount })
        // labels and parsing
        assertEquals("6-Inst every 2 months", PaymentPlan(PaymentTerm.CUSTOM, 6, 2).label)
        assertEquals(PaymentPlan(PaymentTerm.CUSTOM, 6, 2), PaymentPlan.parse("6-Inst every 2 months"))
        assertEquals(PaymentPlan(PaymentTerm.CUSTOM, 4), PaymentPlan.parse("4 Inst"))
        assertEquals(PaymentPlan(PaymentTerm.FULL), PaymentPlan.parse("Full payment"))
        assertEquals(PaymentPlan(PaymentTerm.FULL), PaymentPlan.parse("Fully paid"))
        assertEquals(PaymentPlan(PaymentTerm.THREE_INST), PaymentPlan.parse("3-Inst"))
        assertEquals(PaymentPlan(PaymentTerm.MONTHLY), PaymentPlan.parse("Monthly"))
        assertNull(PaymentPlan.parse("weekly"))
        assertEquals(1, ScheduleCalculator.parsePaymentFor("Full payment", PaymentTerm.FULL))
        assertEquals(5, ScheduleCalculator.parsePaymentFor("Inst-5", PaymentTerm.CUSTOM, 12, 6))
        assertNull(ScheduleCalculator.parsePaymentFor("Inst-7", PaymentTerm.CUSTOM, 12, 6))
    }

    @Test fun moduleImportReadsTermsChoicesAndClassOptions() {
        val rates = ("Class,Training Fee,Fees for All Students,Training Options,Book Options\r\n" +
            "PIANO,250000,No,\"Advanced=300000; Private=450000\",Workbook=15000\r\n").toByteArray()
        val r = ModuleImporter.read(ImportModule.FEE_RATES, rates, base)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        val u = r.classRates.single()
        assertEquals(false, u.commonFees)
        val applied = u.applyTo(null, 20, 12)
        assertEquals(listOf("Advanced", "Private", "Workbook"), applied.options.map { it.name })
        assertEquals(false, applied.commonFees)

        val students = ("Student Name,Class,Payment Term,Training Option,Book Options\r\n" +
            "Aung Aung,PIANO,6-Inst every 2 months,advanced,Standard + Workbook\r\n" +
            "Su Su,PIANO,Full,,\r\n" +
            "Mya,PIANO,Monthly,Gold,Atlas\r\n").toByteArray()
        val s = ModuleImporter.read(ImportModule.STUDENTS, students, card)
        val (a, b, c) = s.students.map { it.enrollment!! }
        assertEquals(PaymentTerm.CUSTOM, a.term); assertEquals(6, a.termParts); assertEquals(2, a.termEvery)
        assertEquals(FeeChoice("Advanced", true, listOf("Workbook")), a.choice)
        assertEquals(PaymentTerm.FULL, b.term); assertEquals(FeeChoice.DEFAULT, b.choice)
        assertEquals(FeeChoice.DEFAULT, c.choice)                                   // unknown options are dropped with a warning
        assertEquals(2, s.warnings.count { it.message.contains("no training option") || it.message.contains("no book option") })
    }

    // ------------------------------------------------------------------ database v2 → v3
    private fun createStatements(version: Int): List<String> {
        val json = File("schemas/com.school.feemanager.data.AppDatabase/$version.json").readText()
        val out = ArrayList<String>()
        json.split("\"tableName\": \"").drop(1).forEach { chunk ->
            val table = chunk.substringBefore('"')
            Regex("\"createSql\": \"((?:[^\"\\\\]|\\\\.)*)\"").findAll(chunk).forEach { m -> out += m.groupValues[1].replace("\\\"", "\"").replace("\${TABLE_NAME}", table) }
        }
        return out
    }

    private fun Connection.rows(sql: String): List<List<Any?>> = createStatement().use { st ->
        st.executeQuery(sql).use { rs -> val n = rs.metaData.columnCount; val out = ArrayList<List<Any?>>(); while (rs.next()) out += (1..n).map { rs.getObject(it) }; out }
    }

    /** columns incl. DEFAULT values (Room checks defaults of columns that declare one) */
    private fun Connection.columns(): Map<String, List<List<Any?>>> =
        rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table' ORDER BY name").map { it[0] as String }
            .associateWith { t -> rows("PRAGMA table_info(`$t`)").map { listOf(it[1], (it[2] as String).uppercase(), it[3], it[4], it[5]) }.sortedBy { it[0] as String } }

    @Test fun migration2to3AddsColumnsAndKeepsData() {
        val old = DriverManager.getConnection("jdbc:sqlite::memory:")
        createStatements(2).forEach { old.createStatement().execute(it) }
        old.createStatement().execute("INSERT INTO fee_rates (className, sortOrder, trainingFee, bookDayMyanmar, bookDayIntl, bookHostelMyanmar, bookHostelIntl, hostelFee, startMonth, startDay, periodMonths) VALUES ('G-3', 0, 220000, 30000, 36000, 33000, 40000, 950000, 6, 1, 12)")
        old.createStatement().execute("INSERT INTO students (code, enrollNo, name, nameKey, remarks, active, registeredOn, updatedAt) VALUES ('STU-0001', NULL, 'A', 'a', '', 1, 0, 0)")
        old.createStatement().execute("INSERT INTO enrollments (studentId, className, academicYear, startDate, periodMonths, studentType, studentStatus, bookType, paymentTerm, discountBp, remarks, active, createdOn, updatedAt, trainingFee, instrumentFee, bookFee, hostelFee, laundryFee, entranceFee, grossTotal, discountAmount, netTotal, assessedAt) VALUES (1, 'G-3', '2026-2027', 0, 12, 'DAY', 'NEW', 'MYANMAR', 'MONTHLY', 0, '', 1, 0, 0, 220000, 200000, 30000, 0, 0, 100000, 550000, 0, 550000, 0)")
        MigrationSql.STATEMENTS_2_3.forEach { old.createStatement().execute(it) }

        val fresh = DriverManager.getConnection("jdbc:sqlite::memory:")
        createStatements(3).forEach { fresh.createStatement().execute(it) }
        assertEquals(fresh.columns(), old.columns())
        assertEquals(listOf(listOf(1, "")), old.rows("SELECT commonFees, feeOptions FROM fee_rates"))
        assertEquals(listOf(listOf("MONTHLY", 550000, 0, 0, "")), old.rows("SELECT paymentTerm, netTotal, termParts, termEvery, feeChoice FROM enrollments"))
        // the schema the Windows app creates is Room's version-3 schema
        val win = DriverManager.getConnection("jdbc:sqlite::memory:")
        MigrationSql.CREATE_ALL_V3.forEach { win.createStatement().execute(it) }
        assertEquals(fresh.columns(), win.columns())
        assertTrue(MigrationSql.CREATE_ALL_V3.last().contains(Regex("'[0-9a-f]{32}'")))
    }
}
