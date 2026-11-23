package com.school.feemanager.data

import com.school.feemanager.domain.academicYearLabel
import java.time.LocalDate

/**
 * Version 1 → 2: one class per student becomes many enrollments per student, and one receipt can
 * hold several payment lines (combined receipts).
 *
 *  - every v1 student keeps its id; its class/fee data becomes enrollment #id (same id), using the
 *    school's academic-year start and months/year as that enrollment's start date and period;
 *  - schedules move from the student to that enrollment;
 *  - each v1 receipt (1 transaction) becomes a receipt with one line;
 *  - fee_rates gets a default start month/day and period per class.
 *
 * No amount, date, receipt number or payment is changed. Pure Kotlin, used by Room on Android
 * (Migrations.MIGRATION_1_2) and by the Windows app when it opens a version-1 backup;
 * unit-tested against a real SQLite database (MigrationTest).
 */
object MigrationSql {

    data class Stmt(val sql: String, val args: Array<Any?> = emptyArray())

    fun statements(academicYearStart: Long, monthsPerYear: Int): List<Stmt> {
        val start = LocalDate.ofEpochDay(academicYearStart)
        val period = monthsPerYear.coerceIn(1, 24)
        val label = academicYearLabel(start, period)
        val out = ArrayList<Stmt>()
        fun x(sql: String, vararg args: Any?) { out += Stmt(sql.trimIndent(), arrayOf(*args)) }

        // fee_rates: per-class default start date and period
        x("ALTER TABLE `fee_rates` ADD COLUMN `startMonth` INTEGER NOT NULL DEFAULT 6")
        x("ALTER TABLE `fee_rates` ADD COLUMN `startDay` INTEGER NOT NULL DEFAULT 1")
        x("ALTER TABLE `fee_rates` ADD COLUMN `periodMonths` INTEGER NOT NULL DEFAULT 12")
        x("UPDATE `fee_rates` SET `startMonth` = ?, `startDay` = ?, `periodMonths` = ?", start.monthValue, start.dayOfMonth, period)

        // keep the old rows, then rebuild the tables whose structure changed
        x("CREATE TEMP TABLE m_students AS SELECT * FROM `students`")
        x("CREATE TEMP TABLE m_fees AS SELECT * FROM `student_fees`")
        x("CREATE TEMP TABLE m_sched AS SELECT * FROM `payment_schedule`")
        x("CREATE TEMP TABLE m_tx AS SELECT * FROM `payment_transactions`")
        x("CREATE TEMP TABLE m_rc AS SELECT * FROM `receipts`")
        listOf("receipts", "payment_transactions", "payment_schedule", "student_fees", "students").forEach { x("DROP TABLE `$it`") }

        CREATE_V2.forEach { x(it) }

        x(
            """
            INSERT INTO `students` (id, code, enrollNo, name, nameKey, remarks, active, registeredOn, updatedAt)
            SELECT id, code, NULL, name, nameKey, remarks, active, registeredOn, updatedAt FROM m_students
            """,
        )
        x(
            """
            INSERT INTO `enrollments` (id, studentId, className, academicYear, startDate, periodMonths, studentType, studentStatus, bookType,
                paymentTerm, discountBp, remarks, active, createdOn, updatedAt, trainingFee, instrumentFee, bookFee, hostelFee, laundryFee,
                entranceFee, grossTotal, discountAmount, netTotal, assessedAt)
            SELECT s.id, s.id, s.className, ?, ?, ?, s.studentType, s.studentStatus, s.bookType,
                s.paymentTerm, COALESCE(f.discountBp, s.discountBp), '', 1, s.registeredOn, s.updatedAt,
                COALESCE(f.trainingFee, 0), COALESCE(f.instrumentFee, 0), COALESCE(f.bookFee, 0), COALESCE(f.hostelFee, 0), COALESCE(f.laundryFee, 0),
                COALESCE(f.entranceFee, 0), COALESCE(f.grossTotal, 0), COALESCE(f.discountAmount, 0), COALESCE(f.netTotal, 0), COALESCE(f.assessedAt, 0)
            FROM m_students s LEFT JOIN m_fees f ON f.studentId = s.id
            """,
            label, academicYearStart, period,
        )
        x("INSERT INTO `payment_schedule` (id, enrollmentId, seq, label, dueDate, amount) SELECT id, studentId, seq, label, dueDate, amount FROM m_sched")
        x(
            """
            INSERT INTO `receipts` (id, receiptNo, date, payerName, issuedAt, previousBalance, remainingBalance, receivedBy, remarks, printCount,
                voided, voidReason, voidedBy, imported)
            SELECT r.id, r.receiptNo, t.date, COALESCE(s.name, ''), r.issuedAt, r.previousBalance, r.remainingBalance, r.receivedBy, '', r.printCount,
                t.voided, t.voidReason, t.voidedBy, t.imported
            FROM m_rc r JOIN m_tx t ON t.id = r.transactionId LEFT JOIN m_students s ON s.id = t.studentId
            """,
        )
        // A v1 transaction always has a receipt; should one be missing, give it a receipt instead of losing it.
        x(
            """
            INSERT INTO `receipts` (receiptNo, date, payerName, issuedAt, previousBalance, remainingBalance, receivedBy, remarks, printCount,
                voided, voidReason, voidedBy, imported)
            SELECT 'MIGRATED-' || t.id, t.date, COALESCE(s.name, ''), t.createdAt, 0, 0, t.receivedBy, '', 0, t.voided, t.voidReason, t.voidedBy, t.imported
            FROM m_tx t LEFT JOIN m_students s ON s.id = t.studentId
            WHERE NOT EXISTS (SELECT 1 FROM m_rc r WHERE r.transactionId = t.id)
            """,
        )
        x(
            """
            INSERT INTO `payment_transactions` (id, receiptId, studentId, enrollmentId, date, amount, scheduleSeq, paymentFor, remarks, receivedBy,
                createdAt, voided, voidReason, voidedBy, imported)
            SELECT t.id, COALESCE((SELECT r.id FROM m_rc r WHERE r.transactionId = t.id), (SELECT r2.id FROM `receipts` r2 WHERE r2.receiptNo = 'MIGRATED-' || t.id)),
                t.studentId, t.studentId, t.date, t.amount, t.scheduleSeq, t.paymentFor, t.remarks, t.receivedBy,
                t.createdAt, t.voided, t.voidReason, t.voidedBy, t.imported
            FROM m_tx t
            """,
        )
        listOf("m_students", "m_fees", "m_sched", "m_tx", "m_rc").forEach { x("DROP TABLE temp.$it") }
        return out
    }

    /** Copied from schemas/…/2.json (Room's own CREATE statements) so the migrated tables match exactly. */
    private val CREATE_V2 = listOf(
        "CREATE TABLE IF NOT EXISTS `students` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `code` TEXT NOT NULL, `enrollNo` TEXT, `name` TEXT NOT NULL, `nameKey` TEXT NOT NULL, `remarks` TEXT NOT NULL, `active` INTEGER NOT NULL, `registeredOn` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_students_code` ON `students` (`code`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_students_enrollNo` ON `students` (`enrollNo`)",
        "CREATE INDEX IF NOT EXISTS `index_students_nameKey` ON `students` (`nameKey`)",
        "CREATE TABLE IF NOT EXISTS `enrollments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `studentId` INTEGER NOT NULL, `className` TEXT NOT NULL, `academicYear` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `periodMonths` INTEGER NOT NULL, `studentType` TEXT NOT NULL, `studentStatus` TEXT NOT NULL, `bookType` TEXT NOT NULL, `paymentTerm` TEXT NOT NULL, `discountBp` INTEGER NOT NULL, `remarks` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdOn` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `trainingFee` INTEGER NOT NULL, `instrumentFee` INTEGER NOT NULL, `bookFee` INTEGER NOT NULL, `hostelFee` INTEGER NOT NULL, `laundryFee` INTEGER NOT NULL, `entranceFee` INTEGER NOT NULL, `grossTotal` INTEGER NOT NULL, `discountAmount` INTEGER NOT NULL, `netTotal` INTEGER NOT NULL, `assessedAt` INTEGER NOT NULL, FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_enrollments_studentId_className_academicYear` ON `enrollments` (`studentId`, `className`, `academicYear`)",
        "CREATE INDEX IF NOT EXISTS `index_enrollments_className` ON `enrollments` (`className`)",
        "CREATE TABLE IF NOT EXISTS `payment_schedule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `enrollmentId` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `label` TEXT NOT NULL, `dueDate` INTEGER NOT NULL, `amount` INTEGER NOT NULL, FOREIGN KEY(`enrollmentId`) REFERENCES `enrollments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_schedule_enrollmentId_seq` ON `payment_schedule` (`enrollmentId`, `seq`)",
        "CREATE TABLE IF NOT EXISTS `receipts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `receiptNo` TEXT NOT NULL, `date` INTEGER NOT NULL, `payerName` TEXT NOT NULL, `issuedAt` INTEGER NOT NULL, `previousBalance` INTEGER NOT NULL, `remainingBalance` INTEGER NOT NULL, `receivedBy` TEXT NOT NULL, `remarks` TEXT NOT NULL, `printCount` INTEGER NOT NULL, `voided` INTEGER NOT NULL, `voidReason` TEXT NOT NULL, `voidedBy` TEXT NOT NULL, `imported` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_receipts_receiptNo` ON `receipts` (`receiptNo`)",
        "CREATE INDEX IF NOT EXISTS `index_receipts_date` ON `receipts` (`date`)",
        "CREATE TABLE IF NOT EXISTS `payment_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `receiptId` INTEGER NOT NULL, `studentId` INTEGER NOT NULL, `enrollmentId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `scheduleSeq` INTEGER, `paymentFor` TEXT NOT NULL, `remarks` TEXT NOT NULL, `receivedBy` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `voided` INTEGER NOT NULL, `voidReason` TEXT NOT NULL, `voidedBy` TEXT NOT NULL, `imported` INTEGER NOT NULL, FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`enrollmentId`) REFERENCES `enrollments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        "CREATE INDEX IF NOT EXISTS `index_payment_transactions_receiptId` ON `payment_transactions` (`receiptId`)",
        "CREATE INDEX IF NOT EXISTS `index_payment_transactions_studentId` ON `payment_transactions` (`studentId`)",
        "CREATE INDEX IF NOT EXISTS `index_payment_transactions_enrollmentId` ON `payment_transactions` (`enrollmentId`)",
        "CREATE INDEX IF NOT EXISTS `index_payment_transactions_date` ON `payment_transactions` (`date`)",
    )

    /** The complete version-2 schema exactly as Room creates it (from schemas/…/2.json), incl. Room's identity hash. */
    val CREATE_ALL_V2 = listOf(
        "CREATE TABLE IF NOT EXISTS `fee_rates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `className` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `trainingFee` INTEGER NOT NULL, `bookDayMyanmar` INTEGER NOT NULL, `bookDayIntl` INTEGER NOT NULL, `bookHostelMyanmar` INTEGER NOT NULL, `bookHostelIntl` INTEGER NOT NULL, `hostelFee` INTEGER NOT NULL, `startMonth` INTEGER NOT NULL, `startDay` INTEGER NOT NULL, `periodMonths` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_fee_rates_className` ON `fee_rates` (`className`)",
        "CREATE TABLE IF NOT EXISTS `school_settings` (`id` INTEGER NOT NULL, `schoolName` TEXT NOT NULL, `address` TEXT NOT NULL, `phone` TEXT NOT NULL, `instrumentFee` INTEGER NOT NULL, `laundryPerMonth` INTEGER NOT NULL, `monthsPerYear` INTEGER NOT NULL, `entranceFee` INTEGER NOT NULL, `academicYearStart` INTEGER NOT NULL, `receiptPrefix` TEXT NOT NULL, `nextReceiptSeq` INTEGER NOT NULL, `nextStudentSeq` INTEGER NOT NULL, `ratesUpdatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `students` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `code` TEXT NOT NULL, `enrollNo` TEXT, `name` TEXT NOT NULL, `nameKey` TEXT NOT NULL, `remarks` TEXT NOT NULL, `active` INTEGER NOT NULL, `registeredOn` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_students_code` ON `students` (`code`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_students_enrollNo` ON `students` (`enrollNo`)",
        "CREATE INDEX IF NOT EXISTS `index_students_nameKey` ON `students` (`nameKey`)",
        "CREATE TABLE IF NOT EXISTS `enrollments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `studentId` INTEGER NOT NULL, `className` TEXT NOT NULL, `academicYear` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `periodMonths` INTEGER NOT NULL, `studentType` TEXT NOT NULL, `studentStatus` TEXT NOT NULL, `bookType` TEXT NOT NULL, `paymentTerm` TEXT NOT NULL, `discountBp` INTEGER NOT NULL, `remarks` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdOn` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `trainingFee` INTEGER NOT NULL, `instrumentFee` INTEGER NOT NULL, `bookFee` INTEGER NOT NULL, `hostelFee` INTEGER NOT NULL, `laundryFee` INTEGER NOT NULL, `entranceFee` INTEGER NOT NULL, `grossTotal` INTEGER NOT NULL, `discountAmount` INTEGER NOT NULL, `netTotal` INTEGER NOT NULL, `assessedAt` INTEGER NOT NULL, FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_enrollments_studentId_className_academicYear` ON `enrollments` (`studentId`, `className`, `academicYear`)",
        "CREATE INDEX IF NOT EXISTS `index_enrollments_className` ON `enrollments` (`className`)",
        "CREATE TABLE IF NOT EXISTS `payment_schedule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `enrollmentId` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `label` TEXT NOT NULL, `dueDate` INTEGER NOT NULL, `amount` INTEGER NOT NULL, FOREIGN KEY(`enrollmentId`) REFERENCES `enrollments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_schedule_enrollmentId_seq` ON `payment_schedule` (`enrollmentId`, `seq`)",
        "CREATE TABLE IF NOT EXISTS `receipts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `receiptNo` TEXT NOT NULL, `date` INTEGER NOT NULL, `payerName` TEXT NOT NULL, `issuedAt` INTEGER NOT NULL, `previousBalance` INTEGER NOT NULL, `remainingBalance` INTEGER NOT NULL, `receivedBy` TEXT NOT NULL, `remarks` TEXT NOT NULL, `printCount` INTEGER NOT NULL, `voided` INTEGER NOT NULL, `voidReason` TEXT NOT NULL, `voidedBy` TEXT NOT NULL, `imported` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_receipts_receiptNo` ON `receipts` (`receiptNo`)",
        "CREATE INDEX IF NOT EXISTS `index_receipts_date` ON `receipts` (`date`)",
        "CREATE TABLE IF NOT EXISTS `payment_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `receiptId` INTEGER NOT NULL, `studentId` INTEGER NOT NULL, `enrollmentId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `scheduleSeq` INTEGER, `paymentFor` TEXT NOT NULL, `remarks` TEXT NOT NULL, `receivedBy` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `voided` INTEGER NOT NULL, `voidReason` TEXT NOT NULL, `voidedBy` TEXT NOT NULL, `imported` INTEGER NOT NULL, FOREIGN KEY(`receiptId`) REFERENCES `receipts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`enrollmentId`) REFERENCES `enrollments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        "CREATE INDEX IF NOT EXISTS `index_payment_transactions_receiptId` ON `payment_transactions` (`receiptId`)",
        "CREATE INDEX IF NOT EXISTS `index_payment_transactions_studentId` ON `payment_transactions` (`studentId`)",
        "CREATE INDEX IF NOT EXISTS `index_payment_transactions_enrollmentId` ON `payment_transactions` (`enrollmentId`)",
        "CREATE INDEX IF NOT EXISTS `index_payment_transactions_date` ON `payment_transactions` (`date`)",
        "CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `username` TEXT NOT NULL, `fullName` TEXT NOT NULL, `role` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, `salt` TEXT NOT NULL, `active` INTEGER NOT NULL, `mustChangePassword` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)",
        "CREATE TABLE IF NOT EXISTS `audit_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `at` INTEGER NOT NULL, `username` TEXT NOT NULL, `action` TEXT NOT NULL, `details` TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_audit_log_at` ON `audit_log` (`at`)",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'da114bc870d258254a605b0a8f12084a')",
    )

    const val VERSION = 2
}
