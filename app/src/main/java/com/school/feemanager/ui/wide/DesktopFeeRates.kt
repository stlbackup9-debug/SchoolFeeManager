package com.school.feemanager.ui.wide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.school.feemanager.data.AppSnapshot
import com.school.feemanager.domain.ClassRate
import com.school.feemanager.domain.FeeRateCard
import com.school.feemanager.domain.Money
import com.school.feemanager.ui.AppNav
import com.school.feemanager.ui.AppViewModel
import com.school.feemanager.ui.ConfirmDialog
import com.school.feemanager.ui.DesktopPageHeader
import com.school.feemanager.ui.Session
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** One editable class row of the fee table. */
private class RateRow(r: ClassRate) {
    val original = r.className
    val name = mutableStateOf(r.className)
    val training = mutableStateOf(r.trainingFee.toString())
    val dm = mutableStateOf(r.bookDayMyanmar.toString())
    val di = mutableStateOf(r.bookDayIntl.toString())
    val hm = mutableStateOf(r.bookHostelMyanmar.toString())
    val hi = mutableStateOf(r.bookHostelIntl.toString())
    val hostel = mutableStateOf(r.hostelFee.toString())
    val startMonth = mutableStateOf(r.startMonth)
    val startDay = mutableStateOf(r.startDay.toString())
    val period = mutableStateOf(r.periodMonths.toString())

    val money: List<MutableState<String>> get() = listOf(training, dm, di, hm, hi, hostel)

    fun toRate(i: Int): ClassRate? {
        val n = name.value.trim().uppercase(Locale.ROOT).takeIf { it.isNotEmpty() } ?: return null
        val v = money.map { it.value.toLongOrNull() ?: return null }
        val day = startDay.value.toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        val p = period.value.toIntOrNull()?.takeIf { it in 1..24 } ?: return null
        return ClassRate(n, i, v[0], v[1], v[2], v[3], v[4], v[5], startMonth.value, day, p)
    }
}

private fun monthName(m: Int) = Month.of(m).getDisplayName(TextStyle.SHORT, Locale.US)

/** Settings → Fee rates on Windows: the four school-wide fees in one row, then one table row per class. */
@Composable
fun DesktopFeeRatesPage(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav) {
    val card = snap.card
    val key = snap.settings.ratesUpdatedAt
    val rows = remember(key) { mutableStateListOf<RateRow>().apply { addAll(card.classes.sortedBy { it.sortOrder }.map { RateRow(it) }) } }
    var instrument by remember(key) { mutableStateOf(card.instrumentFee.toString()) }
    var entrance by remember(key) { mutableStateOf(card.entranceFee.toString()) }
    var laundry by remember(key) { mutableStateOf(card.laundryPerMonth.toString()) }
    var months by remember(key) { mutableStateOf(card.monthsPerYear.toString()) }
    var confirm by remember { mutableStateOf(false) }
    var offerRecalc by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    val edit = session.isAdmin
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val used = snap.ledgers.flatMap { l -> l.enrollments.map { it.enrollment.className } }.toSet()

    val parsed = rows.mapIndexed { i, r -> r.toRate(i) }
    val names = rows.map { it.name.value.trim().uppercase(Locale.ROOT) }
    val dupNames = names.groupBy { it }.filter { it.value.size > 1 }.keys
    val newCard = if (parsed.any { it == null } || dupNames.isNotEmpty() || listOf(instrument, entrance, laundry).any { it.toLongOrNull() == null } || months.toIntOrNull() !in 1..24) null
    else FeeRateCard(parsed.filterNotNull(), instrument.toLong(), laundry.toLong(), months.toInt(), entrance.toLong())
    val changed = newCard != null && newCard != card

    Column(Modifier.fillMaxSize()) {
        DesktopPageHeader("Fee Rates", onBack = { nav.back() }) {
            if (edit) {
                if (changed) Text("Unsaved changes", color = School.Amber, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 6.dp))
                SecondaryButton("+  Add class", { addOpen = true }, modifier = Modifier.height(44.dp))
                PrimaryButton("Save fee rates", { focus.clearFocus(); confirm = true }, enabled = changed)
            }
        }
        Column(Modifier.fillMaxSize().background(School.Background).verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("All amounts are MMK per academic period unless stated. Saved changes apply to NEW enrollments; existing students keep their approved fees until you recalculate them.",
                style = MaterialTheme.typography.bodyLarge, color = School.Muted)

            Panel("Fees for all students") {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    LabeledInput("B) Instrument fee (all)", instrument, { instrument = it.digits(13) }, edit, Modifier.weight(1f))
                    LabeledInput("Entrance fee (new students)", entrance, { entrance = it.digits(13) }, edit, Modifier.weight(1f))
                    LabeledInput("Laundry / month (hostel)", laundry, { laundry = it.digits(13) }, edit, Modifier.weight(1f))
                    LabeledInput("Default months", months, { months = it.digits(2) }, edit, Modifier.weight(1f), isError = months.toIntOrNull() !in 1..24)
                }
                Spacer(Modifier.height(10.dp))
                Text("Laundry is charged for each month of a hostel enrollment's period (12 months = ${Money.mmk((laundry.toLongOrNull() ?: 0) * 12)}).",
                    style = MaterialTheme.typography.bodyMedium, color = School.Muted)
            }

            Panel("Fees by class") {
                val w = listOf(0.72f, 0.95f, 0.95f, 0.95f, 0.95f, 0.95f, 1.02f, 1.05f, 0.47f, 0.9f)
                // the table keeps a usable width; it scrolls sideways at large scales
                androidx.compose.foundation.layout.BoxWithConstraints {
                val minW = 1180.dp
                val boxW = maxWidth
                val narrow = boxW < minW
                val hs = androidx.compose.foundation.rememberScrollState()
                Column(if (narrow) Modifier.horizontalScroll(hs) else Modifier) {
                Column(Modifier.then(if (narrow) Modifier.width(minW) else Modifier).border(1.dp, School.Line, RoundedCornerShape(10.dp))) {
                    Row(Modifier.fillMaxWidth().background(School.TableHeader, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).heightIn(min = 72.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        listOf("Class", "A) Training", "C) Book Day–Myanmar", "Book Day–Intl", "Book Hostel–Myanmar", "Book Hostel–Intl", "D) Hostel fee", "Year starts", "Day", "Months")
                            .forEachIndexed { i, h -> HeadCell(h, w[i]) }
                        Box(Modifier.width(86.dp))
                    }
                    rows.forEachIndexed { i, r ->
                        val dup = r.name.value.trim().uppercase(Locale.ROOT) in dupNames
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TableInput(r.name.value, { r.name.value = it.take(10).uppercase(Locale.ROOT) }, edit && r.original !in used, w[0], numeric = false, isError = r.name.value.isBlank() || dup)
                            r.money.forEachIndexed { j, st -> TableInput(st.value, { st.value = it.digits(13) }, edit, w[j + 1], numeric = true, isError = st.value.toLongOrNull() == null) }
                            Box(Modifier.weight(w[7]).padding(horizontal = 5.dp)) {
                                if (edit) SelectBox((1..12).toList(), r.startMonth.value, ::monthName, { r.startMonth.value = it }, Modifier.fillMaxWidth())
                                else Text(monthName(r.startMonth.value), style = MaterialTheme.typography.bodyLarge)
                            }
                            TableInput(r.startDay.value, { r.startDay.value = it.digits(2) }, edit, w[8], numeric = true, isError = r.startDay.value.toIntOrNull() !in 1..31)
                            TableInput(r.period.value, { r.period.value = it.digits(2) }, edit, w[9], numeric = true, isError = r.period.value.toIntOrNull() !in 1..24)
                            Box(Modifier.width(86.dp), contentAlignment = Alignment.Center) {
                                if (edit && r.original !in used) LinkText("Remove", { rows.removeAt(i) }, color = School.Red)
                            }
                        }
                        if (i < rows.lastIndex) HorizontalDivider(color = School.Line)
                    }
                }
                if (narrow) com.school.feemanager.ui.PlatformHorizontalScrollbar(hs, Modifier.width(boxW).padding(top = 4.dp))
                }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        dupNames.isNotEmpty() -> "Class names must be unique: ${dupNames.joinToString()}."
                        newCard == null -> "Every amount must be a whole number; day 1–31; months 1–24 (fields in red)."
                        else -> "Classes that students are enrolled in cannot be renamed or removed. “Year starts” + “Day” is the academic-year start date and “Months” the period – the defaults when a student is enrolled in the class. Laundry is not per class (see above)."
                    },
                    color = if (newCard == null) School.Red else School.Muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (addOpen) {
        var name by remember { mutableStateOf("") }
        val n = name.trim().uppercase(Locale.ROOT)
        val exists = rows.any { it.name.value.trim().equals(n, true) }
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text("Add class") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(10) }, label = { Text("Class name (e.g. G-13 or PIANO-1)") }, singleLine = true, isError = exists,
                        supportingText = { if (exists) Text("This class already exists") })
                    Text("The new row is added at the end of the table with amounts 0 – fill them in and click “Save fee rates”.", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(enabled = n.isNotEmpty() && !exists, onClick = {
                    val ys = snap.yearStart
                    rows.add(RateRow(ClassRate(n, rows.size, 0, 0, 0, 0, 0, 0, ys.monthValue, ys.dayOfMonth, months.toIntOrNull()?.takeIf { it in 1..24 } ?: 12)))
                    addOpen = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addOpen = false }) { Text("Cancel") } },
        )
    }
    if (confirm && newCard != null) ConfirmDialog(
        title = "Save new fee rates?",
        text = "New class enrollments will use these rates. Existing enrollments' approved fee assessments are NOT changed.",
        confirmLabel = "Save",
        onConfirm = { vm.saveRates(newCard) { offerRecalc = true } }, onDismiss = { confirm = false },
    )
    if (offerRecalc) ConfirmDialog(
        title = "Recalculate existing students?",
        text = "Rates are saved. Do you also want to re-assess all active class enrollments with the new rates now? You can do this later from Settings.",
        confirmLabel = "Recalculate now", destructive = true,
        onConfirm = { vm.recalculate(null) }, onDismiss = { offerRecalc = false },
    )
}

private fun String.digits(max: Int) = filter { it.isDigit() }.take(max)

@Composable
private fun RowScope.HeadCell(text: String, weight: Float) =
    Text(text, color = School.Navy, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(weight).padding(horizontal = 8.dp))

/** Bordered single-line input used inside the table (numbers right-aligned). */
@Composable
private fun RowScope.TableInput(value: String, onChange: (String) -> Unit, enabled: Boolean, weight: Float, numeric: Boolean, isError: Boolean = false) {
    Box(Modifier.weight(weight).padding(horizontal = 5.dp)) { InputBox(value, onChange, enabled, numeric, isError, Modifier.fillMaxWidth()) }
}

@Composable
private fun InputBox(value: String, onChange: (String) -> Unit, enabled: Boolean, numeric: Boolean, isError: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    val border = when { isError -> School.Red; enabled -> School.InputBorder; else -> School.Line }
    Box(modifier.height(46.dp).background(if (enabled) School.Surface else School.InputDisabled, shape).border(1.dp, border, shape).padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart) {
        BasicTextField(value, onChange, enabled = enabled, singleLine = true, cursorBrush = SolidColor(School.Navy), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (numeric || value.all { it.isDigit() }) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (enabled) School.Text else School.Muted, textAlign = if (numeric) TextAlign.End else TextAlign.Start))
    }
}

@Composable
private fun LabeledInput(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean, modifier: Modifier, isError: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = School.Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        InputBox(value, onChange, enabled, numeric = false, isError = isError || value.isEmpty(), modifier = Modifier.fillMaxWidth())
    }
}
