package com.school.feemanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM school_settings WHERE id = 1")
    fun observe(): Flow<SchoolSettingsEntity?>

    @Query("SELECT * FROM school_settings WHERE id = 1")
    suspend fun get(): SchoolSettingsEntity?

    @Upsert
    suspend fun upsert(s: SchoolSettingsEntity)
}

@Dao
interface FeeRateDao {
    @Query("SELECT * FROM fee_rates ORDER BY sortOrder")
    fun observeAll(): Flow<List<FeeRateEntity>>

    @Query("SELECT * FROM fee_rates ORDER BY sortOrder")
    suspend fun getAll(): List<FeeRateEntity>

    @Query("DELETE FROM fee_rates")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(list: List<FeeRateEntity>)
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students")
    suspend fun getAll(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun get(id: Long): StudentEntity?

    @Query("SELECT * FROM students WHERE nameKey = :nameKey AND className = :className AND id != :excludeId LIMIT 1")
    suspend fun findDuplicate(nameKey: String, className: String, excludeId: Long): StudentEntity?

    @Query("SELECT COUNT(*) FROM students WHERE code = :code")
    suspend fun countCode(code: String): Int

    @Insert
    suspend fun insert(s: StudentEntity): Long

    @Update
    suspend fun update(s: StudentEntity)
}

@Dao
interface StudentFeeDao {
    @Query("SELECT * FROM student_fees")
    fun observeAll(): Flow<List<StudentFeeEntity>>

    @Query("SELECT * FROM student_fees WHERE studentId = :id")
    suspend fun get(id: Long): StudentFeeEntity?

    @Upsert
    suspend fun upsert(f: StudentFeeEntity)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM payment_schedule ORDER BY studentId, seq")
    fun observeAll(): Flow<List<PaymentScheduleEntity>>

    @Query("SELECT * FROM payment_schedule WHERE studentId = :id ORDER BY seq")
    suspend fun forStudent(id: Long): List<PaymentScheduleEntity>

    @Query("DELETE FROM payment_schedule WHERE studentId = :id")
    suspend fun deleteForStudent(id: Long)

    @Insert
    suspend fun insertAll(list: List<PaymentScheduleEntity>)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM payment_transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<PaymentTransactionEntity>>

    @Query("SELECT * FROM payment_transactions WHERE studentId = :id AND voided = 0")
    suspend fun validForStudent(id: Long): List<PaymentTransactionEntity>

    @Query("SELECT * FROM payment_transactions WHERE id = :id")
    suspend fun get(id: Long): PaymentTransactionEntity?

    @Insert
    suspend fun insert(t: PaymentTransactionEntity): Long

    @Update
    suspend fun update(t: PaymentTransactionEntity)
}

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM receipts ORDER BY id DESC")
    fun observeAll(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun get(id: Long): ReceiptEntity?

    @Query("SELECT COUNT(*) FROM receipts WHERE receiptNo = :no COLLATE NOCASE")
    suspend fun countNo(no: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(r: ReceiptEntity): Long

    @Query("UPDATE receipts SET printCount = printCount + 1 WHERE id = :id")
    suspend fun incrementPrint(id: Long)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY username")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE username = :u COLLATE NOCASE LIMIT 1")
    suspend fun byUsername(u: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun get(id: Long): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND active = 1")
    suspend fun activeAdminCount(): Int

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Insert
    suspend fun insert(u: UserEntity): Long

    @Update
    suspend fun update(u: UserEntity)
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(a: AuditLogEntity)

    @Query("SELECT * FROM audit_log ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>
}
