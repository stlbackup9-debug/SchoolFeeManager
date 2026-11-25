@file:OptIn(ExperimentalMaterial3Api::class)

package com.school.feemanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.school.feemanager.data.AppSnapshot
import com.school.feemanager.data.EnrollmentForm
import com.school.feemanager.data.StudentForm
import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.EnrollmentLedger
import com.school.feemanager.domain.EnrollmentRecord
import com.school.feemanager.domain.FeeAssessment
import com.school.feemanager.domain.FeeCalculator
import com.school.feemanager.domain.FeeChoice
import com.school.feemanager.domain.FeeKind
import com.school.feemanager.domain.PaymentPlan
import com.school.feemanager.domain.Money
import com.school.feemanager.domain.PayStatus
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.Reports
import com.school.feemanager.domain.ScheduleCalculator
import com.school.feemanager.domain.StudentLedger
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import com.school.feemanager.domain.academicYearLabel
import com.school.feemanager.ui.theme.Green
import com.school.feemanager.ui.theme.Red
import java.time.LocalDate

// ============================================================== list
@Composable
fun StudentsScreen(snap: AppSnapshot, session: Session, onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var cls by rememberSaveable { mutableStateOf<String?>(null) }
    var type by rememberSaveable { mutableStateOf<StudentType?>(null) }
    var status by rememberSaveable { mutableStateOf<PayStatus?>(null) }
    var showInactive by rememberSaveable { mutableStateOf(false) }
    val list = snap.ledgers
        .filter { showInactive || it.student.active }
        .filter { l -> cls == null || l.enrollments.any { it.enrollment.className == cls && (showInactive || it.enrollment.active) } }
        .filter { l -> type == null || l.activeEnrollments.any { it.enrollment.type == type } }
        .filter { status == null || Reports.statusMatches(it, status!!) }
        .filter { Reports.matches(it.student, query) }
        .sortedWith(compareBy<StudentLedger> { l -> l.activeEnrollments.minOfOrNull { e -> snap.card.classNames.indexOf(e.enrollment.className).let { if (it < 0) 999 else it } } ?: 999 }.thenBy { it.student.name.lowercase() })

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar("Student Register (${list.size})")
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SearchField(query, { query = it }, "Search name, Enroll No. or Student ID")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dropdown("Class", listOf<String?>(null) + snap.card.classNames, cls, { it ?: "All classes" }, { cls = it }, Modifier.width(150.dp))
                    FilterChip(selected = type == StudentType.DAY, onClick = { type = if (type == StudentType.DAY) null else StudentType.DAY }, label = { Text("Day") })
                    FilterChip(selected = type == StudentType.HOSTEL, onClick = { type = if (type == StudentType.HOSTEL) null else StudentType.HOSTEL }, label = { Text("Hostel") })
                    PayStatus.entries.forEach { st ->
                        FilterChip(selected = status == st, onClick = { status = if (status == st) null else st }, label = { Text(st.label) })
                    }
                    FilterChip(selected = showInactive, onClick = { showInactive = !showInactive }, label = { Text("Show inactive") })
                }
            }
            if (list.isEmpty()) EmptyState(if (snap.ledgers.isEmpty()) "No students yet.\nTap “Register” to add the first student, or import Excel / CSV from Settings → Data." else "No students match the filters")
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.student.id }) { l -> StudentRow(l) { onOpen(l.student.id) } }
            }
        }
        if (session.isAdmin) {
            ExtendedFloatingActionButton(
                onClick = onAdd, icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text("Register") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentRow(l: StudentLedger, onClick: () -> Unit) {
    val s = l.student
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (s.enrollNo.isNotBlank()) SmallTag("No. ${s.enrollNo}")
                    SmallTag(s.code)
                    l.activeEnrollments.forEach { SmallTag("${it.enrollment.className} · ${it.enrollment.type.label}") }
                    if (l.activeEnrollments.isEmpty()) SmallTag("No class", Red)
                    if (!s.active) SmallTag("Inactive", Red)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(l.balance), style = MaterialTheme.typography.titleSmall, color = if (l.balance > 0) Red else Green)
                Text("of ${Money.format(l.net)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                StatusChip(l.status)
            }
        }
    }
}

// ============================================================== detail
@Composable
fun StudentDetailScreen(
    vm: AppViewModel, snap: AppSnapshot, session: Session, id: Long,
    onBack: () -> Unit, onEdit: () -> Unit, onAddClass: () -> Unit, onEditClass: (Long) -> Unit,
    onPay: (List<String>) -> Unit, onReceipt: (Long) -> Unit,
) {
    val l = snap.ledger(id)
    if (l == null) { Column { AppTopBar("Student", onBack); EmptyState("Student not found") }; return }
    val s = l.student
    var menu by remember { mutableStateOf(false) }
    var confirmActive by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(s.name, onBack) {
            if (session.isAdmin) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Add class / next year") }, onClick = { menu = false; onAddClass() })
                        DropdownMenuItem(text = { Text(if (s.active) "Deactivate student" else "Reactivate student") }, onClick = { menu = false; confirmActive = true })
                    }
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionCard("Profile") {
                KeyValue("Enroll No.", s.enrollNo.ifBlank { "–" }, bold = true)
                KeyValue("Student ID", s.code)
                KeyValue("Registered", s.registeredOn.fmt())
                KeyValue("Status", if (s.active) "Active" else "Inactive", valueColor = if (s.active) Green else Red)
                if (s.remarks.isNotBlank()) KeyValue("Remarks", s.remarks)
            }
            SectionCard("Balance – all classes") {
                KeyValue("Net total receivable", Money.mmk(l.net), bold = true)
                KeyValue("Paid amount", Money.mmk(l.paid), valueColor = Green)
                KeyValue("Balance due", Money.mmk(l.balance), bold = true, valueColor = if (l.balance > 0) Red else Green)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Status  ", style = MaterialTheme.typography.bodyMedium); StatusChip(l.status) }
                if (s.active && l.activeEnrollments.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val n = l.payables.size
                    Button(onClick = { onPay(emptyList()) }, modifier = Modifier.fillMaxWidth()) { Text(if (n > 0) "Collect payment ($n payable)" else "Collect payment") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Classes (${l.enrollments.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (session.isAdmin && s.active) TextButton(onClick = onAddClass) { Icon(Icons.Default.Add, contentDescription = null); Text("Add class") }
            }
            if (l.enrollments.isEmpty()) Text("Not enrolled in any class yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            l.enrollments.forEach { e ->
                EnrollmentCard(vm, snap, session, e, studentActive = s.active, onEdit = { onEditClass(e.enrollment.id) }, onPayLine = { seq -> onPay(listOf("${e.enrollment.id}:$seq")) })
            }
            SectionCard("Payment history") {
                val receipts = snap.receipts.filter { r -> r.items.any { it.studentId == s.id } }.sortedWith(compareByDescending<com.school.feemanager.domain.ReceiptRecord> { it.date }.thenByDescending { it.id })
                if (receipts.isEmpty()) Text("No payments yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                receipts.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider()
                    val mine = r.items.filter { it.studentId == s.id }
                    Row(Modifier.fillMaxWidth().clickable { onReceipt(r.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${r.receiptNo}  ·  ${r.date.fmt()}${if (r.items.size > 1) "  ·  combined" else ""}", style = MaterialTheme.typography.bodyMedium)
                            Text(listOf(mine.joinToString(", ") { p -> (snap.enrollment(p.enrollmentId)?.second?.enrollment?.className?.let { "$it " } ?: "") + p.paymentFor }, "by ${r.receivedBy}", if (r.voided) "VOIDED" else "")
                                .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = if (r.voided) Red else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money.format(mine.sumOf { it.amount }), fontWeight = FontWeight.SemiBold, color = if (r.voided) Red else Green)
                    }
                }
            }
        }
    }

    if (confirmActive) ConfirmDialog(
        title = if (s.active) "Deactivate ${s.name}?" else "Reactivate ${s.name}?",
        text = if (s.active) "The student will be hidden from lists and cannot pay until reactivated. Payment history and balances are kept." else "The student will appear in lists again.",
        confirmLabel = if (s.active) "Deactivate" else "Reactivate", destructive = s.active,
        onConfirm = { vm.setStudentActive(s.id, !s.active) }, onDismiss = { confirmActive = false },
    )
}

@Composable
private fun EnrollmentCard(vm: AppViewModel, snap: AppSnapshot, session: Session, l: EnrollmentLedger, studentActive: Boolean, onEdit: () -> Unit, onPayLine: (Int) -> Unit) {
    val e = l.enrollment
    var open by rememberSaveable(e.id) { mutableStateOf(e.active && l.balance > 0) }
    var menu by remember { mutableStateOf(false) }
    var confirmRecalc by remember { mutableStateOf(false) }
    var confirmActive by remember { mutableStateOf(false) }
    val currentFee = FeeCalculator.assess(snap.card, e)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall, color = if (e.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("From ${e.startDate.fmt()} · ${e.periodMonths} months · ${e.type.label} · ${e.termText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Net ${Money.format(l.net)} · Paid ${Money.format(l.paid)} · Due ${Money.format(l.balance)}", style = MaterialTheme.typography.bodySmall)
                    if (!e.choice.isStandard) Text(e.choice.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!e.active) Text("Inactive", style = MaterialTheme.typography.labelSmall, color = Red)
                }
                StatusChip(l.status)
                if (session.isAdmin) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Class actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit class details") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Recalculate with current rates") }, onClick = { menu = false; confirmRecalc = true })
                        DropdownMenuItem(text = { Text(if (e.active) "Deactivate this class" else "Reactivate this class") }, onClick = { menu = false; confirmActive = true })
                    }
                }
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (open) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                KeyValue("Academic year", e.academicYear)
                KeyValue("New / Old · Book", "${e.status.label} · ${e.bookType.label}")
                if (e.remarks.isNotBlank()) KeyValue("Remarks", e.remarks)
                Spacer(Modifier.height(4.dp))
                FeeBreakdown(l.fee)
                if (currentFee != l.fee) Text("Current fee rates would give ${Money.mmk(currentFee.netTotal)}. The approved assessment above is kept until an Admin recalculates.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(6.dp))
                Text("Payment schedule", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                ScheduleTable(l, onCollect = if (studentActive && e.active) onPayLine else null)
            }
        }
    }
    if (confirmRecalc) ConfirmDialog(
        title = "Recalculate ${e.title}?",
        text = "Net total changes from ${Money.mmk(l.net)} to ${Money.mmk(currentFee.netTotal)} using the current fee rates. The payment schedule is regenerated; recorded payments are kept.",
        confirmLabel = "Recalculate",
        onConfirm = { vm.recalculate(listOf(e.id)) }, onDismiss = { confirmRecalc = false },
    )
    if (confirmActive) ConfirmDialog(
        title = if (e.active) "Deactivate ${e.title}?" else "Reactivate ${e.title}?",
        text = if (e.active) "Use this when the student left the class. Its payables are hidden and it cannot be paid; recorded payments and the balance are kept in reports." else "The class becomes payable again.",
        confirmLabel = if (e.active) "Deactivate" else "Reactivate", destructive = e.active,
        onConfirm = { vm.setEnrollmentActive(e.id, !e.active) }, onDismiss = { confirmActive = false },
    )
}

@Composable
fun FeeBreakdown(f: FeeAssessment) {
    KeyValue("Training fee", Money.format(f.trainingFee))
    KeyValue("Instrument fee", Money.format(f.instrumentFee))
    KeyValue("Book fee", Money.format(f.bookFee))
    KeyValue("Hostel fee", Money.format(f.hostelFee))
    KeyValue("Laundry fee", Money.format(f.laundryFee))
    KeyValue("Entrance fee", Money.format(f.entranceFee))
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    KeyValue("Gross total", Money.mmk(f.grossTotal), bold = true)
    KeyValue("Discount (${Money.bpToPercentText(f.discountBp)})", "− " + Money.format(f.discountAmount))
    KeyValue("Net total receivable", Money.mmk(f.netTotal), bold = true)
}

@Composable
fun ScheduleTable(l: EnrollmentLedger, onCollect: ((Int) -> Unit)?) {
    l.lines.forEachIndexed { i, ls ->
        if (i > 0) HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${ls.line.label}  ·  due ${ls.line.dueDate.fmt()}", style = MaterialTheme.typography.bodyMedium)
                Text("Scheduled ${Money.format(ls.line.amount)} · Paid ${Money.format(ls.paid)} · Remaining ${Money.format(ls.remaining)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(ls.status)
            if (onCollect != null && ls.remaining > 0) TextButton(onClick = { onCollect(ls.line.seq) }) { Text("Pay") }
        }
    }
}

// ============================================================== enrollment fields
/** Editable state of the class fields, shared by "Register student" and "Add / edit class". */
private class EnrollmentState(snap: AppSnapshot, e: EnrollmentRecord?, suggestedClass: String?, suggestedStatus: StudentStatus) {
    private val first = snap.card.classes.firstOrNull { it.className == (e?.className ?: suggestedClass) } ?: snap.card.classes.firstOrNull()
    var cls by mutableStateOf(e?.className ?: first?.className ?: "")
    var start by mutableStateOf(e?.startDate ?: first?.defaultStart(snap.today) ?: snap.yearStart)
    var period by mutableStateOf((e?.periodMonths ?: first?.periodMonths ?: 12).toString())
    var year by mutableStateOf(e?.academicYear ?: academicYearLabel(start, first?.periodMonths ?: 12))
    var yearEdited by mutableStateOf(e != null)
    var type by mutableStateOf(e?.type ?: StudentType.DAY)
    var status by mutableStateOf(e?.status ?: suggestedStatus)
    var book by mutableStateOf(e?.bookType ?: BookType.MYANMAR)
    var term by mutableStateOf(e?.term ?: PaymentTerm.THREE_INST)
    var parts by mutableStateOf((e?.termParts?.takeIf { it > 0 } ?: 4).toString())
    var every by mutableStateOf((e?.termEvery ?: 0).toString())
    var discount by mutableStateOf(e?.discountBp?.let { Money.bpToPlain(it) } ?: "0")
    var remarks by mutableStateOf(e?.remarks ?: "")
    // training option / book options of the class (FeeChoice)
    var training by mutableStateOf(e?.choice?.training ?: FeeChoice.STANDARD)
    var standardBook by mutableStateOf(e?.choice?.standardBook ?: true)
    val books = mutableStateListOf<String>().apply { e?.choice?.books?.let { addAll(it) } }

    val bp: Int? get() = Money.parsePercentToBp(discount)
    val periodInt: Int? get() = period.toIntOrNull()?.takeIf { it in 1..24 }
    val partsInt: Int? get() = if (term != PaymentTerm.CUSTOM) 0 else parts.toIntOrNull()?.takeIf { it in 1..PaymentPlan.MAX_PARTS }
    val everyInt: Int? get() = if (term != PaymentTerm.CUSTOM) 0 else every.ifBlank { "0" }.toIntOrNull()?.takeIf { it in 0..24 }
    val valid: Boolean get() = bp != null && periodInt != null && year.isNotBlank() && cls.isNotBlank() && partsInt != null && everyInt != null

    fun autoYear() { if (!yearEdited) periodInt?.let { year = academicYearLabel(start, it) } }

    /** keeps only choices the (new) class offers */
    fun fitChoice(snap: AppSnapshot) {
        val r = snap.card.rateFor(cls)
        if (training != FeeChoice.STANDARD && training != FeeChoice.NONE && r?.option(FeeKind.TRAINING, training) == null) training = FeeChoice.STANDARD
        books.removeAll { r?.option(FeeKind.BOOK, it) == null }
    }

    val choice: FeeChoice get() = FeeChoice(training, standardBook, books.toList())

    fun assess(snap: AppSnapshot): FeeAssessment = FeeCalculator.assess(snap.card, cls, type, status, book, bp ?: 0, periodInt ?: 12, choice)

    fun form() = EnrollmentForm(cls, year.trim(), start, periodInt ?: 12, type, status, book, bp ?: 0, term, remarks, partsInt ?: 0, everyInt ?: 0, choice)
}

@Composable
private fun EnrollmentFields(snap: AppSnapshot, st: EnrollmentState, isNew: Boolean) {
    Dropdown("Class", snap.card.classNames, st.cls, { it }, { c ->
        st.cls = c
        if (isNew) snap.card.rateFor(c)?.let { r -> st.start = r.defaultStart(snap.today); st.period = r.periodMonths.toString() }
        st.fitChoice(snap)
        st.autoYear()
    })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateField("Start date", st.start, { if (it != null) { st.start = it; st.autoYear() } }, Modifier.weight(1.3f))
        OutlinedTextField(st.period, { st.period = it.filter { c -> c.isDigit() }.take(2); st.autoYear() }, label = { Text("Months") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = st.periodInt == null, modifier = Modifier.weight(0.8f))
    }
    OutlinedTextField(st.year, { st.year = it.take(20); st.yearEdited = true }, label = { Text("Academic year") }, singleLine = true, isError = st.year.isBlank(),
        supportingText = { Text("Ends ${st.periodInt?.let { st.start.plusMonths(it.toLong()).minusDays(1).fmt() } ?: "–"}") }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dropdown("Student type", StudentType.entries, st.type, { it.label }, { st.type = it }, Modifier.weight(1f))
        Dropdown("New / Old", StudentStatus.entries, st.status, { it.label }, { st.status = it }, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dropdown("Book type", BookType.entries, st.book, { it.label }, { st.book = it }, Modifier.weight(1f))
        Dropdown("Payment term", PaymentTerm.entries, st.term, { it.label }, { st.term = it }, Modifier.weight(1f))
    }
    if (st.term == PaymentTerm.CUSTOM) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(st.parts, { st.parts = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Installments") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = st.partsInt == null,
            supportingText = { Text("1 – ${PaymentPlan.MAX_PARTS}") }, modifier = Modifier.weight(1f))
        OutlinedTextField(st.every, { st.every = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Every … months") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = st.everyInt == null,
            supportingText = { Text("0 = spread over the period") }, modifier = Modifier.weight(1f))
    }
    ClassFeeChoices(snap, st)
    OutlinedTextField(st.discount, { st.discount = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, label = { Text("Discount %") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = st.bp == null,
        supportingText = { if (st.bp == null) Text("Enter 0 – 100") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(st.remarks, { st.remarks = it }, label = { Text("Class remarks") }, modifier = Modifier.fillMaxWidth())
}

/** Training option, standard / extra books, and a note when the class has no "fees for all students". */
@Composable
private fun ClassFeeChoices(snap: AppSnapshot, st: EnrollmentState) {
    val r = snap.card.rateFor(st.cls) ?: return
    if (!r.commonFees) Text("${r.className} does not take the fees for all students (instrument fee, entrance fee).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (r.trainingOptions.isNotEmpty()) {
        val choices = listOf(FeeChoice.STANDARD) + r.trainingOptions.map { it.name } + FeeChoice.NONE
        fun label(c: String) = when (c) {
            FeeChoice.STANDARD -> "Standard – ${Money.format(r.trainingFee)}"
            FeeChoice.NONE -> "No training fee"
            else -> "$c – ${Money.format(r.option(FeeKind.TRAINING, c)?.amount ?: 0)}"
        }
        Dropdown("Training fee", choices, st.training, ::label, { st.training = it }, Modifier.fillMaxWidth())
    }
    if (r.bookOptions.isNotEmpty()) {
        Text("Book fees", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { st.standardBook = !st.standardBook }) {
            Checkbox(st.standardBook, { st.standardBook = it })
            Text("Standard book (${st.type.label}, ${st.book.label}) – ${Money.format(r.standardBook(st.type, st.book))}")
        }
        r.bookOptions.forEach { o ->
            val on = st.books.any { it.equals(o.name, true) }
            fun toggle() { if (on) st.books.removeAll { it.equals(o.name, true) } else st.books.add(o.name) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { toggle() }) {
                Checkbox(on, { toggle() })
                Text("${o.name} – ${Money.format(o.amount)}")
            }
        }
    }
}

@Composable
private fun FeePreview(snap: AppSnapshot, st: EnrollmentState, fee: FeeAssessment, title: String, alreadyPaid: Long?) {
    SectionCard(title) {
        FeeBreakdown(fee)
        val plan = PaymentPlan(st.term, st.partsInt ?: 1, st.everyInt ?: 0)
        val period = st.periodInt ?: 12
        val lines = ScheduleCalculator.generate(plan, fee.netTotal, st.start, period)
        Spacer(Modifier.height(4.dp))
        Text("${plan.label}: ${lines.size} payment${if (lines.size == 1) "" else "s"} of of ${lines.map { it.amount }.distinct().joinToString(" / ") { Money.format(it) }}, first due ${lines.firstOrNull()?.dueDate?.fmt() ?: "–"}, last due ${lines.lastOrNull()?.dueDate?.fmt() ?: "–"}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (alreadyPaid != null) KeyValue("Already paid", Money.mmk(alreadyPaid))
    }
}

// ============================================================== add / edit student
@Composable
fun StudentEditScreen(vm: AppViewModel, snap: AppSnapshot, id: Long?, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val existing = id?.let { snap.ledger(it) }
    val s0 = existing?.student
    var enrollNo by rememberSaveable { mutableStateOf(s0?.enrollNo ?: "") }
    var name by rememberSaveable { mutableStateOf(s0?.name ?: "") }
    var remarks by rememberSaveable { mutableStateOf(s0?.remarks ?: "") }
    var withClass by rememberSaveable { mutableStateOf(true) }
    val st = remember { EnrollmentState(snap, null, null, StudentStatus.NEW) }

    val cleanName = name.trim().replace(Regex("\\s+"), " ")
    val enDup = enrollNo.trim().takeIf { it.isNotEmpty() }?.let { en -> snap.ledgers.firstOrNull { it.student.id != id && it.student.enrollNo.equals(en, true) } }
    val nameDup = if (id == null && withClass) snap.ledgers.firstOrNull { l ->
        l.student.name.equals(cleanName, true) && l.enrollments.any { it.enrollment.className == st.cls && it.enrollment.academicYear.equals(st.year.trim(), true) }
    } else null
    val sameName = if (id == null) snap.ledgers.firstOrNull { it.student.name.equals(cleanName, true) } else null
    val valid = cleanName.isNotBlank() && enDup == null && nameDup == null && (!withClass || id != null || st.valid)

    Column(Modifier.fillMaxSize()) {
        AppTopBar(if (id == null) "Register Student" else "Edit ${s0?.code ?: ""}", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(enrollNo, { enrollNo = it.take(30) }, label = { Text("Enroll No.") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = enDup != null,
                supportingText = { Text(if (enDup != null) "Already used by ${enDup.student.name} (${enDup.student.code})" else "Your school's enroll number (optional, must be unique). Student ID ${s0?.code ?: "is generated automatically"}.") })
            OutlinedTextField(name, { name = it }, label = { Text("Student name *") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = nameDup != null,
                supportingText = {
                    when {
                        nameDup != null -> Text("Already registered in ${st.cls} ${st.year}: ${nameDup.student.idText}. Open that student and add the class there.")
                        sameName != null -> Text("Note: ${sameName.student.name} (${sameName.student.idText}) already exists. To add a class for the same child, open that student instead.")
                    }
                })
            OutlinedTextField(remarks, { remarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())
            if (id == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(withClass, { withClass = it })
                    Text("Enroll in a class now (more classes can be added later)")
                }
                if (withClass) {
                    SectionCard("Class") { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { EnrollmentFields(snap, st, isNew = true) } }
                    val fee = st.assess(snap)
                    FeePreview(snap, st, fee, "Fee calculation (current rates)", null)
                }
            } else {
                Text("Classes, fees and schedules are edited per class on the student page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { vm.saveStudent(id, StudentForm(enrollNo, name, remarks), if (id == null && withClass) st.form() else null, onSaved) }, enabled = valid, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
}

// ============================================================== add / edit class enrollment
@Composable
fun EnrollmentEditScreen(vm: AppViewModel, snap: AppSnapshot, studentId: Long, enrollmentId: Long?, onBack: () -> Unit, onSaved: () -> Unit) {
    val l = snap.ledger(studentId)
    if (l == null) { Column { AppTopBar("Class", onBack); EmptyState("Student not found") }; return }
    val existing = enrollmentId?.let { l.enrollment(it) }
    // New class: suggest the class after the latest one (promotion), as an Old student.
    val latest = l.enrollments.maxByOrNull { it.enrollment.startDate }?.enrollment
    val suggested = latest?.let { snap.card.classNames.getOrNull(snap.card.classNames.indexOf(it.className) + 1) ?: it.className }
    val st = remember { EnrollmentState(snap, existing?.enrollment, suggested, if (l.enrollments.isEmpty()) StudentStatus.NEW else StudentStatus.OLD) }
    var confirm by remember { mutableStateOf(false) }
    val e0 = existing?.enrollment
    val preview = st.assess(snap)
    val feeChanged = e0 != null && (e0.className != st.cls || e0.type != st.type || e0.status != st.status || e0.bookType != st.book || e0.discountBp != (st.bp ?: 0) ||
        e0.periodMonths != st.periodInt || e0.choice != st.choice)
    val shown = if (existing != null && !feeChanged) existing.fee else preview
    val dup = l.enrollments.firstOrNull { it.enrollment.id != enrollmentId && it.enrollment.className == st.cls && it.enrollment.academicYear.equals(st.year.trim(), true) }
    val valid = st.valid && dup == null

    Column(Modifier.fillMaxSize()) {
        AppTopBar(if (existing == null) "Add class – ${l.student.name}" else "Edit ${existing.enrollment.title}", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (existing == null && latest != null) Text("Current: ${l.enrollments.filter { it.enrollment.active }.joinToString { it.enrollment.title }.ifEmpty { "none active" }}. Each class has its own start date, period, fees and schedule.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            EnrollmentFields(snap, st, isNew = existing == null)
            if (dup != null) Text("Already enrolled in ${st.cls} for ${st.year}.", color = Red, style = MaterialTheme.typography.bodySmall)
            FeePreview(snap, st, shown, if (existing != null && !feeChanged) "Fee assessment (approved – unchanged)" else "Fee calculation (current rates)", existing?.paid)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { if (feeChanged) confirm = true else vm.saveEnrollment(enrollmentId, studentId, st.form()) { onSaved() } }, enabled = valid, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
    if (confirm && existing != null) ConfirmDialog(
        title = "Re-assess fees?",
        text = "Changing class, type, status, book type, discount or period re-assesses this class with the CURRENT fee rates.\n\nNet total: ${Money.mmk(existing.net)} → ${Money.mmk(preview.netTotal)}\nAlready paid: ${Money.mmk(existing.paid)}",
        confirmLabel = "Save", onConfirm = { vm.saveEnrollment(enrollmentId, studentId, st.form()) { onSaved() } }, onDismiss = { confirm = false },
    )
}
