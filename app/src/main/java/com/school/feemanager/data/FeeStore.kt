package com.school.feemanager.data

import java.io.InputStream
import java.io.OutputStream

/**
 * The database as the repository sees it: the DAOs, transactions and backup/restore.
 * Android: [RoomFeeStore] (Room). Windows desktop: JdbcFeeStore (SQLite through JDBC).
 * Both use the same SQLite schema, so a backup file can move between phone and PC.
 */
interface FeeStore {
    fun settingsDao(): SettingsDao
    fun feeRateDao(): FeeRateDao
    fun studentDao(): StudentDao
    fun enrollmentDao(): EnrollmentDao
    fun scheduleDao(): ScheduleDao
    fun transactionDao(): TransactionDao
    fun receiptDao(): ReceiptDao
    fun userDao(): UserDao
    fun auditDao(): AuditDao

    /** Runs [block] in one database transaction; an exception rolls everything back. */
    suspend fun <R> transaction(block: suspend () -> R): R

    /** Writes a consistent single-file copy of the database. */
    fun backupTo(out: OutputStream)

    /** Validates a backup and replaces the database with it. The app must be restarted afterwards. */
    fun restoreFrom(input: InputStream)
}
