package com.school.feemanager.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Android implementation of [FeeStore] on Room. */
class RoomFeeStore(private val context: Context, private val db: AppDatabase) : FeeStore {
    override fun settingsDao() = db.settingsDao()
    override fun feeRateDao() = db.feeRateDao()
    override fun studentDao() = db.studentDao()
    override fun enrollmentDao() = db.enrollmentDao()
    override fun scheduleDao() = db.scheduleDao()
    override fun transactionDao() = db.transactionDao()
    override fun receiptDao() = db.receiptDao()
    override fun userDao() = db.userDao()
    override fun auditDao() = db.auditDao()

    override suspend fun <R> transaction(block: suspend () -> R): R = db.withTransaction { block() }

    override fun backupTo(out: OutputStream) {
        val file = context.getDatabasePath(AppDatabase.NAME)
        // An exclusive transaction blocks writers while the single database file is copied.
        db.runInTransaction(Runnable { file.inputStream().use { it.copyTo(out) } })
        out.flush()
    }

    override fun restoreFrom(input: InputStream) {
        val tmp = File(context.cacheDir, "restore_candidate.db")
        input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        val header = tmp.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
        if (String(header, Charsets.US_ASCII) != "SQLite format 3\u0000") { tmp.delete(); throw FeeException("This file is not a School Fee Manager backup") }
        try {
            SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { check ->
                check.rawQuery("SELECT COUNT(*) FROM students", null).use { it.moveToFirst() }
                check.rawQuery("SELECT COUNT(*) FROM payment_transactions", null).use { it.moveToFirst() }
                check.rawQuery("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", null).use { c -> c.moveToFirst(); if (c.getInt(0) == 0) throw FeeException("Backup has no Admin user") }
            }
        } catch (e: FeeException) {
            tmp.delete(); throw e
        } catch (e: Exception) {
            tmp.delete(); throw FeeException("Backup file is damaged or from another app: ${e.message}")
        }
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        // Safety copy of the current data before overwriting.
        File(context.filesDir, "before_restore_${System.currentTimeMillis()}.db").let { safety -> dbFile.copyTo(safety, overwrite = true) }
        db.close()
        listOf("-journal", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
        tmp.copyTo(dbFile, overwrite = true)
        tmp.delete()
    }
}
