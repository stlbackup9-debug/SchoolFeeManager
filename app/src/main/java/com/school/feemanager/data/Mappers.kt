package com.school.feemanager.data

import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.ClassRate
import com.school.feemanager.domain.EnrollmentRecord
import com.school.feemanager.domain.FeeAssessment
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.FeeChoice
import com.school.feemanager.domain.FeeOption
import com.school.feemanager.domain.PaymentRecord
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.ReceiptRecord
import com.school.feemanager.domain.ScheduleLine
import com.school.feemanager.domain.StudentRecord
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import java.time.LocalDate

fun FeeRateEntity.toDomain() = ClassRate(
    className, sortOrder, trainingFee, bookDayMyanmar, bookDayIntl, bookHostelMyanmar, bookHostelIntl, hostelFee,
    startMonth, startDay, periodMonths, commonFees, FeeOption.decode(feeOptions),
)

fun ClassRate.toEntity() = FeeRateEntity(
    className = className, sortOrder = sortOrder, trainingFee = trainingFee, bookDayMyanmar = bookDayMyanmar,
    bookDayIntl = bookDayIntl, bookHostelMyanmar = bookHostelMyanmar, bookHostelIntl = bookHostelIntl, hostelFee = hostelFee,
    startMonth = startMonth, startDay = startDay, periodMonths = periodMonths,
    commonFees = commonFees, feeOptions = FeeOption.encode(options),
)

fun rateCardOf(settings: SchoolSettingsEntity, rates: List<FeeRateEntity>) = FeeRateCard(
    classes = rates.sortedBy { it.sortOrder }.map { it.toDomain() },
    instrumentFee = settings.instrumentFee,
    laundryPerMonth = settings.laundryPerMonth,
    monthsPerYear = settings.monthsPerYear,
    entranceFee = settings.entranceFee,
)

fun StudentEntity.toDomain() = StudentRecord(
    id = id, code = code, enrollNo = enrollNo ?: "", name = name, remarks = remarks, active = active,
    registeredOn = LocalDate.ofEpochDay(registeredOn),
)

fun EnrollmentEntity.toDomain() = EnrollmentRecord(
    id = id, studentId = studentId, className = className, academicYear = academicYear,
    startDate = LocalDate.ofEpochDay(startDate), periodMonths = periodMonths,
    type = StudentType.valueOf(studentType), status = StudentStatus.valueOf(studentStatus),
    bookType = BookType.valueOf(bookType), term = PaymentTerm.valueOf(paymentTerm),
    discountBp = discountBp, remarks = remarks, active = active, createdOn = LocalDate.ofEpochDay(createdOn),
    termParts = termParts, termEvery = termEvery, choice = FeeChoice.decode(feeChoice),
)

fun EnrollmentEntity.fee() = FeeAssessment(trainingFee, instrumentFee, bookFee, hostelFee, laundryFee, entranceFee, discountBp)

/** Writes [fee] into the enrollment's snapshot columns. */
fun EnrollmentEntity.withFee(fee: FeeAssessment, now: Long) = copy(
    trainingFee = fee.trainingFee, instrumentFee = fee.instrumentFee, bookFee = fee.bookFee, hostelFee = fee.hostelFee,
    laundryFee = fee.laundryFee, entranceFee = fee.entranceFee, grossTotal = fee.grossTotal, discountBp = fee.discountBp,
    discountAmount = fee.discountAmount, netTotal = fee.netTotal, assessedAt = now,
)

fun PaymentScheduleEntity.toDomain() = ScheduleLine(seq, label, LocalDate.ofEpochDay(dueDate), amount)

fun ScheduleLine.toEntity(enrollmentId: Long) = PaymentScheduleEntity(enrollmentId = enrollmentId, seq = seq, label = label, dueDate = dueDate.toEpochDay(), amount = amount)

fun PaymentTransactionEntity.toDomain(receiptNo: String) = PaymentRecord(
    id = id, studentId = studentId, date = LocalDate.ofEpochDay(date), amount = amount, scheduleSeq = scheduleSeq,
    paymentFor = paymentFor, receiptNo = receiptNo, remarks = remarks, receivedBy = receivedBy, voided = voided, createdAtMillis = createdAt,
    enrollmentId = enrollmentId, receiptId = receiptId,
)

fun ReceiptEntity.toDomain(items: List<PaymentRecord>) = ReceiptRecord(
    id = id, receiptNo = receiptNo, date = LocalDate.ofEpochDay(date), payerName = payerName, issuedAtMillis = issuedAt,
    previousBalance = previousBalance, remainingBalance = remainingBalance, receivedBy = receivedBy, remarks = remarks,
    printCount = printCount, voided = voided, voidReason = voidReason, items = items,
)
