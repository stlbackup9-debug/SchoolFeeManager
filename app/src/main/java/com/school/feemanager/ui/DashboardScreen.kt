@file:OptIn(ExperimentalMaterial3Api::class)

package com.school.feemanager.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.school.feemanager.data.AppSnapshot
import com.school.feemanager.domain.Money
import com.school.feemanager.domain.MonthTotal
import com.school.feemanager.ui.theme.Amber
import com.school.feemanager.ui.theme.Blue
import com.school.feemanager.ui.theme.Green
import com.school.feemanager.ui.theme.LightBlue
import com.school.feemanager.ui.theme.Navy
import com.school.feemanager.ui.theme.Red
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(snap: AppSnapshot, onStudent: (Long) -> Unit, onReceipt: (Long) -> Unit) {
    val d = snap.dashboard
    Column(Modifier.fillMaxSize()) {
        AppTopBar(snap.settings.schoolName)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Academic year from ${snap.yearStart.fmt()}  ·  Today ${snap.today.fmt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            StatRow(
                { StatCard("Total Students Enrolled", d.totalStudents.toString(), Icons.Default.Groups, it) },
                { StatCard("Total Fee Receivable", Money.mmk(d.totalReceivable), Icons.Default.RequestQuote, it, Navy) },
            )
            StatRow(
                { StatCard("Total Cash Collected", Money.mmk(d.totalCollected), Icons.Default.Savings, it, Green) },
                { StatCard("Total Outstanding", Money.mmk(d.totalOutstanding), Icons.Default.AccountBalanceWallet, it, Red) },
            )
            StatRow(
                { StatCard("Day Students", d.dayStudents.toString(), Icons.Default.WbSunny, it, Blue) },
                { StatCard("Hostel Students", d.hostelStudents.toString(), Icons.Default.Bed, it, Blue) },
            )
            StatRow(
                { StatCard("Collected Today", Money.mmk(d.collectedToday), Icons.Default.Today, it, Green) },
                { StatCard("Collected This Month", Money.mmk(d.collectedThisMonth), Icons.Default.DateRange, it, Green) },
            )
            StatRow(
                { StatCard("Receipts Issued", d.receiptsIssued.toString(), Icons.Default.Receipt, it) },
                { StatCard("Students With Balance", "${d.studentsWithBalance}  (${d.overdueStudents} overdue)", if (d.overdueStudents > 0) Icons.Default.Warning else Icons.Default.HourglassBottom, it, Amber) },
            )

            SectionCard("Collection performance") {
                Text("${d.collectionRatePct}% of total receivable collected", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { (d.collectionRatePct / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Green, trackColor = LightBlue)
                Spacer(Modifier.height(14.dp))
                Text("Cash collected – last 6 months (MMK)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                MonthBars(d.lastSixMonths)
            }

            SectionCard("Fee collection summary by class") {
                val rows = d.classSummaries.filter { it.totalStudents > 0 || it.totalReceivable > 0 }
                if (rows.isEmpty()) EmptyState("No students registered yet")
                else DataTable(
                    headers = listOf("Class", "Day", "Hostel", "Total", "Receivable", "Collected", "Balance"),
                    rows = rows.map { listOf(it.className, it.dayStudents.toLong(), it.hostelStudents.toLong(), it.totalStudents.toLong(), it.totalReceivable, it.collected, it.balance) },
                    totals = listOf("TOTAL", rows.sumOf { it.dayStudents }.toLong(), rows.sumOf { it.hostelStudents }.toLong(), rows.sumOf { it.totalStudents }.toLong(),
                        rows.sumOf { it.totalReceivable }, rows.sumOf { it.collected }, rows.sumOf { it.balance }),
                    numeric = { it > 0 },
                    colWidth = { if (it == 0) 64 else if (it in 1..3) 56 else 110 },
                )
            }

            SectionCard("Recent cash collection") {
                if (d.recentPayments.isEmpty()) EmptyState("No payments recorded yet")
                val byId = snap.ledgers.associateBy { it.student.id }
                d.recentPayments.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider()
                    val s = byId[p.studentId]?.student
                    Row(
                        Modifier.fillMaxWidth().clickable { snap.receiptsByTx[p.id]?.let { onReceipt(it.id) } }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s?.name ?: "?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("${p.receiptNo} · ${s?.className ?: ""} · ${p.date.fmt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money.format(p.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Green)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        a(Modifier.weight(1f))
        b(Modifier.weight(1f))
    }
}

private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")

@Composable
private fun MonthBars(months: List<MonthTotal>) {
    val max = (months.maxOfOrNull { it.amount } ?: 0L).coerceAtLeast(1L)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val n = months.size.coerceAtLeast(1)
        val slot = size.width / n
        val barW = slot * 0.55f
        drawLine(LightBlue, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        months.forEachIndexed { i, m ->
            val h = (m.amount.toFloat() / max) * (size.height - 4f)
            val left = i * slot + (slot - barW) / 2
            if (m.amount > 0) drawRoundRect(Blue, topLeft = Offset(left, size.height - h), size = Size(barW, h), cornerRadius = CornerRadius(6f, 6f))
            else drawRoundRect(LightBlue, topLeft = Offset(left, size.height - 3f), size = Size(barW, 3f), style = Stroke(1f))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        months.forEach { m ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(m.month.format(MONTH), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                Text(shortMoney(m.amount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

fun shortMoney(v: Long): String = when {
    v >= 1_000_000 -> "%.1fM".format(v / 1_000_000.0)
    v >= 1_000 -> "${v / 1000}K"
    else -> v.toString()
}
