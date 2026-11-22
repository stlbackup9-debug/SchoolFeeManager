package com.school.feemanager.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Money {
    private val symbols = DecimalFormatSymbols(Locale.US)

    /** 1234567 -> "1,234,567" */
    fun format(amount: Long): String = DecimalFormat("#,##0", symbols).format(amount)

    /** 1234567 -> "1,234,567 MMK" */
    fun mmk(amount: Long): String = format(amount) + " MMK"

    /** Parses user input such as "1,200,000" or "1200000 MMK". Returns null when invalid. */
    fun parse(text: String?): Long? {
        val t = text?.replace(",", "")?.replace("MMK", "", ignoreCase = true)?.trim() ?: return null
        if (t.isEmpty()) return null
        return t.toLongOrNull() ?: t.toBigDecimalOrNull()?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()
    }

    /** Excel ROUND(x,0) for a positive/negative ratio a/b – half away from zero. */
    fun roundDiv(a: Long, b: Long): Long {
        require(b > 0)
        val q = a / b
        val r = a % b
        return if (r * 2 >= b) q + 1 else if (-r * 2 >= b) q - 1 else q
    }

    /** amount × bp / 10000, rounded to the nearest kyat. */
    fun percentOf(amount: Long, basisPoints: Int): Long = roundDiv(amount * basisPoints, 10000)

    /** "10.5" -> 1050 bp. Returns null if invalid or outside 0..100 %. */
    fun parsePercentToBp(text: String?): Int? {
        val t = text?.replace("%", "")?.trim() ?: return null
        if (t.isEmpty()) return 0
        val bd = t.toBigDecimalOrNull() ?: return null
        val bp = bd.multiply(java.math.BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_UP).toInt()
        return if (bp in 0..10000) bp else null
    }

    /** Excel stores 10 % as 0.1 – convert a fraction to bp. */
    fun fractionToBp(fraction: Double): Int =
        java.math.BigDecimal(fraction.toString()).multiply(java.math.BigDecimal(10000))
            .setScale(0, java.math.RoundingMode.HALF_UP).toInt()

    /** 1050 -> "10.5%" */
    fun bpToPercentText(bp: Int): String {
        val whole = bp / 100
        val frac = bp % 100
        return if (frac == 0) "$whole%" else "$whole.${frac.toString().padStart(2, '0').trimEnd('0')}%"
    }

    /** 1050 -> "10.5" (for edit fields) */
    fun bpToPlain(bp: Int): String = bpToPercentText(bp).removeSuffix("%")
}
