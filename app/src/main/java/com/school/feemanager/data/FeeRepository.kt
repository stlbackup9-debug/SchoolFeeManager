package com.school.feemanager.data

import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.ClassRate
import com.school.feemanager.domain.DashboardSummary
import com.school.feemanager.domain.FeeAssessment
import com.school.feemanager.domain.FeeCalculator
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.FeeChoice
import com.school.feemanager.domain.PaymentPlan
import com.school.feemanager.domain.Ledger
import com.school.feemanager.domain.LedgerInput
import com.school.feemanager.domain.PasswordHasher
import com.school.feemanager.domain.PaymentRecord
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.ReceiptRecord
import com.school.feemanager.domain.Role
import com.school.feemanager.domain.ScheduleCalculator
import com.school.feemanager.domain.StudentLedger
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import com.school.feemanager.domain.EnrollmentLedger
import com.school.feemanager.domain.academicYearLabel
import com.school.feemanager.io.ImportBundle
import com.school.feemanager.io.ImportPlanner
import com.school.feemanager.io.ImportedEnrollment
import com.school.feemanager.io.ImportedPayment
import com.school.feemanager.io.MatchCandidate
import com.school.feemanager.io.MatchResult
import com.school.feemanager.io.StudentRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale

/** Everything the UI shows, recomputed whenever any table changes. */
data class AppSnapshot(
    val settings: SchoolSettingsEntity,
    val card: FeeRateCard,
    val ledgers: List<StudentLedger>,
    val payments: List<PaymentRecord>,
    val receipts: List<ReceiptRecord>,
    val dashboard: DashboardSummary,
    val users: List<UserEntity>,
    val today: LocalDate,
) {
    val yearStart: LocalDate get() = LocalDate.ofEpochDay(settings.academicYearStart)
    private val byStudent: Map<Long, StudentLedger> by lazy { ledgers.associateBy { it.student.id } }
    val receiptById: Map<Long, ReceiptRecord> by lazy { receipts.associateBy { it.id } }
    private val byEnrollment: Map<Long, Pair<StudentLedger, EnrollmentLedger>> by lazy {
        ledgers.flatMap { l -> l.enrollments.map { it.enrollment.id to (l to it) } }.toMap()
    }

    fun ledger(studentId: Long): StudentLedger? = byStudent[studentId]
    fun enrollment(enrollmentId: Long): Pair<StudentLedger, EnrollmentLedger>? = byEnrollment[enrollmentId]
}

/** Identity of a student. Class data is entered per enrollment ([EnrollmentForm]). */
data class StudentForm(
    val enrollNo: String,
    val name: String,
    val remarks: String,
)

data class EnrollmentForm(
    val className: String,
    val academicYear: String,
    val startDate: LocalDate,
    val periodMonths: Int,
    val type: StudentType,
    val status: StudentStatus,
    val bookType: BookType,
    val discountBp: Int,
    val term: PaymentTerm,
    val remarks: String,
    /** CUSTOM term: number of installments and months between them (0 = spread evenly over the period) */
    val termParts: Int = 0,
    val termEvery: Int = 0,
    /** training option / book options chosen for this student */
    val choice: FeeChoice = FeeChoice.DEFAULT,
) {
    val plan: PaymentPlan get() = PaymentPlan(term, termParts, termEvery)
    /** only CUSTOM keeps installment details */
    val termPartsStored: Int get() = if (term == PaymentTerm.CUSTOM) termParts else 0
    val termEveryStored: Int get() = if (term == PaymentTerm.CUSTOM) termEvery else 0
}

/** One line of a payment: money for one enrollment, optionally one schedule line. */
data class PayItem(
    val studentId: Long,
    val enrollmentId: Long,
    val scheduleSeq: Int?,
    val amount: Long,
    val paymentFor: String,
)

/** A payment with one or more lines – saved as ONE receipt (a combined receipt when there are several lines). */
data class PaymentRequest(
    val date: LocalDate,
    val items: List<PayItem>,
    val remarks: String,
)

data class ImportSummary(
    val dryRun: Boolean,
    val ratesUpdated: Boolean,
    val classesAdded: Int,
    val classesUpdated: Int,
    val studentsImported: Int,
    val studentsExisting: Int,
    val enrollmentsImported: Int,
    val enrollmentsSkippedDuplicate: Int,
    val receiptsImported: Int,
    val paymentsImported: Int,
    val paymentsSkippedDuplicate: Int,
    val problems: List<String>,
) {
    val studentsSkippedDuplicate: Int get() = studentsExisting
}

class FeeException(message: String) : Exception(message)

private class DryRunRollback(val summary: ImportSummary) : Exception()

/** All business operations. Platform-neutral: the database comes in through [FeeStore]. */
class FeeRepository(private val db: FeeStore) {
    private val settingsDao = db.settingsDao()
    private val rateDao = db.feeRateDao()
    private val studentDao = db.studentDao()
    private val enrollmentDao = db.enrollmentDao()
    private val scheduleDao = db.scheduleDao()
    private val txDao = db.transactionDao()
    private val receiptDao = db.receiptDao()
    private val userDao = db.userDao()
    private val auditDao = db.auditDao()

    fun auditLog(limit: Int): Flow<List<AuditLogEntity>> = auditDao.observeRecent(limit)

    // ------------------------------------------------------------------ setup
    suspend fun ensureSeeded() = db.transaction {
        if (settingsDao.get() == null) {
            val today = LocalDate.now()
            // Myanmar academic year starts in June.
            val start = if (today.monthValue >= 6) LocalDate.of(today.year, 6, 1) else LocalDate.of(today.year - 1, 6, 1)
            val defaults = FeeRateCard.workbookDefaults()
            settingsDao.upsert(
                SchoolSettingsEntity(
                    academicYearStart = start.toEpochDay(),
                    instrumentFee = defaults.instrumentFee, laundryPerMonth = defaults.laundryPerMonth,
                    monthsPerYear = defaults.monthsPerYear, entranceFee = defaults.entranceFee,
                    ratesUpdatedAt = System.currentTimeMillis(),
                ),
            )
            rateDao.insertAll(defaults.classes.map { it.copy(startMonth = start.monthValue, startDay = start.dayOfMonth, periodMonths = defaults.monthsPerYear).toEntity() })
        }
        if (userDao.count() == 0) {
            val salt = PasswordHasher.newSalt()
            userDao.insert(UserEntity(username = "admin", fullName = "Administrator", role = Role.ADMIN.name, passwordHash = PasswordHasher.hash("admin123", salt), salt = salt, mustChangePassword = true))
        }
    }

    // --------------------------------------------------------------- snapshot
    fun snapshot(): Flow<AppSnapshot> {
        val base = combine(settingsDao.observe().filterNotNull(), rateDao.observeAll(), studentDao.observeAll(), enrollmentDao.observeAll(), scheduleDao.observeAll()) { s, r, st, en, sc ->
            Base(s, r, st, en, sc)
        }
        return combine(base, txDao.observeAll(), receiptDao.observeAll(), userDao.observeAll()) { b, tx, rc, users ->
            val today = LocalDate.now()
            val card = rateCardOf(b.settings, b.rates)
            val receiptNo = rc.associate { it.id to it.receiptNo }
            val payments = tx.map { it.toDomain(receiptNo[it.receiptId] ?: "") }
            val itemsByReceipt = payments.groupBy { it.receiptId }
            val receipts = rc.map { it.toDomain(itemsByReceipt[it.id].orEmpty().sortedBy { p -> p.id }) }
            val ledgers = Ledger.build(
                LedgerInput(
                    students = b.students.map { it.toDomain() },
                    enrollments = b.enrollments.map { it.toDomain() },
                    fees = b.enrollments.associate { it.id to it.fee() },
                    schedules = b.schedules.groupBy({ it.enrollmentId }, { it.toDomain() }),
                    payments = payments,
                    classOrder = card.classNames,
                    today = today,
                ),
            )
            AppSnapshot(b.settings, card, ledgers, payments, receipts, Ledger.dashboard(ledgers, payments, card.classNames, today), users, today)
        }.flowOn(Dispatchers.Default)
    }

    private data class Base(
        val settings: SchoolSettingsEntity,
        val rates: List<FeeRateEntity>,
        val students: List<StudentEntity>,
        val enrollments: List<EnrollmentEntity>,
        val schedules: List<PaymentScheduleEntity>,
    )

    private suspend fun currentCard(): Pair<SchoolSettingsEntity, FeeRateCard> {
        val s = settingsDao.get() ?: throw FeeException("Settings missing")
        return s to rateCardOf(s, rateDao.getAll())
    }

    private suspend fun audit(user: String, action: String, details: String) =
        auditDao.insert(AuditLogEntity(at = System.currentTimeMillis(), username = user, action = action, details = details))

    // ------------------------------------------------------------------ users
    suspend fun login(username: String, password: String): UserEntity? = withContext(Dispatchers.IO) {
        val u = userDao.byUsername(username.trim()) ?: return@withContext null
        if (!u.active) return@withContext null
        if (PasswordHasher.verify(password, u.salt, u.passwordHash)) u else null
    }

    suspend fun addUser(username: String, fullName: String, role: Role, password: String, by: String) = withContext(Dispatchers.IO) {
        val name = username.trim()
        if (!Regex("^[A-Za-z0-9._-]{3,30}$").matches(name)) throw FeeException("Username must be 3–30 letters, digits, dot, dash or underscore")
        if (password.length < 6) throw FeeException("Password must be at least 6 characters")
        if (userDao.byUsername(name) != null) throw FeeException("Username '$name' already exists")
        val salt = PasswordHasher.newSalt()
        userDao.insert(UserEntity(username = name, fullName = fullName.trim(), role = role.name, passwordHash = PasswordHasher.hash(password, salt), salt = salt, mustChangePassword = true))
        audit(by, "USER_ADD", "$name (${role.label})")
    }

    suspend fun setPassword(userId: Long, newPassword: String, by: String, forceChange: Boolean) = withContext(Dispatchers.IO) {
        if (newPassword.length < 6) throw FeeException("Password must be at least 6 characters")
        val u = userDao.get(userId) ?: throw FeeException("User not found")
        val salt = PasswordHasher.newSalt()
        userDao.update(u.copy(passwordHash = PasswordHasher.hash(newPassword, salt), salt = salt, mustChangePassword = forceChange))
        audit(by, "PASSWORD", "Password changed for ${u.username}")
    }

    suspend fun setUserActive(userId: Long, active: Boolean, by: String) = db.transaction {
        val u = userDao.get(userId) ?: throw FeeException("User not found")
        if (!active && u.role == Role.ADMIN.name && userDao.activeAdminCount() <= 1) throw FeeException("At least one active Admin is required")
        userDao.update(u.copy(active = active))
        audit(by, if (active) "USER_ENABLE" else "USER_DISABLE", u.username)
    }

    suspend fun setUserRole(userId: Long, role: Role, by: String) = db.transaction {
        val u = userDao.get(userId) ?: throw FeeException("User not found")
        if (u.role == Role.ADMIN.name && role != Role.ADMIN && userDao.activeAdminCount() <= 1) throw FeeException("At least one active Admin is required")
        userDao.update(u.copy(role = role.name))
        audit(by, "USER_ROLE", "${u.username} -> ${role.label}")
    }

    // --------------------------------------------------------------- students
    fun preview(card: FeeRateCard, form: EnrollmentForm): FeeAssessment =
        FeeCalculator.assess(card, form.className, form.type, form.status, form.bookType, form.discountBp, form.periodMonths, form.choice)

    private suspend fun nextStudentCode(): String {
        var s = settingsDao.get()!!
        var code: String
        do {
            code = "STU-%04d".format(s.nextStudentSeq)
            s = s.copy(nextStudentSeq = s.nextStudentSeq + 1)
        } while (studentDao.countCode(code) > 0)
        settingsDao.upsert(s)
        return code
    }

    private fun cleanName(name: String) = name.trim().replace(Regex("\\s+"), " ")

    private suspend fun validateStudent(id: Long?, form: StudentForm) {
        val name = cleanName(form.name)
        if (name.isBlank()) throw FeeException("Student name is required")
        if (name.length > 100) throw FeeException("Student name is too long")
        val en = form.enrollNo.trim()
        if (en.length > 30) throw FeeException("Enroll No. is too long (30 characters max)")
        if (en.isNotEmpty()) studentDao.byEnrollNo(en, id ?: -1)?.let { throw FeeException("Enroll No. $en is already used by ${it.name} (${it.code})") }
    }

    private fun validateEnrollment(form: EnrollmentForm, card: FeeRateCard) {
        if (card.rateFor(form.className) == null) throw FeeException("Class '${form.className}' has no fee rates")
        if (form.discountBp !in 0..10000) throw FeeException("Discount must be between 0% and 100%")
        if (form.periodMonths !in 1..24) throw FeeException("Period must be 1–24 months")
        if (form.academicYear.isBlank()) throw FeeException("Academic year is required")
        if (form.academicYear.trim().length > 20) throw FeeException("Academic year is too long")
        if (form.term == PaymentTerm.CUSTOM) {
            if (form.termParts !in 1..PaymentPlan.MAX_PARTS) throw FeeException("Custom payment term: number of installments must be 1–${PaymentPlan.MAX_PARTS}")
            if (form.termEvery !in 0..24) throw FeeException("Custom payment term: months between installments must be 0–24")
        }
        FeeCalculator.unknownChoices(card, form.className, form.choice).firstOrNull()
            ?.let { throw FeeException("${form.className} has no $it – choose again in the class fees") }
    }

    /** Same student name already enrolled in this class and year (another student record)? */
    private suspend fun nameClash(studentId: Long, nameKey: String, form: EnrollmentForm): String? {
        val others = studentDao.getAll().filter { it.nameKey == nameKey && it.id != studentId }
        for (o in others) {
            enrollmentDao.forStudent(o.id).firstOrNull { it.className.equals(form.className, true) && it.academicYear.equals(form.academicYear.trim(), true) }
                ?.let { return "${o.name} is already registered in ${it.className} ${it.academicYear} (${o.code}). Duplicate registration is not allowed – open that student instead." }
        }
        return null
    }

    /** Creates or updates a student; a new student can get a first class enrollment at once. Returns the student id. */
    suspend fun saveStudent(id: Long?, form: StudentForm, firstEnrollment: EnrollmentForm?, by: String): Long = db.transaction {
        validateStudent(id, form)
        val name = cleanName(form.name)
        val key = name.lowercase(Locale.ROOT)
        val en = form.enrollNo.trim().ifEmpty { null }
        val now = System.currentTimeMillis()
        if (id == null) {
            val (_, card) = currentCard()
            if (firstEnrollment != null) {
                validateEnrollment(firstEnrollment, card)
                nameClash(-1, key, firstEnrollment)?.let { throw FeeException(it) }
            }
            val code = nextStudentCode()
            val newId = studentDao.insert(
                StudentEntity(code = code, enrollNo = en, name = name, nameKey = key, remarks = form.remarks.trim(), registeredOn = LocalDate.now().toEpochDay(), updatedAt = now),
            )
            audit(by, "STUDENT_ADD", "$code $name${en?.let { " enroll no. $it" } ?: ""}")
            if (firstEnrollment != null) insertEnrollment(newId, firstEnrollment, card, by)
            newId
        } else {
            val old = studentDao.get(id) ?: throw FeeException("Student not found")
            studentDao.update(old.copy(enrollNo = en, name = name, nameKey = key, remarks = form.remarks.trim(), updatedAt = now))
            audit(by, "STUDENT_EDIT", "${old.code} $name${if ((old.enrollNo ?: "") != (en ?: "")) " enroll no. ${old.enrollNo ?: "–"} -> ${en ?: "–"}" else ""}")
            id
        }
    }

    suspend fun setStudentActive(id: Long, active: Boolean, by: String) = db.transaction {
        val s = studentDao.get(id) ?: throw FeeException("Student not found")
        studentDao.update(s.copy(active = active, updatedAt = System.currentTimeMillis()))
        audit(by, if (active) "STUDENT_REACTIVATE" else "STUDENT_DEACTIVATE", s.code)
    }

    // ------------------------------------------------------------ enrollments
    private suspend fun writeSchedule(e: EnrollmentEntity) {
        scheduleDao.deleteForEnrollment(e.id)
        scheduleDao.insertAll(
            ScheduleCalculator.generate(PaymentTerm.valueOf(e.paymentTerm), e.netTotal, LocalDate.ofEpochDay(e.startDate), e.periodMonths, e.termParts, e.termEvery).map { it.toEntity(e.id) },
        )
    }

    private suspend fun insertEnrollment(studentId: Long, form: EnrollmentForm, card: FeeRateCard, by: String, approvedFee: FeeAssessment? = null): Long {
        val year = form.academicYear.trim()
        enrollmentDao.findDuplicate(studentId, form.className, year, -1)?.let {
            throw FeeException("This student is already enrolled in ${it.className} for $year")
        }
        val now = System.currentTimeMillis()
        val fee = approvedFee ?: preview(card, form)
        val base = EnrollmentEntity(
            studentId = studentId, className = form.className, academicYear = year, startDate = form.startDate.toEpochDay(), periodMonths = form.periodMonths,
            studentType = form.type.name, studentStatus = form.status.name, bookType = form.bookType.name, paymentTerm = form.term.name,
            discountBp = form.discountBp, remarks = form.remarks.trim(), createdOn = LocalDate.now().toEpochDay(), updatedAt = now,
            trainingFee = 0, instrumentFee = 0, bookFee = 0, hostelFee = 0, laundryFee = 0, entranceFee = 0, grossTotal = 0, discountAmount = 0, netTotal = 0, assessedAt = now,
            termParts = form.termPartsStored, termEvery = form.termEveryStored, feeChoice = form.choice.encode(),
        ).withFee(fee, now)
        val id = enrollmentDao.insert(base)
        writeSchedule(base.copy(id = id))
        audit(by, "ENROLL_ADD", "student $studentId: ${form.className} $year from ${form.startDate} (${form.periodMonths} months) net ${fee.netTotal}")
        return id
    }

    /** Adds (id == null) or edits a class enrollment. Fee-relevant changes re-assess with the CURRENT rates. */
    suspend fun saveEnrollment(id: Long?, studentId: Long, form: EnrollmentForm, by: String): Long = db.transaction {
        val (_, card) = currentCard()
        validateEnrollment(form, card)
        val st = studentDao.get(studentId) ?: throw FeeException("Student not found")
        nameClash(studentId, st.nameKey, form)?.let { throw FeeException(it) }
        if (id == null) return@transaction insertEnrollment(studentId, form, card, by)
        val old = enrollmentDao.get(id) ?: throw FeeException("Enrollment not found")
        val year = form.academicYear.trim()
        enrollmentDao.findDuplicate(studentId, form.className, year, id)?.let { throw FeeException("This student is already enrolled in ${it.className} for $year") }
        val now = System.currentTimeMillis()
        var updated = old.copy(
            className = form.className, academicYear = year, startDate = form.startDate.toEpochDay(), periodMonths = form.periodMonths,
            studentType = form.type.name, studentStatus = form.status.name, bookType = form.bookType.name, paymentTerm = form.term.name,
            discountBp = form.discountBp, remarks = form.remarks.trim(), updatedAt = now,
            termParts = form.termPartsStored, termEvery = form.termEveryStored, feeChoice = form.choice.encode(),
        )
        val feeChanged = old.className != updated.className || old.studentType != updated.studentType || old.studentStatus != updated.studentStatus ||
            old.bookType != updated.bookType || old.discountBp != updated.discountBp || old.periodMonths != updated.periodMonths || old.feeChoice != updated.feeChoice
        if (feeChanged) {
            val fee = preview(card, form)
            updated = updated.withFee(fee, now)
            audit(by, "ENROLL_REASSESS", "enrollment $id: net ${old.netTotal} -> ${fee.netTotal}")
        }
        enrollmentDao.update(updated)
        if (feeChanged || old.paymentTerm != updated.paymentTerm || old.startDate != updated.startDate || old.periodMonths != updated.periodMonths ||
            old.termParts != updated.termParts || old.termEvery != updated.termEvery) writeSchedule(updated)
        audit(by, "ENROLL_EDIT", "enrollment $id: ${updated.className} ${updated.academicYear}")
        id
    }

    suspend fun setEnrollmentActive(id: Long, active: Boolean, by: String) = db.transaction {
        val e = enrollmentDao.get(id) ?: throw FeeException("Enrollment not found")
        enrollmentDao.update(e.copy(active = active, updatedAt = System.currentTimeMillis()))
        audit(by, if (active) "ENROLL_REACTIVATE" else "ENROLL_DEACTIVATE", "enrollment $id ${e.className} ${e.academicYear}")
    }

    /** Explicit re-assessment of enrollments with the CURRENT fee rates (null = every active enrollment). */
    suspend fun recalculate(enrollmentIds: List<Long>?, by: String): Int = db.transaction {
        val (_, card) = currentCard()
        val activeStudents = studentDao.getAll().filter { it.active }.map { it.id }.toSet()
        val targets = if (enrollmentIds == null) enrollmentDao.getAll().filter { it.active && it.studentId in activeStudents } else enrollmentIds.mapNotNull { enrollmentDao.get(it) }
        val now = System.currentTimeMillis()
        targets.forEach { e ->
            val fee = FeeCalculator.assess(card, e.toDomain())
            val updated = e.withFee(fee, now)
            enrollmentDao.update(updated)
            writeSchedule(updated)
        }
        audit(by, "RECALCULATE", "${targets.size} enrollment(s) re-assessed with current rates")
        targets.size
    }

    // --------------------------------------------------------------- payments
    private suspend fun studentBalance(studentId: Long): Long =
        enrollmentDao.forStudent(studentId).sumOf { it.netTotal } - txDao.validForStudent(studentId).sumOf { it.amount }

    /** Records a payment of one or more lines as ONE receipt, atomically. Returns the receipt id. */
    suspend fun recordPayment(req: PaymentRequest, by: String): Long = db.transaction {
        if (req.items.isEmpty()) throw FeeException("Select at least one item to pay")
        if (req.items.any { it.amount <= 0 }) throw FeeException("Every amount must be greater than zero")
        if (req.date.isAfter(LocalDate.now())) throw FeeException("Collection date cannot be in the future")
        val students = req.items.map { it.studentId }.distinct().map { sid -> studentDao.get(sid) ?: throw FeeException("Student not found") }
        students.firstOrNull { !it.active }?.let { throw FeeException("${it.name} is deactivated. Reactivate the student before collecting fees.") }
        req.items.forEach { item ->
            val e = enrollmentDao.get(item.enrollmentId) ?: throw FeeException("Class enrollment not found")
            if (e.studentId != item.studentId) throw FeeException("Enrollment does not belong to the student")
            if (!e.active) throw FeeException("${e.className} ${e.academicYear} is deactivated")
        }
        val previous = students.sumOf { studentBalance(it.id) }
        val total = req.items.sumOf { it.amount }
        val now = System.currentTimeMillis()
        val receiptNo = nextReceiptNo(req.date)
        val rid = receiptDao.insert(
            ReceiptEntity(
                receiptNo = receiptNo, date = req.date.toEpochDay(), payerName = students.joinToString(", ") { it.name }, issuedAt = now,
                previousBalance = previous, remainingBalance = previous - total, receivedBy = by, remarks = req.remarks.trim(),
            ),
        )
        req.items.forEach { item ->
            txDao.insert(
                PaymentTransactionEntity(
                    receiptId = rid, studentId = item.studentId, enrollmentId = item.enrollmentId, date = req.date.toEpochDay(), amount = item.amount,
                    scheduleSeq = item.scheduleSeq, paymentFor = item.paymentFor.trim(), remarks = req.remarks.trim(), receivedBy = by, createdAt = now,
                ),
            )
        }
        audit(by, "PAYMENT", "$receiptNo ${req.items.size} line(s) ${students.joinToString(",") { it.code }} $total")
        rid
    }

    private suspend fun nextReceiptNo(date: LocalDate): String {
        var s = settingsDao.get()!!
        var no: String
        do {
            no = "%s-%d-%06d".format(s.receiptPrefix.ifBlank { "RCP" }, date.year, s.nextReceiptSeq)
            s = s.copy(nextReceiptSeq = s.nextReceiptSeq + 1)
        } while (receiptDao.countNo(no) > 0)
        settingsDao.upsert(s)
        return no
    }

    /** Voids a whole receipt (all its lines). */
    suspend fun voidReceipt(receiptId: Long, reason: String, by: String) = db.transaction {
        if (reason.isBlank()) throw FeeException("A reason is required to void a receipt")
        val r = receiptDao.get(receiptId) ?: throw FeeException("Receipt not found")
        if (r.voided) throw FeeException("Already voided")
        receiptDao.update(r.copy(voided = true, voidReason = reason.trim(), voidedBy = by))
        txDao.forReceipt(receiptId).forEach { txDao.update(it.copy(voided = true, voidReason = reason.trim(), voidedBy = by)) }
        audit(by, "RECEIPT_VOID", "${r.receiptNo}: ${reason.trim()}")
    }

    suspend fun markPrinted(receiptIds: List<Long>) = withContext(Dispatchers.IO) { receiptIds.forEach { receiptDao.incrementPrint(it) } }

    // ------------------------------------------------------------ settings
    private fun validateCard(card: FeeRateCard) {
        if (card.classes.isEmpty()) throw FeeException("At least one class is required")
        if (card.classes.map { it.className.uppercase(Locale.ROOT) }.toSet().size != card.classes.size) throw FeeException("Class names must be unique")
        if (card.classes.any { it.className.isBlank() }) throw FeeException("Class name cannot be blank")
        val all = card.classes.flatMap { listOf(it.trainingFee, it.bookDayMyanmar, it.bookDayIntl, it.bookHostelMyanmar, it.bookHostelIntl, it.hostelFee) } +
            listOf(card.instrumentFee, card.laundryPerMonth, card.entranceFee)
        if (all.any { it < 0 }) throw FeeException("Fee amounts cannot be negative")
        if (card.monthsPerYear !in 1..24) throw FeeException("Default period must be 1–24 months")
        card.classes.forEach { c ->
            if (c.startMonth !in 1..12) throw FeeException("${c.className}: start month must be 1–12")
            if (c.startDay !in 1..31) throw FeeException("${c.className}: start day must be 1–31")
            if (c.periodMonths !in 1..24) throw FeeException("${c.className}: period must be 1–24 months")
            if (c.options.any { it.amount < 0 }) throw FeeException("${c.className}: option amounts cannot be negative")
            if (c.options.any { it.name.isBlank() }) throw FeeException("${c.className}: every training / book option needs a name")
            c.options.groupBy { it.kind to it.name.lowercase(Locale.ROOT) }.values.firstOrNull { it.size > 1 }
                ?.let { throw FeeException("${c.className}: ${it[0].kind.label.lowercase()} option '${it[0].name}' appears twice") }
        }
    }

    private suspend fun writeCard(card: FeeRateCard) {
        val s = settingsDao.get()!!
        settingsDao.upsert(s.copy(instrumentFee = card.instrumentFee, laundryPerMonth = card.laundryPerMonth, monthsPerYear = card.monthsPerYear, entranceFee = card.entranceFee, ratesUpdatedAt = System.currentTimeMillis()))
        rateDao.deleteAll()
        rateDao.insertAll(card.classes.mapIndexed { i, c -> c.copy(sortOrder = i).toEntity() })
    }

    suspend fun saveRates(card: FeeRateCard, by: String) = db.transaction {
        validateCard(card)
        writeCard(card)
        audit(by, "RATES_UPDATE", "Fee rates changed (${card.classes.size} classes). Existing assessments unchanged.")
    }

    suspend fun saveSchoolProfile(name: String, address: String, phone: String, receiptPrefix: String, yearStart: LocalDate, by: String) = db.transaction {
        if (name.isBlank()) throw FeeException("School name is required")
        if (!Regex("^[A-Za-z0-9]{1,8}$").matches(receiptPrefix.trim())) throw FeeException("Receipt prefix: 1–8 letters or digits")
        val s = settingsDao.get()!!
        settingsDao.upsert(s.copy(schoolName = name.trim(), address = address.trim(), phone = phone.trim(), receiptPrefix = receiptPrefix.trim().uppercase(Locale.ROOT), academicYearStart = yearStart.toEpochDay()))
        audit(by, "SCHOOL_PROFILE", "Updated school profile")
    }

    // -------------------------------------------------------------- import
    /** Default start date / period / academic year for an imported enrollment, from the class settings. */
    private fun formOf(card: FeeRateCard, e: ImportedEnrollment, today: LocalDate): EnrollmentForm {
        val rate = card.rateFor(e.className)!!
        val period = e.periodMonths ?: rate.periodMonths
        // "2025-2026" without a start date → the class's start day in 2025
        val start = e.startDate
            ?: e.academicYear?.let { y -> Regex("(19|20)\\d{2}").find(y)?.value?.toInt()?.let { rate.startIn(it) } }
            ?: rate.defaultStart(today)
        return EnrollmentForm(
            className = rate.className, academicYear = e.academicYear ?: academicYearLabel(start, period), startDate = start, periodMonths = period,
            type = e.type, status = e.status, bookType = e.bookType, discountBp = e.discountBp, term = e.term, remarks = e.remarks,
            termParts = e.termParts, termEvery = e.termEvery, choice = e.choice,
        )
    }

    /**
     * Applies an import (workbook or module file) in ONE transaction. With [dryRun] everything is
     * checked and counted exactly as a real import, then rolled back – used for the preview.
     */
    suspend fun applyImport(imp: ImportBundle, updateRates: Boolean, dryRun: Boolean, by: String): ImportSummary = try {
        db.transaction {
            val s = runImport(imp, updateRates, dryRun, by)
            if (dryRun) throw DryRunRollback(s)
            s
        }
    } catch (e: DryRunRollback) {
        e.summary
    }

    private suspend fun runImport(imp: ImportBundle, updateRates: Boolean, dryRun: Boolean, by: String): ImportSummary {
        val problems = ArrayList<String>()
        val today = LocalDate.now()
        var ratesUpdated = false
        var classesAdded = 0
        var classesUpdated = 0
        if (updateRates && imp.rates != null) {
            validateCard(imp.rates)
            writeCard(imp.rates)
            ratesUpdated = true
        }
        if (imp.classRates.isNotEmpty()) {
            val (settings, card) = currentCard()
            val merged = card.classes.toMutableList()
            imp.classRates.forEach { u ->
                val i = merged.indexOfFirst { it.className.equals(u.className, true) }
                if (i >= 0) {
                    val updated = u.applyTo(merged[i], merged[i].sortOrder, settings.monthsPerYear)
                    if (updated != merged[i]) classesUpdated++
                    merged[i] = updated
                } else {
                    merged += u.applyTo(null, merged.size, settings.monthsPerYear)
                    classesAdded++
                }
            }
            val newCard = card.copy(classes = merged)
            validateCard(newCard)
            writeCard(newCard)
        }
        val (_, card) = currentCard()
        val now = System.currentTimeMillis()

        // Candidates for matching: existing students + those created by this import.
        val enrollmentsByStudent = enrollmentDao.getAll().groupBy { it.studentId }.mapValues { (_, v) -> v.toMutableList() }.toMutableMap()
        val candidates = studentDao.getAll().map { MatchCandidate(it.id, it.code, it.enrollNo ?: "", it.name, enrollmentsByStudent[it.id].orEmpty().map { e -> e.className }.toSet()) }.toMutableList()
        val createdThisImport = HashMap<String, Long>()  // lower-case name -> id, for rows without IDs
        fun refresh(id: Long) {
            val i = candidates.indexOfFirst { it.key == id }
            if (i >= 0) candidates[i] = candidates[i].copy(classNames = enrollmentsByStudent[id].orEmpty().map { it.className }.toSet())
        }

        var studentsAdded = 0
        var studentsExisting = 0
        var enrollAdded = 0
        var enrollDup = 0

        suspend fun addEnrollment(studentId: Long, e: ImportedEnrollment, where: String) {
            val form = formOf(card, e, today)
            val list = enrollmentsByStudent.getOrPut(studentId) { ArrayList() }
            if (list.any { it.className.equals(form.className, true) && it.academicYear.equals(form.academicYear, true) }) { enrollDup++; return }
            val id = insertEnrollment(studentId, form, card, by, e.approvedFee)
            list += enrollmentDao.get(id)!!
            refresh(studentId)
            enrollAdded++
        }

        val existingSeen = HashSet<Long>()
        // Rows without Enroll No. / Student ID (e.g. the original workbook) are matched by name: a student
        // created earlier in this file, else the ONE student in the app with exactly that name.
        val dbByName = studentDao.getAll().groupBy { it.nameKey }
        for (st in imp.students) {
            val where = "${st.sheet} row ${st.row}"
            val nameKey = cleanName(st.name).lowercase(Locale.ROOT)
            val existingId: Long? = when {
                st.enrollNo != null -> studentDao.byEnrollNo(st.enrollNo)?.id
                st.code != null -> studentDao.byCode(st.code)?.id
                else -> createdThisImport[nameKey] ?: dbByName[nameKey]?.singleOrNull()?.id
            }
            val id = if (existingId != null) {
                if (createdThisImport.values.none { it == existingId } && existingSeen.add(existingId)) studentsExisting++
                existingId
            } else {
                val code = st.code?.takeIf { studentDao.countCode(it) == 0 } ?: nextStudentCode()
                val en = st.enrollNo?.takeIf { studentDao.byEnrollNo(it) == null }
                val name = cleanName(st.name)
                val newId = studentDao.insert(StudentEntity(code = code, enrollNo = en, name = name, nameKey = name.lowercase(Locale.ROOT), remarks = st.remarks, registeredOn = today.toEpochDay(), updatedAt = now))
                candidates += MatchCandidate(newId, code, en ?: "", name, emptySet())
                if (st.enrollNo == null && st.code == null) createdThisImport[nameKey] = newId
                else createdThisImport["#" + newId] = newId
                studentsAdded++
                newId
            }
            if (st.enrollment != null) {
                try { addEnrollment(id, st.enrollment, where) } catch (e: FeeException) { problems += "$where: ${e.message} – class not added" }
            }
        }

        for (row in imp.enrollments) {
            val where = "${row.sheet} row ${row.row}"
            when (val m = ImportPlanner.match(row.ref, candidates)) {
                is MatchResult.Found -> try { addEnrollment(m.candidate.key, row.enrollment, where) } catch (e: FeeException) { problems += "$where: ${e.message} – skipped" }
                is MatchResult.Problem -> problems += "$where: ${m.message} – skipped"
            }
        }

        // Payments: rows with the same receipt number are one receipt; blank numbers get one receipt each.
        var receiptsAdded = 0
        var payAdded = 0
        var dupPays = 0
        val balances = HashMap<Long, Long>()
        suspend fun balanceOf(id: Long) = balances[id] ?: studentBalance(id)
        val groups = imp.payments.groupBy { if (it.receiptNo.isBlank()) "#row${it.sheet}${it.row}" else it.receiptNo.uppercase(Locale.ROOT) }.values
            .sortedWith(compareBy({ it.first().date }, { it.first().row }))
        for (group in groups) {
            val first = group.first()
            if (first.receiptNo.isNotBlank() && receiptDao.countNo(first.receiptNo) > 0) { dupPays += group.size; continue }
            val resolved = ArrayList<Triple<ImportedPayment, Long, EnrollmentEntity>>()
            var failed: String? = null
            for (p in group) {
                val sid = when (val m = ImportPlanner.match(p.ref, candidates)) {
                    is MatchResult.Found -> m.candidate.key
                    is MatchResult.Problem -> { failed = "${p.sheet} row ${p.row}: ${m.message}"; break }
                }
                val e = pickEnrollment(enrollmentsByStudent[sid].orEmpty(), p)
                if (e == null) { failed = "${p.sheet} row ${p.row}: ${p.ref.display}: ${enrollmentProblem(enrollmentsByStudent[sid].orEmpty(), p)}"; break }
                resolved += Triple(p, sid, e)
            }
            if (failed != null) {
                problems += "$failed – ${if (group.size > 1) "receipt ${first.receiptNo} (${group.size} rows)" else "row"} skipped"
                continue
            }
            val sids = resolved.map { it.second }.distinct()
            val prev = sids.sumOf { balanceOf(it) }
            val total = group.sumOf { it.amount }
            val no = first.receiptNo.ifBlank { nextReceiptNo(first.date) }
            val names = sids.mapNotNull { id -> candidates.firstOrNull { it.key == id }?.name }
            val rid = receiptDao.insert(
                ReceiptEntity(
                    receiptNo = no, date = first.date.toEpochDay(), payerName = names.joinToString(", "), issuedAt = now, previousBalance = prev,
                    remainingBalance = prev - total, receivedBy = first.receivedBy, remarks = first.remarks, imported = true,
                ),
            )
            resolved.forEach { (p, sid, e) ->
                txDao.insert(
                    PaymentTransactionEntity(
                        receiptId = rid, studentId = sid, enrollmentId = e.id, date = p.date.toEpochDay(), amount = p.amount,
                        scheduleSeq = ScheduleCalculator.parsePaymentFor(p.paymentFor, PaymentTerm.valueOf(e.paymentTerm), e.periodMonths, e.termParts),
                        paymentFor = p.paymentFor, remarks = p.remarks, receivedBy = p.receivedBy, createdAt = now, imported = true,
                    ),
                )
                balances[sid] = balanceOf(sid) - p.amount
            }
            receiptsAdded++
            payAdded += group.size
        }
        if (!dryRun) audit(by, "IMPORT", "${imp.title}: rates=$ratesUpdated classes+$classesAdded/~$classesUpdated students=$studentsAdded enrollments=$enrollAdded receipts=$receiptsAdded lines=$payAdded")
        return ImportSummary(dryRun, ratesUpdated, classesAdded, classesUpdated, studentsAdded, studentsExisting, enrollAdded, enrollDup, receiptsAdded, payAdded, dupPays, problems)
    }

    /** The enrollment a payment row belongs to: by class / academic year, else the only one, else the one running on the payment date. */
    private fun pickEnrollment(list: List<EnrollmentEntity>, p: ImportedPayment): EnrollmentEntity? {
        var c = list
        p.ref.className?.let { cls -> c = c.filter { it.className.equals(cls, true) } }
        p.academicYear?.let { y -> c = c.filter { it.academicYear.equals(y, true) } }
        if (c.size <= 1) return c.firstOrNull()
        val running = c.filter { e ->
            val start = LocalDate.ofEpochDay(e.startDate)
            !p.date.isBefore(start.minusMonths(3)) && p.date.isBefore(start.plusMonths(e.periodMonths.toLong()))
        }
        return running.singleOrNull()
    }

    private fun enrollmentProblem(list: List<EnrollmentEntity>, p: ImportedPayment): String = when {
        list.isEmpty() -> "the student has no class enrollment – import the class first"
        p.ref.className != null && list.none { it.className.equals(p.ref.className, true) } -> "the student is not enrolled in ${p.ref.className}"
        else -> "the student has several classes (${list.joinToString { "${it.className} ${it.academicYear}" }}) – fill in Class and Academic Year"
    }

    // ------------------------------------------------------- backup/restore
    fun backupTo(out: OutputStream) = db.backupTo(out)

    /** Validates a backup file and replaces the database. The caller must restart the app afterwards. */
    fun restoreFrom(input: InputStream) = db.restoreFrom(input)
}
