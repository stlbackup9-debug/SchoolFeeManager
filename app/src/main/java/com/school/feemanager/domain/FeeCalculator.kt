package com.school.feemanager.domain

/**
 * Re-implementation of Student_Register columns G–P.
 *
 *   G Training   = VLOOKUP(class, Fee_Rates training table)
 *   H Instrument = fixed instrument fee (all students)            [Excel cell ref fixed: G7 -> G6]
 *   I Book       = class × (Day|Hostel) × (Myanmar|International)
 *   J Hostel     = hostel fee by class, Hostel students only       [Excel range fixed: A23:B35 -> A22:B34, so KG is found]
 *   K Laundry    = laundry/month × months (the enrollment's period, default months/year), Hostel only
 *   L Entrance   = entrance fee, New students only
 *   M Gross      = G+H+I+J+K+L
 *   O Discount   = M × N
 *   P Net        = M − O
 *
 * Both corrections were approved by the user on 22 Nov 2026.
 *
 * Version 1.3: a class can be marked as NOT taking the "Fees for all students" (instrument and
 * entrance fee are then 0), and a student can choose a training option / extra book options of the
 * class instead of (or in addition to) the standard fees – see [FeeChoice].
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
        periodMonths: Int = card.monthsPerYear,
        choice: FeeChoice = FeeChoice.DEFAULT,
    ): FeeAssessment {
        if (className.isBlank()) return FeeAssessment.ZERO.copy(discountBp = discountBp)
        val r = card.rateFor(className)
        val training = when {
            r == null -> 0
            choice.training == FeeChoice.STANDARD -> r.trainingFee
            choice.training == FeeChoice.NONE -> 0
            else -> r.option(FeeKind.TRAINING, choice.training)?.amount ?: r.trainingFee
        }
        val book = if (r == null) 0 else
            (if (choice.standardBook) r.standardBook(type, bookType) else 0) + choice.books.sumOf { r.option(FeeKind.BOOK, it)?.amount ?: 0 }
        val common = r?.commonFees ?: true
        val hostel = if (type == StudentType.HOSTEL) r?.hostelFee ?: 0 else 0
        val laundry = if (type == StudentType.HOSTEL) card.laundryPerMonth * periodMonths else 0
        val entrance = if (status == StudentStatus.NEW && common) card.entranceFee else 0
        return FeeAssessment(
            trainingFee = training,
            instrumentFee = if (common) card.instrumentFee else 0,
            bookFee = book,
            hostelFee = hostel,
            laundryFee = laundry,
            entranceFee = entrance,
            discountBp = discountBp.coerceIn(0, 10000),
        )
    }

    /** Choice names that the class does not (or no longer) offer. */
    fun unknownChoices(card: FeeRateCard, className: String, choice: FeeChoice): List<String> {
        val r = card.rateFor(className) ?: return emptyList()
        val t = choice.training.takeIf { it != FeeChoice.STANDARD && it != FeeChoice.NONE && r.option(FeeKind.TRAINING, it) == null }
        return listOfNotNull(t?.let { "training option '$it'" }) + choice.books.filter { r.option(FeeKind.BOOK, it) == null }.map { "book option '$it'" }
    }

    fun assess(card: FeeRateCard, e: EnrollmentRecord): FeeAssessment =
        assess(card, e.className, e.type, e.status, e.bookType, e.discountBp, e.periodMonths, e.choice)
}
