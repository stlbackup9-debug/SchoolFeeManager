package com.school.feemanager

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.school.feemanager.data.EnrollmentForm
import com.school.feemanager.data.FeeRepository
import com.school.feemanager.data.JdbcFeeStore
import com.school.feemanager.data.PayItem
import com.school.feemanager.data.PaymentRequest
import com.school.feemanager.data.StudentForm
import com.school.feemanager.desktop.DesktopFrame
import com.school.feemanager.desktop.ui.Accent
import com.school.feemanager.desktop.ui.DesktopShell
import com.school.feemanager.desktop.ui.DisplaySettingsDialog
import com.school.feemanager.desktop.ui.DisplayState
import com.school.feemanager.desktop.ui.LocalDisplay
import com.school.feemanager.desktop.ui.MenuActions
import com.school.feemanager.desktop.ui.School
import com.school.feemanager.desktop.ui.SchoolDesktopTheme
import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import com.school.feemanager.domain.academicYearLabel
import com.school.feemanager.ui.AppRoot
import com.school.feemanager.ui.AppViewModel
import com.school.feemanager.ui.LocalDesktop
import com.school.feemanager.ui.LocalPlatform
import com.school.feemanager.ui.PlatformBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/** Scale & Layout dialog: scale, compact sidebar, colour and night mode – applied at once and saved. */
@OptIn(ExperimentalTestApi::class)
class DisplaySettingsUiTest {
    @get:Rule val tmp = TemporaryFolder()
    private val shots = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @After fun reset() { Dispatchers.resetMain(); School.palette = com.school.feemanager.desktop.ui.Palette.of(Accent.BLUE, false) }

    private fun ComposeUiTest.shot(name: String) {
        waitForIdle()
        val roots = onAllNodes(isRoot())
        ImageIO.write(roots[roots.fetchSemanticsNodes().size - 1].captureToImage().toAwtImage(), "png", File(shots, "$name.png"))
    }

    private fun ComposeUiTest.waitText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun scaleColourAndNightMode() = runDesktopComposeUiTest(1400, 900) {
        val store = JdbcFeeStore(File(tmp.root, "d.db"))
        val repo = FeeRepository(store)
        val june = LocalDate.now().let { if (it.monthValue >= 6) LocalDate.of(it.year, 6, 1) else LocalDate.of(it.year - 1, 6, 1) }
        runBlocking {
            repo.ensureSeeded()
            repo.setPassword(repo.snapshot().first().users.single().id, "admin123", "t", forceChange = false)
            repo.saveSchoolProfile("Shwe Pyi Music School", "Yangon", "", "RCP", june, "t")
            val f = EnrollmentForm("G-3", academicYearLabel(june, 12), june, 12, StudentType.DAY, StudentStatus.NEW, BookType.MYANMAR, 0, PaymentTerm.THREE_INST, "")
            val a = repo.saveStudent(null, StudentForm("EN-001", "Aung Aung", ""), f, "t")
            repo.saveStudent(null, StudentForm("EN-002", "Su Su", ""), f.copy(className = "KG", type = StudentType.HOSTEL), "t")
            val e = repo.snapshot().first().ledger(a)!!.enrollments.single()
            repo.recordPayment(PaymentRequest(june.coerceAtMost(LocalDate.now()), listOf(PayItem(a, e.enrollment.id, 1, 183333, "G-3 Installment 1")), ""), "admin")
        }
        val file = File(tmp.root, "display.properties")
        val display = DisplayState(file)
        val actions = MenuActions()
        val vm = AppViewModel(repo)
        val platform = object : PlatformBridge { override fun printHtml(html: String, jobName: String, isReport: Boolean) {}; override fun restartApp() {} }
        setContent {
            CompositionLocalProvider(LocalPlatform provides platform, LocalDesktop provides true, LocalDisplay provides display) {
                SchoolDesktopTheme {
                    DesktopFrame {
                        AppRoot(vm) { v, snap, session, busy, snackbar -> DesktopShell(actions, v, snap, session, busy, snackbar, null, "1.1.0") }
                        if (actions.showDisplay) DisplaySettingsDialog(display) { actions.showDisplay = false }
                    }
                }
            }
        }
        waitText("Log in")
        onNode(hasSetTextAction() and hasText("Username")).performTextInput("admin")
        onNode(hasSetTextAction() and hasText("Password")).performTextInput("admin123")
        onAllNodesWithText("Log in")[0].performClick()
        waitText("Total Students Enrolled")

        // the Scale & Layout button in the sidebar opens the dialog
        onAllNodesWithText("Scale & Layout")[0].performClick()
        waitText("Reset to default")
        onAllNodesWithText("Night")[0].performScrollTo().performClick()
        onAllNodesWithText("Forest green")[0].performScrollTo().performClick()
        onAllNodesWithText("125%")[0].performScrollTo().performClick()
        shot("20-display-dialog-night")
        assertTrue(display.settings.night)
        assertEquals(Accent.GREEN, display.settings.accent)
        assertEquals(1.25f, display.settings.scale)
        onAllNodesWithText("Close")[0].performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Reset to default").fetchSemanticsNodes().isEmpty() }
        shot("21-dashboard-night-green-125")

        onAllNodesWithText("Students")[0].performClick()
        waitText("Student Register (2)")
        shot("22-students-night")

        // compact sidebar + day mode via the sidebar moon/sun button, back to 100 %
        display.update { it.copy(compactSidebar = true, scale = 1f) }
        onAllNodesWithContentDescription("Day mode")[0].performClick()
        waitForIdle()
        assertTrue(!display.settings.night)
        shot("23-students-compact-day-green")

        // saved to the file and read back by a new instance
        val again = DisplayState(file).settings
        assertEquals(display.settings, again)
        assertTrue(file.readText().contains("accent=GREEN"))
        display.zoom(+1); assertEquals(1.1f, display.settings.scale)
        display.zoom(-2); assertEquals(0.9f, display.settings.scale)
        store.close()
    }
}
