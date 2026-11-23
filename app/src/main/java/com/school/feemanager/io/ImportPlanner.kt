package com.school.feemanager.io

import java.util.Locale

/** A student that an import row can be matched to (already in the DB, or being imported). */
data class MatchCandidate(val key: Long, val code: String, val enrollNo: String, val name: String, val classNames: Set<String>) {
    constructor(key: Long, code: String, name: String, className: String) : this(key, code, "", name, setOf(className))
}

sealed class MatchResult {
    data class Found(val candidate: MatchCandidate) : MatchResult()
    data class Problem(val message: String) : MatchResult()
}

object ImportPlanner {
    /**
     * Excel's Paid Amount column is SUMIF(Cash_Collection!B:B, name, …), i.e. matched by name.
     * The app matches by Enroll No., then Student ID, then name + class, then a unique name.
     */
    fun match(ref: StudentRef, candidates: List<MatchCandidate>): MatchResult {
        ref.enrollNo?.let { en ->
            candidates.firstOrNull { it.enrollNo.isNotEmpty() && it.enrollNo.equals(en, true) }?.let { return MatchResult.Found(it) }
            if (ref.code == null && ref.name.isBlank()) return MatchResult.Problem("no student with Enroll No. $en")
        }
        ref.code?.let { code ->
            candidates.firstOrNull { it.code.equals(code, true) }?.let { return MatchResult.Found(it) }
            if (ref.name.isBlank()) return MatchResult.Problem("no student with Student ID $code")
        }
        val name = ref.name.trim().lowercase(Locale.US)
        val byName = candidates.filter { it.name.trim().lowercase(Locale.US) == name }
        if (byName.isEmpty()) return MatchResult.Problem("${ref.name}: no student with this name in the file or the app")
        if (byName.size == 1) return MatchResult.Found(byName[0])
        val byClass = ref.className?.let { c -> byName.filter { cand -> cand.classNames.any { it.equals(c, true) } } }.orEmpty()
        return if (byClass.size == 1) MatchResult.Found(byClass[0])
        else MatchResult.Problem("${ref.name}: ${byName.size} students share this name – add the Enroll No. or Student ID")
    }

    fun match(p: ImportedPayment, candidates: List<MatchCandidate>): MatchResult = match(p.ref, candidates)
}
