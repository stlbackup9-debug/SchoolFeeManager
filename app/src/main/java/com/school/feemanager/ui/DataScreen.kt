@file:OptIn(ExperimentalMaterial3Api::class)

package com.school.feemanager.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.school.feemanager.data.AppSnapshot
import com.school.feemanager.data.ImportSummary
import com.school.feemanager.io.ImportBundle
import com.school.feemanager.io.ImportModule
import com.school.feemanager.ui.theme.Amber
import com.school.feemanager.ui.theme.Green
import com.school.feemanager.ui.theme.Red
import com.school.feemanager.util.AppRestarter

private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private val IMPORT_TYPES = arrayOf(XLSX, "text/csv", "text/comma-separated-values", "text/plain", "application/csv", "application/vnd.ms-excel", "application/octet-stream")

@Composable
fun DataScreen(vm: AppViewModel, snap: AppSnapshot, session: Session, onBack: () -> Unit) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<ImportBundle?>(null) }
    var dryRun by remember { mutableStateOf<ImportSummary?>(null) }
    var updateRates by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf<ImportSummary?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var module by remember { mutableStateOf<ImportModule?>(null) }
    var templateFor by remember { mutableStateOf<Pair<ImportModule, Boolean>?>(null) }
    val stamp = snap.today.toString()

    fun showPreview(imp: ImportBundle, rates: Boolean) {
        preview = imp; dryRun = null; updateRates = rates
        if (!imp.isEmpty) vm.previewImport(imp, rates) { dryRun = it }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.parseImport(uri) { showPreview(it, it.rates != null && it.rates != snap.card) }
    }
    val moduleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val m = module
        if (uri != null && m != null) vm.parseModuleImport(m, uri) { showPreview(it, false) }
    }
    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val t = templateFor
        if (uri != null && t != null) vm.writeTemplate(uri, t.first, t.second)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { uri -> if (uri != null) vm.exportWorkbook(uri) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) vm.backup(uri) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("Data", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (session.isAdmin) {
                Text("Import from Excel (.xlsx) or CSV", style = MaterialTheme.typography.titleMedium)
                Text("Download a template, fill it in Excel / Google Sheets (or save it as CSV), then import it. You always see a preview first; nothing is saved until you tap Import. Suggested order: Fee rates → Students → Class enrollments → Payments.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(ImportModule.FEE_RATES, ImportModule.STUDENTS, ImportModule.ENROLLMENTS, ImportModule.PAYMENTS).forEach { m ->
                    SectionCard(m.label) {
                        Text(m.description, style = MaterialTheme.typography.bodySmall)
                        Text("Columns: " + m.columns.joinToString(", ") { it.header + if (it.required) " *" else "" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { module = m; moduleLauncher.launch(IMPORT_TYPES) }, modifier = Modifier.weight(1.2f)) { Text("Import…") }
                            OutlinedButton(onClick = { templateFor = m to false; templateLauncher.launch("${m.sheetName}_template.xlsx") }, modifier = Modifier.weight(1f)) { Text(".xlsx") }
                            OutlinedButton(onClick = { templateFor = m to true; templateLauncher.launch("${m.sheetName}_template.csv") }, modifier = Modifier.weight(1f)) { Text(".csv") }
                        }
                    }
                }
                SectionCard("Import full workbook") {
                    Text("School_Fee_Management.xlsx (or a workbook exported by this app): Student_Register, Cash_Collection and Fee_Rates. Formulas are not imported – fees, balances, schedules and the dashboard are recalculated by the app. Receipt numbers and dates are preserved; duplicates are skipped.",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { importLauncher.launch(arrayOf(XLSX, "application/octet-stream", "application/vnd.ms-excel")) }, modifier = Modifier.fillMaxWidth()) { Text("Choose .xlsx workbook…") }
                }
            }
            SectionCard("Export to Excel") {
                Text("Writes Dashboard, Student_Register (one row per class enrollment), Payment_Schedule, Cash_Collection and Fee_Rates. The file can be imported again.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { exportLauncher.launch("School_Fee_Management_$stamp.xlsx") }, modifier = Modifier.fillMaxWidth()) { Text("Export workbook") }
            }
            if (session.isAdmin) SectionCard("Backup & restore") {
                Text("A backup is a complete copy of the database (students, classes, payments, receipts, users, rates). Save it to Google Drive, a USB drive or another phone regularly.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { backupLauncher.launch("school_fees_backup_$stamp.db") }, modifier = Modifier.weight(1f)) { Text("Back up now") }
                    OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Restore…") }
                }
            }
        }
    }

    preview?.let { imp ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Import preview – ${imp.title}") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyValue("Sheet(s) read", imp.sheetsFound.joinToString(", "))
                    if (imp.rates != null || imp.sheetsFound.any { it.equals("Fee_Rates", true) }) KeyValue("Fee rates", if (imp.rates == null) "not found" else if (imp.rates == snap.card) "same as current" else "${imp.rates.classes.size} classes – different from current")
                    if (imp.classRates.isNotEmpty()) KeyValue("Class rate rows", imp.classRates.size.toString(), bold = true)
                    if (imp.students.isNotEmpty()) KeyValue("Student rows", imp.students.size.toString(), bold = true)
                    if (imp.enrollments.isNotEmpty()) KeyValue("Class enrollment rows", imp.enrollments.size.toString(), bold = true)
                    if (imp.payments.isNotEmpty()) KeyValue("Payment rows", "${imp.payments.size} (${com.school.feemanager.domain.Money.mmk(imp.payments.sumOf { it.amount })})", bold = true)
                    KeyValue("Rows with errors (skipped)", imp.errors.size.toString(), valueColor = if (imp.errors.isEmpty()) Green else Red)
                    KeyValue("Warnings", imp.warnings.size.toString(), valueColor = if (imp.warnings.isEmpty()) Green else Amber)
                    if (imp.rates != null && imp.rates != snap.card) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(updateRates, { updateRates = it; vm.previewImport(imp, it) { s -> dryRun = s } }); Text("Replace current fee rates with the workbook's Fee_Rates")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    val d = dryRun
                    if (imp.isEmpty) Text("Nothing to import in this file.", color = Red)
                    else if (d == null) Text("Checking against the data in the app…", style = MaterialTheme.typography.bodySmall)
                    else {
                        Text("What the import will do:", style = MaterialTheme.typography.titleSmall)
                        SummaryLines(d)
                    }
                    if (imp.issues.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        imp.issues.take(200).forEach { Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = if (it.isError) Red else Amber) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { val i = imp; val r = updateRates; preview = null; vm.applyImport(i, r) { summary = it } },
                    enabled = !imp.isEmpty && dryRun != null) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancel") } },
        )
    }

    summary?.let { s ->
        AlertDialog(
            onDismissRequest = { summary = null },
            title = { Text("Import complete") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SummaryLines(s)
                    Text("Balances, schedules and the dashboard have been recalculated.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { summary = null }) { Text("OK") } },
        )
    }

    restoreUri?.let { uri ->
        ConfirmDialog(
            title = "Restore backup?",
            text = "ALL current data on this phone will be replaced by the backup. A safety copy of the current database is kept inside the app. The app will restart.",
            confirmLabel = "Restore", destructive = true,
            onConfirm = { vm.restore(uri) { AppRestarter.restart(context) } },
            onDismiss = { restoreUri = null },
        )
    }
}

@Composable
private fun SummaryLines(s: ImportSummary) {
    if (s.ratesUpdated) KeyValue("Fee rates replaced", "Yes")
    if (s.classesAdded + s.classesUpdated > 0) KeyValue("Classes added / updated", "${s.classesAdded} / ${s.classesUpdated}", bold = true)
    KeyValue("New students", s.studentsImported.toString(), bold = true)
    if (s.studentsExisting > 0) KeyValue("Students already in the app", s.studentsExisting.toString())
    KeyValue("Class enrollments added", s.enrollmentsImported.toString(), bold = true)
    if (s.enrollmentsSkippedDuplicate > 0) KeyValue("Enrollments skipped (already exist)", s.enrollmentsSkippedDuplicate.toString())
    KeyValue("Receipts / payment lines added", "${s.receiptsImported} / ${s.paymentsImported}", bold = true)
    if (s.paymentsSkippedDuplicate > 0) KeyValue("Payment lines skipped (receipt no. exists)", s.paymentsSkippedDuplicate.toString())
    if (s.problems.isNotEmpty()) {
        KeyValue("Rows that cannot be imported", s.problems.size.toString(), valueColor = Red)
        s.problems.take(200).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Red) }
    }
}
