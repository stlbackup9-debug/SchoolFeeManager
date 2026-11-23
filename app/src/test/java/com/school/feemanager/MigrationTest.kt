package com.school.feemanager

import com.school.feemanager.data.MigrationSql
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate

/**
 * Runs the real v1 → v2 migration SQL on SQLite and checks that
 *  1. the migrated tables are identical (columns, types, NOT NULL, keys, foreign keys, indices) to a fresh v2 database
 *     created from Room's own schema export – this is what Room validates when the app opens the database;
 *  2. no data is lost or changed.
 */
class MigrationTest {
    private val schemaDir = File("schemas/com.school.feemanager.data.AppDatabase")

    /** CREATE statements from Room's exported schema JSON (tables and indices). */
    private fun createStatements(version: Int): List<String> {
        val json = File(schemaDir, "$version.json").readText()
        val out = ArrayList<String>()
        json.split("\"tableName\": \"").drop(1).forEach { chunk ->
            val table = chunk.substringBefore('"')
            Regex("\"createSql\": \"((?:[^\"\\\\]|\\\\.)*)\"").findAll(chunk).forEach { m ->
                out += m.groupValues[1].replace("\\\"", "\"").replace("\${TABLE_NAME}", table)
            }
        }
        return out
    }

    private fun db(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:").also { it.createStatement().execute("PRAGMA foreign_keys = ON") }

    private fun Connection.exec(sql: String, vararg args: Any?) = prepareStatement(sql).use { ps ->
        args.forEachIndexed { i, a -> ps.setObject(i + 1, a) }
        ps.execute()
    }

    private fun Connection.rows(sql: String): List<List<Any?>> = createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            val n = rs.metaData.columnCount
            val out = ArrayList<List<Any?>>()
            while (rs.next()) out += (1..n).map { rs.getObject(it) }
            out
        }
    }

    private fun Connection.one(sql: String): Any? = rows(sql).single().single()

    /** Structure as Room's TableInfo sees it (default values are not compared by Room for entities without one). */
    private fun Connection.structure(): Map<String, Any> {
        val tables = rows("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT IN ('sqlite_sequence', 'room_master_table', 'android_metadata') ORDER BY name").map { it[0] as String }
        return tables.associateWith { t ->
            listOf(
                rows("PRAGMA table_info(`$t`)").map { listOf(it[1], (it[2] as String).uppercase(), it[3], it[5]) }.sortedBy { it[0] as String },
                rows("PRAGMA foreign_key_list(`$t`)").map { listOf(it[2], it[3], it[4], it[5], it[6]) }.sortedBy { it.toString() },
                rows("PRAGMA index_list(`$t`)").filter { (it[1] as String).startsWith("index_") }.map { ix ->
                    listOf(ix[1], ix[2], rows("PRAGMA index_info(`${ix[1]}`)").map { it[2] })
                }.sortedBy { it[0] as String },
            )
        }
    }

    @Test fun migration1to2KeepsAllDataAndMatchesRoomSchema() {
        val start = LocalDate.of(2026, 6, 1).toEpochDay()
        val v1 = db()
        createStatements(1).forEach { v1.exec(it) }
        v1.exec("INSERT INTO school_settings VALUES (1, 'S', '', '', 200000, 70000, 12, 100000, ?, 'RCP', 5, 4, 0)", start)
        v1.exec("INSERT INTO fee_rates (className, sortOrder, trainingFee, bookDayMyanmar, bookDayIntl, bookHostelMyanmar, bookHostelIntl, hostelFee) VALUES ('G-3', 0, 220000, 30000, 36000, 33000, 40000, 950000)")
        listOf(1L to "Aung Aung", 2L to "Su Su", 3L to "No Fee Row").forEach { (id, n) ->
            v1.exec("INSERT INTO students VALUES (?, ?, ?, ?, 'G-3', 'DAY', 'NEW', 'MYANMAR', 'THREE_INST', 1000, 'r$id', ?, ?, 99)", id, "STU-000$id", n, n.lowercase(), if (id == 2L) 0 else 1, start)
        }
        v1.exec("INSERT INTO student_fees VALUES (1, 220000, 200000, 30000, 0, 0, 100000, 550000, 1000, 55000, 495000, 7)")
        v1.exec("INSERT INTO student_fees VALUES (2, 220000, 200000, 30000, 0, 0, 100000, 550000, 0, 0, 550000, 7)")
        listOf(1L, 2L).forEach { sid -> (1..3).forEach { seq -> v1.exec("INSERT INTO payment_schedule (studentId, seq, label, dueDate, amount) VALUES (?, ?, ?, ?, 165000)", sid, seq, "Inst-$seq", start + seq) } }
        v1.exec("INSERT INTO payment_transactions VALUES (1, 1, ?, 100000, 1, 'Installment 1', '', 'admin', 11, 0, '', '', 0)", start)
        v1.exec("INSERT INTO payment_transactions VALUES (2, 1, ?, 5000, NULL, 'General payment', 'x', 'cashier', 12, 1, 'typo', 'admin', 0)", start + 1)
        v1.exec("INSERT INTO payment_transactions VALUES (3, 2, ?, 70000, 2, 'Installment 2', '', 'Imported', 13, 0, '', '', 1)", start + 2)
        v1.exec("INSERT INTO payment_transactions VALUES (4, 2, ?, 1, NULL, 'orphan', '', 'admin', 14, 0, '', '', 0)", start + 3)  // no receipt (should not happen)
        v1.exec("INSERT INTO receipts VALUES (10, 'RCP-2026-000001', 1, 21, 495000, 395000, 'admin', 2)")
        v1.exec("INSERT INTO receipts VALUES (11, 'RCP-2026-000002', 2, 22, 395000, 390000, 'cashier', 0)")
        v1.exec("INSERT INTO receipts VALUES (12, 'OLD-77', 3, 23, 550000, 480000, 'Imported', 0)")

        v1.autoCommit = false
        MigrationSql.statements(start, 12).forEach { s -> v1.exec(s.sql, *s.args) }
        v1.commit()
        v1.autoCommit = true

        val v2 = db()
        createStatements(2).forEach { v2.exec(it) }
        assertEquals(v2.structure(), v1.structure())
        assertEquals(emptyList<Any>(), v1.rows("PRAGMA foreign_key_check"))

        // students keep id, code, name, remarks, active; enroll no. empty
        assertEquals(listOf(listOf(1, "STU-0001", null, "Aung Aung", "r1", 1), listOf(2, "STU-0002", null, "Su Su", "r2", 0), listOf(3, "STU-0003", null, "No Fee Row", "r3", 1)),
            v1.rows("SELECT id, code, enrollNo, name, remarks, active FROM students ORDER BY id"))
        // one enrollment per student, same id, school start + 12 months, approved fees kept
        assertEquals(listOf(listOf(1, 1, "G-3", "2026-2027", start.toInt(), 12, 1000, 495000, 55000), listOf(2, 2, "G-3", "2026-2027", start.toInt(), 12, 0, 550000, 0), listOf(3, 3, "G-3", "2026-2027", start.toInt(), 12, 1000, 0, 0)),
            v1.rows("SELECT id, studentId, className, academicYear, startDate, periodMonths, discountBp, netTotal, discountAmount FROM enrollments ORDER BY id"))
        assertEquals(6, (v1.one("SELECT COUNT(*) FROM payment_schedule WHERE enrollmentId IN (1, 2)") as Number).toInt())
        // receipts: numbers, balances, print counts, voids preserved; one line each
        assertEquals(listOf(
            listOf(10, "RCP-2026-000001", start.toInt(), "Aung Aung", 495000, 395000, 2, 0),
            listOf(11, "RCP-2026-000002", start.toInt() + 1, "Aung Aung", 395000, 390000, 0, 1),
            listOf(12, "OLD-77", start.toInt() + 2, "Su Su", 550000, 480000, 0, 0),
        ), v1.rows("SELECT id, receiptNo, date, payerName, previousBalance, remainingBalance, printCount, voided FROM receipts WHERE id IN (10, 11, 12) ORDER BY id"))
        assertEquals(listOf(listOf(1, 10, 1, 1, 100000, 0), listOf(2, 11, 1, 1, 5000, 1), listOf(3, 12, 2, 2, 70000, 0)),
            v1.rows("SELECT id, receiptId, studentId, enrollmentId, amount, voided FROM payment_transactions WHERE id <= 3 ORDER BY id"))
        // the orphan transaction got its own receipt instead of being lost
        assertEquals("MIGRATED-4", v1.one("SELECT r.receiptNo FROM payment_transactions t JOIN receipts r ON r.id = t.receiptId WHERE t.id = 4"))
        assertEquals(listOf(listOf(6, 1, 12)), v1.rows("SELECT DISTINCT startMonth, startDay, periodMonths FROM fee_rates"))
        assertTrue(v1.rows("SELECT name FROM sqlite_temp_master WHERE type = 'table'").isEmpty())
    }
}
