@file:OptIn(ExperimentalMaterial3Api::class)

package com.school.feemanager.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.school.feemanager.domain.PayStatus
import com.school.feemanager.ui.fmt
import com.school.feemanager.ui.statusColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ================================================================ school theme
object School {
    val Navy = Color(0xFF1F4E79)
    val NavyDark = Color(0xFF173B5C)
    val Gold = Color(0xFFE2B33C)
    val Background = Color(0xFFF3F6FA)
    val TableHeader = Color(0xFFDDE9F5)
    val Line = Color(0xFFDCE3EB)
    val Text = Color(0xFF1E2A36)
    val Muted = Color(0xFF5C6B7A)
    val Green = Color(0xFF2E7D32)
    val Red = Color(0xFFC62828)
    val Amber = Color(0xFFB7791F)
    val Blue = Color(0xFF2F6FB0)
}

private val desktopColors: ColorScheme = lightColorScheme(
    primary = School.Navy,
    onPrimary = Color.White,
    primaryContainer = School.TableHeader,
    onPrimaryContainer = School.Navy,
    secondary = School.Blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F1FA),
    onSecondaryContainer = School.Navy,
    tertiary = School.Gold,
    background = School.Background,
    onBackground = School.Text,
    surface = Color.White,
    onSurface = School.Text,
    surfaceVariant = Color(0xFFEEF2F7),
    onSurfaceVariant = School.Muted,
    outline = Color(0xFFC3CCD6),
    outlineVariant = School.Line,
    error = School.Red,
)

private val desktopType = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun SchoolDesktopTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = desktopColors, typography = desktopType, content = content)

// ================================================================ page layout
/** Page body under the header: background colour and the standard padding. */
@Composable
fun PageBody(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().background(School.Background).padding(horizontal = 28.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(8.dp), modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(containerColor = School.Navy, disabledContainerColor = School.Navy.copy(alpha = 0.35f), disabledContentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 20.dp)) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(8.dp), modifier = modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun LinkText(text: String, onClick: () -> Unit, color: Color = School.Blue) {
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 2.dp))
}

// ================================================================ filter controls (48 dp, like Windows forms)
private val boxShape = RoundedCornerShape(6.dp)

@Composable
private fun FieldBox(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.height(46.dp).background(Color.White, boxShape).border(1.dp, Color(0xFFC6D0DB), boxShape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    FieldBox(modifier) {
        Icon(Icons.Default.Search, contentDescription = null, tint = School.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = Color(0xFF8A97A6), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            BasicTextField(value, onChange, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = School.Text), cursorBrush = SolidColor(School.Navy), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun <T> SelectBox(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FieldBox(Modifier.clickable { open = true }) {
            Text(label(selected), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = School.Text)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(label(o)) }, onClick = { onSelect(o); open = false }) }
        }
    }
}

@Composable
fun DateBox(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        FieldBox(Modifier.width(158.dp).clickable { open = true }) {
            Text(date?.fmt() ?: "dd-MMM-yyyy", color = if (date == null) Color(0xFF8A97A6) else School.Text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = School.Text, modifier = Modifier.size(20.dp))
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }); open = false
                }) { Text("OK") }
            },
            dismissButton = { Row { TextButton(onClick = { onChange(null); open = false }) { Text("Clear") }; TextButton(onClick = { open = false }) { Text("Cancel") } } },
        ) { DatePicker(state = state) }
    }
}

@Composable
fun CheckLabel(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.clickable { onChange(!checked) }.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = School.Navy))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Status in a rounded outline, as in the sample ("Partially Paid", "Overdue"). */
@Composable
fun StatusPill(s: PayStatus) {
    val c = when (s) { PayStatus.PAID -> School.Green; PayStatus.PARTIAL -> School.Amber; PayStatus.OVERDUE -> School.Red; else -> statusColor(s) }
    Text(s.label, color = c, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.background(c.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).border(1.dp, c.copy(alpha = 0.7f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 3.dp))
}

// ================================================================ cards
/** White card with a coloured edge on the left (dashboard figures). */
@Composable
fun AccentCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier, extra: String? = null) {
    Row(modifier.height(IntrinsicSize.Min).background(Color.White, RoundedCornerShape(10.dp)).border(1.dp, School.Line, RoundedCornerShape(10.dp))) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(accent, RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)))
        Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = School.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = School.Text, maxLines = 1)
                if (extra != null) { Spacer(Modifier.width(8.dp)); Text(extra, style = MaterialTheme.typography.bodyMedium, color = School.Text, modifier = Modifier.padding(bottom = 4.dp)) }
            }
        }
    }
}

@Composable
fun Panel(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.background(Color.White, RoundedCornerShape(10.dp)).border(1.dp, School.Line, RoundedCornerShape(10.dp)).padding(24.dp)) {
        if (title != null) { Text(title, style = MaterialTheme.typography.titleMedium, color = School.Navy); Spacer(Modifier.height(14.dp)) }
        content()
    }
}

/** "0 selected  0 MMK  [action] [Clear selection]" bar above a table. */
@Composable
fun SelectionBar(count: Int, amount: String, actionLabel: String, onAction: () -> Unit, onClear: () -> Unit, extra: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().background(Color(0xFFE9EFF6), RoundedCornerShape(8.dp)).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$count selected", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(14.dp))
        Text(amount, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        extra()
        PrimaryButton(actionLabel, onAction, enabled = count > 0, modifier = Modifier.height(40.dp))
        Spacer(Modifier.width(10.dp))
        SecondaryButton("Clear selection", onClear, enabled = count > 0)
    }
}

@Composable
fun TabStrip(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Row {
            tabs.forEachIndexed { i, t ->
                Column(Modifier.clickable { onSelect(i) }.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t, style = MaterialTheme.typography.bodyLarge, color = if (i == selected) School.Navy else School.Muted,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                    Box(Modifier.height(3.dp).width(if (i == selected) 150.dp else 0.dp).background(School.Navy))
                }
            }
        }
        HorizontalDivider(color = School.Line)
    }
}

// ================================================================ data grid
/** A table column; [weight] shares the width, [end] right-aligns (numbers). */
data class Col(val title: String, val weight: Float, val end: Boolean = false, val width: Dp? = null)

@Composable
fun RowScope.Cell(col: Col, content: @Composable () -> Unit) {
    val m = if (col.width != null) Modifier.width(col.width) else Modifier.weight(col.weight)
    Box(m.padding(horizontal = 10.dp), contentAlignment = if (col.end) Alignment.CenterEnd else Alignment.CenterStart) { content() }
}

@Composable
fun RowScope.TextCell(col: Col, text: String, color: Color = School.Text, bold: Boolean = false, decoration: TextDecoration? = null) = Cell(col) {
    Text(text, color = color, style = MaterialTheme.typography.bodyLarge, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (col.end) TextAlign.End else TextAlign.Start, textDecoration = decoration)
}

@Composable
fun GridRow(modifier: Modifier = Modifier, background: Color = Color.White, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Column {
        Row(modifier.fillMaxWidth().background(background).let { if (onClick != null) it.clickable(onClick = onClick) else it }.heightIn(min = 52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically, content = content)
        HorizontalDivider(color = School.Line)
    }
}

/**
 * Bordered table: blue header row, scrolling body with a scrollbar, optional total row.
 * [body] adds rows with LazyColumn's item/items.
 */
@Composable
fun DataGrid(
    cols: List<Col>,
    modifier: Modifier = Modifier,
    header: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    empty: String? = null,
    body: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    // The body takes only the height its rows need (up to what is available), so short tables end
    // after the last row and long ones scroll while the header and total row stay visible.
    BoxWithConstraints(modifier) {
        val reserved = 50.dp + (if (footer != null) 50.dp else 0.dp) + (if (empty != null) 70.dp else 0.dp)
        val bodyMax = if (maxHeight == Dp.Infinity) 2000.dp else (maxHeight - reserved).coerceAtLeast(60.dp)
        Column(Modifier.background(Color.White, RoundedCornerShape(10.dp)).border(1.dp, School.Line, RoundedCornerShape(10.dp))) {
            Row(Modifier.fillMaxWidth().background(School.TableHeader, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).height(50.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (header != null) header() else cols.forEach { c -> TextCell(c, c.title, color = School.Navy, bold = true) }
            }
            var bodyHeight by remember { mutableStateOf(0.dp) }
            val density = LocalDensity.current
            Box(Modifier.heightIn(max = bodyMax)) {
                LazyColumn(state = state, modifier = Modifier.fillMaxWidth().padding(end = 10.dp).onSizeChanged { bodyHeight = with(density) { it.height.toDp() } }) { body() }
                // sized to the rows (a full-height scrollbar would stretch the table to the bottom of the page)
                VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.TopEnd).height(bodyHeight))
            }
            if (empty != null) Text(empty, color = School.Muted, modifier = Modifier.padding(24.dp))
            if (footer != null) Row(Modifier.fillMaxWidth().background(Color(0xFFF1F4F8), RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)).height(50.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, content = footer)
        }
    }
}
