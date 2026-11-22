import com.school.feemanager.domain.*
import com.school.feemanager.io.*
import java.io.File
fun main(a: Array<String>) {
    val imp = WorkbookImporter.read(File(a[0]).inputStream(), FeeRateCard.workbookDefaults())
    System.err.println("errors=${imp.errors.size} students=${imp.students.size} payments=${imp.payments.size}")
    val card = imp.rates!!
    val cands = imp.students.mapIndexed { i, s -> MatchCandidate(i.toLong(), "", s.name, s.className) }
    val paid = LongArray(imp.students.size)
    imp.payments.forEach { p -> val m = ImportPlanner.match(p, cands) as MatchResult.Found; paid[m.candidate.key.toInt()] += p.amount }
    imp.students.forEachIndexed { i, s ->
        val f = FeeCalculator.assess(card, s.className, s.type, s.status, s.bookType, s.discountBp)
        val sch = ScheduleCalculator.generate(s.term, f.netTotal, java.time.LocalDate.of(2026,6,1)).map { it.amount }
        println(listOf(s.name, f.trainingFee, f.instrumentFee, f.bookFee, f.hostelFee, f.laundryFee, f.entranceFee, f.grossTotal, f.discountAmount, f.netTotal, paid[i], f.netTotal - paid[i], sch.joinToString("/")).joinToString("\t"))
    }
}
