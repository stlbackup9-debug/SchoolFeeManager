package com.school.feemanager.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.DashboardSummary
import com.school.feemanager.domain.FeeAssessment
import com.school.feemanager.domain.FeeCalculator
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.Ledger
import com.school.feemanager.domain.LedgerInput
import com.school.feemanager.domain.PasswordHasher
import com.school.feemanager.domain.PaymentRecord
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.Role
import com.school.feemanager.domain.ScheduleCalculator
import com.school.feemanager.domain.StudentLedger
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import com.school.feemanager.io.ImportPlanner
import com.school.feemanager.io.MatchCandidate
import com.school.feemanager.io.MatchResult
import com.school.feemanager.io.WorkbookImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
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
    val receiptsByTx: Map<Long, ReceiptEntity>,
    val dashboard: DashboardSummary,
    val users: List<UserEntity>,
    val today: LocalDate,
) {
    val yearStart: LocalDate get() = LocalDate.ofEpochDay(settings.academicYearStart)
    fun ledger(studentId: Long): StudentLedger? = ledgers.firstOrNull { it.student.id == studentId }
}

data class StudentForm(
    val name: String,
    val className: String,
    val type: StudentType,
    val status: StudentStatus,
    val bookType: BookType,
    val discountBp: Int,
    val term: PaymentTerm,
    val remarks: String,
)

data class PaymentRequest(
    val studentId: Long,
    val date: LocalDate,
    val amount: Long,
    val scheduleSeq: Int?,
    val paymentFor: String,
    val remarks: String,
)

data class ImportSummary(
    val ratesUpdated: Boolean,
    val studentsImported: Int,
    val studentsSkippedDuplicate: Int,
    val paymentsImported: Int,
    val paymentsSkippedDuplicate: Int,
    val problems: List<String>,
)

class FeeException(message: String) : Exception(message)

class FeeRepository(private val context: Context, private val db: AppDatabase) {
    private val settingsDao = db.settingsDao()
    private val rateDao = db.feeRateDao()
    private val studentDao = db.studentDao()
    private val feeDao = db.studentFeeDao()
    private val scheduleDao = db.scheduleDao()
    private val txDao = db.transactionDao()
    private val receiptDao = db.receiptDao()
    private val userDao = db.userDao()
    private val auditDao = db.auditDao()

    // ------------------------------------------------------------------ setup
    suspend fun ensureSeeded() = db.withTransaction {
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
            rateDao.insertAll(defaults.classes.map { it.toEntity() })
        }
        if (userDao.count() == 0) {
            val salt = PasswordHasher.newSalt()
            userDao.insert(UserEntity(username = "admin", fullName = "Administrator", role = Role.ADMIN.name, passwordHash = PasswordHasher.hash("admin123", salt), salt = salt, mustChangePassword = true))
        }
    }

    // --------------------------------------------------------------- snapshot
    fun snapshot(): Flow<AppSnapshot> {
        val base = combine(settingsDao.observe().filterNotNull(), rateDao.observeAll(), studentDao.observeAll(), feeDao.observeAll(), scheduleDao.observeAll()) { s, r, st, f, sc ->
            Base(s, r, st, f, sc)
        }
        return combine(base, txDao.observeAll(), receiptDao.observeAll(), userDao.observeAll()) { b, tx, rc, users ->
            val today = LocalDate.now()
            val card = rateCardOf(b.settings, b.rates)
            val receiptsByTx = rc.associateBy { it.transactionId }
            val payments = tx.map { it.toDomain(receiptsByTx[it.id]?.receiptNo ?: "") }
            val students = b.students.map { it.toDomain() }
            val ledgers = Ledger.build(
                LedgerInput(
                    students = students,
                    fees = b.fees.associate { it.studentId to it.toDomain() },
                    schedules = b.schedules.groupBy({ it.studentId }, { it.toDomain() }),
                    payments = payments,
                    classOrder = card.classNames,
                    today = today,
                ),
            )
            AppSnapshot(b.settings, card, ledgers, payments, receiptsByTx, Ledger.dashboard(ledgers, payments, card.classNames, today), users, today)
        }.flowOn(Dispatchers.Default)
    }

    private data class Base(
        val settings: SchoolSettingsEntity,
        val rates: List<FeeRateEntity>,
        val students: List<StudentEntity>,
        val fees: List<StudentFeeEntity>,
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

    suspend fun setUserActive(userId: Long, active: Boolean, by: String) = db.withTransaction {
        val u = userDao.get(userId) ?: throw FeeException("User not found")
        if (!active && u.role == Role.ADMIN.name && userDao.activeAdminCount() <= 1) throw FeeException("At least one active Admin is required")
        userDao.update(u.copy(active = active))
        audit(by, if (active) "USER_ENABLE" else "USER_DISABLE", u.username)
    }

    suspend fun setUserRole(userId: Long, role: Role, by: String) = db.withTransaction {
        val u = userDao.get(userId) ?: throw FeeException("User not found")
        if (u.role == Role.ADMIN.name && role != Role.ADMIN && userDao.activeAdminCount() <= 1) throw FeeException("At least one active Admin is required")
        userDao.update(u.copy(role = role.name))
        audit(by, "USER_ROLE", "${u.username} -> ${role.label}")
    }

    // --------------------------------------------------------------- students
    fun preview(card: FeeRateCard, form: StudentForm): FeeAssessment =
        FeeCalculator.assess(card, form.className, form.type, form.status, form.bookType, form.discountBp)

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

    private suspend fun writeAssessment(studentId: Long, fee: FeeAssessment, term: PaymentTerm, yearStart: LocalDate) {
        feeDao.upsert(fee.toEntity(studentId, System.currentTimeMillis()))
        scheduleDao.deleteForStudent(studentId)
        scheduleDao.insertAll(ScheduleCalculator.generate(term, fee.netTotal, yearStart).map { it.toEntity(studentId) })
    }

    private fun validate(form: StudentForm, card: FeeRateCard) {
        if (form.name.isBlank()) throw FeeException("Student name is required")
        if (form.name.trim().length > 100) throw FeeException("Student name is too long")
        if (card.rateFor(form.className) == null) throw FeeException("Class '${form.className}' has no fee rates")
        if (form.discountBp !in 0..10000) throw FeeException("Discount must be between 0% and 100%")
    }

    /** Creates or updates a student. Returns the student id. */
    suspend fun saveStudent(id: Long?, form: StudentForm, by: String): Long = db.withTransaction {
        val (settings, card) = currentCard()
        validate(form, card)
        val name = form.name.trim().replace(Regex("\\s+"), " ")
        val key = name.lowercase(Locale.ROOT)
        studentDao.findDuplicate(key, form.className, id ?: -1)?.let {
            throw FeeException("${it.name} is already registered in ${it.className} (${it.code}). Duplicate registration is not allowed.")
        }
        val now = System.currentTimeMillis()
        val yearStart = LocalDate.ofEpochDay(settings.academicYearStart)
        if (id == null) {
            val newId = studentDao.insert(
                StudentEntity(
                    code = nextStudentCode(), name = name, nameKey = key, className = form.className, studentType = form.type.name,
                    studentStatus = form.status.name, bookType = form.bookType.name, paymentTerm = form.term.name,
                    discountBp = form.discountBp, remarks = form.remarks.trim(), registeredOn = LocalDate.now().toEpochDay(), updatedAt = now,
                ),
            )
            val fee = preview(card, form.copy(name = name))
            writeAssessment(newId, fee, form.term, yearStart)
            audit(by, "STUDENT_ADD", "$name ${form.className} net ${fee.netTotal}")
            newId
        } else {
            val old = studentDao.get(id) ?: throw FeeException("Student not found")
            val updated = old.copy(
                name = name, nameKey = key, className = form.className, studentType = form.type.name, studentStatus = form.status.name,
                bookType = form.bookType.name, paymentTerm = form.term.name, discountBp = form.discountBp, remarks = form.remarks.trim(), updatedAt = now,
            )
            studentDao.update(updated)
            val feeChanged = old.className != updated.className || old.studentType != updated.studentType || old.studentStatus != updated.studentStatus ||
                old.bookType != updated.bookType || old.discountBp != updated.discountBp
            val existingFee = feeDao.get(id)?.toDomain()
            when {
                feeChanged || existingFee == null -> {
                    val fee = preview(card, form)
                    writeAssessment(id, fee, form.term, yearStart)
                    audit(by, "STUDENT_REASSESS", "${old.code}: net ${existingFee?.netTotal ?: 0} -> ${fee.netTotal}")
                }
                old.paymentTerm != updated.paymentTerm -> writeAssessment(id, existingFee ?: preview(card, form), form.term, yearStart)
            }
            audit(by, "STUDENT_EDIT", "${old.code} $name")
            id
        }
    }

    suspend fun setStudentActive(id: Long, active: Boolean, by: String) = db.withTransaction {
        val s = studentDao.get(id) ?: throw FeeException("Student not found")
        studentDao.update(s.copy(active = active, updatedAt = System.currentTimeMillis()))
        audit(by, if (active) "STUDENT_REACTIVATE" else "STUDENT_DEACTIVATE", s.code)
    }

    /** Moves a student to another class; payments are kept. Optionally re-assesses with current rates. */
    suspend fun promote(id: Long, newClass: String, markOld: Boolean, recalculate: Boolean, by: String) = db.withTransaction {
        val (settings, card) = currentCard()
        if (card.rateFor(newClass) == null) throw FeeException("Class '$newClass' has no fee rates")
        val s = studentDao.get(id) ?: throw FeeException("Student not found")
        studentDao.findDuplicate(s.nameKey, newClass, id)?.let { throw FeeException("${it.name} already exists in $newClass (${it.code})") }
        val updated = s.copy(className = newClass, studentStatus = if (markOld) StudentStatus.OLD.name else s.studentStatus, updatedAt = System.currentTimeMillis())
        studentDao.update(updated)
        if (recalculate) {
            val fee = FeeCalculator.assess(card, updated.toDomain())
            writeAssessment(id, fee, PaymentTerm.valueOf(updated.paymentTerm), LocalDate.ofEpochDay(settings.academicYearStart))
        }
        audit(by, "STUDENT_PROMOTE", "${s.code}: ${s.className} -> $newClass${if (recalculate) " (fees recalculated)" else ""}")
    }

    /** Explicit re-assessment of existing students with the CURRENT fee rates. */
    suspend fun recalculate(ids: List<Long>?, by: String): Int = db.withTransaction {
        val (settings, card) = currentCard()
        val targets = if (ids == null) studentDao.getAll().filter { it.active } else ids.mapNotNull { studentDao.get(it) }
        val start = LocalDate.ofEpochDay(settings.academicYearStart)
        targets.forEach { s ->
            val fee = FeeCalculator.assess(card, s.toDomain())
            writeAssessment(s.id, fee, PaymentTerm.valueOf(s.paymentTerm), start)
        }
        audit(by, "RECALCULATE", "${targets.size} student(s) re-assessed with current rates")
        targets.size
    }

    // --------------------------------------------------------------- payments
    /** Records a payment and its receipt atomically. Returns the receipt id. */
    suspend fun recordPayment(req: PaymentRequest, by: String): Long = db.withTransaction {
        if (req.amount <= 0) throw FeeException("Amount must be greater than zero")
        if (req.date.isAfter(LocalDate.now())) throw FeeException("Collection date cannot be in the future")
        val s = studentDao.get(req.studentId) ?: throw FeeException("Student not found")
        if (!s.active) throw FeeException("${s.name} is deactivated. Reactivate the student before collecting fees.")
        val net = feeDao.get(s.id)?.netTotal ?: 0
        val previousBalance = net - txDao.validForStudent(s.id).sumOf { it.amount }
        val now = System.currentTimeMillis()
        val txId = txDao.insert(
            PaymentTransactionEntity(
                studentId = s.id, date = req.date.toEpochDay(), amount = req.amount, scheduleSeq = req.scheduleSeq,
                paymentFor = req.paymentFor.trim(), remarks = req.remarks.trim(), receivedBy = by, createdAt = now,
            ),
        )
        val receiptNo = nextReceiptNo(req.date)
        val rid = receiptDao.insert(
            ReceiptEntity(receiptNo = receiptNo, transactionId = txId, issuedAt = now, previousBalance = previousBalance, remainingBalance = previousBalance - req.amount, receivedBy = by),
        )
        audit(by, "PAYMENT", "$receiptNo ${s.code} ${req.amount}")
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

    suspend fun voidPayment(txId: Long, reason: String, by: String) = db.withTransaction {
        if (reason.isBlank()) throw FeeException("A reason is required to void a payment")
        val t = txDao.get(txId) ?: throw FeeException("Transaction not found")
        if (t.voided) throw FeeException("Already voided")
        txDao.update(t.copy(voided = true, voidReason = reason.trim(), voidedBy = by))
        audit(by, "PAYMENT_VOID", "tx $txId amount ${t.amount}: ${reason.trim()}")
    }

    suspend fun markPrinted(receiptId: Long) = withContext(Dispatchers.IO) { receiptDao.incrementPrint(receiptId) }

    suspend fun receipt(receiptId: Long): ReceiptEntity? = withContext(Dispatchers.IO) { receiptDao.get(receiptId) }

    // ------------------------------------------------------------ settings
    suspend fun saveRates(card: FeeRateCard, by: String) = db.withTransaction {
        if (card.classes.isEmpty()) throw FeeException("At least one class is required")
        if (card.classes.map { it.className.uppercase(Locale.ROOT) }.toSet().size != card.classes.size) throw FeeException("Class names must be unique")
        val all = card.classes.flatMap { listOf(it.trainingFee, it.bookDayMyanmar, it.bookDayIntl, it.bookHostelMyanmar, it.bookHostelIntl, it.hostelFee) } +
            listOf(card.instrumentFee, card.laundryPerMonth, card.entranceFee)
        if (all.any { it < 0 }) throw FeeException("Fee amounts cannot be negative")
        if (card.monthsPerYear !in 1..12) throw FeeException("Months per year must be 1–12")
        val s = settingsDao.get()!!
        settingsDao.upsert(s.copy(instrumentFee = card.instrumentFee, laundryPerMonth = card.laundryPerMonth, monthsPerYear = card.monthsPerYear, entranceFee = card.entranceFee, ratesUpdatedAt = System.currentTimeMillis()))
        rateDao.deleteAll()
        rateDao.insertAll(card.classes.mapIndexed { i, c -> c.copy(sortOrder = i).toEntity() })
        audit(by, "RATES_UPDATE", "Fee rates changed (${card.classes.size} classes). Existing assessments unchanged.")
    }

    suspend fun saveSchoolProfile(name: String, address: String, phone: String, receiptPrefix: String, yearStart: LocalDate, by: String) = db.withTransaction {
        if (name.isBlank()) throw FeeException("School name is required")
        if (!Regex("^[A-Za-z0-9]{1,8}$").matches(receiptPrefix.trim())) throw FeeException("Receipt prefix: 1–8 letters or digits")
        val s = settingsDao.get()!!
        settingsDao.upsert(s.copy(schoolName = name.trim(), address = address.trim(), phone = phone.trim(), receiptPrefix = receiptPrefix.trim().uppercase(Locale.ROOT), academicYearStart = yearStart.toEpochDay()))
        if (s.academicYearStart != yearStart.toEpochDay()) {
            // Due dates follow the academic-year start; amounts and payments are untouched.
            studentDao.getAll().forEach { st ->
                val fee = feeDao.get(st.id) ?: return@forEach
                scheduleDao.deleteForStudent(st.id)
                scheduleDao.insertAll(ScheduleCalculator.generate(PaymentTerm.valueOf(st.paymentTerm), fee.netTotal, yearStart).map { it.toEntity(st.id) })
            }
        }
        audit(by, "SCHOOL_PROFILE", "Updated school profile")
    }

    // -------------------------------------------------------------- import
    suspend fun applyImport(imp: WorkbookImport, updateRates: Boolean, by: String): ImportSummary = db.withTransaction {
        val problems = ArrayList<String>()
        var ratesUpdated = false
        if (updateRates && imp.rates != null) {
            val s = settingsDao.get()!!
            val r = imp.rates
            settingsDao.upsert(s.copy(instrumentFee = r.instrumentFee, laundryPerMonth = r.laundryPerMonth, monthsPerYear = r.monthsPerYear, entranceFee = r.entranceFee, ratesUpdatedAt = System.currentTimeMillis()))
            rateDao.deleteAll()
            rateDao.insertAll(r.classes.map { it.toEntity() })
            ratesUpdated = true
        }
        val (settings, card) = currentCard()
        val start = LocalDate.ofEpochDay(settings.academicYearStart)
        val existing = studentDao.getAll()
        val keyToId = existing.associate { ImportPlanner.studentKey(it.name, it.className) to it.id }.toMutableMap()
        val candidates = existing.map { MatchCandidate(it.id, it.code, it.name, it.className) }.toMutableList()
        var added = 0
        var dupStudents = 0
        val now = System.currentTimeMillis()
        for (st in imp.students) {
            val key = ImportPlanner.studentKey(st.name, st.className)
            if (keyToId.containsKey(key)) { dupStudents++; continue }
            if (card.rateFor(st.className) == null) { problems += "Student_Register row ${st.row}: class ${st.className} has no rates – skipped"; continue }
            val code = st.code?.takeIf { studentDao.countCode(it) == 0 } ?: nextStudentCode()
            val id = studentDao.insert(
                StudentEntity(
                    code = code, name = st.name, nameKey = st.name.trim().lowercase(Locale.ROOT), className = st.className, studentType = st.type.name,
                    studentStatus = st.status.name, bookType = st.bookType.name, paymentTerm = st.term.name, discountBp = st.discountBp,
                    remarks = st.remarks, registeredOn = LocalDate.now().toEpochDay(), updatedAt = now,
                ),
            )
            writeAssessment(id, FeeCalculator.assess(card, st.className, st.type, st.status, st.bookType, st.discountBp), st.term, start)
            keyToId[key] = id
            candidates += MatchCandidate(id, code, st.name, st.className)
            added++
        }
        var payAdded = 0
        var dupPays = 0
        val terms = HashMap<Long, PaymentTerm>()
        // Running balances so imported receipts show correct previous/remaining balances.
        val balances = HashMap<Long, Long>()
        for (p in imp.payments.sortedWith(compareBy({ it.date }, { it.row }))) {
            if (p.receiptNo.isNotBlank() && receiptDao.countNo(p.receiptNo) > 0) { dupPays++; continue }
            val id = when (val m = ImportPlanner.match(p, candidates)) {
                is MatchResult.Found -> m.candidate.key
                is MatchResult.Problem -> { problems += "Cash_Collection row ${p.row}: ${m.message} – skipped"; continue }
            }
            val st = studentDao.get(id)!!
            val term = terms.getOrPut(id) { PaymentTerm.valueOf(st.paymentTerm) }
            val prev = balances[id] ?: ((feeDao.get(id)?.netTotal ?: 0) - txDao.validForStudent(id).sumOf { it.amount })
            val txId = txDao.insert(
                PaymentTransactionEntity(
                    studentId = id, date = p.date.toEpochDay(), amount = p.amount, scheduleSeq = ScheduleCalculator.parsePaymentFor(p.paymentFor, term),
                    paymentFor = p.paymentFor, remarks = p.remarks, receivedBy = p.receivedBy, createdAt = now, imported = true,
                ),
            )
            val no = p.receiptNo.ifBlank { nextReceiptNo(p.date) }
            receiptDao.insert(ReceiptEntity(receiptNo = no, transactionId = txId, issuedAt = now, previousBalance = prev, remainingBalance = prev - p.amount, receivedBy = p.receivedBy))
            balances[id] = prev - p.amount
            payAdded++
        }
        audit(by, "IMPORT", "rates=$ratesUpdated students=$added payments=$payAdded")
        ImportSummary(ratesUpdated, added, dupStudents, payAdded, dupPays, problems)
    }

    // ------------------------------------------------------- backup/restore
    fun backupTo(out: OutputStream) {
        val file = context.getDatabasePath(AppDatabase.NAME)
        // An exclusive transaction blocks writers while the single database file is copied.
        db.runInTransaction(Runnable { file.inputStream().use { it.copyTo(out) } })
        out.flush()
    }

    /** Validates a backup file and replaces the database. The caller must restart the app afterwards. */
    fun restoreFrom(input: InputStream) {
        val tmp = File(context.cacheDir, "restore_candidate.db")
        input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        val header = tmp.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
        if (String(header, Charsets.US_ASCII) != "SQLite format 3\u0000") { tmp.delete(); throw FeeException("This file is not a School Fee Manager backup") }
        try {
            SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { check ->
                check.rawQuery("SELECT COUNT(*) FROM students", null).use { it.moveToFirst() }
                check.rawQuery("SELECT COUNT(*) FROM payment_transactions", null).use { it.moveToFirst() }
                check.rawQuery("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", null).use { c -> c.moveToFirst(); if (c.getInt(0) == 0) throw FeeException("Backup has no Admin user") }
            }
        } catch (e: FeeException) {
            tmp.delete(); throw e
        } catch (e: Exception) {
            tmp.delete(); throw FeeException("Backup file is damaged or from another app: ${e.message}")
        }
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        // Safety copy of the current data before overwriting.
        File(context.filesDir, "before_restore_${System.currentTimeMillis()}.db").let { safety -> dbFile.copyTo(safety, overwrite = true) }
        db.close()
        listOf("-journal", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
        tmp.copyTo(dbFile, overwrite = true)
        tmp.delete()
    }
}
