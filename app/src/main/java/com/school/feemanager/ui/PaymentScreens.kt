@file:OptIn(ExperimentalMaterial3Api::class)

package com.school.feemanager.ui

import android.content.Context
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.school.feemanager.data.PaymentRequest
import com.school.feemanager.domain.Money
import com.school.feemanager.domain.PayStatus
import com.school.feemanager.domain.PaymentRecord
import com.school.feemanager.domain.Reports
import com.school.feemanager.domain.ScheduleCalculator
import com.school.feemanager.domain.StudentLedger
import com.school.feemanager.io.HtmlDocs
import com.school.feemanager.io.ReceiptView
import com.school.feemanager.ui.theme.Green
import com.school.feemanager.ui.theme.Navy
import com.school.feemanager.ui.theme.Red
import com.school.feemanager.util.Printer
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// ===================================================== Payment schedule (tab 3)
@Composable
fun PaymentScheduleScreen(snap: AppSnapshot, onCollect: (Long, Int) -> Unit, onStudent: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var cls by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf<PayStatus?>(null) }
    val list = snap.ledgers.filter { it.student.active }
        .filter { cls == null || it.student.className == cls }
        .filter { status == null || Reports.statusMatches(it, status!!) }
        .filter { Reports.matches(it.student, query) }
        .sortedWith(compareBy<StudentLedger> { snap.card.classNames.indexOf(it.student.className) }.thenBy { it.student.name.lowercase() })
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Payment Schedule")
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchField(query, { query = it }, "Search student")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Dropdown("Class", listOf<String?>(null) + snap.card.classNames, cls, { it ?: "All classes" }, { cls = it }, Modifier.width(150.dp))
                PayStatus.entries.forEach { st -> FilterChip(selected = status == st, onClick = { status = if (status == st) null else st }, label = { Text(st.label) }) }
            }
            Text("Scheduled ${Money.mmk(list.sumOf { it.net })} · Paid ${Money.mmk(list.sumOf { it.paid })} · Remaining ${Money.mmk(list.sumOf { it.balance })}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (list.isEmpty()) EmptyState("No students match")
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.student.id }) { l -> ScheduleCard(l, { onCollect(l.student.id, it) }, { onStudent(l.student.id) }) }
        }
    }
}

@Composable
private fun ScheduleCard(l: StudentLedger, onCollect: (Int) -> Unit, onStudent: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.student.name, style = MaterialTheme.typography.titleSmall)
                    Text("${l.student.className} · ${l.student.term.label} · Net ${Money.format(l.net)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Paid ${Money.format(l.paid)} · Remaining ${Money.format(l.balance)}", style = MaterialTheme.typography.bodySmall)
                }
                StatusChip(l.status)
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (open) {
                Spacer(Modifier.height(6.dp))
                ScheduleTable(l, onCollect)
                TextButton(onClick = onStudent) { Text("Open student profile") }
            }
        }
    }
}

// ===================================================== Cash collection register (tab 4)
@Composable
fun CollectionScreen(vm: AppViewModel, snap: AppSnapshot, session: Session, onNew: () -> Unit, onReceipt: (Long) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar("Cash Collection")
            TabRow(selectedTabIndex = tab) {
                listOf("Register", "Daily", "Monthly").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> RegisterTab(vm, snap, session, onReceipt)
                1 -> DailySummaryTab(snap)
                else -> MonthlySummaryTab(snap)
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNew, icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text("Collect") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun RegisterTab(vm: AppViewModel, snap: AppSnapshot, session: Session, onReceipt: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    var voidTarget by remember { mutableStateOf<PaymentRecord?>(null) }
    val byId = snap.ledgers.associateBy { it.student.id }
    val f = from
    val t = to
    val list = snap.payments
        .filter { f == null || !it.date.isBefore(f) }
        .filter { t == null || !it.date.isAfter(t) }
        .filter { p ->
            val q = query.trim()
            q.isEmpty() || p.receiptNo.contains(q, true) || byId[p.studentId]?.student?.let { Reports.matches(it, q) } == true
        }
    Column {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchField(query, { query = it }, "Search receipt no., student name or ID")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("From", from, { from = it }, Modifier.weight(1f), allowClear = true)
                DateField("To", to, { to = it }, Modifier.weight(1f), allowClear = true)
            }
            val valid = list.filter { !it.voided }
            Text("${valid.size} receipts · ${Money.mmk(valid.sumOf { it.amount })}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        if (list.isEmpty()) EmptyState("No transactions")
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp)) {
            items(list, key = { it.id }) { p ->
                val s = byId[p.studentId]?.student
                Row(Modifier.fillMaxWidth().clickable { snap.receiptsByTx[p.id]?.let { onReceipt(it.id) } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${p.receiptNo} · ${p.date.fmt()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${s?.name ?: "?"} (${s?.className ?: ""}) · ${p.paymentFor}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (p.voided) Text("VOIDED", style = MaterialTheme.typography.labelSmall, color = Red)
                    }
                    Text(Money.format(p.amount), fontWeight = FontWeight.SemiBold, color = if (p.voided) Red else Green)
                    if (session.isAdmin && !p.voided) IconButton(onClick = { voidTarget = p }) { Icon(Icons.Default.Block, contentDescription = "Void", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                HorizontalDivider()
            }
        }
    }
    voidTarget?.let { p -> VoidDialog(p, onDismiss = { voidTarget = null }) { reason -> vm.voidPayment(p.id, reason) } }
}

@Composable
private fun VoidDialog(p: PaymentRecord, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Void receipt ${p.receiptNo}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The transaction of ${Money.mmk(p.amount)} stays in the register marked VOIDED and no longer counts toward the student's payments. This cannot be undone.")
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
            rows = days.map { (d, l) -> listOf(d.fmt(), l.size.toLong(), l.sumOf { it.amount }) },
            totals = listOf("TOTAL", days.values.sumOf { it.size }.toLong(), days.values.sumOf { l -> l.sumOf { it.amount } }),
            numeric = { it > 0 }, colWidth = { if (it == 0) 120 else 120 },
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
            rows = months.map { (m, l) -> listOf(m.format(fmt), l.size.toLong(), l.sumOf { it.amount }) },
            totals = listOf("TOTAL", months.values.sumOf { it.size }.toLong(), months.values.sumOf { l -> l.sumOf { it.amount } }),
            numeric = { it > 0 }, colWidth = { 120 },
        )
        Spacer(Modifier.height(80.dp))
    }
}

// ===================================================== Collect a payment
@Composable
fun CollectPaymentScreen(vm: AppViewModel, snap: AppSnapshot, presetStudent: Long?, presetSeq: Int?, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    var studentId by rememberSaveable { mutableStateOf(presetStudent) }
    var query by rememberSaveable { mutableStateOf("") }
    val l = studentId?.let { snap.ledger(it) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("Collect Payment", onBack)
        if (l == null) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("1. Select a student", style = MaterialTheme.typography.titleSmall)
                SearchField(query, { query = it }, "Type name or Student ID")
            }
            val matches = snap.ledgers.filter { it.student.active && Reports.matches(it.student, query) }
                .sortedWith(compareByDescending<StudentLedger> { it.balance > 0 }.thenBy { it.student.name.lowercase() }).take(50)
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(matches, key = { it.student.id }) { m ->
                    Row(Modifier.fillMaxWidth().clickable { studentId = m.student.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.student.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${m.student.code} · ${m.student.className} · ${m.student.term.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money.format(m.balance), color = if (m.balance > 0) Red else Green, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider()
                }
            }
        } else {
            CollectForm(vm, snap, l, presetSeq, onChangeStudent = if (presetStudent == null) ({ studentId = null }) else null, onCancel = onBack, onSaved = onSaved)
        }
    }
}

private data class PayOption(val seq: Int?, val label: String, val remaining: Long)

@Composable
private fun CollectForm(vm: AppViewModel, snap: AppSnapshot, l: StudentLedger, presetSeq: Int?, onChangeStudent: (() -> Unit)?, onCancel: () -> Unit, onSaved: (Long) -> Unit) {
    val s = l.student
    val options = l.lines.filter { it.remaining > 0 || it.line.seq == presetSeq }
        .map { PayOption(it.line.seq, "${ScheduleCalculator.longLabelFor(s.term, it.line.seq)} (${it.line.label}) – due ${it.line.dueDate.fmt()}", it.remaining) } +
        PayOption(null, "General / other payment", l.balance.coerceAtLeast(0))
    val initial = options.firstOrNull { it.seq == presetSeq } ?: options.first()
    var option by remember(s.id) { mutableStateOf(initial) }
    var amount by remember(s.id) { mutableStateOf(initial.remaining.takeIf { it > 0 }?.toString() ?: "") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var remarks by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val amt = amount.toLongOrNull() ?: 0L
    val over = amt - l.balance

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionCard("Student") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                    Text("${s.code} · ${s.className} · ${s.type.label} · ${s.term.label}", style = MaterialTheme.typography.bodySmall)
                }
                if (onChangeStudent != null) TextButton(onClick = onChangeStudent) { Text("Change") }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            KeyValue("Net total", Money.mmk(l.net))
            KeyValue("Paid so far", Money.mmk(l.paid))
            KeyValue("Outstanding balance", Money.mmk(l.balance), bold = true, valueColor = if (l.balance > 0) Red else Green)
        }
        Dropdown("Payment for", options, option, { it.label }, { option = it; if (it.remaining > 0) amount = it.remaining.toString() })
        if (option.seq != null) Text("Remaining on this ${if (s.term.parts == 3) "installment" else "month"}: ${Money.mmk(option.remaining)}", style = MaterialTheme.typography.bodySmall)
        MoneyField("Amount received (MMK)", amount, { amount = it }, isError = amount.isNotEmpty() && amt <= 0)
        if (over > 0) Text("This is ${Money.mmk(over)} more than the outstanding balance. You will be asked to confirm.", color = Red, style = MaterialTheme.typography.bodySmall)
        DateField("Collection date", date, { if (it != null) date = it }, maxDate = LocalDate.now())
        OutlinedTextField(remarks, { remarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = { confirm = true }, enabled = amt > 0 && !saving, modifier = Modifier.weight(1f)) { Text("Save & Receipt") }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (over > 0) "Confirm OVERPAYMENT" else "Confirm payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyValue("Student", s.name)
                    KeyValue("Payment for", option.label.substringBefore(" –"))
                    KeyValue("Date", date.fmt())
                    KeyValue("Amount", Money.mmk(amt), bold = true)
                    KeyValue("Balance after", Money.mmk(l.balance - amt), valueColor = if (l.balance - amt < 0) Red else Color.Unspecified)
                    if (over > 0) Text("The total paid will exceed the student's net total by ${Money.mmk(over)}. Only continue if this is intended (e.g. advance payment).", color = Red, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false; saving = true
                    val forText = option.seq?.let { ScheduleCalculator.longLabelFor(s.term, it) } ?: "General payment"
                    vm.recordPayment(PaymentRequest(s.id, date, amt, option.seq, forText, remarks)) { rid -> saving = false; onSaved(rid) }
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
    val receipt = snap.receiptsByTx.values.firstOrNull { it.id == receiptId }
    val p = receipt?.let { r -> snap.payments.firstOrNull { it.id == r.transactionId } }
    val st = p?.let { snap.ledger(it.studentId)?.student }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Receipt", onBack) {
            if (receipt != null && p != null && st != null) IconButton(onClick = {
                val view = receiptView(snap, receipt.receiptNo, p, st.code, st.name, st.className, receipt.previousBalance, receipt.remainingBalance, receipt.printCount > 0)
                printReceipt(context, view)
                vm.markPrinted(receipt.id)
            }) { Icon(Icons.Default.Print, contentDescription = "Print") }
        }
        if (receipt == null || p == null || st == null) EmptyState("Receipt not found")
        else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(Modifier.widthIn(max = 520.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(snap.settings.schoolName, style = MaterialTheme.typography.titleLarge, color = Navy, textAlign = TextAlign.Center)
                    if (snap.settings.address.isNotBlank()) Text(snap.settings.address, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("OFFICIAL FEE RECEIPT", style = MaterialTheme.typography.titleSmall)
                    if (p.voided) Text("*** VOIDED ***", color = Red, fontWeight = FontWeight.Bold)
                    else if (receipt.printCount > 0) Text("Printed ${receipt.printCount} time(s) – reprints are marked DUPLICATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    KeyValue("Receipt No.", receipt.receiptNo, bold = true)
                    KeyValue("Date", p.date.fmt())
                    KeyValue("Student", st.name, bold = true)
                    KeyValue("Student ID", st.code)
                    KeyValue("Class", st.className)
                    KeyValue("Payment for", p.paymentFor)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    KeyValue("Amount received", Money.mmk(p.amount), bold = true, valueColor = Green)
                    KeyValue("Previous balance", Money.mmk(receipt.previousBalance))
                    KeyValue("Remaining balance", Money.mmk(receipt.remainingBalance), bold = true)
                    if (p.remarks.isNotBlank()) KeyValue("Remarks", p.remarks)
                    KeyValue("Received by", p.receivedBy)
                    Spacer(Modifier.height(28.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("______________\nSignature", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                        Text("______________\nParent / Guardian", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                printReceipt(context, receiptView(snap, receipt.receiptNo, p, st.code, st.name, st.className, receipt.previousBalance, receipt.remainingBalance, receipt.printCount > 0))
                vm.markPrinted(receipt.id)
            }, modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Icon(Icons.Default.Print, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(if (receipt.printCount > 0) "Reprint" else "Print receipt")
            }
        }
    }
}

private fun receiptView(snap: AppSnapshot, no: String, p: PaymentRecord, code: String, name: String, cls: String, prev: Long, rem: Long, reprint: Boolean) = ReceiptView(
    schoolName = snap.settings.schoolName, schoolAddress = snap.settings.address, schoolPhone = snap.settings.phone,
    receiptNo = no, date = p.date, studentCode = code, studentName = name, className = cls, amount = p.amount,
    description = p.paymentFor, previousBalance = prev, remainingBalance = rem, receivedBy = p.receivedBy, remarks = p.remarks,
    reprint = reprint, voided = p.voided,
)

private fun printReceipt(context: Context, v: ReceiptView) = Printer.printHtml(context, HtmlDocs.receipt(v), "Receipt ${v.receiptNo}", isReport = false)
