package com.school.feemanager.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.school.feemanager.data.AppSnapshot
import com.school.feemanager.domain.Money
import com.school.feemanager.domain.PayStatus
import com.school.feemanager.domain.Payable
import com.school.feemanager.domain.ReceiptRecord
import com.school.feemanager.domain.ReportType
import com.school.feemanager.domain.Reports
import com.school.feemanager.domain.StudentLedger
import com.school.feemanager.domain.StudentType
import com.school.feemanager.io.HtmlDocs
import com.school.feemanager.ui.AppNav
import com.school.feemanager.ui.AppViewModel
import com.school.feemanager.ui.DesktopPageHeader
import com.school.feemanager.ui.LocalPlatform
import com.school.feemanager.ui.Session
import com.school.feemanager.ui.TopScreens
import com.school.feemanager.ui.description
import com.school.feemanager.ui.fmt
import com.school.feemanager.ui.receiptView
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** The Windows layouts of the five main pages (wide tables, filters in one row). */
object DesktopScreens : TopScreens {

    // ============================================================ Dashboard
    @Composable
    override fun Dashboard(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav) {
        val d = snap.dashboard
        Column(Modifier.fillMaxSize()) {
            DesktopPageHeader("Dashboard") {
                Text("${d.activeEnrollments} active class enrollments · Today ${snap.today.fmt()}", style = MaterialTheme.typography.bodyLarge, color = School.Muted)
            }
            Column(Modifier.fillMaxSize().background(School.Background).verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    AccentCard("Total Students Enrolled", d.totalStudents.toString(), School.Navy, Modifier.weight(1f))
                    AccentCard("Total Fee Receivable (MMK)", Money.format(d.totalReceivable), School.Navy, Modifier.weight(1f))
                    AccentCard("Total Cash Collected (MMK)", Money.format(d.totalCollected), School.Green, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    AccentCard("Total Outstanding Balance (MMK)", Money.format(d.totalOutstanding), School.Red, Modifier.weight(1f))
                    AccentCard("Day Students", d.dayStudents.toString(), School.Blue, Modifier.weight(1f))
                    AccentCard("Hostel Students", d.hostelStudents.toString(), School.Blue, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    AccentCard("Collected Today", Money.format(d.collectedToday), School.Green, Modifier.weight(1f))
                    AccentCard("Collected This Month", Money.format(d.collectedThisMonth), School.Green, Modifier.weight(1f))
                    AccentCard("Receipts Issued", d.receiptsIssued.toString(), School.Navy, Modifier.weight(1f))
                    AccentCard("Students With Balance", d.studentsWithBalance.toString(), School.Amber, Modifier.weight(1f), extra = "(${d.overdueStudents} overdue)")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Panel("Collection performance", Modifier.weight(0.85f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${d.collectionRatePct}%", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(10.dp))
                            Text("of total receivable collected", style = MaterialTheme.typography.bodyLarge, color = School.Muted)
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { (d.collectionRatePct / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(14.dp), color = School.Green, trackColor = School.TableHeader, drawStopIndicator = {})
                        Spacer(Modifier.height(24.dp))
                        Text("Cash collected – last 6 months (MMK)", style = MaterialTheme.typography.bodyLarge, color = School.Muted)
                        Spacer(Modifier.height(10.dp))
                        MonthChart(d.lastSixMonths.map { it.month to it.amount })
                    }
                    Panel("Recent cash collection", Modifier.weight(1.15f)) {
                        val cols = listOf(Col("Date", 0f, width = 124.dp), Col("Receipt", 0f, width = 160.dp), Col("Student", 1f), Col("Class", 0f, width = 78.dp), Col("Amount", 0f, true, width = 105.dp))
                        val recent = snap.payments.filter { !it.voided }.sortedWith(compareByDescending<com.school.feemanager.domain.PaymentRecord> { it.date }.thenByDescending { it.id }).take(8)
                        DataGrid(cols, Modifier.fillMaxWidth().heightIn(max = 520.dp), empty = if (recent.isEmpty()) "No payments recorded yet" else null) {
                            items(recent, key = { it.id }) { p ->
                                val pair = snap.enrollment(p.enrollmentId)
                                GridRow(onClick = { nav.receipt(p.receiptId) }) {
                                    TextCell(cols[0], p.date.fmt()); TextCell(cols[1], p.receiptNo)
                                    TextCell(cols[2], pair?.first?.student?.name ?: "?"); TextCell(cols[3], pair?.second?.enrollment?.className ?: "")
                                    TextCell(cols[4], Money.format(p.amount))
                                }
                            }
                        }
                    }
                }
                val rows = d.classSummaries.filter { it.totalStudents > 0 || it.totalReceivable > 0 }
                if (rows.isNotEmpty()) Panel("Fee collection summary by class") {
                    val cols = listOf(Col("Class", 1f), Col("Day", 0.7f, true), Col("Hostel", 0.7f, true), Col("Total", 0.7f, true), Col("Receivable", 1.3f, true), Col("Collected", 1.3f, true), Col("Balance", 1.3f, true))
                    DataGrid(cols, Modifier.fillMaxWidth().heightIn(max = 900.dp), footer = {
                        TextCell(cols[0], "TOTAL", bold = true)
                        TextCell(cols[1], rows.sumOf { it.dayStudents }.toString(), bold = true); TextCell(cols[2], rows.sumOf { it.hostelStudents }.toString(), bold = true)
                        TextCell(cols[3], rows.sumOf { it.totalStudents }.toString(), bold = true); TextCell(cols[4], Money.format(rows.sumOf { it.totalReceivable }), bold = true)
                        TextCell(cols[5], Money.format(rows.sumOf { it.collected }), bold = true); TextCell(cols[6], Money.format(rows.sumOf { it.balance }), bold = true)
                    }) {
                        items(rows, key = { it.className }) { c ->
                            GridRow {
                                TextCell(cols[0], c.className); TextCell(cols[1], c.dayStudents.toString()); TextCell(cols[2], c.hostelStudents.toString())
                                TextCell(cols[3], c.totalStudents.toString()); TextCell(cols[4], Money.format(c.totalReceivable)); TextCell(cols[5], Money.format(c.collected))
                                TextCell(cols[6], Money.format(c.balance), color = if (c.balance > 0) School.Red else School.Text)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MonthChart(months: List<Pair<YearMonth, Long>>) {
        val max = (months.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
        val fmt = DateTimeFormatter.ofPattern("MMM")
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val slot = size.width / months.size.coerceAtLeast(1)
            val barW = slot * 0.5f
            drawLine(School.Line, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
            months.forEachIndexed { i, (_, v) ->
                val h = (v.toFloat() / max) * (size.height - 6f)
                val left = i * slot + (slot - barW) / 2
                drawRoundRect(if (v > 0) School.Blue else School.TableHeader, Offset(left, size.height - h.coerceAtLeast(3f)), Size(barW, h.coerceAtLeast(3f)), CornerRadius(6f, 6f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            months.forEach { (m, v) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(m.format(fmt), style = MaterialTheme.typography.bodySmall)
                    Text(Money.format(v), style = MaterialTheme.typography.bodySmall, color = School.Muted)
                }
            }
        }
    }

    // ============================================================ Students
    @Composable
    override fun Students(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav) {
        var query by rememberSaveable { mutableStateOf("") }
        var cls by rememberSaveable { mutableStateOf<String?>(null) }
        var type by rememberSaveable { mutableStateOf<StudentType?>(null) }
        var status by rememberSaveable { mutableStateOf<PayStatus?>(null) }
        var inactive by rememberSaveable { mutableStateOf(false) }
        val list = snap.ledgers
            .filter { inactive || it.student.active }
            .filter { l -> cls == null || l.enrollments.any { it.enrollment.className == cls } }
            .filter { l -> type == null || l.activeEnrollments.any { it.enrollment.type == type } }
            .filter { status == null || Reports.statusMatches(it, status!!) }
            .filter { Reports.matches(it.student, query) }
            .sortedWith(compareBy<StudentLedger> { it.student.code })
        val cols = listOf(Col("Enroll No.", 0f, width = 120.dp), Col("Student ID", 0f, width = 122.dp), Col("Student Name", 1.5f), Col("Classes", 1f), Col("Type", 0f, width = 96.dp),
            Col("Net Total", 0f, true, width = 118.dp), Col("Paid", 0f, true, width = 112.dp), Col("Balance", 0f, true, width = 118.dp), Col("Status", 0f, width = 145.dp))
        Column(Modifier.fillMaxSize()) {
            DesktopPageHeader("Student Register (${list.size})") { if (session.isAdmin) PrimaryButton("+  Register student", nav::newStudent) }
            PageBody {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SearchBox(query, { query = it }, "Search name, Enroll No. or Student ID", Modifier.width(380.dp))
                    SelectBox(listOf<String?>(null) + snap.card.classNames, cls, { it ?: "All classes" }, { cls = it })
                    SelectBox(listOf<StudentType?>(null) + StudentType.entries, type, { it?.label ?: "Day & Hostel" }, { type = it })
                    SelectBox(listOf<PayStatus?>(null) + PayStatus.entries, status, { it?.label ?: "Any status" }, { status = it })
                    CheckLabel("Show inactive", inactive) { inactive = it }
                }
                DataGrid(cols, Modifier.weight(1f, fill = false),
                    empty = if (list.isEmpty()) (if (snap.ledgers.isEmpty()) "No students yet – click “Register student”, or import Excel / CSV (Settings → Data)." else "No students match the filters") else null,
                    footer = if (list.isEmpty()) null else ({
                        TextCell(cols[0], "TOTAL", bold = true); TextCell(cols[1], ""); TextCell(cols[2], "${list.size} students", bold = true); TextCell(cols[3], ""); TextCell(cols[4], "")
                        TextCell(cols[5], Money.format(list.sumOf { it.net }), bold = true); TextCell(cols[6], Money.format(list.sumOf { it.paid }), bold = true)
                        TextCell(cols[7], Money.format(list.sumOf { it.balance }), bold = true); TextCell(cols[8], "")
                    })) {
                    items(list, key = { it.student.id }) { l ->
                        val s = l.student
                        GridRow(onClick = { nav.student(s.id) }) {
                            TextCell(cols[0], s.enrollNo.ifBlank { "–" }); TextCell(cols[1], s.code)
                            TextCell(cols[2], s.name, color = if (s.active) School.Text else School.Muted)
                            TextCell(cols[3], l.classesText)
                            TextCell(cols[4], l.activeEnrollments.map { it.enrollment.type.label }.distinct().joinToString(" / ").ifEmpty { "–" })
                            TextCell(cols[5], Money.format(l.net)); TextCell(cols[6], Money.format(l.paid))
                            TextCell(cols[7], Money.format(l.balance), color = if (l.balance > 0) School.Red else School.Green)
                            Cell(cols[8]) { if (s.active) StatusPill(l.status) else Text("Inactive", color = School.Muted) }
                        }
                    }
                }
            }
        }
    }

    // ============================================================ Payments (payables, multi-select)
    @Composable
    override fun Payables(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav) {
        var query by rememberSaveable { mutableStateOf("") }
        var cls by rememberSaveable { mutableStateOf<String?>(null) }
        var status by rememberSaveable { mutableStateOf<PayStatus?>(null) }
        var dueOnly by rememberSaveable { mutableStateOf(false) }
        var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
        val open = remember { mutableStateMapOf<Long, Boolean>() }
        val today = snap.today
        val groups: List<Pair<StudentLedger, List<Payable>>> = snap.ledgers
            .filter { it.student.active && Reports.matches(it.student, query) }
            .map { l ->
                l to l.payables.filter { cls == null || it.enrollment.enrollment.className == cls }
                    .filter { status == null || it.line.status == status }
                    .filter { !dueOnly || !it.line.line.dueDate.isAfter(today) }
            }
            .filter { it.second.isNotEmpty() }
            .sortedWith(compareBy<Pair<StudentLedger, List<Payable>>> { g -> g.second.minOf { it.line.line.dueDate } }.thenBy { it.first.student.name.lowercase() })
        val all = groups.flatMap { it.second }.associateBy { it.key }
        val sel = selected.filter { it in all }.toSet()
        val cols = listOf(Col("", 0f, width = 92.dp), Col("Student / Installment", 1.5f), Col("Enroll No. · ID", 1.1f), Col("Class", 1f), Col("Due", 0f, width = 118.dp),
            Col("Scheduled", 0f, true, width = 112.dp), Col("Paid", 0f, true, width = 100.dp), Col("Remaining", 0f, true, width = 115.dp), Col("Status", 0f, width = 145.dp), Col("", 0f, width = 64.dp))
        Column(Modifier.fillMaxSize()) {
            DesktopPageHeader("Payments – Payables") { PrimaryButton("+  Collect payment", { nav.collect() }) }
            PageBody {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SearchBox(query, { query = it }, "Search name, Enroll No. or Student ID", Modifier.width(380.dp))
                    SelectBox(listOf<String?>(null) + snap.card.classNames, cls, { it ?: "All classes" }, { cls = it })
                    SelectBox(listOf(null, PayStatus.OVERDUE, PayStatus.UNPAID, PayStatus.PARTIAL), status, { it?.label ?: "Any status" }, { status = it })
                    CheckLabel("Due by today", dueOnly) { dueOnly = it }
                }
                Text("Click ▸ to show a student's installments / months. Tick any items – of one or several students – and click “Pay selected” to collect them on ONE combined receipt.",
                    style = MaterialTheme.typography.bodyMedium, color = School.Muted)
                SelectionBar(sel.size, Money.mmk(sel.sumOf { all[it]!!.remaining }), "Pay selected", { nav.collect(null, sel.toList()) }, { selected = emptySet() }) {
                    LinkText(if (all.isNotEmpty() && sel.size == all.size) "Unselect all" else "Select all shown", { selected = if (sel.size == all.size) emptySet() else all.keys })
                    Spacer(Modifier.width(14.dp))
                }
                DataGrid(cols, Modifier.weight(1f, fill = false), empty = if (groups.isEmpty()) "Nothing to pay for the selected filters" else null,
                    footer = if (groups.isEmpty()) null else ({
                        TextCell(cols[0], ""); TextCell(cols[1], "TOTAL", bold = true); TextCell(cols[2], "${groups.size} students · ${all.size} items", bold = true); TextCell(cols[3], ""); TextCell(cols[4], "")
                        TextCell(cols[5], Money.format(all.values.sumOf { it.line.line.amount }), bold = true); TextCell(cols[6], Money.format(all.values.sumOf { it.line.paid }), bold = true)
                        TextCell(cols[7], Money.format(all.values.sumOf { it.remaining }), bold = true); TextCell(cols[8], ""); TextCell(cols[9], "")
                    })) {
                    groups.forEach { (l, items) ->
                        val sid = l.student.id
                        val isOpen = open[sid] == true
                        val keys = items.map { it.key }
                        val state = when { keys.all { it in sel } -> ToggleableState.On; keys.any { it in sel } -> ToggleableState.Indeterminate; else -> ToggleableState.Off }
                        item(key = "s$sid") {
                            GridRow(background = Color(0xFFF7FAFD), onClick = { open[sid] = !isOpen }) {
                                Cell(cols[0]) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (isOpen) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = if (isOpen) "Hide" else "Show", tint = School.Navy)
                                        TriStateCheckbox(state, onClick = { selected = if (state == ToggleableState.On) sel - keys.toSet() else sel + keys }, colors = CheckboxDefaults.colors(checkedColor = School.Navy))
                                    }
                                }
                                TextCell(cols[1], l.student.name, bold = true); TextCell(cols[2], l.student.idText); TextCell(cols[3], l.classesText)
                                TextCell(cols[4], items.minOf { it.line.line.dueDate }.fmt())
                                TextCell(cols[5], Money.format(items.sumOf { it.line.line.amount })); TextCell(cols[6], Money.format(items.sumOf { it.line.paid }))
                                TextCell(cols[7], Money.format(items.sumOf { it.remaining }), color = School.Red, bold = true)
                                Cell(cols[8]) { StatusPill(l.status) }
                                Cell(cols[9]) { LinkText("Open", { nav.student(sid) }) }
                            }
                        }
                        if (isOpen) items(items, key = { it.key }) { p ->
                            val on = p.key in sel
                            GridRow(onClick = { selected = if (on) sel - p.key else sel + p.key }) {
                                Cell(cols[0]) { Row { Spacer(Modifier.width(36.dp)); Checkbox(on, { selected = if (on) sel - p.key else sel + p.key }, colors = CheckboxDefaults.colors(checkedColor = School.Navy)) } }
                                TextCell(cols[1], "    " + com.school.feemanager.domain.ScheduleCalculator.longLabelFor(p.enrollment.enrollment.term, p.line.line.seq))
                                TextCell(cols[2], ""); TextCell(cols[3], "${p.enrollment.enrollment.className} ${p.enrollment.enrollment.academicYear}", color = School.Muted)
                                TextCell(cols[4], p.line.line.dueDate.fmt())
                                TextCell(cols[5], Money.format(p.line.line.amount)); TextCell(cols[6], Money.format(p.line.paid)); TextCell(cols[7], Money.format(p.remaining))
                                Cell(cols[8]) { StatusPill(p.line.status) }
                                Cell(cols[9]) { LinkText("Pay", { nav.collect(sid, listOf(p.key)) }) }
                            }
                        }
                    }
                }
            }
        }
    }

    // ============================================================ Collection
    @Composable
    override fun Collection(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav) {
        var tab by rememberSaveable { mutableStateOf(0) }
        Column(Modifier.fillMaxSize()) {
            DesktopPageHeader("Cash Collection") { PrimaryButton("+  Collect payment", { nav.collect() }) }
            PageBody {
                TabStrip(listOf("Cash register", "Daily summary", "Monthly summary"), tab) { tab = it }
                when (tab) {
                    0 -> Register(vm, snap, session, nav)
                    1 -> SummaryTable(snap, daily = true)
                    else -> SummaryTable(snap, daily = false)
                }
            }
        }
    }

    @Composable
    private fun ColumnScope.Register(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav) {
        var query by rememberSaveable { mutableStateOf("") }
        var from by remember { mutableStateOf<LocalDate?>(null) }
        var to by remember { mutableStateOf<LocalDate?>(null) }
        var selected by rememberSaveable { mutableStateOf(setOf<Long>()) }
        var voidTarget by remember { mutableStateOf<ReceiptRecord?>(null) }
        val platform = LocalPlatform.current
        val f = from; val t = to
        val list = snap.receipts
            .filter { f == null || !it.date.isBefore(f) }
            .filter { t == null || !it.date.isAfter(t) }
            .filter { r ->
                val q = query.trim()
                q.isEmpty() || r.receiptNo.contains(q, true) || r.payerName.contains(q, true) || r.studentIds.any { id -> snap.ledger(id)?.student?.let { Reports.matches(it, q) } == true }
            }
            .sortedWith(compareByDescending<ReceiptRecord> { it.date }.thenByDescending { it.id })
        val shownIds = list.map { it.id }.toSet()
        val sel = selected.filter { it in shownIds }.toSet()
        val valid = list.filter { !it.voided }
        val cols = listOf(Col("", 0f, width = 56.dp), Col("Date", 0f, width = 118.dp), Col("Receipt No.", 0f, width = 170.dp), Col("Student", 1.1f), Col("Class", 0f, width = 82.dp), Col("Payment For", 1.6f),
            Col("Received By", 0f, width = 112.dp), Col("Amount", 0f, true, width = 118.dp), Col("", 0f, width = 88.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchBox(query, { query = it }, "Receipt no., student name or ID", Modifier.width(300.dp))
            DateBox("From", from, { from = it })
            DateBox("To", to, { to = it })
            LinkText("Today", { from = snap.today; to = snap.today })
            LinkText("All dates", { from = null; to = null })
            Spacer(Modifier.weight(1f))
            Text("${valid.size} receipts · ${Money.mmk(valid.sumOf { it.total })}", style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
        }
        SelectionBar(sel.size, Money.mmk(sel.mapNotNull { snap.receiptById[it] }.filter { !it.voided }.sumOf { it.total }), "Combined receipt…", {
            val rs = sel.mapNotNull { snap.receiptById[it] }.sortedWith(compareBy<ReceiptRecord> { it.date }.thenBy { it.id })
            platform.printHtml(HtmlDocs.combinedReceipts(rs.map { receiptView(snap, it) }, snap.settings.schoolName, snap.settings.address, snap.settings.phone),
                "Combined receipt ${rs.first().receiptNo}", isReport = false)
            vm.markPrinted(rs.map { it.id })
        }, { selected = emptySet() })
        val allOn = list.isNotEmpty() && list.all { it.id in sel }
        DataGrid(cols, Modifier.weight(1f, fill = false), empty = if (list.isEmpty()) "No receipts" else null, header = {
            Cell(cols[0]) { Checkbox(allOn, { selected = if (allOn) emptySet() else shownIds }, colors = CheckboxDefaults.colors(checkedColor = School.Navy)) }
            cols.drop(1).forEach { c -> TextCell(c, c.title, color = School.Navy, bold = true) }
        }) {
            items(list, key = { it.id }) { r ->
                val on = r.id in sel
                val classes = r.items.mapNotNull { snap.enrollment(it.enrollmentId)?.second?.enrollment?.className }.distinct().joinToString(", ")
                GridRow(onClick = { nav.receipt(r.id) }, background = if (on) Color(0xFFEEF4FB) else Color.White) {
                    Cell(cols[0]) { Checkbox(on, { selected = if (on) sel - r.id else sel + r.id }, colors = CheckboxDefaults.colors(checkedColor = School.Navy)) }
                    TextCell(cols[1], r.date.fmt()); TextCell(cols[2], r.receiptNo); TextCell(cols[3], r.payerName); TextCell(cols[4], classes)
                    TextCell(cols[5], if (r.voided) "VOIDED – ${r.voidReason}" else r.items.joinToString(", ") { it.paymentFor }, color = if (r.voided) School.Red else School.Text)
                    TextCell(cols[6], r.receivedBy)
                    TextCell(cols[7], Money.format(r.total), color = if (r.voided) School.Red else School.Green, decoration = if (r.voided) TextDecoration.LineThrough else null)
                    Cell(cols[8]) { if (session.isAdmin && !r.voided) SecondaryButton("Void", { voidTarget = r }) }
                }
            }
        }
        voidTarget?.let { r ->
            var reason by remember(r.id) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { voidTarget = null },
                title = { Text("Void receipt ${r.receiptNo}?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("All ${r.items.size} line(s) totalling ${Money.mmk(r.total)} stay in the register marked VOIDED and no longer count toward the students' payments. This cannot be undone.")
                        OutlinedTextField(reason, { reason = it }, label = { Text("Reason (required)") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = { TextButton(onClick = { vm.voidReceipt(r.id, reason); voidTarget = null }, enabled = reason.isNotBlank()) { Text("Void", color = School.Red) } },
                dismissButton = { TextButton(onClick = { voidTarget = null }) { Text("Cancel") } },
            )
        }
    }

    @Composable
    private fun SummaryTable(snap: AppSnapshot, daily: Boolean) {
        val valid = snap.payments.filter { !it.voided }
        val fmt = DateTimeFormatter.ofPattern("MMMM yyyy")
        val rows: List<Triple<String, Int, Long>> = if (daily) valid.groupBy { it.date }.toSortedMap(compareByDescending { it }).map { (d, l) -> Triple(d.fmt(), l.map { it.receiptId }.distinct().size, l.sumOf { it.amount }) }
        else valid.groupBy { YearMonth.from(it.date) }.toSortedMap(compareByDescending { it }).map { (m, l) -> Triple(m.format(fmt), l.map { it.receiptId }.distinct().size, l.sumOf { it.amount }) }
        val cols = listOf(Col(if (daily) "Date" else "Month", 1.5f), Col("Receipts", 1f, true), Col("Amount (MMK)", 1.5f, true))
        DataGrid(cols, Modifier.width(760.dp), empty = if (rows.isEmpty()) "No collections yet" else null, footer = if (rows.isEmpty()) null else ({
            TextCell(cols[0], "TOTAL", bold = true); TextCell(cols[1], rows.sumOf { it.second }.toString(), bold = true); TextCell(cols[2], Money.format(rows.sumOf { it.third }), bold = true)
        })) {
            items(rows, key = { it.first }) { (label, n, amount) -> GridRow { TextCell(cols[0], label); TextCell(cols[1], n.toString()); TextCell(cols[2], Money.format(amount)) } }
        }
    }

    // ============================================================ Reports
    @Composable
    override fun Reports(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav) {
        Column(Modifier.fillMaxSize()) {
            DesktopPageHeader("Reports")
            Column(Modifier.fillMaxSize().background(School.Background).verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ReportType.entries.chunked(2).forEachIndexed { r, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        pair.forEachIndexed { c, t ->
                            Row(
                                Modifier.weight(1f).background(Color.White, RoundedCornerShape(10.dp)).border(1.dp, School.Line, RoundedCornerShape(10.dp))
                                    .clickable { nav.report(t) }.padding(horizontal = 22.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = School.Muted, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(18.dp))
                                Column {
                                    Text("${r * 2 + c + 1}. ${t.title}", style = MaterialTheme.typography.titleMedium, color = School.Navy)
                                    Text(t.description(), style = MaterialTheme.typography.bodyLarge, color = School.Muted)
                                }
                            }
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
