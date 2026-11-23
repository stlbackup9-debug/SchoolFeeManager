package com.school.feemanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FeeRateEntity::class, SchoolSettingsEntity::class, StudentEntity::class, EnrollmentEntity::class,
        PaymentScheduleEntity::class, ReceiptEntity::class, PaymentTransactionEntity::class,
        UserEntity::class, AuditLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun feeRateDao(): FeeRateDao
    abstract fun studentDao(): StudentDao
    abstract fun enrollmentDao(): EnrollmentDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun transactionDao(): TransactionDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun userDao(): UserDao
    abstract fun auditDao(): AuditDao

    companion object {
        const val NAME = "school_fees.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // TRUNCATE journal keeps the whole database in ONE file, so backup/restore is a plain file copy.
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(Migrations.MIGRATION_1_2)
                .build()
    }
}
