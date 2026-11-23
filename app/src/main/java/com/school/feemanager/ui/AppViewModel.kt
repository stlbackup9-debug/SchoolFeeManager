package com.school.feemanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.feemanager.data.AppSnapshot
import com.school.feemanager.data.AuditLogEntity
import com.school.feemanager.data.EnrollmentForm
import com.school.feemanager.data.FeeException
import com.school.feemanager.data.FeeRepository
import com.school.feemanager.data.ImportSummary
import com.school.feemanager.data.PaymentRequest
import com.school.feemanager.data.StudentForm
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.ReportTable
import com.school.feemanager.domain.Role
import com.school.feemanager.io.ImportBundle
import com.school.feemanager.io.ImportModule
import com.school.feemanager.io.ModuleImporter
import com.school.feemanager.io.WorkbookExporter
import com.school.feemanager.io.WorkbookImport
import com.school.feemanager.io.WorkbookImporter
import com.school.feemanager.util.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class Session(val userId: Long, val username: String, val fullName: String, val role: Role, val mustChangePassword: Boolean) {
    val isAdmin: Boolean get() = role == Role.ADMIN
    val display: String get() = fullName.ifBlank { username }
}

/**
 * Single activity-scoped ViewModel. Screens observe [snapshot] (all data, recomputed by the
 * repository on every database change) and call the action methods below. Errors are reported
 * through [message] and shown as a snackbar.
 */
class AppViewModel(private val repo: FeeRepository) : ViewModel() {

    val snapshot: StateFlow<AppSnapshot?> = repo.snapshot().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val auditLog: StateFlow<List<AuditLogEntity>> = repo.auditLog(300).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            try { repo.ensureSeeded() } catch (e: Exception) { _message.value = "Database error: ${e.message}" }
        }
    }

    fun clearMessage() { _message.value = null }
    fun toast(text: String) { _message.value = text }

    private fun action(ok: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
                if (ok != null) _message.value = ok
            } catch (e: FeeException) {
                _message.value = e.message
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "Error: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _busy.value = false
            }
        }
    }

    private fun user(): String = _session.value?.username ?: throw FeeException("Please log in again")
    private fun admin(): String {
        val s = _session.value ?: throw FeeException("Please log in again")
        if (!s.isAdmin) throw FeeException("Only an Admin can do this")
        return s.username
    }

    // ------------------------------------------------------------ auth
    fun login(username: String, password: String) = action {
        val u = repo.login(username, password) ?: throw FeeException("Wrong username or password, or the account is disabled")
        _session.value = Session(u.id, u.username, u.fullName, Role.valueOf(u.role), u.mustChangePassword)
    }

    fun logout() { _session.value = null }

    fun changeOwnPassword(current: String, newPassword: String, confirm: String, onDone: () -> Unit) = action("Password changed") {
        val s = _session.value ?: throw FeeException("Please log in again")
        if (newPassword != confirm) throw FeeException("New passwords do not match")
        if (newPassword == current) throw FeeException("Choose a different password")
        repo.login(s.username, current) ?: throw FeeException("Current password is wrong")
        repo.setPassword(s.userId, newPassword, s.username, forceChange = false)
        _session.value = s.copy(mustChangePassword = false)
        onDone()
    }

    fun addUser(username: String, fullName: String, role: Role, password: String, onDone: () -> Unit) = action("User added") {
        repo.addUser(username, fullName, role, password, admin()); onDone()
    }

    fun resetPassword(userId: Long, newPassword: String) = action("Password reset – user must change it at next login") {
        repo.setPassword(userId, newPassword, admin(), forceChange = true)
    }

    fun setUserActive(userId: Long, active: Boolean) = action {
        if (userId == _session.value?.userId && !active) throw FeeException("You cannot disable your own account")
        repo.setUserActive(userId, active, admin())
    }

    fun setUserRole(userId: Long, role: Role) = action { repo.setUserRole(userId, role, admin()) }

    // -------------------------------------------------------- students
    fun saveStudent(id: Long?, form: StudentForm, firstEnrollment: EnrollmentForm?, onSaved: (Long) -> Unit) = action(if (id == null) "Student registered" else "Student updated") {
        val newId = repo.saveStudent(id, form, firstEnrollment, admin())
        onSaved(newId)
    }

    fun setStudentActive(id: Long, active: Boolean) = action(if (active) "Student reactivated" else "Student deactivated") {
        repo.setStudentActive(id, active, admin())
    }

    fun saveEnrollment(id: Long?, studentId: Long, form: EnrollmentForm, onSaved: (Long) -> Unit) = action(if (id == null) "Class added" else "Class updated") {
        onSaved(repo.saveEnrollment(id, studentId, form, admin()))
    }

    fun setEnrollmentActive(id: Long, active: Boolean) = action(if (active) "Class reactivated" else "Class deactivated") {
        repo.setEnrollmentActive(id, active, admin())
    }

    fun recalculate(enrollmentIds: List<Long>?) = action {
        val n = repo.recalculate(enrollmentIds, admin())
        _message.value = "$n class enrollment(s) re-assessed with current fee rates"
    }

    // -------------------------------------------------------- payments
    fun recordPayment(req: PaymentRequest, onDone: (Long) -> Unit) = action(if (req.items.size > 1) "Payment saved – combined receipt" else "Payment saved") {
        val rid = repo.recordPayment(req, user())
        onDone(rid)
    }

    fun voidReceipt(receiptId: Long, reason: String) = action("Receipt voided") { repo.voidReceipt(receiptId, reason, admin()) }

    fun markPrinted(receiptIds: List<Long>) = viewModelScope.launch { try { repo.markPrinted(receiptIds) } catch (_: Exception) {} }

    // -------------------------------------------------------- settings
    fun saveRates(card: FeeRateCard, onDone: () -> Unit) = action("Fee rates saved. New registrations will use them.") {
        repo.saveRates(card, admin()); onDone()
    }

    fun saveSchool(name: String, address: String, phone: String, prefix: String, yearStart: LocalDate, onDone: () -> Unit) = action("School profile saved") {
        repo.saveSchoolProfile(name, address, phone, prefix, yearStart, admin()); onDone()
    }

    // ------------------------------------------------------ import/export
    private suspend fun readBytes(file: FileRef): ByteArray = withContext(Dispatchers.IO) { file.openInput().use { it.readBytes() } }

    private suspend fun write(file: FileRef, block: (java.io.OutputStream) -> Unit) = withContext(Dispatchers.IO) { file.openOutput().use(block) }

    fun parseImport(file: FileRef, onParsed: (ImportBundle) -> Unit) = action {
        admin()
        val card = snapshot.value?.card ?: FeeRateCard.workbookDefaults()
        val bytes = readBytes(file)
        val imp = withContext(Dispatchers.Default) {
            try { WorkbookImporter.read(bytes.inputStream(), card) } catch (e: Exception) { throw FeeException("Not a valid .xlsx workbook: ${e.message}") }
        }
        onParsed(imp)
    }

    fun parseModuleImport(module: ImportModule, file: FileRef, onParsed: (ImportBundle) -> Unit) = action {
        admin()
        val card = snapshot.value?.card ?: FeeRateCard.workbookDefaults()
        val bytes = readBytes(file)
        val imp = withContext(Dispatchers.Default) {
            try { ModuleImporter.read(module, bytes, card) } catch (e: IllegalArgumentException) { throw FeeException(e.message ?: "File not understood") }
            catch (e: Exception) { throw FeeException("Cannot read the file (use .xlsx or .csv): ${e.message}") }
        }
        onParsed(imp)
    }

    /** Runs the import without saving anything and reports what WOULD happen. */
    fun previewImport(imp: ImportBundle, updateRates: Boolean, onDone: (ImportSummary) -> Unit) = action {
        onDone(repo.applyImport(imp, updateRates, dryRun = true, by = admin()))
    }

    fun applyImport(imp: ImportBundle, updateRates: Boolean, onDone: (ImportSummary) -> Unit) = action {
        val r = repo.applyImport(imp, updateRates, dryRun = false, by = admin())
        onDone(r)
    }

    fun writeTemplate(file: FileRef, module: ImportModule, csv: Boolean) = action("Template saved") {
        val bytes = if (csv) ModuleImporter.templateCsv(module) else ModuleImporter.templateXlsx(module)
        write(file) { it.write(bytes) }
    }

    fun exportWorkbook(file: FileRef) = action("Workbook exported") {
        val s = snapshot.value ?: throw FeeException("Data is still loading")
        val bytes = withContext(Dispatchers.Default) { WorkbookExporter.export(s.settings.schoolName, s.ledgers, s.payments, s.card, s.dashboard) }
        write(file) { it.write(bytes) }
    }

    fun exportReport(file: FileRef, table: ReportTable, asPdf: Boolean) = action(if (asPdf) "PDF saved" else "Excel file saved") {
        val school = snapshot.value?.settings?.schoolName ?: ""
        write(file) { out -> if (asPdf) PdfExporter.writeReport(table, school, out) else out.write(WorkbookExporter.exportReport(table, school)) }
    }

    fun backup(file: FileRef) = action("Backup saved") {
        admin()
        write(file) { repo.backupTo(it) }
    }

    fun restore(file: FileRef, onRestored: () -> Unit) = action {
        admin()
        withContext(Dispatchers.IO) { repo.restoreFrom(file.openInput()) }
        onRestored()
    }
}
