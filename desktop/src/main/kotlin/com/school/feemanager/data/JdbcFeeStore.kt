package com.school.feemanager.data

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types
import java.util.concurrent.Executors
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Windows/desktop implementation of [FeeStore]: the same SQLite schema as the Android app (Room v2),
 * accessed through JDBC. A database file (or backup) can therefore move between phone and PC.
 *
 * All access runs on one database thread. Flows re-query when a write is committed.
 */
class JdbcFeeStore(private val file: File) : FeeStore {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "school-fees-db").apply { isDaemon = true } }.asCoroutineDispatcher()
    private val changes = MutableStateFlow(0L)
    private var dirty = false
    private var inTransaction = false
    private var conn: Connection = runBlocking(dispatcher) { open(file) }

    // ------------------------------------------------------------ connection
    private fun open(f: File): Connection {
        f.absoluteFile.parentFile?.mkdirs()
        val c = DriverManager.getConnection("jdbc:sqlite:" + f.absolutePath)
        c.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON")
            st.execute("PRAGMA busy_timeout = 5000")
        }
        upgrade(c)
        return c
    }

    /** Creates the schema in a new file, or upgrades a version-1 database (e.g. a restored phone backup). */
    private fun upgrade(c: Connection) {
        val version = c.scalar("PRAGMA user_version")
        val hasTables = c.scalar("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'students'") > 0
        when {
            !hasTables -> c.inTx { MigrationSql.CREATE_ALL_V2.forEach { sql -> c.exec(sql) } }
            version == 1L -> c.inTx {
                var start = java.time.LocalDate.of(java.time.LocalDate.now().year, 6, 1).toEpochDay()
                var months = 12
                c.prepareStatement("SELECT academicYearStart, monthsPerYear FROM school_settings WHERE id = 1").use { ps ->
                    ps.executeQuery().use { rs -> if (rs.next()) { start = rs.getLong(1); months = rs.getInt(2) } }
                }
                c.exec("PRAGMA defer_foreign_keys = ON")
                MigrationSql.statements(start, months).forEach { s -> c.exec(s.sql, *s.args) }
                // Room's identity table, so the file stays valid for the Android app
                MigrationSql.CREATE_ALL_V2.filter { it.contains("room_master_table") }.forEach { c.exec(it) }
            }
            version > MigrationSql.VERSION -> throw FeeException("This database was made by a newer version of School Fee Manager (v$version). Please update the app.")
        }
        c.exec("PRAGMA user_version = ${MigrationSql.VERSION}")
    }

    private fun Connection.inTx(block: () -> Unit) {
        autoCommit = false
        try { block(); commit() } catch (e: Throwable) { rollback(); throw e } finally { autoCommit = true }
    }

    // ---------------------------------------------------------------- helpers
    private fun bind(ps: java.sql.PreparedStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, a ->
            when (a) {
                null -> ps.setNull(i + 1, Types.NULL)
                is Boolean -> ps.setInt(i + 1, if (a) 1 else 0)
                is Int -> ps.setInt(i + 1, a)
                is Long -> ps.setLong(i + 1, a)
                is String -> ps.setString(i + 1, a)
                else -> ps.setObject(i + 1, a)
            }
        }
    }

    private fun Connection.exec(sql: String, vararg args: Any?) { prepareStatement(sql).use { ps -> bind(ps, args); ps.execute() } }

    private fun Connection.scalar(sql: String, vararg args: Any?): Long =
        prepareStatement(sql).use { ps -> bind(ps, args); ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0 } }

    private fun <T> Connection.list(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
        prepareStatement(sql).use { ps ->
            bind(ps, args)
            ps.executeQuery().use { rs -> val out = ArrayList<T>(); while (rs.next()) out += map(rs); out }
        }

    private fun changed() { if (inTransaction) dirty = true else changes.value++ }

    private suspend fun <T> db(block: (Connection) -> T): T = withContext(dispatcher) { block(conn) }

    private suspend fun write(sql: String, vararg args: Any?) = db { c -> c.exec(sql, *args); changed() }

    private suspend fun insert(sql: String, vararg args: Any?): Long = db { c ->
        c.exec(sql, *args)
        changed()
        c.scalar("SELECT last_insert_rowid()")
    }

    private suspend fun <T> all(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> = db { c -> c.list(sql, *args, map = map) }

    private fun <T> observe(sql: String, vararg args: Any?, map: (ResultSet) -> T): Flow<List<T>> =
        changes.map { all(sql, *args, map = map) }.distinctUntilChanged()

    private fun ResultSet.intOrNull(col: String): Int? = getInt(col).let { if (wasNull()) null else it }

    // ---------------------------------------------------------------- mappers
    private fun settings(r: ResultSet) = SchoolSettingsEntity(
        id = r.getInt("id"), schoolName = r.getString("schoolName"), address = r.getString("address"), phone = r.getString("phone"),
        instrumentFee = r.getLong("instrumentFee"), laundryPerMonth = r.getLong("laundryPerMonth"), monthsPerYear = r.getInt("monthsPerYear"),
        entranceFee = r.getLong("entranceFee"), academicYearStart = r.getLong("academicYearStart"), receiptPrefix = r.getString("receiptPrefix"),
        nextReceiptSeq = r.getLong("nextReceiptSeq"), nextStudentSeq = r.getLong("nextStudentSeq"), ratesUpdatedAt = r.getLong("ratesUpdatedAt"),
    )

    private fun rate(r: ResultSet) = FeeRateEntity(
        id = r.getLong("id"), className = r.getString("className"), sortOrder = r.getInt("sortOrder"), trainingFee = r.getLong("trainingFee"),
        bookDayMyanmar = r.getLong("bookDayMyanmar"), bookDayIntl = r.getLong("bookDayIntl"), bookHostelMyanmar = r.getLong("bookHostelMyanmar"),
        bookHostelIntl = r.getLong("bookHostelIntl"), hostelFee = r.getLong("hostelFee"), startMonth = r.getInt("startMonth"),
        startDay = r.getInt("startDay"), periodMonths = r.getInt("periodMonths"),
    )

    private fun student(r: ResultSet) = StudentEntity(
        id = r.getLong("id"), code = r.getString("code"), enrollNo = r.getString("enrollNo"), name = r.getString("name"), nameKey = r.getString("nameKey"),
        remarks = r.getString("remarks"), active = r.getBoolean("active"), registeredOn = r.getLong("registeredOn"), updatedAt = r.getLong("updatedAt"),
    )

    private fun enrollment(r: ResultSet) = EnrollmentEntity(
        id = r.getLong("id"), studentId = r.getLong("studentId"), className = r.getString("className"), academicYear = r.getString("academicYear"),
        startDate = r.getLong("startDate"), periodMonths = r.getInt("periodMonths"), studentType = r.getString("studentType"),
        studentStatus = r.getString("studentStatus"), bookType = r.getString("bookType"), paymentTerm = r.getString("paymentTerm"),
        discountBp = r.getInt("discountBp"), remarks = r.getString("remarks"), active = r.getBoolean("active"), createdOn = r.getLong("createdOn"),
        updatedAt = r.getLong("updatedAt"), trainingFee = r.getLong("trainingFee"), instrumentFee = r.getLong("instrumentFee"), bookFee = r.getLong("bookFee"),
        hostelFee = r.getLong("hostelFee"), laundryFee = r.getLong("laundryFee"), entranceFee = r.getLong("entranceFee"), grossTotal = r.getLong("grossTotal"),
        discountAmount = r.getLong("discountAmount"), netTotal = r.getLong("netTotal"), assessedAt = r.getLong("assessedAt"),
    )

    private fun schedule(r: ResultSet) = PaymentScheduleEntity(
        id = r.getLong("id"), enrollmentId = r.getLong("enrollmentId"), seq = r.getInt("seq"), label = r.getString("label"),
        dueDate = r.getLong("dueDate"), amount = r.getLong("amount"),
    )

    private fun tx(r: ResultSet) = PaymentTransactionEntity(
        id = r.getLong("id"), receiptId = r.getLong("receiptId"), studentId = r.getLong("studentId"), enrollmentId = r.getLong("enrollmentId"),
        date = r.getLong("date"), amount = r.getLong("amount"), scheduleSeq = r.intOrNull("scheduleSeq"), paymentFor = r.getString("paymentFor"),
        remarks = r.getString("remarks"), receivedBy = r.getString("receivedBy"), createdAt = r.getLong("createdAt"), voided = r.getBoolean("voided"),
        voidReason = r.getString("voidReason"), voidedBy = r.getString("voidedBy"), imported = r.getBoolean("imported"),
    )

    private fun receipt(r: ResultSet) = ReceiptEntity(
        id = r.getLong("id"), receiptNo = r.getString("receiptNo"), date = r.getLong("date"), payerName = r.getString("payerName"),
        issuedAt = r.getLong("issuedAt"), previousBalance = r.getLong("previousBalance"), remainingBalance = r.getLong("remainingBalance"),
        receivedBy = r.getString("receivedBy"), remarks = r.getString("remarks"), printCount = r.getInt("printCount"), voided = r.getBoolean("voided"),
        voidReason = r.getString("voidReason"), voidedBy = r.getString("voidedBy"), imported = r.getBoolean("imported"),
    )

    private fun user(r: ResultSet) = UserEntity(
        id = r.getLong("id"), username = r.getString("username"), fullName = r.getString("fullName"), role = r.getString("role"),
        passwordHash = r.getString("passwordHash"), salt = r.getString("salt"), active = r.getBoolean("active"), mustChangePassword = r.getBoolean("mustChangePassword"),
    )

    private fun audit(r: ResultSet) = AuditLogEntity(id = r.getLong("id"), at = r.getLong("at"), username = r.getString("username"), action = r.getString("action"), details = r.getString("details"))

    // ------------------------------------------------------------------ DAOs
    private val settings = object : SettingsDao {
        override fun observe(): Flow<SchoolSettingsEntity?> = observe("SELECT * FROM school_settings WHERE id = 1", map = ::settings).map { it.firstOrNull() }
        override suspend fun get(): SchoolSettingsEntity? = all("SELECT * FROM school_settings WHERE id = 1", map = ::settings).firstOrNull()
        override suspend fun upsert(s: SchoolSettingsEntity) = write(
            "INSERT OR REPLACE INTO school_settings (id, schoolName, address, phone, instrumentFee, laundryPerMonth, monthsPerYear, entranceFee, academicYearStart, receiptPrefix, nextReceiptSeq, nextStudentSeq, ratesUpdatedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            s.id, s.schoolName, s.address, s.phone, s.instrumentFee, s.laundryPerMonth, s.monthsPerYear, s.entranceFee, s.academicYearStart, s.receiptPrefix, s.nextReceiptSeq, s.nextStudentSeq, s.ratesUpdatedAt,
        )
    }

    private val rates = object : FeeRateDao {
        override fun observeAll() = observe("SELECT * FROM fee_rates ORDER BY sortOrder", map = ::rate)
        override suspend fun getAll() = all("SELECT * FROM fee_rates ORDER BY sortOrder", map = ::rate)
        override suspend fun deleteAll() = write("DELETE FROM fee_rates")
        override suspend fun insertAll(list: List<FeeRateEntity>) = list.forEach { r ->
            write(
                "INSERT INTO fee_rates (${if (r.id != 0L) "id, " else ""}className, sortOrder, trainingFee, bookDayMyanmar, bookDayIntl, bookHostelMyanmar, bookHostelIntl, hostelFee, startMonth, startDay, periodMonths) VALUES (${if (r.id != 0L) "?, " else ""}?,?,?,?,?,?,?,?,?,?,?)",
                *(if (r.id != 0L) arrayOf<Any?>(r.id) else emptyArray()),
                r.className, r.sortOrder, r.trainingFee, r.bookDayMyanmar, r.bookDayIntl, r.bookHostelMyanmar, r.bookHostelIntl, r.hostelFee, r.startMonth, r.startDay, r.periodMonths,
            )
        }
    }

    private val students = object : StudentDao {
        override fun observeAll() = observe("SELECT * FROM students ORDER BY name COLLATE NOCASE", map = ::student)
        override suspend fun getAll() = all("SELECT * FROM students", map = ::student)
        override suspend fun get(id: Long) = all("SELECT * FROM students WHERE id = ?", id, map = ::student).firstOrNull()
        override suspend fun byEnrollNo(enrollNo: String, excludeId: Long) =
            all("SELECT * FROM students WHERE enrollNo = ? COLLATE NOCASE AND id != ? LIMIT 1", enrollNo, excludeId, map = ::student).firstOrNull()
        override suspend fun byCode(code: String) = all("SELECT * FROM students WHERE code = ? COLLATE NOCASE LIMIT 1", code, map = ::student).firstOrNull()
        override suspend fun countCode(code: String) = db { it.scalar("SELECT COUNT(*) FROM students WHERE code = ?", code).toInt() }
        override suspend fun insert(s: StudentEntity) = insert(
            "INSERT INTO students (code, enrollNo, name, nameKey, remarks, active, registeredOn, updatedAt) VALUES (?,?,?,?,?,?,?,?)",
            s.code, s.enrollNo, s.name, s.nameKey, s.remarks, s.active, s.registeredOn, s.updatedAt,
        )
        override suspend fun update(s: StudentEntity) = write(
            "UPDATE students SET code = ?, enrollNo = ?, name = ?, nameKey = ?, remarks = ?, active = ?, registeredOn = ?, updatedAt = ? WHERE id = ?",
            s.code, s.enrollNo, s.name, s.nameKey, s.remarks, s.active, s.registeredOn, s.updatedAt, s.id,
        )
    }

    private val enrollmentCols = "studentId, className, academicYear, startDate, periodMonths, studentType, studentStatus, bookType, paymentTerm, discountBp, remarks, active, createdOn, updatedAt, trainingFee, instrumentFee, bookFee, hostelFee, laundryFee, entranceFee, grossTotal, discountAmount, netTotal, assessedAt"

    private fun enrollmentArgs(e: EnrollmentEntity) = arrayOf<Any?>(
        e.studentId, e.className, e.academicYear, e.startDate, e.periodMonths, e.studentType, e.studentStatus, e.bookType, e.paymentTerm, e.discountBp,
        e.remarks, e.active, e.createdOn, e.updatedAt, e.trainingFee, e.instrumentFee, e.bookFee, e.hostelFee, e.laundryFee, e.entranceFee, e.grossTotal,
        e.discountAmount, e.netTotal, e.assessedAt,
    )

    private val enrollments = object : EnrollmentDao {
        override fun observeAll() = observe("SELECT * FROM enrollments", map = ::enrollment)
        override suspend fun getAll() = all("SELECT * FROM enrollments", map = ::enrollment)
        override suspend fun get(id: Long) = all("SELECT * FROM enrollments WHERE id = ?", id, map = ::enrollment).firstOrNull()
        override suspend fun forStudent(studentId: Long) = all("SELECT * FROM enrollments WHERE studentId = ?", studentId, map = ::enrollment)
        override suspend fun findDuplicate(studentId: Long, className: String, year: String, excludeId: Long) = all(
            "SELECT * FROM enrollments WHERE studentId = ? AND className = ? COLLATE NOCASE AND academicYear = ? COLLATE NOCASE AND id != ? LIMIT 1",
            studentId, className, year, excludeId, map = ::enrollment,
        ).firstOrNull()
        override suspend fun insert(e: EnrollmentEntity) =
            insert("INSERT INTO enrollments ($enrollmentCols) VALUES (${enrollmentCols.split(",").joinToString(",") { "?" }})", *enrollmentArgs(e))
        override suspend fun update(e: EnrollmentEntity) =
            write("UPDATE enrollments SET ${enrollmentCols.split(",").joinToString(", ") { it.trim() + " = ?" }} WHERE id = ?", *enrollmentArgs(e), e.id)
    }

    private val schedules = object : ScheduleDao {
        override fun observeAll() = observe("SELECT * FROM payment_schedule ORDER BY enrollmentId, seq", map = ::schedule)
        override suspend fun forEnrollment(id: Long) = all("SELECT * FROM payment_schedule WHERE enrollmentId = ? ORDER BY seq", id, map = ::schedule)
        override suspend fun deleteForEnrollment(id: Long) = write("DELETE FROM payment_schedule WHERE enrollmentId = ?", id)
        override suspend fun insertAll(list: List<PaymentScheduleEntity>) = list.forEach { s ->
            write("INSERT INTO payment_schedule (enrollmentId, seq, label, dueDate, amount) VALUES (?,?,?,?,?)", s.enrollmentId, s.seq, s.label, s.dueDate, s.amount)
        }
    }

    private val txCols = "receiptId, studentId, enrollmentId, date, amount, scheduleSeq, paymentFor, remarks, receivedBy, createdAt, voided, voidReason, voidedBy, imported"

    private fun txArgs(t: PaymentTransactionEntity) = arrayOf<Any?>(
        t.receiptId, t.studentId, t.enrollmentId, t.date, t.amount, t.scheduleSeq, t.paymentFor, t.remarks, t.receivedBy, t.createdAt, t.voided, t.voidReason, t.voidedBy, t.imported,
    )

    private val transactions = object : TransactionDao {
        override fun observeAll() = observe("SELECT * FROM payment_transactions ORDER BY date DESC, id DESC", map = ::tx)
        override suspend fun validForStudent(id: Long) = all("SELECT * FROM payment_transactions WHERE studentId = ? AND voided = 0", id, map = ::tx)
        override suspend fun validForEnrollment(id: Long) = all("SELECT * FROM payment_transactions WHERE enrollmentId = ? AND voided = 0", id, map = ::tx)
        override suspend fun forReceipt(receiptId: Long) = all("SELECT * FROM payment_transactions WHERE receiptId = ?", receiptId, map = ::tx)
        override suspend fun insert(t: PaymentTransactionEntity) = insert("INSERT INTO payment_transactions ($txCols) VALUES (${txCols.split(",").joinToString(",") { "?" }})", *txArgs(t))
        override suspend fun update(t: PaymentTransactionEntity) =
            write("UPDATE payment_transactions SET ${txCols.split(",").joinToString(", ") { it.trim() + " = ?" }} WHERE id = ?", *txArgs(t), t.id)
    }

    private val receiptCols = "receiptNo, date, payerName, issuedAt, previousBalance, remainingBalance, receivedBy, remarks, printCount, voided, voidReason, voidedBy, imported"

    private fun receiptArgs(r: ReceiptEntity) = arrayOf<Any?>(
        r.receiptNo, r.date, r.payerName, r.issuedAt, r.previousBalance, r.remainingBalance, r.receivedBy, r.remarks, r.printCount, r.voided, r.voidReason, r.voidedBy, r.imported,
    )

    private val receipts = object : ReceiptDao {
        override fun observeAll() = observe("SELECT * FROM receipts ORDER BY id DESC", map = ::receipt)
        override suspend fun get(id: Long) = all("SELECT * FROM receipts WHERE id = ?", id, map = ::receipt).firstOrNull()
        override suspend fun countNo(no: String) = db { it.scalar("SELECT COUNT(*) FROM receipts WHERE receiptNo = ? COLLATE NOCASE", no).toInt() }
        override suspend fun insert(r: ReceiptEntity) = insert("INSERT INTO receipts ($receiptCols) VALUES (${receiptCols.split(",").joinToString(",") { "?" }})", *receiptArgs(r))
        override suspend fun update(r: ReceiptEntity) =
            write("UPDATE receipts SET ${receiptCols.split(",").joinToString(", ") { it.trim() + " = ?" }} WHERE id = ?", *receiptArgs(r), r.id)
        override suspend fun incrementPrint(id: Long) = write("UPDATE receipts SET printCount = printCount + 1 WHERE id = ?", id)
    }

    private val users = object : UserDao {
        override fun observeAll() = observe("SELECT * FROM users ORDER BY username", map = ::user)
        override suspend fun byUsername(u: String) = all("SELECT * FROM users WHERE username = ? COLLATE NOCASE LIMIT 1", u, map = ::user).firstOrNull()
        override suspend fun get(id: Long) = all("SELECT * FROM users WHERE id = ?", id, map = ::user).firstOrNull()
        override suspend fun activeAdminCount() = db { it.scalar("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND active = 1").toInt() }
        override suspend fun count() = db { it.scalar("SELECT COUNT(*) FROM users").toInt() }
        override suspend fun insert(u: UserEntity) = insert(
            "INSERT INTO users (username, fullName, role, passwordHash, salt, active, mustChangePassword) VALUES (?,?,?,?,?,?,?)",
            u.username, u.fullName, u.role, u.passwordHash, u.salt, u.active, u.mustChangePassword,
        )
        override suspend fun update(u: UserEntity) = write(
            "UPDATE users SET username = ?, fullName = ?, role = ?, passwordHash = ?, salt = ?, active = ?, mustChangePassword = ? WHERE id = ?",
            u.username, u.fullName, u.role, u.passwordHash, u.salt, u.active, u.mustChangePassword, u.id,
        )
    }

    private val audits = object : AuditDao {
        override suspend fun insert(a: AuditLogEntity) { insert("INSERT INTO audit_log (at, username, action, details) VALUES (?,?,?,?)", a.at, a.username, a.action, a.details) }
        override fun observeRecent(limit: Int) = observe("SELECT * FROM audit_log ORDER BY at DESC LIMIT ?", limit, map = ::audit)
    }

    override fun settingsDao() = settings
    override fun feeRateDao() = rates
    override fun studentDao() = students
    override fun enrollmentDao() = enrollments
    override fun scheduleDao() = schedules
    override fun transactionDao() = transactions
    override fun receiptDao() = receipts
    override fun userDao() = users
    override fun auditDao() = audits

    // ------------------------------------------------------------ transactions
    private object InTransaction : AbstractCoroutineContextElement(Key) {
        object Key : CoroutineContext.Key<InTransaction>
    }

    override suspend fun <R> transaction(block: suspend () -> R): R {
        if (coroutineContext[InTransaction.Key] != null) return block()   // nested: join the outer transaction
        return withContext(dispatcher + InTransaction) {
            conn.autoCommit = false
            inTransaction = true
            dirty = false
            try {
                val r = block()
                conn.commit()
                r
            } catch (e: Throwable) {
                conn.rollback()
                dirty = false
                throw e
            } finally {
                conn.autoCommit = true
                inTransaction = false
                if (dirty) { dirty = false; changes.value++ }
            }
        }
    }

    // --------------------------------------------------------- backup/restore
    override fun backupTo(out: OutputStream) {
        val tmp = File.createTempFile("school_fees_backup", ".db").apply { delete() }
        try {
            runBlocking(dispatcher) { conn.exec("VACUUM INTO ?", tmp.absolutePath) }
            tmp.inputStream().use { it.copyTo(out) }
            out.flush()
        } finally {
            tmp.delete()
        }
    }

    override fun restoreFrom(input: InputStream) {
        val tmp = File(file.absoluteFile.parentFile, "restore_candidate.db")
        input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        val header = tmp.inputStream().use { s -> ByteArray(16).also { s.read(it) } }
        if (String(header, Charsets.US_ASCII) != "SQLite format 3\u0000") { tmp.delete(); throw FeeException("This file is not a School Fee Manager backup") }
        try {
            DriverManager.getConnection("jdbc:sqlite:" + tmp.absolutePath).use { c ->
                c.scalar("SELECT COUNT(*) FROM students")
                c.scalar("SELECT COUNT(*) FROM payment_transactions")
                if (c.scalar("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'") == 0L) throw FeeException("Backup has no Admin user")
                val v = c.scalar("PRAGMA user_version")
                if (v > MigrationSql.VERSION) throw FeeException("This backup was made by a newer version of School Fee Manager. Please update the app.")
            }
        } catch (e: FeeException) {
            tmp.delete(); throw e
        } catch (e: Exception) {
            tmp.delete(); throw FeeException("Backup file is damaged or from another app: ${e.message}")
        }
        runBlocking(dispatcher) {
            conn.close()
            if (file.exists()) file.copyTo(File(file.absoluteFile.parentFile, "before_restore_${System.currentTimeMillis()}.db"), overwrite = true)
            listOf("-journal", "-wal", "-shm").forEach { File(file.path + it).delete() }
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
            conn = open(file)
            changes.value++
        }
    }

    fun close() = runBlocking(dispatcher) { conn.close() }
}
