@file:OptIn(ExperimentalLayoutApi::class)

package com.school.feemanager.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private fun pct(f: Float) = "${(f * 100).roundToInt()}%"

/** Scale & Layout, colour and night mode. Changes apply at once and are remembered on this computer. */
@Composable
fun DisplaySettingsDialog(display: DisplayState, onClose: () -> Unit) {
    val s = display.settings
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Scale & Layout") },
        text = {
            Column(Modifier.widthIn(min = 560.dp, max = 640.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Section("Scale – change the size of text, apps and other items") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DisplaySettings.SCALES.forEach { v ->
                            Choice(pct(v) + if (v == 1f) " (recommended)" else "", selected = kotlin.math.abs(s.scale - v) < 0.001f) { display.update { it.copy(scale = v) } }
                        }
                    }
                    Hint("Also: Ctrl + =  bigger,  Ctrl + –  smaller,  Ctrl + 0  normal size.")
                }
                Section("Text size – ${pct(s.textSize)}") {
                    Slider(
                        value = s.textSize, onValueChange = { v -> display.update { it.copy(textSize = (v * 20).roundToInt() / 20f) } },
                        valueRange = DisplaySettings.MIN_TEXT..DisplaySettings.MAX_TEXT, steps = 12,
                        colors = SliderDefaults.colors(thumbColor = School.Navy, activeTrackColor = School.Navy),
                    )
                    Text("Sample: Aung Aung · G-3 · 1,146,000 MMK", style = MaterialTheme.typography.bodyLarge)
                }
                Section("Layout") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconChoice("Full sidebar (icons and names)", Icons.Default.ViewSidebar, !s.compactSidebar) { display.update { it.copy(compactSidebar = false) } }
                        IconChoice("Compact sidebar (icons only)", Icons.Default.Menu, s.compactSidebar) { display.update { it.copy(compactSidebar = true) } }
                    }
                }
                Section("Colour") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Accent.entries.forEach { a -> Swatch(a, s.accent == a) { display.update { it.copy(accent = a) } } }
                    }
                }
                Section("Mode") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconChoice("Day", Icons.Default.LightMode, !s.night) { display.update { it.copy(night = false) } }
                        IconChoice("Night", Icons.Default.DarkMode, s.night) { display.update { it.copy(night = true) } }
                    }
                    Hint("Night mode uses dark backgrounds – easier on the eyes in the evening. Printed receipts and reports stay white.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        dismissButton = { TextButton(onClick = { display.update { DisplaySettings() } }) { Text("Reset to default") } },
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = School.Navy)
        content()
        Spacer(Modifier.height(2.dp))
        HorizontalDivider(color = School.Line)
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = School.Muted)

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) School.OnButton else School.Text,
        modifier = Modifier.background(if (selected) School.Button else School.Surface, shape).border(1.dp, if (selected) School.Button else School.InputBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun IconChoice(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.background(if (selected) School.SelectionBar else School.Surface, shape).border(if (selected) 2.dp else 1.dp, if (selected) School.Navy else School.InputBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = School.Navy, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun Swatch(a: Accent, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(78.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp).background(a.base, CircleShape).border(if (selected) 3.dp else 1.dp, if (selected) a.highlight else School.InputBorder, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(a.label, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = School.Text)
    }
}
