package com.school.feemanager.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room schema. Amounts are INTEGER kyat; dates are epoch days (LocalDate.toEpochDay()).
 * Enum columns store the enum constant name.
 */

/** Fee_Rates per-class rows (training, book matrix, hostel). */
@Entity(tableName = "fee_rates", indices = [Index(value = ["className"], unique = true)])
data class FeeRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val className: String,
    val sortOrder: Int,
    val trainingFee: Long,
    val bookDayMyanmar: Long,
    val bookDayIntl: Long,
    val bookHostelMyanmar: Long,
    val bookHostelIntl: Long,
    val hostelFee: Long,
)

/** Single-row table: school profile, global fee rates and number sequences. */
@Entity(tableName = "school_settings")
data class SchoolSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val schoolName: String = "My School",
    val address: String = "",
    val phone: String = "",
    val instrumentFee: Long = 200000,
    val laundryPerMonth: Long = 70000,
    val monthsPerYear: Int = 12,
    val entranceFee: Long = 100000,
    val academicYearStart: Long,
    val receiptPrefix: String = "RCP",
    val nextReceiptSeq: Long = 1,
    val nextStudentSeq: Long = 1,
    val ratesUpdatedAt: Long = 0,
)

@Entity(
    tableName = "students",
    indices = [Index(value = ["code"], unique = true), Index(value = ["className"]), Index(value = ["nameKey", "className"])],
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    /** lower-cased trimmed name, used for duplicate checks */
    val nameKey: String,
    val className: String,
    val studentType: String,
    val studentStatus: String,
    val bookType: String,
    val paymentTerm: String,
    val discountBp: Int,
    val remarks: String,
    val active: Boolean = true,
    val registeredOn: Long,
    val updatedAt: Long,
)

/** The approved fee assessment (one per student). Not changed when fee rates change. */
@Entity(
    tableName = "student_fees",
    foreignKeys = [ForeignKey(entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.RESTRICT)],
)
data class StudentFeeEntity(
    @PrimaryKey val studentId: Long,
    val trainingFee: Long,
    val instrumentFee: Long,
    val bookFee: Long,
    val hostelFee: Long,
    val laundryFee: Long,
    val entranceFee: Long,
    val grossTotal: Long,
    val discountBp: Int,
    val discountAmount: Long,
    val netTotal: Long,
    val assessedAt: Long,
)

@Entity(
    tableName = "payment_schedule",
    foreignKeys = [ForeignKey(entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["studentId", "seq"], unique = true)],
)
data class PaymentScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val seq: Int,
    val label: String,
    val dueDate: Long,
    val amount: Long,
)

/** Cash collection register. Rows are never deleted; a mistaken entry is voided by an admin. */
@Entity(
    tableName = "payment_transactions",
    foreignKeys = [ForeignKey(entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["studentId"]), Index(value = ["date"])],
)
data class PaymentTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val date: Long,
    val amount: Long,
    val scheduleSeq: Int?,
    val paymentFor: String,
    val remarks: String,
    val receivedBy: String,
    val createdAt: Long,
    val voided: Boolean = false,
    val voidReason: String = "",
    val voidedBy: String = "",
    val imported: Boolean = false,
)

@Entity(
    tableName = "receipts",
    foreignKeys = [ForeignKey(entity = PaymentTransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["receiptNo"], unique = true), Index(value = ["transactionId"], unique = true)],
)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNo: String,
    val transactionId: Long,
    val issuedAt: Long,
    val previousBalance: Long,
    val remainingBalance: Long,
    val receivedBy: String,
    val printCount: Int = 0,
)

@Entity(tableName = "users", indices = [Index(value = ["username"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val fullName: String,
    val role: String,
    val passwordHash: String,
    val salt: String,
    val active: Boolean = true,
    val mustChangePassword: Boolean = false,
)

/** Audit trail for financial and master-data changes. */
@Entity(tableName = "audit_log", indices = [Index(value = ["at"])])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val username: String,
    val action: String,
    val details: String,
)
