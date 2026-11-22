@file:OptIn(ExperimentalMaterial3Api::class)

package com.school.feemanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import com.school.feemanager.data.StudentForm
import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.FeeAssessment
import com.school.feemanager.domain.FeeCalculator
import com.school.feemanager.domain.Money
import com.school.feemanager.domain.PayStatus
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.Reports
import com.school.feemanager.domain.StudentLedger
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import com.school.feemanager.ui.theme.Green
import com.school.feemanager.ui.theme.Red

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
        .filter { cls == null || it.student.className == cls }
        .filter { type == null || it.student.type == type }
        .filter { status == null || Reports.statusMatches(it, status!!) }
        .filter { Reports.matches(it.student, query) }
        .sortedWith(compareBy<StudentLedger> { snap.card.classNames.indexOf(it.student.className) }.thenBy { it.student.name.lowercase() })

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar("Student Register (${list.size})")
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SearchField(query, { query = it }, "Search name or Student ID")
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
            if (list.isEmpty()) EmptyState(if (snap.ledgers.isEmpty()) "No students yet.\nTap “Register” to add the first student, or import your Excel workbook from Settings → Data." else "No students match the filters")
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

@Composable
private fun StudentRow(l: StudentLedger, onClick: () -> Unit) {
    val s = l.student
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallTag(s.code); SmallTag(s.className); SmallTag(s.type.label); SmallTag(s.term.label)
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
    onBack: () -> Unit, onEdit: () -> Unit, onCollect: (Int) -> Unit, onReceipt: (Long) -> Unit,
) {
    val l = snap.ledger(id)
    if (l == null) { Column { AppTopBar("Student", onBack); EmptyState("Student not found") }; return }
    val s = l.student
    var menu by remember { mutableStateOf(false) }
    var confirmActive by remember { mutableStateOf(false) }
    var confirmRecalc by remember { mutableStateOf(false) }
    var promoteOpen by remember { mutableStateOf(false) }
    val currentFee = FeeCalculator.assess(snap.card, s)

    Column(Modifier.fillMaxSize()) {
        AppTopBar(s.name, onBack) {
            if (session.isAdmin) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Promote / change class") }, onClick = { menu = false; promoteOpen = true })
                        DropdownMenuItem(text = { Text("Recalculate fees with current rates") }, onClick = { menu = false; confirmRecalc = true })
                        DropdownMenuItem(text = { Text(if (s.active) "Deactivate student" else "Reactivate student") }, onClick = { menu = false; confirmActive = true })
                    }
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionCard("Profile") {
                KeyValue("Student ID", s.code, bold = true)
                KeyValue("Class", s.className)
                KeyValue("Student type", s.type.label)
                KeyValue("New / Old", s.status.label)
                KeyValue("Book type", s.bookType.label)
                KeyValue("Payment term", s.term.label)
                KeyValue("Registered", s.registeredOn.fmt())
                KeyValue("Status", if (s.active) "Active" else "Inactive", valueColor = if (s.active) Green else Red)
                if (s.remarks.isNotBlank()) KeyValue("Remarks", s.remarks)
            }
            SectionCard("Fee assessment") {
                FeeBreakdown(l.fee)
                if (currentFee != l.fee) {
                    Spacer(Modifier.height(6.dp))
                    Text("Current fee rates would give ${Money.mmk(currentFee.netTotal)}. The approved assessment above is kept until an Admin recalculates.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            SectionCard("Balance") {
                KeyValue("Net total receivable", Money.mmk(l.net), bold = true)
                KeyValue("Paid amount", Money.mmk(l.paid), valueColor = Green)
                KeyValue("Balance due", Money.mmk(l.balance), bold = true, valueColor = if (l.balance > 0) Red else Green)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Status  ", style = MaterialTheme.typography.bodyMedium); StatusChip(l.status) }
                if (s.active && l.balance > 0) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onCollect(-1) }, modifier = Modifier.fillMaxWidth()) { Text("Collect payment") }
                }
            }
            SectionCard("Payment schedule (${s.term.label})") {
                ScheduleTable(l, onCollect = if (s.active) onCollect else null)
            }
            SectionCard("Payment history") {
                val all = snap.payments.filter { it.studentId == s.id }.sortedWith(compareByDescending<com.school.feemanager.domain.PaymentRecord> { it.date }.thenByDescending { it.id })
                if (all.isEmpty()) Text("No payments yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                all.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable { snap.receiptsByTx[p.id]?.let { onReceipt(it.id) } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${p.receiptNo}  ·  ${p.date.fmt()}", style = MaterialTheme.typography.bodyMedium)
                            Text(listOf(p.paymentFor, "by ${p.receivedBy}", if (p.voided) "VOIDED" else "").filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = if (p.voided) Red else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money.format(p.amount), fontWeight = FontWeight.SemiBold, color = if (p.voided) Red else Green)
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
    if (confirmRecalc) ConfirmDialog(
        title = "Recalculate fees?",
        text = "Net total changes from ${Money.mmk(l.net)} to ${Money.mmk(currentFee.netTotal)} using the current fee rates. The payment schedule is regenerated; recorded payments are kept.",
        confirmLabel = "Recalculate",
        onConfirm = { vm.recalculate(listOf(s.id)) }, onDismiss = { confirmRecalc = false },
    )
    if (promoteOpen) PromoteDialog(snap, l, onDismiss = { promoteOpen = false }) { cls, markOld, recalc -> vm.promote(s.id, cls, markOld, recalc) }
}

@Composable
fun FeeBreakdown(f: FeeAssessment) {
    KeyValue("Training fee", Money.format(f.trainingFee))
    KeyValue("Instrument fee", Money.format(f.instrumentFee))
    KeyValue("Book fee", Money.format(f.bookFee))
    KeyValue("Hostel fee", Money.format(f.hostelFee))
    KeyValue("Laundry fee (annual)", Money.format(f.laundryFee))
    KeyValue("Entrance fee", Money.format(f.entranceFee))
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    KeyValue("Gross total", Money.mmk(f.grossTotal), bold = true)
    KeyValue("Discount (${Money.bpToPercentText(f.discountBp)})", "− " + Money.format(f.discountAmount))
    KeyValue("Net total receivable", Money.mmk(f.netTotal), bold = true)
}

@Composable
fun ScheduleTable(l: StudentLedger, onCollect: ((Int) -> Unit)?) {
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

@Composable
private fun PromoteDialog(snap: AppSnapshot, l: StudentLedger, onDismiss: () -> Unit, onConfirm: (String, Boolean, Boolean) -> Unit) {
    val classes = snap.card.classNames
    val idx = classes.indexOf(l.student.className)
    var target by remember { mutableStateOf(classes.getOrNull(idx + 1) ?: l.student.className) }
    var markOld by remember { mutableStateOf(true) }
    var recalc by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Promote ${l.student.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current class: ${l.student.className}")
                Dropdown("New class", classes, target, { it }, { target = it })
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(markOld, { markOld = it }); Text("Mark as Old student (no entrance fee)") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(recalc, { recalc = it }); Text("Recalculate fees for the new class") }
                Text("Payments already recorded stay on the student's account.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(target, markOld, recalc); onDismiss() }, enabled = target != l.student.className) { Text("Promote") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ============================================================== add / edit
@Composable
fun StudentEditScreen(vm: AppViewModel, snap: AppSnapshot, id: Long?, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val existing = id?.let { snap.ledger(it) }
    val s0 = existing?.student
    var name by rememberSaveable { mutableStateOf(s0?.name ?: "") }
    var cls by rememberSaveable { mutableStateOf(s0?.className ?: snap.card.classNames.firstOrNull() ?: "") }
    var type by rememberSaveable { mutableStateOf(s0?.type ?: StudentType.DAY) }
    var status by rememberSaveable { mutableStateOf(s0?.status ?: StudentStatus.NEW) }
    var book by rememberSaveable { mutableStateOf(s0?.bookType ?: BookType.MYANMAR) }
    var term by rememberSaveable { mutableStateOf(s0?.term ?: PaymentTerm.THREE_INST) }
    var discount by rememberSaveable { mutableStateOf(s0?.discountBp?.let { Money.bpToPlain(it) } ?: "0") }
    var remarks by rememberSaveable { mutableStateOf(s0?.remarks ?: "") }
    var confirm by remember { mutableStateOf(false) }

    val bp = Money.parsePercentToBp(discount)
    val form = StudentForm(name, cls, type, status, book, bp ?: 0, term, remarks)
    val preview = FeeCalculator.assess(snap.card, cls, type, status, book, bp ?: 0)
    val feeFieldsChanged = s0 != null && (s0.className != cls || s0.type != type || s0.status != status || s0.bookType != book || s0.discountBp != (bp ?: 0))
    val shown = if (existing != null && !feeFieldsChanged) existing.fee else preview
    val dup = snap.ledgers.firstOrNull { it.student.id != id && it.student.className == cls && it.student.name.trim().equals(name.trim().replace(Regex("\\s+"), " "), true) }
    val valid = name.isNotBlank() && bp != null && dup == null

    Column(Modifier.fillMaxSize()) {
        AppTopBar(if (id == null) "Register Student" else "Edit ${s0?.code ?: ""}", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (id == null) Text("Student ID will be generated automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, label = { Text("Student name *") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = dup != null, supportingText = { if (dup != null) Text("Already registered: ${dup.student.code} in ${dup.student.className}") })
            Dropdown("Class", snap.card.classNames, cls, { it }, { cls = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown("Student type", StudentType.entries, type, { it.label }, { type = it }, Modifier.weight(1f))
                Dropdown("New / Old", StudentStatus.entries, status, { it.label }, { status = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown("Book type", BookType.entries, book, { it.label }, { book = it }, Modifier.weight(1f))
                Dropdown("Payment term", PaymentTerm.entries, term, { it.label }, { term = it }, Modifier.weight(1f))
            }
            OutlinedTextField(discount, { discount = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, label = { Text("Discount %") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = bp == null,
                supportingText = { if (bp == null) Text("Enter 0 – 100") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(remarks, { remarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())

            SectionCard(if (existing != null && !feeFieldsChanged) "Fee assessment (approved – unchanged)" else "Fee calculation (current rates)") {
                FeeBreakdown(shown)
                val perPart = com.school.feemanager.domain.ScheduleCalculator.splitAmounts(shown.netTotal, term.parts)
                Spacer(Modifier.height(4.dp))
                Text("${term.label}: ${perPart.distinct().joinToString(" / ") { Money.format(it) }} per ${if (term == PaymentTerm.THREE_INST) "installment" else "month"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (existing != null) KeyValue("Already paid", Money.mmk(existing.paid))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { if (feeFieldsChanged) confirm = true else vm.saveStudent(id, form, onSaved) }, enabled = valid, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
    if (confirm && existing != null) ConfirmDialog(
        title = "Re-assess fees?",
        text = "Changing class, type, status, book type or discount re-assesses this student with the CURRENT fee rates.\n\nNet total: ${Money.mmk(existing.net)} → ${Money.mmk(preview.netTotal)}\nAlready paid: ${Money.mmk(existing.paid)}",
        confirmLabel = "Save", onConfirm = { vm.saveStudent(id, form, onSaved) }, onDismiss = { confirm = false },
    )
}
