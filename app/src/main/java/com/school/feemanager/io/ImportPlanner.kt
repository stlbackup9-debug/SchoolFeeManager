package com.school.feemanager.io

import java.util.Locale

/** A student that a payment row can be matched to (already in the DB, or being imported). */
data class MatchCandidate(val key: Long, val code: String, val name: String, val className: String)

sealed class MatchResult {
    data class Found(val candidate: MatchCandidate) : MatchResult()
    data class Problem(val message: String) : MatchResult()
}

object ImportPlanner {
    /**
     * Excel's Paid Amount column is SUMIF(Cash_Collection!B:B, name, …), i.e. matched by name.
     * The app matches by Student ID when present, then name + class, then a unique name.
     */
    fun match(p: ImportedPayment, candidates: List<MatchCandidate>): MatchResult {
        p.studentCode?.let { code ->
            candidates.firstOrNull { it.code.equals(code, true) }?.let { return MatchResult.Found(it) }
        }
        val byName = candidates.filter { it.name.trim().lowercase(Locale.US) == p.studentName.trim().lowercase(Locale.US) }
        if (byName.isEmpty()) return MatchResult.Problem("${p.studentName}: no student with this name in Student_Register or the app")
        if (byName.size == 1) return MatchResult.Found(byName[0])
        val byClass = byName.filter { it.className.equals(p.className, true) }
        return if (byClass.size == 1) MatchResult.Found(byClass[0])
        else MatchResult.Problem("${p.studentName}: ${byName.size} students share this name – add the class to identify the student")
    }

    fun studentKey(name: String, className: String): String = name.trim().lowercase(Locale.US) + "|" + className.trim().uppercase(Locale.US)
}
