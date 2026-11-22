package com.school.feemanager.io

import com.school.feemanager.domain.Money
import com.school.feemanager.domain.ReportTable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Receipt data as printed. */
data class ReceiptView(
    val schoolName: String,
    val schoolAddress: String,
    val schoolPhone: String,
    val receiptNo: String,
    val date: LocalDate,
    val studentCode: String,
    val studentName: String,
    val className: String,
    val amount: Long,
    val description: String,
    val previousBalance: Long,
    val remainingBalance: Long,
    val receivedBy: String,
    val remarks: String,
    val reprint: Boolean,
    val voided: Boolean,
)

/** HTML used for printing through Android's print framework (receipts and reports). */
object HtmlDocs {
    private val D: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy")

    fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun receipt(r: ReceiptView): String = """
<!DOCTYPE html><html><head><meta charset="utf-8"><style>
@page { size: A5 landscape; margin: 10mm; }
body { font-family: sans-serif; color: #111; font-size: 12px; }
.box { border: 1.5px solid #1F4E79; padding: 14px 18px; }
h1 { margin: 0; color: #1F4E79; font-size: 20px; text-align: center; }
.sub { text-align: center; color: #444; margin-bottom: 6px; }
h2 { text-align: center; font-size: 14px; letter-spacing: 2px; margin: 8px 0; border-top: 1px solid #ccc; border-bottom: 1px solid #ccc; padding: 4px 0; }
table { width: 100%; border-collapse: collapse; }
td { padding: 4px 2px; vertical-align: top; }
td.l { color: #555; width: 32%; }
.amt { font-size: 16px; font-weight: bold; }
.sig { margin-top: 34px; display: flex; justify-content: space-between; }
.sig div { border-top: 1px solid #333; width: 40%; text-align: center; padding-top: 4px; }
.flag { color: #b00; font-weight: bold; text-align: center; }
</style></head><body><div class="box">
<h1>${esc(r.schoolName)}</h1>
<div class="sub">${esc(r.schoolAddress)}${if (r.schoolPhone.isNotBlank()) " &middot; Tel: " + esc(r.schoolPhone) else ""}</div>
<h2>OFFICIAL FEE RECEIPT</h2>
${if (r.voided) "<div class=\"flag\">*** VOIDED – NOT VALID ***</div>" else if (r.reprint) "<div class=\"flag\">DUPLICATE COPY</div>" else ""}
<table>
<tr><td class="l">Receipt No.</td><td><b>${esc(r.receiptNo)}</b></td><td class="l">Date</td><td>${r.date.format(D)}</td></tr>
<tr><td class="l">Student Name</td><td><b>${esc(r.studentName)}</b></td><td class="l">Student ID</td><td>${esc(r.studentCode)}</td></tr>
<tr><td class="l">Class</td><td>${esc(r.className)}</td><td class="l">Payment For</td><td>${esc(r.description)}</td></tr>
<tr><td class="l">Amount Received</td><td colspan="3" class="amt">${Money.mmk(r.amount)}</td></tr>
<tr><td class="l">Previous Balance</td><td>${Money.mmk(r.previousBalance)}</td><td class="l">Remaining Balance</td><td><b>${Money.mmk(r.remainingBalance)}</b></td></tr>
${if (r.remarks.isNotBlank()) "<tr><td class=\"l\">Remarks</td><td colspan=\"3\">${esc(r.remarks)}</td></tr>" else ""}
<tr><td class="l">Received By</td><td colspan="3">${esc(r.receivedBy)}</td></tr>
</table>
<div class="sig"><div>Received By (Signature)</div><div>Parent / Guardian</div></div>
</div></body></html>
""".trimIndent()

    fun report(t: ReportTable, schoolName: String): String {
        val sb = StringBuilder()
        sb.append("""<!DOCTYPE html><html><head><meta charset="utf-8"><style>
@page { size: A4 landscape; margin: 10mm; }
body { font-family: sans-serif; font-size: 10px; color: #111; }
h1 { font-size: 15px; margin: 0; color: #1F4E79; } h2 { font-size: 12px; margin: 2px 0; } .sub { color: #555; margin-bottom: 8px; }
table { border-collapse: collapse; width: 100%; }
th { background: #DDEBF7; text-align: left; } th, td { border: 1px solid #bbb; padding: 3px 4px; }
td.n, th.n { text-align: right; } tr.t td { font-weight: bold; background: #f2f2f2; }
</style></head><body>""")
        sb.append("<h1>${esc(schoolName)}</h1><h2>${esc(t.title)}</h2><div class=\"sub\">${esc(t.subtitle)}</div><table><thead><tr>")
        t.headers.forEachIndexed { i, h -> sb.append("<th${if (t.isNumeric(i)) " class=\"n\"" else ""}>${esc(h)}</th>") }
        sb.append("</tr></thead><tbody>")
        t.rows.forEach { row ->
            sb.append("<tr>")
            row.forEachIndexed { i, v -> sb.append("<td${if (v is Long) " class=\"n\"" else ""}>${esc(ReportTable.cellText(v))}</td>") }
            sb.append("</tr>")
        }
        t.totals?.let { tot ->
            sb.append("<tr class=\"t\">")
            tot.forEach { v -> sb.append("<td${if (v is Long) " class=\"n\"" else ""}>${esc(ReportTable.cellText(v))}</td>") }
            sb.append("</tr>")
        }
        if (t.rows.isEmpty()) sb.append("<tr><td colspan=\"${t.headers.size}\">No records match the selected filters.</td></tr>")
        sb.append("</tbody></table></body></html>")
        return sb.toString()
    }
}
