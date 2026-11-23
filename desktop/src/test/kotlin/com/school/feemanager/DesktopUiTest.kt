package com.school.feemanager

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isToggleable
import com.school.feemanager.data.EnrollmentForm
import com.school.feemanager.data.FeeRepository
import com.school.feemanager.data.JdbcFeeStore
import com.school.feemanager.data.PayItem
import com.school.feemanager.data.PaymentRequest
import com.school.feemanager.data.StudentForm
import com.school.feemanager.desktop.DesktopFrame
import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import com.school.feemanager.domain.academicYearLabel
import com.school.feemanager.ui.AppRoot
import com.school.feemanager.ui.AppViewModel
import com.school.feemanager.ui.LocalPlatform
import com.school.feemanager.ui.PlatformBridge
import com.school.feemanager.ui.theme.SchoolFeeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/** Drives the real screens of the desktop app (login → students → payables → combined payment → receipt) and saves screenshots. */
@OptIn(ExperimentalTestApi::class)
class DesktopUiTest {
    @get:Rule val tmp = TemporaryFolder()
    private val shots = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }
    private val printed = ArrayList<String>()

    // The real app runs on the Swing UI thread (= Main). In the test, make Main run where the test drives the UI.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Before fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun ComposeUiTest.shot(name: String) {
        waitForIdle()
        // with a dialog open there are two roots; capture the top one (the dialog is drawn over a dimmed screen)
        val roots = onAllNodes(androidx.compose.ui.test.isRoot())
        val n = roots.fetchSemanticsNodes().size
        ImageIO.write(roots[n - 1].captureToImage().toAwtImage(), "png", File(shots, "$name.png"))
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.first(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        onAllNodesWithText(text, substring = substring)[0]

    @Test fun mainFlow() = runDesktopComposeUiTest(1400, 900) {
        val store = JdbcFeeStore(File(tmp.root, "ui.db"))
        val repo = FeeRepository(store)
        val today = LocalDate.now()
        val june = if (today.monthValue >= 6) LocalDate.of(today.year, 6, 1) else LocalDate.of(today.year - 1, 6, 1)
        runBlocking {
            repo.ensureSeeded()
            val admin = repo.snapshot().first().users.single()
            repo.setPassword(admin.id, "admin123", "test", forceChange = false)
            repo.saveSchoolProfile("Shwe Pyi Music School", "Yangon", "09-123456", "RCP", june, "test")
            fun f(c: String, start: LocalDate, p: Int, t: PaymentTerm, ty: StudentType = StudentType.DAY) = EnrollmentForm(c, academicYearLabel(start, p), start, p, ty, StudentStatus.NEW, BookType.MYANMAR, 0, t, "")
            val a = repo.saveStudent(null, StudentForm("EN-001", "Aung Aung", ""), f("G-3", june, 12, PaymentTerm.THREE_INST), "test")
            repo.saveEnrollment(null, a, f("G-5", june.plusMonths(3), 6, PaymentTerm.MONTHLY), "test")
            val b = repo.saveStudent(null, StudentForm("EN-002", "Su Su", ""), f("KG", june, 12, PaymentTerm.MONTHLY, StudentType.HOSTEL), "test")
            repo.saveStudent(null, StudentForm("EN-003", "မောင်မောင်", ""), f("G-1", june, 12, PaymentTerm.THREE_INST), "test")
            val kg = repo.snapshot().first().ledger(b)!!.enrollments.single()
            repo.recordPayment(PaymentRequest(june.coerceAtMost(today), listOf(PayItem(b, kg.enrollment.id, 1, 100000, "KG Month 1")), ""), "admin")
        }
        val vm = AppViewModel(repo)
        val platform = object : PlatformBridge {
            override fun printHtml(html: String, jobName: String, isReport: Boolean) { printed += jobName; File(shots, "printed-${printed.size}.html").writeText(html) }
            override fun restartApp() {}
        }
        setContent {
            CompositionLocalProvider(LocalPlatform provides platform, com.school.feemanager.ui.LocalDesktop provides true) {
                com.school.feemanager.desktop.ui.SchoolDesktopTheme {
                    DesktopFrame {
                        AppRoot(vm) { v, snap, session, busy, snackbar ->
                            com.school.feemanager.desktop.ui.DesktopShell(null, v, snap, session, busy, snackbar, null, "1.1.0")
                        }
                    }
                }
            }
        }

        waitText("Log in")
        shot("01-login")
        onNode(hasSetTextAction() and hasText("Username")).performTextInput("admin")
        onNode(hasSetTextAction() and hasText("Password")).performTextInput("admin123")
        first("Log in").performClick()
        waitText("Total Students Enrolled")
        shot("02-dashboard")

        first("Students").performClick()
        waitText("Student Register", substring = true)
        shot("03-students")
        first("Aung Aung").performClick()
        waitText("Classes (2)")
        shot("04-student-detail")

        first("Payments").performClick()
        waitText("Payments – Payables")
        shot("05-payables")
        // find Aung Aung, open the row, select all his items (two classes) and pay them on one receipt
        onAllNodes(hasSetTextAction())[0].performTextInput("Aung")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Su Su").fetchSemanticsNodes().isEmpty() }
        first("Aung Aung").performClick()
        waitText("Installment 1", substring = true)
        first("Select all shown").performClick()
        waitText("9 selected")
        shot("06-payables-selected")
        first("Pay selected").performClick()
        waitText("Save & Receipt")
        shot("07-collect")
        first("Save & Receipt").performScrollTo().performClick()
        waitText("Confirm combined payment")
        shot("08-confirm")
        first("Save").performClick()
        waitText("OFFICIAL FEE RECEIPT (COMBINED)")
        shot("09-receipt")
        first("Print / save as PDF").performScrollTo().performClick()
        assertEquals(1, printed.size)

        first("Collection").performClick()
        waitText("Cash Collection")
        // tick both receipts and print them as one combined document
        onAllNodes(isToggleable())[1].performClick()
        onAllNodes(isToggleable())[2].performClick()
        waitText("2 selected")
        shot("10-collection")
        first("Combined receipt…").performClick()
        assertEquals(2, printed.size)

        first("Reports").performClick()
        waitText("Payment Schedule Report", substring = true)
        first("Payment Schedule Report", substring = true).performClick()
        waitText("row(s)", substring = true)
        shot("11-report")

        first("Settings").performClick()
        waitText("Data: import Excel / CSV, export, backup")
        first("Data: import Excel / CSV, export, backup").performClick()
        waitText("Import from Excel (.xlsx) or CSV")
        shot("12-data")

        val s = runBlocking { repo.snapshot().first() }
        assertEquals(2, s.receipts.size)
        assertEquals(1, s.receipts.count { it.items.size > 1 })
        store.close()
    }
}
