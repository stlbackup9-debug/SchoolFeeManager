@file:OptIn(ExperimentalMaterial3Api::class)

package com.school.feemanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.school.feemanager.data.AppSnapshot
import com.school.feemanager.domain.ClassRate
import com.school.feemanager.domain.FeeCalculator
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.Money
import com.school.feemanager.domain.Role
import com.school.feemanager.ui.theme.Red
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ============================================================== menu
@Composable
fun SettingsScreen(vm: AppViewModel, snap: AppSnapshot, session: Session, onOpen: (String) -> Unit) {
    var recalcAll by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Settings")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionCard {
                Text("Signed in as ${session.display}", style = MaterialTheme.typography.titleSmall)
                Text("Role: ${session.role.label}", style = MaterialTheme.typography.bodySmall)
            }
            SectionCard("Fees") {
                MenuRow(Icons.Default.PriceChange, "Fee rates", if (session.isAdmin) "Training, book, hostel, laundry, instrument and entrance fees" else "View current fee rates") { onOpen("rates") }
                if (session.isAdmin) MenuRow(Icons.Default.Calculate, "Recalculate existing students", "Re-assess all active students with the current fee rates") { recalcAll = true }
            }
            if (session.isAdmin) SectionCard("Administration") {
                MenuRow(Icons.Default.School, "School profile", "Name, address, receipt prefix, academic year start") { onOpen("school") }
                MenuRow(Icons.Default.ManageAccounts, "Users & roles", "Admin and Cashier accounts") { onOpen("users") }
                MenuRow(Icons.Default.Storage, "Data: Excel import / export, backup & restore", "School_Fee_Management.xlsx and database backups") { onOpen("data") }
                MenuRow(Icons.Default.History, "Activity log", "Audit trail of changes and payments") { onOpen("audit") }
            } else SectionCard("Data") {
                MenuRow(Icons.Default.Storage, "Export to Excel", "Download the workbook") { onOpen("data") }
            }
            SectionCard("Account") {
                MenuRow(Icons.Default.Lock, "Change password", null) { onOpen("password") }
                MenuRow(Icons.AutoMirrored.Filled.Logout, "Log out", null) { logout = true }
            }
            Text("School Fee Manager 1.0 · all amounts in MMK · works offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (recalcAll) {
        val changed = snap.ledgers.filter { it.student.active && FeeCalculator.assess(snap.card, it.student) != it.fee }
        ConfirmDialog(
            title = "Recalculate all active students?",
            text = "${changed.size} of ${snap.ledgers.count { it.student.active }} active students would get a different fee with the current rates " +
                "(total net change ${Money.mmk(changed.sumOf { FeeCalculator.assess(snap.card, it.student).netTotal - it.net })}). " +
                "Their schedules are regenerated; payments are kept. This overwrites approved assessments.",
            confirmLabel = "Recalculate", destructive = true,
            onConfirm = { vm.recalculate(null) }, onDismiss = { recalcAll = false },
        )
    }
    if (logout) ConfirmDialog("Log out?", "You will need your password to log in again.", "Log out", onConfirm = { vm.logout() }, onDismiss = { logout = false })
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

// ============================================================== fee rates
private class RateRow(r: ClassRate) {
    val name = mutableStateOf(r.className)
    val training = mutableStateOf(r.trainingFee.toString())
    val dm = mutableStateOf(r.bookDayMyanmar.toString())
    val di = mutableStateOf(r.bookDayIntl.toString())
    val hm = mutableStateOf(r.bookHostelMyanmar.toString())
    val hi = mutableStateOf(r.bookHostelIntl.toString())
    val hostel = mutableStateOf(r.hostelFee.toString())
    val original = r.className

    fun toRate(i: Int): ClassRate? {
        fun v(s: MutableState<String>) = s.value.toLongOrNull()
        return ClassRate(name.value.trim().uppercase(), i, v(training) ?: return null, v(dm) ?: return null, v(di) ?: return null, v(hm) ?: return null, v(hi) ?: return null, v(hostel) ?: return null)
    }
}

@Composable
fun FeeRatesScreen(vm: AppViewModel, snap: AppSnapshot, session: Session, onBack: () -> Unit) {
    val card = snap.card
    val rows = remember(snap.settings.ratesUpdatedAt) { mutableStateListOf<RateRow>().apply { addAll(card.classes.sortedBy { it.sortOrder }.map { RateRow(it) }) } }
    var instrument by remember(snap.settings.ratesUpdatedAt) { mutableStateOf(card.instrumentFee.toString()) }
    var laundry by remember(snap.settings.ratesUpdatedAt) { mutableStateOf(card.laundryPerMonth.toString()) }
    var months by remember(snap.settings.ratesUpdatedAt) { mutableStateOf(card.monthsPerYear.toString()) }
    var entrance by remember(snap.settings.ratesUpdatedAt) { mutableStateOf(card.entranceFee.toString()) }
    var confirm by remember { mutableStateOf(false) }
    var offerRecalc by remember { mutableStateOf(false) }
    var newClass by remember { mutableStateOf("") }
    val edit = session.isAdmin
    val used = snap.ledgers.map { it.student.className }.toSet()

    val parsedRows = rows.mapIndexed { i, r -> r.toRate(i) }
    val newCard = if (parsedRows.any { it == null } || instrument.toLongOrNull() == null || laundry.toLongOrNull() == null || months.toIntOrNull() == null || entrance.toLongOrNull() == null) null
    else FeeRateCard(parsedRows.filterNotNull(), instrument.toLong(), laundry.toLong(), months.toInt(), entrance.toLong())

    Column(Modifier.fillMaxSize()) {
        AppTopBar("Fee Rates", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("All amounts are MMK per year unless stated. Changes apply to NEW registrations; existing students keep their approved fees until you recalculate them.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionCard("Fees for all students") {
                MoneyField("B) Instrument fee (all students)", instrument, { instrument = it }, enabled = edit)
                MoneyField("Entrance fee (new students only)", entrance, { entrance = it }, enabled = edit)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoneyField("Laundry fee / month (hostel)", laundry, { laundry = it }, Modifier.weight(1.4f), enabled = edit)
                    OutlinedTextField(months, { months = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Months/year") }, enabled = edit, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                val lm = (laundry.toLongOrNull() ?: 0) * (months.toLongOrNull() ?: 0)
                Text("Annual laundry fee = ${Money.mmk(lm)}", style = MaterialTheme.typography.bodySmall)
            }
            rows.forEachIndexed { i, r ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(r.name.value, { r.name.value = it.take(10) }, label = { Text("Class") }, singleLine = true, enabled = edit && r.original !in used, modifier = Modifier.weight(1f))
                        if (edit && r.original !in used) IconButton(onClick = { rows.removeAt(i) }) { Icon(Icons.Default.Delete, contentDescription = "Remove class", tint = Red) }
                    }
                    MoneyField("A) Training fee", r.training.value, { r.training.value = it }, enabled = edit)
                    Text("C) Book fee", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoneyField("Day – Myanmar", r.dm.value, { r.dm.value = it }, Modifier.weight(1f), enabled = edit)
                        MoneyField("Day – Intl", r.di.value, { r.di.value = it }, Modifier.weight(1f), enabled = edit)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoneyField("Hostel – Myanmar", r.hm.value, { r.hm.value = it }, Modifier.weight(1f), enabled = edit)
                        MoneyField("Hostel – Intl", r.hi.value, { r.hi.value = it }, Modifier.weight(1f), enabled = edit)
                    }
                    MoneyField("D) Hostel fee (hostel students)", r.hostel.value, { r.hostel.value = it }, enabled = edit)
                }
            }
            if (edit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newClass, { newClass = it.take(10) }, label = { Text("New class name") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val n = newClass.trim().uppercase()
                        if (n.isNotEmpty() && rows.none { it.name.value.trim().equals(n, true) }) {
                            rows.add(RateRow(ClassRate(n, rows.size, 0, 0, 0, 0, 0, 0))); newClass = ""
                        }
                    }) { Icon(Icons.Default.Add, contentDescription = null); Text("Add class") }
                }
                Button(onClick = { confirm = true }, enabled = newCard != null && newCard != card, modifier = Modifier.fillMaxWidth()) { Text("Save fee rates") }
                if (newCard == null) Text("Every amount must be a whole number.", color = Red, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirm && newCard != null) ConfirmDialog(
        title = "Save new fee rates?",
        text = "New registrations will use these rates. Existing students' approved fee assessments are NOT changed.",
        confirmLabel = "Save",
        onConfirm = { vm.saveRates(newCard) { offerRecalc = true } }, onDismiss = { confirm = false },
    )
    if (offerRecalc) ConfirmDialog(
        title = "Recalculate existing students?",
        text = "Rates are saved. Do you also want to re-assess all active students with the new rates now? You can do this later from Settings.",
        confirmLabel = "Recalculate now", destructive = true,
        onConfirm = { vm.recalculate(null) }, onDismiss = { offerRecalc = false },
    )
}

// ============================================================== school profile
@Composable
fun SchoolProfileScreen(vm: AppViewModel, snap: AppSnapshot, session: Session, onBack: () -> Unit) {
    val s = snap.settings
    var name by remember { mutableStateOf(s.schoolName) }
    var address by remember { mutableStateOf(s.address) }
    var phone by remember { mutableStateOf(s.phone) }
    var prefix by remember { mutableStateOf(s.receiptPrefix) }
    var start by remember { mutableStateOf(snap.yearStart) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("School Profile", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("School name (printed on receipts)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(address, { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(prefix, { prefix = it.filter { c -> c.isLetterOrDigit() }.take(8).uppercase() }, label = { Text("Receipt number prefix") }, singleLine = true,
                supportingText = { Text("Receipts look like $prefix-${snap.today.year}-000123") }, modifier = Modifier.fillMaxWidth())
            DateField("Academic year start", start, { if (it != null) start = it })
            Text("3-Inst due dates: start, +4 months, +8 months. Monthly: one per month from the start. Changing this date moves all due dates; amounts and payments are not changed.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { vm.saveSchool(name, address, phone, prefix, start, onBack) }, enabled = session.isAdmin && name.isNotBlank() && prefix.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

// ============================================================== users
@Composable
fun UsersScreen(vm: AppViewModel, snap: AppSnapshot, session: Session, onBack: () -> Unit) {
    var add by remember { mutableStateOf(false) }
    var resetFor by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Users & Roles", onBack) { IconButton(onClick = { add = true }) { Icon(Icons.Default.Add, contentDescription = "Add user") } }
        Text("Admin: manage students, fee rates, users, import, backup. Cashier: record payments, print receipts, view reports.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
            items(snap.users, key = { it.id }) { u ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${u.username}${if (u.id == session.userId) " (you)" else ""}", style = MaterialTheme.typography.titleSmall)
                            Text(u.fullName.ifBlank { "–" }, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (u.active) "Active" else "Disabled", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = u.active, onCheckedChange = { vm.setUserActive(u.id, it) }, enabled = u.id != session.userId)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Dropdown("Role", Role.entries, Role.valueOf(u.role), { it.label }, { vm.setUserRole(u.id, it) }, Modifier.weight(1f), enabled = u.id != session.userId)
                        OutlinedButton(onClick = { resetFor = u.id }, modifier = Modifier.weight(1f).padding(top = 8.dp)) { Text("Reset password") }
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
        }
    }
    if (add) AddUserDialog(onDismiss = { add = false }) { u, n, r, p -> vm.addUser(u, n, r, p) { add = false } }
    resetFor?.let { id ->
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { resetFor = null },
            title = { Text("Reset password") },
            text = { OutlinedTextField(pw, { pw = it }, label = { Text("Temporary password (min 6)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.resetPassword(id, pw); resetFor = null }, enabled = pw.length >= 6) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { resetFor = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddUserDialog(onDismiss: () -> Unit, onAdd: (String, String, Role, String) -> Unit) {
    var u by remember { mutableStateOf("") }
    var n by remember { mutableStateOf("") }
    var r by remember { mutableStateOf(Role.CASHIER) }
    var p by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add user") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(u, { u = it.trim() }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(n, { n = it }, label = { Text("Full name") }, singleLine = true)
                Dropdown("Role", Role.entries, r, { it.label }, { r = it })
                OutlinedTextField(p, { p = it }, label = { Text("Temporary password (min 6)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Text("The user must change this password at first login.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(u, n, r, p) }, enabled = u.length >= 3 && p.length >= 6) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ============================================================== password
@Composable
fun ChangePasswordForm(vm: AppViewModel, forced: Boolean, onDone: () -> Unit) {
    var cur by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (forced) Text("For security, please set a new password before continuing.", style = MaterialTheme.typography.bodyMedium)
        listOf(Triple("Current password", cur) { v: String -> cur = v }, Triple("New password (min 6)", new1) { v: String -> new1 = v }, Triple("Repeat new password", new2) { v: String -> new2 = v })
            .forEach { (label, value, set) ->
                OutlinedTextField(value, set, label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            }
        if (new2.isNotEmpty() && new1 != new2) Text("Passwords do not match", color = Red, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.changeOwnPassword(cur, new1, new2, onDone) }, enabled = cur.isNotEmpty() && new1.length >= 6 && new1 == new2, modifier = Modifier.fillMaxWidth()) { Text("Change password") }
        if (forced) TextButton(onClick = { vm.logout() }) { Text("Log out") }
    }
}

// ============================================================== audit log
@Composable
fun AuditLogScreen(vm: AppViewModel, onBack: () -> Unit) {
    val log by vm.auditLog.collectAsStateWithLifecycle()
    val fmt = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm")
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Activity Log", onBack)
        if (log.isEmpty()) EmptyState("No activity yet")
        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
            items(log, key = { it.id }) { a ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("${LocalDateTime.ofInstant(Instant.ofEpochMilli(a.at), ZoneId.systemDefault()).format(fmt)} · ${a.username} · ${a.action}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(a.details, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }
}
