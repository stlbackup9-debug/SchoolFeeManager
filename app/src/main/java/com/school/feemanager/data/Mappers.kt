package com.school.feemanager.data

import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.ClassRate
import com.school.feemanager.domain.FeeAssessment
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.PaymentRecord
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.ScheduleLine
import com.school.feemanager.domain.StudentRecord
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import java.time.LocalDate

fun FeeRateEntity.toDomain() = ClassRate(className, sortOrder, trainingFee, bookDayMyanmar, bookDayIntl, bookHostelMyanmar, bookHostelIntl, hostelFee)

fun ClassRate.toEntity() = FeeRateEntity(
    className = className, sortOrder = sortOrder, trainingFee = trainingFee, bookDayMyanmar = bookDayMyanmar,
    bookDayIntl = bookDayIntl, bookHostelMyanmar = bookHostelMyanmar, bookHostelIntl = bookHostelIntl, hostelFee = hostelFee,
)

fun rateCardOf(settings: SchoolSettingsEntity, rates: List<FeeRateEntity>) = FeeRateCard(
    classes = rates.sortedBy { it.sortOrder }.map { it.toDomain() },
    instrumentFee = settings.instrumentFee,
    laundryPerMonth = settings.laundryPerMonth,
    monthsPerYear = settings.monthsPerYear,
    entranceFee = settings.entranceFee,
)

fun StudentEntity.toDomain() = StudentRecord(
    id = id, code = code, name = name, className = className,
    type = StudentType.valueOf(studentType), status = StudentStatus.valueOf(studentStatus),
    bookType = BookType.valueOf(bookType), term = PaymentTerm.valueOf(paymentTerm),
    discountBp = discountBp, remarks = remarks, active = active, registeredOn = LocalDate.ofEpochDay(registeredOn),
)

fun StudentFeeEntity.toDomain() = FeeAssessment(trainingFee, instrumentFee, bookFee, hostelFee, laundryFee, entranceFee, discountBp)

fun FeeAssessment.toEntity(studentId: Long, now: Long) = StudentFeeEntity(
    studentId = studentId, trainingFee = trainingFee, instrumentFee = instrumentFee, bookFee = bookFee,
    hostelFee = hostelFee, laundryFee = laundryFee, entranceFee = entranceFee, grossTotal = grossTotal,
    discountBp = discountBp, discountAmount = discountAmount, netTotal = netTotal, assessedAt = now,
)

fun PaymentScheduleEntity.toDomain() = ScheduleLine(seq, label, LocalDate.ofEpochDay(dueDate), amount)

fun ScheduleLine.toEntity(studentId: Long) = PaymentScheduleEntity(studentId = studentId, seq = seq, label = label, dueDate = dueDate.toEpochDay(), amount = amount)

fun PaymentTransactionEntity.toDomain(receiptNo: String) = PaymentRecord(
    id = id, studentId = studentId, date = LocalDate.ofEpochDay(date), amount = amount, scheduleSeq = scheduleSeq,
    paymentFor = paymentFor, receiptNo = receiptNo, remarks = remarks, receivedBy = receivedBy, voided = voided, createdAtMillis = createdAt,
)
