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
    /** Default academic-year start (month, day) and period (months) for enrollments in this class. */
    val startMonth: Int = 6,
    val startDay: Int = 1,
    val periodMonths: Int = 12,
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

/** A person. Class, fees and schedule are per [EnrollmentEntity]. */
@Entity(
    tableName = "students",
    indices = [Index(value = ["code"], unique = true), Index(value = ["enrollNo"], unique = true), Index(value = ["nameKey"])],
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** automatic Student ID, STU-0001 */
    val code: String,
    /** Enroll number entered by the school; NULL when not given (unique when given) */
    val enrollNo: String?,
    val name: String,
    /** lower-cased trimmed name, used for duplicate checks */
    val nameKey: String,
    val remarks: String,
    val active: Boolean = true,
    val registeredOn: Long,
    val updatedAt: Long,
)

/**
 * One class a student attends in one academic year, with its own start date and period.
 * The fee columns are the APPROVED assessment snapshot – not changed when fee rates change.
 */
@Entity(
    tableName = "enrollments",
    foreignKeys = [ForeignKey(entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["studentId", "className", "academicYear"], unique = true), Index(value = ["className"])],
)
data class EnrollmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val className: String,
    val academicYear: String,
    val startDate: Long,
    val periodMonths: Int,
    val studentType: String,
    val studentStatus: String,
    val bookType: String,
    val paymentTerm: String,
    val discountBp: Int,
    val remarks: String,
    val active: Boolean = true,
    val createdOn: Long,
    val updatedAt: Long,
    val trainingFee: Long,
    val instrumentFee: Long,
    val bookFee: Long,
    val hostelFee: Long,
    val laundryFee: Long,
    val entranceFee: Long,
    val grossTotal: Long,
    val discountAmount: Long,
    val netTotal: Long,
    val assessedAt: Long,
)

@Entity(
    tableName = "payment_schedule",
    foreignKeys = [ForeignKey(entity = EnrollmentEntity::class, parentColumns = ["id"], childColumns = ["enrollmentId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["enrollmentId", "seq"], unique = true)],
)
data class PaymentScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val enrollmentId: Long,
    val seq: Int,
    val label: String,
    val dueDate: Long,
    val amount: Long,
)

/** A receipt. It holds one or more [PaymentTransactionEntity] lines (several = combined receipt). */
@Entity(
    tableName = "receipts",
    indices = [Index(value = ["receiptNo"], unique = true), Index(value = ["date"])],
)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNo: String,
    val date: Long,
    /** student name(s) printed on the receipt */
    val payerName: String,
    val issuedAt: Long,
    /** sum of the balances of the students on this receipt, before / after it */
    val previousBalance: Long,
    val remainingBalance: Long,
    val receivedBy: String,
    val remarks: String,
    val printCount: Int = 0,
    val voided: Boolean = false,
    val voidReason: String = "",
    val voidedBy: String = "",
    val imported: Boolean = false,
)

/** Cash collection register line. Rows are never deleted; a mistaken receipt is voided by an admin. */
@Entity(
    tableName = "payment_transactions",
    foreignKeys = [
        ForeignKey(entity = ReceiptEntity::class, parentColumns = ["id"], childColumns = ["receiptId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = EnrollmentEntity::class, parentColumns = ["id"], childColumns = ["enrollmentId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["receiptId"]), Index(value = ["studentId"]), Index(value = ["enrollmentId"]), Index(value = ["date"])],
)
data class PaymentTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val studentId: Long,
    val enrollmentId: Long,
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
