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
import com.school.feemanager.io.WorkbookImport
import com.school.feemanager.ui.theme.Amber
import com.school.feemanager.ui.theme.Green
import com.school.feemanager.ui.theme.Red
import com.school.feemanager.util.AppRestarter

private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun DataScreen(vm: AppViewModel, snap: AppSnapshot, session: Session, onBack: () -> Unit) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<WorkbookImport?>(null) }
    var updateRates by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf<ImportSummary?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val stamp = snap.today.toString()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.parseImport(uri) { preview = it; updateRates = it.rates != null && it.rates != snap.card }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { uri -> if (uri != null) vm.exportWorkbook(uri) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) vm.backup(uri) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreUri = uri }

    Column(Modifier.fillMaxSize()) {
        AppTopBar("Data", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (session.isAdmin) SectionCard("Import Excel workbook") {
                Text("Imports School_Fee_Management.xlsx: Student_Register, Cash_Collection and Fee_Rates. Formulas are not imported – fees, balances, schedules and the dashboard are recalculated by the app. Receipt numbers and dates are preserved; duplicates are skipped.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { importLauncher.launch(arrayOf(XLSX, "application/octet-stream", "application/vnd.ms-excel")) }, modifier = Modifier.fillMaxWidth()) { Text("Choose .xlsx file…") }
            }
            SectionCard("Export to Excel") {
                Text("Writes Dashboard, Student_Register, Payment_Schedule, Cash_Collection and Fee_Rates in the same structure as the original workbook.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { exportLauncher.launch("School_Fee_Management_$stamp.xlsx") }, modifier = Modifier.fillMaxWidth()) { Text("Export workbook") }
            }
            if (session.isAdmin) SectionCard("Backup & restore") {
                Text("A backup is a complete copy of the database (students, payments, receipts, users, rates). Save it to Google Drive, a USB drive or another phone regularly.", style = MaterialTheme.typography.bodySmall)
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
            title = { Text("Import preview") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyValue("Worksheets found", imp.sheetsFound.joinToString(", "))
                    KeyValue("Fee rates", if (imp.rates == null) "not found" else if (imp.rates == snap.card) "same as current" else "${imp.rates.classes.size} classes – different from current")
                    KeyValue("Students", imp.students.size.toString(), bold = true)
                    KeyValue("Cash transactions", "${imp.payments.size} (${com.school.feemanager.domain.Money.mmk(imp.payments.sumOf { it.amount })})", bold = true)
                    KeyValue("Errors (rows skipped)", imp.errors.size.toString(), valueColor = if (imp.errors.isEmpty()) Green else Red)
                    KeyValue("Warnings", imp.warnings.size.toString(), valueColor = if (imp.warnings.isEmpty()) Green else Amber)
                    if (imp.rates != null && imp.rates != snap.card) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(updateRates, { updateRates = it }); Text("Replace current fee rates with the workbook's Fee_Rates")
                    }
                    if (imp.issues.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        imp.issues.take(200).forEach { Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = if (it.isError) Red else Amber) }
                    }
                    Text("Students already in the app (same name and class) and receipts with numbers already in the app are skipped.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { val i = imp; preview = null; vm.applyImport(i, updateRates) { summary = it } },
                    enabled = imp.students.isNotEmpty() || imp.payments.isNotEmpty() || (imp.rates != null && updateRates)) { Text("Import") }
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
                    KeyValue("Fee rates updated", if (s.ratesUpdated) "Yes" else "No")
                    KeyValue("Students imported", s.studentsImported.toString(), bold = true)
                    KeyValue("Students skipped (already exist)", s.studentsSkippedDuplicate.toString())
                    KeyValue("Transactions imported", s.paymentsImported.toString(), bold = true)
                    KeyValue("Transactions skipped (receipt exists)", s.paymentsSkippedDuplicate.toString())
                    s.problems.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Red) }
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
