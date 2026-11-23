@file:OptIn(ExperimentalMaterial3Api::class)

package com.school.feemanager.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.school.feemanager.data.AppSnapshot
import com.school.feemanager.domain.PayStatus
import com.school.feemanager.domain.ReportFilter
import com.school.feemanager.domain.ReportType
import com.school.feemanager.domain.Reports
import com.school.feemanager.io.HtmlDocs
import com.school.feemanager.util.Printer
import java.time.LocalDate

private fun ReportType.description(): String = when (this) {
    ReportType.ENROLLMENT -> "Every class enrollment: Enroll No., class, academic year, start, period, term"
    ReportType.FEE_ASSESSMENT -> "Fee breakdown per class enrollment: training to net total"
    ReportType.OUTSTANDING -> "Class enrollments that still owe money"
    ReportType.DAILY_COLLECTION -> "Every receipt in the selected period (default: today)"
    ReportType.MONTHLY_COLLECTION -> "Collections totalled by month"
    ReportType.CLASS_WISE -> "Receivable, collected and balance for each class"
    ReportType.DAY_VS_HOSTEL -> "Day and hostel students compared"
    ReportType.PAYMENT_SCHEDULE -> "Every installment / month with due date and status"
    ReportType.UNPAID -> "Students who have paid nothing yet"
    ReportType.PARTIALLY_PAID -> "Students who have paid part of their fees"
}

@Composable
fun ReportsScreen(onOpen: (ReportType) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Reports")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ReportType.entries.forEachIndexed { i, t ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(t) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${i + 1}. ${t.title}", style = MaterialTheme.typography.titleSmall)
                            Text(t.description(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
fun ReportViewScreen(vm: AppViewModel, snap: AppSnapshot, type: ReportType, onBack: () -> Unit) {
    val today = snap.today
    var from by remember { mutableStateOf<LocalDate?>(if (type == ReportType.DAILY_COLLECTION) today else if (type == ReportType.MONTHLY_COLLECTION) today.withDayOfYear(1) else null) }
    var to by remember { mutableStateOf<LocalDate?>(if (type.usesDates) today else null) }
    var cls by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf<PayStatus?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var inactive by rememberSaveable { mutableStateOf(false) }
    val filter = ReportFilter(from, to, cls, search, status, inactive)
    val table = Reports.build(type, snap.ledgers, snap.payments, snap.card.classNames, filter, today)
    val context = LocalContext.current
    val fileBase = type.title.replace(" ", "_") + "_" + today
    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) vm.exportReport(uri, table, asPdf = false)
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.exportReport(uri, table, asPdf = true)
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(type.title, onBack) {
            IconButton(onClick = { excelLauncher.launch("$fileBase.xlsx") }) { Icon(Icons.Default.TableChart, contentDescription = "Export to Excel") }
            IconButton(onClick = { pdfLauncher.launch("$fileBase.pdf") }) { Icon(Icons.Default.PictureAsPdf, contentDescription = "Export to PDF") }
            IconButton(onClick = { Printer.printHtml(context, HtmlDocs.report(table, snap.settings.schoolName), type.title, isReport = true) }) {
                Icon(Icons.Default.Print, contentDescription = "Print")
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionCard("Filters") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (type.usesDates) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField("From", from, { from = it }, Modifier.weight(1f), allowClear = true)
                        DateField("To", to, { to = it }, Modifier.weight(1f), allowClear = true)
                    }
                    Dropdown("Class", listOf<String?>(null) + snap.card.classNames, cls, { it ?: "All classes" }, { cls = it })
                    SearchField(search, { search = it }, "Student name / Enroll No. / ID")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (type.usesStatus) PayStatus.entries.forEach { st ->
                            FilterChip(selected = status == st, onClick = { status = if (status == st) null else st }, label = { Text(st.label) })
                        }
                        FilterChip(selected = inactive, onClick = { inactive = !inactive }, label = { Text("Include inactive") })
                    }
                }
            }
            Text(table.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${table.rows.size} row(s)", style = MaterialTheme.typography.labelSmall)
            if (table.rows.isEmpty()) EmptyState("No records match the selected filters")
            else DataTable(table.headers, table.rows, table.totals, numeric = { table.isNumeric(it) })
            Spacer(Modifier.padding(24.dp))
        }
    }
}
