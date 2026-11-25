package com.school.feemanager.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

/**
 * Room migration; the SQL itself is in [MigrationSql]. Version 1 → 2: one class per student becomes many enrollments per student, and one receipt can
 * hold several payment lines (combined receipts).
 *
 *  - every v1 student keeps its id; its class/fee data becomes enrollment #id (same id), using the
 *    school's academic-year start and months/year as that enrollment's start date and period;
 *  - schedules move from the student to that enrollment;
 *  - each v1 receipt (1 transaction) becomes a receipt with one line;
 *  - fee_rates gets a default start month/day and period per class.
 *
 * No amount, date, receipt number or payment is changed. The SQL ([MigrationSql]) is pure
 * Kotlin and unit-tested against a real SQLite database (MigrationTest).
 */
object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            var startDay = LocalDate.of(LocalDate.now().year, 6, 1).toEpochDay()
            var months = 12
            db.query("SELECT academicYearStart, monthsPerYear FROM school_settings WHERE id = 1").use { c ->
                if (c.moveToFirst()) { startDay = c.getLong(0); months = c.getInt(1) }
            }
            MigrationSql.statements(startDay, months).forEach { s -> if (s.args.isEmpty()) db.execSQL(s.sql) else db.execSQL(s.sql, s.args) }
        }
    }

    /** Version 2 → 3 (app 1.3): per-class "Fees for all students" flag and fee options; custom payment terms and fee choices per enrollment. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MigrationSql.STATEMENTS_2_3.forEach { db.execSQL(it) }
        }
    }
}
