@file:OptIn(ExperimentalMaterial3Api::class)

package com.school.feemanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.school.feemanager.domain.Money
import com.school.feemanager.domain.PayStatus
import com.school.feemanager.domain.ReportTable
import com.school.feemanager.ui.theme.Amber
import com.school.feemanager.ui.theme.Green
import com.school.feemanager.ui.theme.Grey
import com.school.feemanager.ui.theme.Red
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy")
fun LocalDate.fmt(): String = format(DATE_FMT)

/** True in the Windows app: page headers and frames use the desktop style. */
val LocalDesktop = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    if (LocalDesktop.current) { DesktopPageHeader(title, onBack, actions); return }
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
    )
}

/** Windows page header: white bar, large school-blue title, actions on the right (as in the desktop design). */
@Composable
fun DesktopPageHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(4.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.primary) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun KeyValue(label: String, value: String, bold: Boolean = false, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, color = valueColor, textAlign = TextAlign.End)
    }
}

fun statusColor(s: PayStatus): Color = when (s) {
    PayStatus.PAID -> Green
    PayStatus.PARTIAL -> Amber
    PayStatus.OVERDUE -> Red
    PayStatus.UNPAID -> Grey
}

@Composable
fun StatusChip(status: PayStatus) {
    val c = statusColor(status)
    Text(
        status.label,
        color = c,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.border(1.dp, c.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun SmallTag(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text, color = color, fontSize = 11.sp, modifier = Modifier.background(color.copy(alpha = 0.08f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
}

@Composable
fun <T> Dropdown(label: String, options: List<T>, selected: T?, display: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(display) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(display(o)) }, onClick = { onSelect(o); expanded = false })
            }
        }
    }
}

/** Whole-kyat amount field; shows the formatted value underneath. */
@Composable
fun MoneyField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(13)) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { val n = value.toLongOrNull(); if (n != null) Text(Money.mmk(n)) },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun DateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier, allowClear: Boolean = false, maxDate: LocalDate? = null) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = date?.fmt() ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay so the whole field opens the picker.
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = state.selectedDateMillis
                    val picked = ms?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    if (picked != null && maxDate != null && picked.isAfter(maxDate)) onChange(maxDate) else onChange(picked)
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (allowClear) TextButton(onClick = { onChange(null); open = false }) { Text("Clear") }
                    TextButton(onClick = { open = false }) { Text("Cancel") }
                }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String = "Confirm", onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = if (destructive) Red else MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Horizontally scrollable data table used by reports and the dashboard. */
@Composable
fun DataTable(headers: List<String>, rows: List<List<Any?>>, totals: List<Any?>? = null, numeric: (Int) -> Boolean, colWidth: (Int) -> Int = { if (it == 1) 160 else 104 }, maxRows: Int = 500) {
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
            headers.forEachIndexed { i, h ->
                Text(h, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = if (numeric(i)) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.width(colWidth(i).dp).padding(horizontal = 6.dp, vertical = 8.dp))
            }
        }
        rows.take(maxRows).forEachIndexed { r, row ->
            Row(Modifier.background(if (r % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent)) {
                row.forEachIndexed { i, v ->
                    Text(ReportTable.cellText(v), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = if (numeric(i)) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.width(colWidth(i).dp).padding(horizontal = 6.dp, vertical = 6.dp))
                }
            }
        }
        if (rows.size > maxRows) Text("… ${rows.size - maxRows} more rows (export to see all)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp))
        if (totals != null) {
            HorizontalDivider()
            Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                totals.forEachIndexed { i, v ->
                    Text(ReportTable.cellText(v), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1,
                        textAlign = if (numeric(i)) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.width(colWidth(i).dp).padding(horizontal = 6.dp, vertical = 7.dp))
                }
            }
        }
    }
}
