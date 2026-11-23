@file:OptIn(ExperimentalMaterial3Api::class)

package com.school.feemanager.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.school.feemanager.data.AppSnapshot
import com.school.feemanager.data.PayItem
import com.school.feemanager.data.PaymentRequest
import com.school.feemanager.domain.Money
import com.school.feemanager.domain.PayStatus
import com.school.feemanager.domain.Payable
import com.school.feemanager.domain.ReceiptRecord
import com.school.feemanager.domain.Reports
import com.school.feemanager.domain.ScheduleCalculator
import com.school.feemanager.domain.StudentLedger
import com.school.feemanager.io.HtmlDocs
import com.school.feemanager.io.ReceiptLine
import com.school.feemanager.io.ReceiptView
import com.school.feemanager.ui.theme.Green
import com.school.feemanager.ui.theme.Navy
import com.school.feemanager.ui.theme.Red
import com.school.feemanager.util.Printer
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// ===================================================== Payables (tab 3) – multi-select
@Composable
fun PayablesScreen(snap: AppSnapshot, onPay: (List<String>) -> Unit, onStudent: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var cls by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf<PayStatus?>(null) }
    var dueOnly by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    val today = snap.today

    val groups: List<Pair<StudentLedger, List<Payable>>> = snap.ledgers
        .filter { it.student.active && Reports.matches(it.student, query) }
        .map { l ->
            l to l.payables
                .filter { cls == null || it.enrollment.enrollment.className == cls }
                .filter { status == null || it.line.status == status }
                .filter { !dueOnly || !it.line.line.dueDate.isAfter(today) }
        }
        .filter { it.second.isNotEmpty() }
        .sortedWith(compareBy<Pair<StudentLedger, List<Payable>>> { g -> g.second.minOf { it.line.line.dueDate } }.thenBy { it.first.student.name.lowercase() })
    val all = groups.flatMap { it.second }.associateBy { it.key }
    // drop selections that are no longer payable (paid meanwhile)
    val validSel = selected.filter { it in all }.toSet()
    val selTotal = validSel.sumOf { all[it]!!.remaining }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar("Payables") {
                IconButton(onClick = { selected = if (validSel.size == all.size) emptySet() else all.keys }, enabled = all.isNotEmpty()) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Select all shown")
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SearchField(query, { query = it }, "Search name, Enroll No. or Student ID")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dropdown("Class", listOf<String?>(null) + snap.card.classNames, cls, { it ?: "All classes" }, { cls = it }, Modifier.width(150.dp))
                    FilterChip(selected = dueOnly, onClick = { dueOnly = !dueOnly }, label = { Text("Due by today") })
                    listOf(PayStatus.OVERDUE, PayStatus.UNPAID, PayStatus.PARTIAL).forEach { st ->
                        FilterChip(selected = status == st, onClick = { status = if (status == st) null else st }, label = { Text(st.label) })
                    }
                }
                Text("${all.size} payable item(s) · ${groups.size} student(s) · Outstanding ${Money.mmk(all.values.sumOf { it.remaining })}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tick the installments / months to collect – from one or several students – then tap Pay to issue one combined receipt.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (groups.isEmpty()) EmptyState(if (snap.ledgers.isEmpty()) "No students yet" else "Nothing to pay for the selected filters")
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups, key = { it.first.student.id }) { (l, items) ->
                    PayableGroup(l, items, validSel,
                        onToggle = { k -> selected = if (k in validSel) validSel - k else validSel + k },
                        onToggleAll = { on -> selected = if (on) validSel + items.map { it.key } else validSel - items.map { it.key }.toSet() },
                        onStudent = { onStudent(l.student.id) })
                }
            }
        }
        if (validSel.isNotEmpty()) Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), tonalElevation = 6.dp, shadowElevation = 6.dp) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, contentDescription = "Clear selection") }
                Column(Modifier.weight(1f)) {
                    Text("${validSel.size} selected", style = MaterialTheme.typography.labelMedium)
                    Text(Money.mmk(selTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Button(onClick = { onPay(validSel.toList()) }) { Text("Pay selected") }
            }
        }
    }
}

@Composable
private fun PayableGroup(l: StudentLedger, items: List<Payable>, selected: Set<String>, onToggle: (String) -> Unit, onToggleAll: (Boolean) -> Unit, onStudent: () -> Unit) {
    val allOn = items.all { it.key in selected }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(allOn, { onToggleAll(it) })
                Column(Modifier.weight(1f).clickable(onClick = onStudent)) {
                    Text(l.student.name, style = MaterialTheme.typography.titleSmall)
                    Text("${l.student.idText} · ${l.classesText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Money.format(items.sumOf { it.remaining }), color = Red, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 12.dp))
            }
            items.forEach { p ->
                HorizontalDivider(Modifier.padding(start = 48.dp))
                Row(Modifier.fillMaxWidth().clickable { onToggle(p.key) }.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(p.key in selected, { onToggle(p.key) })
                    Column(Modifier.weight(1f)) {
                        Text(p.description, style = MaterialTheme.typography.bodyMedium)
                        Text("Due ${p.line.line.dueDate.fmt()} · ${Money.format(p.line.line.amount)}${if (p.line.paid > 0) " · paid ${Money.format(p.line.paid)}" else ""}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Money.format(p.remaining), fontWeight = FontWeight.Medium)
                        StatusChip(p.line.status)
                    }
                }
            }
        }
    }
}

// ===================================================== Cash collection register (tab 4)
@Composable
fun CollectionScreen(vm: AppViewModel, snap: AppSnapshot, session: Session, onNew: () -> Unit, onReceipt: (Long) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var selectMode by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(setOf<Long>()) }
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selectMode) AppTopBar("${selected.size} receipt(s) selected", onBack = { selectMode = false; selected = emptySet() }) {
                IconButton(onClick = {
                    val list = selected.mapNotNull { snap.receiptById[it] }.sortedWith(compareBy<ReceiptRecord> { it.date }.thenBy { it.id })
                    printCombined(context, snap, list); vm.markPrinted(list.map { it.id })
                }, enabled = selected.isNotEmpty()) { Icon(Icons.Default.Print, contentDescription = "Print / save combined receipt") }
            } else AppTopBar("Cash Collection") {
                IconButton(onClick = { selectMode = true; tab = 0 }) { Icon(Icons.Default.Checklist, contentDescription = "Select receipts for a combined receipt") }
            }
            TabRow(selectedTabIndex = tab) {
                listOf("Receipts", "Daily", "Monthly").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> RegisterTab(vm, snap, session, selectMode, selected, onSelect = { selected = it }, onLongPress = { id -> selectMode = true; selected = selected + id }, onReceipt = onReceipt)
                1 -> DailySummaryTab(snap)
                else -> MonthlySummaryTab(snap)
            }
        }
        if (selectMode) {
            if (selected.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = {
                    val list = selected.mapNotNull { snap.receiptById[it] }.sortedWith(compareBy<ReceiptRecord> { it.date }.thenBy { it.id })
                    printCombined(context, snap, list); vm.markPrinted(list.map { it.id })
                },
                icon = { Icon(Icons.Default.Print, contentDescription = null) }, text = { Text("Combined receipt (${selected.size})") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        } else ExtendedFloatingActionButton(
            onClick = onNew, icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text("Collect") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RegisterTab(
    vm: AppViewModel, snap: AppSnapshot, session: Session, selectMode: Boolean, selected: Set<Long>,
    onSelect: (Set<Long>) -> Unit, onLongPress: (Long) -> Unit, onReceipt: (Long) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    var voidTarget by remember { mutableStateOf<ReceiptRecord?>(null) }
    val f = from
    val t = to
    val list = snap.receipts
        .filter { f == null || !it.date.isBefore(f) }
        .filter { t == null || !it.date.isAfter(t) }
        .filter { r ->
            val q = query.trim()
            q.isEmpty() || r.receiptNo.contains(q, true) || r.payerName.contains(q, true) || r.studentIds.any { id -> snap.ledger(id)?.student?.let { Reports.matches(it, q) } == true }
        }
        .sortedWith(compareByDescending<ReceiptRecord> { it.date }.thenByDescending { it.id })
    Column {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchField(query, { query = it }, "Search receipt no., student name, Enroll No.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("From", from, { from = it }, Modifier.weight(1f), allowClear = true)
                DateField("To", to, { to = it }, Modifier.weight(1f), allowClear = true)
            }
            val valid = list.filter { !it.voided }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${valid.size} receipts · ${Money.mmk(valid.sumOf { it.total })}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (selectMode) TextButton(onClick = { onSelect(if (list.all { it.id in selected }) selected - list.map { it.id }.toSet() else selected + list.map { it.id }) }) {
                    Text(if (list.isNotEmpty() && list.all { it.id in selected }) "Clear shown" else "Select shown")
                }
            }
            if (!selectMode) Text("Long-press receipts (or tap ☑ above) to select several and print / save them as one combined receipt.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (list.isEmpty()) EmptyState("No receipts")
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp)) {
            items(list, key = { it.id }) { r ->
                val isSel = r.id in selected
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .combinedClickable(
                            onClick = { if (selectMode) onSelect(if (isSel) selected - r.id else selected + r.id) else onReceipt(r.id) },
                            onLongClick = { onLongPress(r.id) },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectMode) Checkbox(isSel, { onSelect(if (isSel) selected - r.id else selected + r.id) })
                    Column(Modifier.weight(1f)) {
                        Text("${r.receiptNo} · ${r.date.fmt()}${if (r.items.size > 1) " · ${r.items.size} items" else ""}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${r.payerName} · ${itemsText(snap, r)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        if (r.voided) Text("VOIDED – ${r.voidReason}", style = MaterialTheme.typography.labelSmall, color = Red)
                    }
                    Text(Money.format(r.total), fontWeight = FontWeight.SemiBold, color = if (r.voided) Red else Green)
                    if (!selectMode && session.isAdmin && !r.voided) IconButton(onClick = { voidTarget = r }) { Icon(Icons.Default.Block, contentDescription = "Void", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                HorizontalDivider()
            }
        }
    }
    voidTarget?.let { r -> VoidDialog(r, onDismiss = { voidTarget = null }) { reason -> vm.voidReceipt(r.id, reason) } }
}

private fun itemsText(snap: AppSnapshot, r: ReceiptRecord): String =
    r.items.joinToString(", ") { p -> (snap.enrollment(p.enrollmentId)?.second?.enrollment?.className?.let { "$it " } ?: "") + p.paymentFor }

@Composable
private fun VoidDialog(r: ReceiptRecord, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Void receipt ${r.receiptNo}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("All ${r.items.size} line(s) totalling ${Money.mmk(r.total)} stay in the register marked VOIDED and no longer count toward the students' payments. This cannot be undone.")
                OutlinedTextField(reason, { reason = it }, label = { Text("Reason (required)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason); onDismiss() }, enabled = reason.isNotBlank()) { Text("Void", color = Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DailySummaryTab(snap: AppSnapshot) {
    val days = snap.payments.filter { !it.voided }.groupBy { it.date }.toSortedMap(compareByDescending { it })
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        if (days.isEmpty()) EmptyState("No collections yet")
        else DataTable(
            headers = listOf("Date", "Receipts", "Amount (MMK)"),
            rows = days.map { (d, l) -> listOf(d.fmt(), l.map { it.receiptId }.distinct().size.toLong(), l.sumOf { it.amount }) },
            totals = listOf("TOTAL", days.values.sumOf { l -> l.map { it.receiptId }.distinct().size }.toLong(), days.values.sumOf { l -> l.sumOf { it.amount } }),
            numeric = { it > 0 }, colWidth = { 120 },
        )
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun MonthlySummaryTab(snap: AppSnapshot) {
    val fmt = DateTimeFormatter.ofPattern("MMM yyyy")
    val months = snap.payments.filter { !it.voided }.groupBy { YearMonth.from(it.date) }.toSortedMap(compareByDescending { it })
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        if (months.isEmpty()) EmptyState("No collections yet")
        else DataTable(
            headers = listOf("Month", "Receipts", "Amount (MMK)"),
            rows = months.map { (m, l) -> listOf(m.format(fmt), l.map { it.receiptId }.distinct().size.toLong(), l.sumOf { it.amount }) },
            totals = listOf("TOTAL", months.values.sumOf { l -> l.map { it.receiptId }.distinct().size }.toLong(), months.values.sumOf { l -> l.sumOf { it.amount } }),
            numeric = { it > 0 }, colWidth = { 120 },
        )
        Spacer(Modifier.height(80.dp))
    }
}

// ===================================================== Collect a payment (one or many items → one receipt)
/** Something that can be paid: a schedule line, or an "other / advance" amount for a class. */
private data class PayOption(
    val key: String,
    val studentId: Long,
    val studentName: String,
    val enrollmentId: Long,
    val seq: Int?,
    val title: String,
    val subtitle: String,
    val remaining: Long,
    val paymentFor: String,
)

private fun optionsFor(l: StudentLedger): List<PayOption> {
    val lines = l.payables.map { p ->
        val e = p.enrollment.enrollment
        PayOption(p.key, l.student.id, l.student.name, e.id, p.line.line.seq, p.description,
            "Due ${p.line.line.dueDate.fmt()} · ${p.line.status.label}", p.remaining, "${e.className} ${ScheduleCalculator.longLabelFor(e.term, p.line.line.seq)}")
    }
    val other = l.activeEnrollments.map { e ->
        PayOption("${e.enrollment.id}:other", l.student.id, l.student.name, e.enrollment.id, null, "${e.enrollment.title} – other / advance payment",
            "Balance of this class ${Money.format(e.balance)}", e.balance.coerceAtLeast(0), "${e.enrollment.className} general payment")
    }
    return lines + other
}

@Composable
fun CollectPaymentScreen(vm: AppViewModel, snap: AppSnapshot, presetStudent: Long?, presetItems: List<String>, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    var studentId by rememberSaveable { mutableStateOf(presetStudent ?: presetItems.firstNotNullOfOrNull { k -> k.substringBefore(':').toLongOrNull()?.let { snap.enrollment(it)?.first?.student?.id } }) }
    var query by rememberSaveable { mutableStateOf("") }
    // Items picked on the Payables tab may belong to several students.
    val presetStudents = presetItems.mapNotNull { k -> k.substringBefore(':').toLongOrNull()?.let { snap.enrollment(it)?.first } }.distinctBy { it.student.id }
    val multi = presetStudents.size > 1

    Column(Modifier.fillMaxSize()) {
        AppTopBar("Collect Payment", onBack)
        val sid = studentId
        if (!multi && (sid == null || snap.ledger(sid) == null)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("1. Select a student", style = MaterialTheme.typography.titleSmall)
                SearchField(query, { query = it }, "Type name, Enroll No. or Student ID")
            }
            val matches = snap.ledgers.filter { it.student.active && it.activeEnrollments.isNotEmpty() && Reports.matches(it.student, query) }
                .sortedWith(compareByDescending<StudentLedger> { it.balance > 0 }.thenBy { it.student.name.lowercase() }).take(50)
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(matches, key = { it.student.id }) { m ->
                    Row(Modifier.fillMaxWidth().clickable { studentId = m.student.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.student.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${m.student.idText} · ${m.classesText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money.format(m.balance), color = if (m.balance > 0) Red else Green, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider()
                }
            }
        } else {
            val ledgers = if (multi) presetStudents else listOf(snap.ledger(sid!!)!!)
            CollectForm(vm, snap, ledgers, presetItems.toSet(),
                onChangeStudent = if (presetStudent == null && presetItems.isEmpty()) ({ studentId = null }) else null, onCancel = onBack, onSaved = onSaved)
        }
    }
}

@Composable
private fun CollectForm(vm: AppViewModel, snap: AppSnapshot, ledgers: List<StudentLedger>, preset: Set<String>, onChangeStudent: (() -> Unit)?, onCancel: () -> Unit, onSaved: (Long) -> Unit) {
    val options = ledgers.flatMap { optionsFor(it) }
    val byKey = options.associateBy { it.key }
    // Line keys "eid:seq"; preset from the Payables tab or the student page. Default: the earliest due line.
    val selected = remember(ledgers.map { it.student.id }) {
        mutableStateMapOf<String, String>().apply {
            val start = preset.filter { it in byKey }.ifEmpty { options.filter { it.seq != null }.take(1).map { it.key } }
            start.forEach { put(it, byKey[it]!!.remaining.takeIf { r -> r > 0 }?.toString() ?: "") }
        }
    }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var remarks by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val chosen = options.filter { it.key in selected.keys }
    val amounts = chosen.associate { it.key to (selected[it.key]?.toLongOrNull() ?: 0L) }
    val total = amounts.values.sum()
    val balance = ledgers.sumOf { it.balance }
    val over = total - balance
    val valid = chosen.isNotEmpty() && amounts.values.all { it > 0 }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionCard(if (ledgers.size > 1) "Students (${ledgers.size})" else "Student") {
            ledgers.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.student.name, style = MaterialTheme.typography.titleMedium)
                        Text("${l.student.idText} · ${l.classesText}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(Money.format(l.balance), color = if (l.balance > 0) Red else Green, fontWeight = FontWeight.SemiBold)
                }
            }
            if (onChangeStudent != null) TextButton(onClick = onChangeStudent) { Text("Change student") }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            KeyValue("Outstanding balance", Money.mmk(balance), bold = true, valueColor = if (balance > 0) Red else Green)
        }
        Text("Select what is being paid (all classes):", style = MaterialTheme.typography.titleSmall)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(vertical = 4.dp)) {
                if (options.isEmpty()) Text("Nothing to pay", Modifier.padding(12.dp))
                options.forEachIndexed { i, o ->
                    if (i > 0) HorizontalDivider()
                    val on = o.key in selected.keys
                    Row(Modifier.fillMaxWidth().clickable { if (on) selected.remove(o.key) else selected[o.key] = o.remaining.takeIf { it > 0 }?.toString() ?: "" }.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(on, { c -> if (!c) selected.remove(o.key) else selected[o.key] = o.remaining.takeIf { it > 0 }?.toString() ?: "" })
                        Column(Modifier.weight(1f)) {
                            Text((if (ledgers.size > 1) "${o.studentName}: " else "") + o.title, style = MaterialTheme.typography.bodyMedium)
                            Text(o.subtitle + if (o.seq != null) " · remaining ${Money.format(o.remaining)}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (on) MoneyField("Amount", selected[o.key] ?: "", { selected[o.key] = it }, Modifier.width(130.dp), isError = (amounts[o.key] ?: 0) <= 0)
                    }
                }
            }
        }
        KeyValue("Total to receive (${chosen.size} item${if (chosen.size == 1) "" else "s"})", Money.mmk(total), bold = true)
        if (chosen.size > 1) Text("All selected items are saved on ONE combined receipt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        chosen.filter { it.seq != null && (amounts[it.key] ?: 0) > it.remaining }.forEach {
            Text("${it.title}: ${Money.format((amounts[it.key] ?: 0) - it.remaining)} more than remaining – the excess goes to the next unpaid parts of that class.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        if (over > 0) Text("This is ${Money.mmk(over)} more than the outstanding balance. You will be asked to confirm.", color = Red, style = MaterialTheme.typography.bodySmall)
        DateField("Collection date", date, { if (it != null) date = it }, maxDate = LocalDate.now())
        OutlinedTextField(remarks, { remarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = { confirm = true }, enabled = valid && !saving, modifier = Modifier.weight(1f)) { Text("Save & Receipt") }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (over > 0) "Confirm OVERPAYMENT" else if (chosen.size > 1) "Confirm combined payment" else "Confirm payment") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyValue("Date", date.fmt())
                    chosen.forEach { KeyValue((if (ledgers.size > 1) "${it.studentName}: " else "") + it.title, Money.format(amounts[it.key] ?: 0)) }
                    HorizontalDivider()
                    KeyValue("Total", Money.mmk(total), bold = true)
                    KeyValue("Balance after", Money.mmk(balance - total), valueColor = if (balance - total < 0) Red else Color.Unspecified)
                    if (over > 0) Text("The total paid will exceed the net total by ${Money.mmk(over)}. Only continue if this is intended (e.g. advance payment).", color = Red, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false; saving = true
                    val items = chosen.map { PayItem(it.studentId, it.enrollmentId, it.seq, amounts[it.key] ?: 0, it.paymentFor) }
                    vm.recordPayment(PaymentRequest(date, items, remarks)) { rid -> saving = false; onSaved(rid) }
                }) { Text(if (over > 0) "Confirm overpayment" else "Save") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
    LaunchedEffect(saving) { if (saving) { kotlinx.coroutines.delay(4000); saving = false } }
}

// ===================================================== Receipt
@Composable
fun ReceiptScreen(vm: AppViewModel, snap: AppSnapshot, session: Session, receiptId: Long, onBack: () -> Unit) {
    val r = snap.receiptById[receiptId]
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Receipt", onBack) {
            if (r != null) IconButton(onClick = { printReceipt(context, receiptView(snap, r)); vm.markPrinted(listOf(r.id)) }) { Icon(Icons.Default.Print, contentDescription = "Print") }
        }
        if (r == null) { EmptyState("Receipt not found"); return@Column }
        val v = receiptView(snap, r)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(Modifier.widthIn(max = 560.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(snap.settings.schoolName, style = MaterialTheme.typography.titleLarge, color = Navy, textAlign = TextAlign.Center)
                    if (snap.settings.address.isNotBlank()) Text(snap.settings.address, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(if (v.lines.size > 1) "OFFICIAL FEE RECEIPT (COMBINED)" else "OFFICIAL FEE RECEIPT", style = MaterialTheme.typography.titleSmall)
                    if (r.voided) Text("*** VOIDED – ${r.voidReason} ***", color = Red, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    else if (r.printCount > 0) Text("Printed ${r.printCount} time(s) – reprints are marked DUPLICATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    KeyValue("Receipt No.", r.receiptNo, bold = true)
                    KeyValue("Date", r.date.fmt())
                    KeyValue("Received from", r.payerName, bold = true)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    v.lines.forEach { line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(line.description, style = MaterialTheme.typography.bodyMedium)
                                Text("${line.studentName} · ${line.studentId} · ${line.className}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Money.format(line.amount), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    KeyValue("Amount received", Money.mmk(r.total), bold = true, valueColor = Green)
                    KeyValue("Previous balance", Money.mmk(r.previousBalance))
                    KeyValue("Remaining balance", Money.mmk(r.remainingBalance), bold = true)
                    if (r.remarks.isNotBlank()) KeyValue("Remarks", r.remarks)
                    KeyValue("Received by", r.receivedBy)
                    Spacer(Modifier.height(28.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("______________\nSignature", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Text("______________\nParent / Guardian", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { printReceipt(context, receiptView(snap, r)); vm.markPrinted(listOf(r.id)) }, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Icon(Icons.Default.Print, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (r.printCount > 0) "Reprint" else "Print / save as PDF")
            }
        }
    }
}

fun receiptView(snap: AppSnapshot, r: ReceiptRecord) = ReceiptView(
    schoolName = snap.settings.schoolName, schoolAddress = snap.settings.address, schoolPhone = snap.settings.phone,
    receiptNo = r.receiptNo, date = r.date, payerName = r.payerName,
    lines = r.items.map { p ->
        val pair = snap.enrollment(p.enrollmentId)
        val s = pair?.first?.student ?: snap.ledger(p.studentId)?.student
        val e = pair?.second?.enrollment
        ReceiptLine(s?.name ?: "?", s?.idText ?: "", e?.let { "${it.className} (${it.academicYear})" } ?: "", p.paymentFor, p.amount)
    },
    previousBalance = r.previousBalance, remainingBalance = r.remainingBalance, receivedBy = r.receivedBy, remarks = r.remarks,
    reprint = r.printCount > 0, voided = r.voided,
)

private fun printReceipt(context: Context, v: ReceiptView) = Printer.printHtml(context, HtmlDocs.receipt(v), "Receipt ${v.receiptNo}", isReport = false)

private fun printCombined(context: Context, snap: AppSnapshot, list: List<ReceiptRecord>) {
    if (list.isEmpty()) return
    val html = HtmlDocs.combinedReceipts(list.map { receiptView(snap, it) }, snap.settings.schoolName, snap.settings.address, snap.settings.phone)
    Printer.printHtml(context, html, "Combined receipt ${list.first().receiptNo}${if (list.size > 1) " +${list.size - 1}" else ""}", isReport = false)
}
