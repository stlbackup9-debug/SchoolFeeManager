package com.school.feemanager.io

import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.ClassRate
import com.school.feemanager.domain.FeeAssessment
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import java.time.LocalDate

data class ImportIssue(val sheet: String, val row: Int, val message: String, val isError: Boolean) {
    override fun toString(): String = "${if (isError) "ERROR" else "Warning"} – $sheet${if (row > 0) " row $row" else ""}: $message"
}

/** How an import row identifies a student: Enroll No., then Student ID, then name (+ class). */
data class StudentRef(val enrollNo: String?, val code: String?, val name: String, val className: String? = null) {
    val display: String get() = listOfNotNull(name.ifBlank { null }, enrollNo?.let { "Enroll No. $it" }, code).joinToString(" / ")
}

/** Class data of an enrollment. Start date / period / year default to the class settings when null. */
data class ImportedEnrollment(
    val className: String,
    val academicYear: String?,
    val startDate: LocalDate?,
    val periodMonths: Int?,
    val type: StudentType,
    val status: StudentStatus,
    val bookType: BookType,
    val discountBp: Int,
    val term: PaymentTerm,
    val remarks: String,
    /** The approved fee amounts, when the file was exported by this app (kept instead of recalculating). */
    val approvedFee: FeeAssessment? = null,
)

/** One class row of a Fee rates import. Null = the cell was blank: keep the current value (0 / default for a new class). */
data class ClassRateUpdate(
    val className: String,
    val trainingFee: Long?,
    val bookDayMyanmar: Long?,
    val bookDayIntl: Long?,
    val bookHostelMyanmar: Long?,
    val bookHostelIntl: Long?,
    val hostelFee: Long?,
    val startMonth: Int?,
    val startDay: Int?,
    val periodMonths: Int?,
) {
    fun applyTo(old: ClassRate?, sortOrder: Int, defaultPeriod: Int): ClassRate = ClassRate(
        className = old?.className ?: className,
        sortOrder = old?.sortOrder ?: sortOrder,
        trainingFee = trainingFee ?: old?.trainingFee ?: 0,
        bookDayMyanmar = bookDayMyanmar ?: old?.bookDayMyanmar ?: 0,
        bookDayIntl = bookDayIntl ?: old?.bookDayIntl ?: 0,
        bookHostelMyanmar = bookHostelMyanmar ?: old?.bookHostelMyanmar ?: 0,
        bookHostelIntl = bookHostelIntl ?: old?.bookHostelIntl ?: 0,
        hostelFee = hostelFee ?: old?.hostelFee ?: 0,
        startMonth = startMonth ?: old?.startMonth ?: 6,
        startDay = startDay ?: old?.startDay ?: 1,
        periodMonths = periodMonths ?: old?.periodMonths ?: defaultPeriod,
    )
}

/** A student row (from Student_Register or a Students import). [enrollment] is optional for module imports. */
data class ImportedStudent(
    val sheet: String,
    val row: Int,
    val code: String?,
    val enrollNo: String?,
    val name: String,
    val remarks: String,
    val enrollment: ImportedEnrollment?,
) {
    // Short-hands kept for readability in tests and the importer.
    val className: String get() = enrollment?.className ?: ""
    val type: StudentType get() = enrollment?.type ?: StudentType.DAY
    val status: StudentStatus get() = enrollment?.status ?: StudentStatus.OLD
    val bookType: BookType get() = enrollment?.bookType ?: BookType.INTERNATIONAL
    val discountBp: Int get() = enrollment?.discountBp ?: 0
    val term: PaymentTerm get() = enrollment?.term ?: PaymentTerm.THREE_INST
}

/** An enrollment for a student who must already exist (or be imported in the same file). */
data class ImportedEnrollmentRow(val sheet: String, val row: Int, val ref: StudentRef, val enrollment: ImportedEnrollment)

data class ImportedPayment(
    val sheet: String,
    val row: Int,
    val ref: StudentRef,
    val academicYear: String?,
    val date: LocalDate,
    val amount: Long,
    /** rows with the same receipt number become ONE (combined) receipt */
    val receiptNo: String,
    val paymentFor: String,
    val remarks: String,
    val receivedBy: String,
) {
    val studentName: String get() = ref.name
    val className: String get() = ref.className ?: ""
    val studentCode: String? get() = ref.code
}

/** Everything one import file contains, ready to be applied by the repository. */
data class ImportBundle(
    val title: String,
    val sheetsFound: List<String>,
    /** full rate card from a workbook (replaces the current one when the user agrees) */
    val rates: FeeRateCard?,
    /** class rows to add or update (module import) */
    val classRates: List<ClassRateUpdate>,
    val students: List<ImportedStudent>,
    val enrollments: List<ImportedEnrollmentRow>,
    val payments: List<ImportedPayment>,
    val issues: List<ImportIssue>,
) {
    val errors: List<ImportIssue> get() = issues.filter { it.isError }
    val warnings: List<ImportIssue> get() = issues.filter { !it.isError }
    val isEmpty: Boolean get() = rates == null && classRates.isEmpty() && students.isEmpty() && enrollments.isEmpty() && payments.isEmpty()
}
