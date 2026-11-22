package com.school.feemanager.domain

/**
 * Re-implementation of Student_Register columns G–P.
 *
 *   G Training   = VLOOKUP(class, Fee_Rates training table)
 *   H Instrument = fixed instrument fee (all students)            [Excel cell ref fixed: G7 -> G6]
 *   I Book       = class × (Day|Hostel) × (Myanmar|International)
 *   J Hostel     = hostel fee by class, Hostel students only       [Excel range fixed: A23:B35 -> A22:B34, so KG is found]
 *   K Laundry    = laundry/month × months/year, Hostel only
 *   L Entrance   = entrance fee, New students only
 *   M Gross      = G+H+I+J+K+L
 *   O Discount   = M × N
 *   P Net        = M − O
 *
 * Both corrections were approved by the user on 22 Nov 2026.
 * An unknown class gives 0 for class-based fees (Excel IFERROR -> "") and is reported by [isClassKnown].
 */
object FeeCalculator {

    fun isClassKnown(card: FeeRateCard, className: String): Boolean = card.rateFor(className) != null

    fun assess(
        card: FeeRateCard,
        className: String,
        type: StudentType,
        status: StudentStatus,
        bookType: BookType,
        discountBp: Int,
    ): FeeAssessment {
        if (className.isBlank()) return FeeAssessment.ZERO.copy(discountBp = discountBp)
        val r = card.rateFor(className)
        val training = r?.trainingFee ?: 0
        val book = when {
            r == null -> 0
            type == StudentType.DAY && bookType == BookType.MYANMAR -> r.bookDayMyanmar
            type == StudentType.DAY -> r.bookDayIntl
            bookType == BookType.MYANMAR -> r.bookHostelMyanmar
            else -> r.bookHostelIntl
        }
        val hostel = if (type == StudentType.HOSTEL) r?.hostelFee ?: 0 else 0
        val laundry = if (type == StudentType.HOSTEL) card.laundryPerMonth * card.monthsPerYear else 0
        val entrance = if (status == StudentStatus.NEW) card.entranceFee else 0
        return FeeAssessment(
            trainingFee = training,
            instrumentFee = card.instrumentFee,
            bookFee = book,
            hostelFee = hostel,
            laundryFee = laundry,
            entranceFee = entrance,
            discountBp = discountBp.coerceIn(0, 10000),
        )
    }

    fun assess(card: FeeRateCard, s: StudentRecord): FeeAssessment =
        assess(card, s.className, s.type, s.status, s.bookType, s.discountBp)
}
