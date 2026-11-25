package com.school.feemanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.school.feemanager.domain.FeeKind
import com.school.feemanager.domain.FeeOption
import com.school.feemanager.domain.Money

private class OptionRow(kind: FeeKind, name: String, amount: String) {
    val kind = kind
    val name = mutableStateOf(name)
    val amount = mutableStateOf(amount)
}

/**
 * Edits the extra training and book fees students of one class can choose
 * (e.g. training "Advanced" 300,000; book "Workbook set" 15,000).
 */
@Composable
fun FeeOptionsDialog(className: String, standardTraining: Long, initial: List<FeeOption>, enabled: Boolean, onDone: (List<FeeOption>) -> Unit, onDismiss: () -> Unit) {
    val rows = remember { mutableStateListOf<OptionRow>().apply { addAll(initial.map { OptionRow(it.kind, it.name, it.amount.toString()) }) } }
    fun clean(r: OptionRow) = FeeOption.cleanName(r.name.value)
    val dup = rows.groupBy { it.kind to clean(it).lowercase() }.filter { it.value.size > 1 }.values.flatten().toSet()
    val bad = rows.any { clean(it).isEmpty() || it.amount.value.toLongOrNull() == null } || dup.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$className – fee choices") },
        text = {
            Column(Modifier.widthIn(max = 560.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Students of this class can choose one training fee and any of the book fees when they are enrolled.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FeeKind.entries.forEach { kind ->
                    Text(if (kind == FeeKind.TRAINING) "Training fee choices" else "Extra book fees", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (kind == FeeKind.TRAINING) "Besides the standard training fee (${Money.format(standardTraining)}) and “no training fee”."
                        else "In addition to (or instead of) the standard book fee.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    rows.filter { it.kind == kind }.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(r.name.value, { r.name.value = it.take(40) }, label = { Text("Name") }, singleLine = true, enabled = enabled,
                                isError = clean(r).isEmpty() || r in dup, modifier = Modifier.weight(1.4f))
                            OutlinedTextField(r.amount.value, { r.amount.value = it.filter { c -> c.isDigit() }.take(13) }, label = { Text("MMK") }, singleLine = true, enabled = enabled,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = r.amount.value.toLongOrNull() == null, modifier = Modifier.weight(1f))
                            if (enabled) IconButton(onClick = { rows.remove(r) }) { Icon(Icons.Default.Delete, contentDescription = "Remove ${r.name.value}") }
                        }
                    }
                    if (enabled) TextButton(onClick = { rows.add(OptionRow(kind, "", "0")) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(if (kind == FeeKind.TRAINING) "Add training choice" else "Add book fee")
                    }
                }
                if (dup.isNotEmpty()) Text("Names must be different.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (enabled) TextButton(enabled = !bad, onClick = {
                onDone(rows.map { FeeOption(it.kind, clean(it), it.amount.value.toLong()) }.sortedBy { it.kind.ordinal })
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (enabled) "Cancel" else "Close") } },
    )
}
