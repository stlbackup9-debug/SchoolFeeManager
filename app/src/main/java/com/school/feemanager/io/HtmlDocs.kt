package com.school.feemanager.io

import com.school.feemanager.domain.Money
import com.school.feemanager.domain.ReportTable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One line of a printed receipt. */
data class ReceiptLine(
    val studentName: String,
    val studentId: String,
    val className: String,
    val description: String,
    val amount: Long,
)

/** Receipt data as printed. A combined receipt has several [lines]. */
data class ReceiptView(
    val schoolName: String,
    val schoolAddress: String,
    val schoolPhone: String,
    val receiptNo: String,
    val date: LocalDate,
    val payerName: String,
    val lines: List<ReceiptLine>,
    val previousBalance: Long,
    val remainingBalance: Long,
    val receivedBy: String,
    val remarks: String,
    val reprint: Boolean,
    val voided: Boolean,
) {
    val amount: Long get() = lines.sumOf { it.amount }
}

/** HTML used for printing through Android's print framework (receipts and reports). */
object HtmlDocs {
    private val D: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy")

    fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private const val RECEIPT_CSS = """
@page { size: A5 landscape; margin: 10mm; }
body { font-family: sans-serif; color: #111; font-size: 12px; }
.box { border: 1.5px solid #1F4E79; padding: 14px 18px; page-break-inside: avoid; }
.page { page-break-after: always; }
.page:last-child { page-break-after: auto; }
h1 { margin: 0; color: #1F4E79; font-size: 20px; text-align: center; }
.sub { text-align: center; color: #444; margin-bottom: 6px; }
h2 { text-align: center; font-size: 14px; letter-spacing: 2px; margin: 8px 0; border-top: 1px solid #ccc; border-bottom: 1px solid #ccc; padding: 4px 0; }
table { width: 100%; border-collapse: collapse; }
td { padding: 4px 2px; vertical-align: top; }
td.l { color: #555; width: 22%; }
table.items { margin: 8px 0; }
table.items th { background: #DDEBF7; text-align: left; padding: 4px; border: 1px solid #bbb; }
table.items td { border: 1px solid #bbb; padding: 4px; }
.n { text-align: right; white-space: nowrap; }
tr.t td { font-weight: bold; background: #f2f2f2; }
.amt { font-size: 16px; font-weight: bold; }
.sig { margin-top: 34px; display: flex; justify-content: space-between; }
.sig div { border-top: 1px solid #333; width: 40%; text-align: center; padding-top: 4px; }
.flag { color: #b00; font-weight: bold; text-align: center; }
"""

    private fun header(schoolName: String, address: String, phone: String) =
        "<h1>${esc(schoolName)}</h1><div class=\"sub\">${esc(address)}${if (phone.isNotBlank()) " &middot; Tel: " + esc(phone) else ""}</div>"

    private fun receiptBody(r: ReceiptView): String {
        val sb = StringBuilder()
        sb.append(header(r.schoolName, r.schoolAddress, r.schoolPhone))
        sb.append("<h2>OFFICIAL FEE RECEIPT${if (r.lines.size > 1) " (COMBINED)" else ""}</h2>")
        if (r.voided) sb.append("<div class=\"flag\">*** VOIDED – NOT VALID ***</div>") else if (r.reprint) sb.append("<div class=\"flag\">DUPLICATE COPY</div>")
        sb.append("<table><tr><td class=\"l\">Receipt No.</td><td><b>${esc(r.receiptNo)}</b></td><td class=\"l\">Date</td><td>${r.date.format(D)}</td></tr>")
        sb.append("<tr><td class=\"l\">Received From</td><td colspan=\"3\"><b>${esc(r.payerName)}</b></td></tr></table>")
        sb.append("<table class=\"items\"><tr><th>#</th><th>Student</th><th>Student ID</th><th>Class</th><th>Payment For</th><th class=\"n\">Amount (MMK)</th></tr>")
        r.lines.forEachIndexed { i, l ->
            sb.append("<tr><td>${i + 1}</td><td>${esc(l.studentName)}</td><td>${esc(l.studentId)}</td><td>${esc(l.className)}</td><td>${esc(l.description)}</td><td class=\"n\">${Money.format(l.amount)}</td></tr>")
        }
        sb.append("<tr class=\"t\"><td colspan=\"5\">TOTAL RECEIVED</td><td class=\"n amt\">${Money.mmk(r.amount)}</td></tr></table>")
        sb.append("<table><tr><td class=\"l\">Previous Balance</td><td>${Money.mmk(r.previousBalance)}</td><td class=\"l\">Remaining Balance</td><td><b>${Money.mmk(r.remainingBalance)}</b></td></tr>")
        if (r.remarks.isNotBlank()) sb.append("<tr><td class=\"l\">Remarks</td><td colspan=\"3\">${esc(r.remarks)}</td></tr>")
        sb.append("<tr><td class=\"l\">Received By</td><td colspan=\"3\">${esc(r.receivedBy)}</td></tr></table>")
        sb.append("<div class=\"sig\"><div>Received By (Signature)</div><div>Parent / Guardian</div></div>")
        return sb.toString()
    }

    fun receipt(r: ReceiptView): String =
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>$RECEIPT_CSS</style></head><body><div class=\"box\">${receiptBody(r)}</div></body></html>"

    /** Several receipts in one document: a summary page, then every receipt on its own page. */
    fun combinedReceipts(list: List<ReceiptView>, schoolName: String, address: String, phone: String): String {
        val sb = StringBuilder("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>$RECEIPT_CSS</style></head><body>")
        val valid = list.filter { !it.voided }
        sb.append("<div class=\"page\"><div class=\"box\">").append(header(schoolName, address, phone))
        sb.append("<h2>COMBINED RECEIPT STATEMENT</h2>")
        val dates = list.map { it.date }.sorted()
        if (dates.isNotEmpty()) sb.append("<div class=\"sub\">${dates.first().format(D)}${if (dates.first() != dates.last()) " to " + dates.last().format(D) else ""} &middot; ${list.size} receipt(s)</div>")
        sb.append("<table class=\"items\"><tr><th>Receipt No.</th><th>Date</th><th>Student</th><th>Class</th><th>Payment For</th><th class=\"n\">Amount (MMK)</th></tr>")
        list.forEach { r ->
            r.lines.forEach { l ->
                sb.append("<tr><td>${esc(r.receiptNo)}${if (r.voided) " (VOIDED)" else ""}</td><td>${r.date.format(D)}</td><td>${esc(l.studentName)} (${esc(l.studentId)})</td><td>${esc(l.className)}</td><td>${esc(l.description)}</td><td class=\"n\">${if (r.voided) "<s>" + Money.format(l.amount) + "</s>" else Money.format(l.amount)}</td></tr>")
            }
        }
        sb.append("<tr class=\"t\"><td colspan=\"5\">TOTAL RECEIVED (excluding voided)</td><td class=\"n amt\">${Money.mmk(valid.sumOf { it.amount })}</td></tr></table>")
        sb.append("<div class=\"sig\"><div>Received By (Signature)</div><div>Parent / Guardian</div></div></div></div>")
        list.forEach { r -> sb.append("<div class=\"page\"><div class=\"box\">").append(receiptBody(r)).append("</div></div>") }
        sb.append("</body></html>")
        return sb.toString()
    }

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
