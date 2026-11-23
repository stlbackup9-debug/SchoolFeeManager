package com.school.feemanager

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
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
import com.school.feemanager.domain.BookType
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.domain.StudentStatus
import com.school.feemanager.domain.StudentType
import com.school.feemanager.domain.academicYearLabel
import com.school.feemanager.ui.AppViewModel
import com.school.feemanager.ui.LocalPlatform
import com.school.feemanager.ui.PlatformBridge
import com.school.feemanager.ui.wide.Accent
import com.school.feemanager.ui.wide.DisplaySettings
import com.school.feemanager.ui.wide.DisplayState
import com.school.feemanager.ui.wide.LayoutMode
import com.school.feemanager.ui.wide.LocalDisplay
import com.school.feemanager.ui.wide.MenuActions
import com.school.feemanager.ui.wide.Palette
import com.school.feemanager.ui.wide.School
import com.school.feemanager.ui.wide.SchoolApp
import com.school.feemanager.ui.wide.SchoolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/**
 * The Android layouts, rendered with the same shared code at phone and tablet sizes:
 * phone → bottom navigation + cards; tablet → school sidebar + tables (compact sidebar in portrait).
 */
@OptIn(ExperimentalTestApi::class)
class AdaptiveLayoutTest {
    @get:Rule val tmp = TemporaryFolder()
    private val shots = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }
    /** behaves like the Android platform (no keyboard-shortcut hints) */
    private val touch = object : PlatformBridge { override fun printHtml(html: String, jobName: String, isReport: Boolean) {}; override fun restartApp() {} }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @After fun reset() { Dispatchers.resetMain(); School.palette = Palette.of(Accent.BLUE, false) }

    private fun ComposeUiTest.shot(name: String) {
        waitForIdle()
        val roots = onAllNodes(isRoot())
        ImageIO.write(roots[roots.fetchSemanticsNodes().size - 1].captureToImage().toAwtImage(), "png", File(shots, "$name.png"))
    }

    private fun ComposeUiTest.waitText(text: String) = waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun repo(name: String): Pair<JdbcFeeStore, FeeRepository> {
        val store = JdbcFeeStore(File(tmp.root, "$name.db"))
        val repo = FeeRepository(store)
        val june = LocalDate.now().let { if (it.monthValue >= 6) LocalDate.of(it.year, 6, 1) else LocalDate.of(it.year - 1, 6, 1) }
        runBlocking {
            repo.ensureSeeded()
            repo.setPassword(repo.snapshot().first().users.single().id, "admin123", "t", forceChange = false)
            repo.saveSchoolProfile("Shwe Pyi Music School", "Yangon", "", "RCP", june, "t")
            val f = EnrollmentForm("G-3", academicYearLabel(june, 12), june, 12, StudentType.DAY, StudentStatus.NEW, BookType.MYANMAR, 0, PaymentTerm.THREE_INST, "")
            val a = repo.saveStudent(null, StudentForm("EN-001", "Aung Aung", ""), f, "t")
            repo.saveEnrollment(null, a, f.copy(className = "G-5", status = StudentStatus.OLD, term = PaymentTerm.MONTHLY), "t")
            repo.saveStudent(null, StudentForm("EN-002", "Su Su", ""), f.copy(className = "KG", type = StudentType.HOSTEL), "t")
            val e = repo.snapshot().first().ledger(a)!!.enrollments.first { it.enrollment.className == "G-3" }
            repo.recordPayment(PaymentRequest(june.coerceAtMost(LocalDate.now()), listOf(PayItem(a, e.enrollment.id, 1, 183333, "G-3 Installment 1")), ""), "admin")
        }
        return store to repo
    }

    private fun ComposeUiTest.start(repo: FeeRepository, display: DisplayState, actions: MenuActions) {
        val vm = AppViewModel(repo)
        setContent {
            CompositionLocalProvider(LocalPlatform provides touch, LocalDisplay provides display) {
                SchoolTheme { DesktopFrame { SchoolApp(vm, display, actions, null, "1.2.0") } }
            }
        }
        waitText("Log in")
        onNode(hasSetTextAction() and hasText("Username")).performTextInput("admin")
        onNode(hasSetTextAction() and hasText("Password")).performTextInput("admin123")
        onAllNodesWithText("Log in")[0].performScrollTo().performClick()
        waitText("Total Students Enrolled")
    }

    @Test fun phone() = runDesktopComposeUiTest(412, 892) {
        val (store, repo) = repo("phone")
        val display = DisplayState(File(tmp.root, "p.properties"), DisplaySettings(followSystem = false))
        val actions = MenuActions()
        start(repo, display, actions)
        // phone layout: bottom navigation, no sidebar
        assertTrue(onAllNodesWithText("Payables").fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithText("Scale & Layout").fetchSemanticsNodes().isEmpty())
        shot("30-phone-dashboard")
        onAllNodesWithText("Students")[0].performClick()
        waitText("Student Register (2)")
        shot("31-phone-students")
        onAllNodesWithText("Payables")[0].performClick()
        waitText("Tick the installments")
        shot("32-phone-payables")
        // Settings → Display opens Scale & Layout (with the phone / tablet layout choice)
        onAllNodesWithText("Settings")[0].performClick()
        waitText("Scale & Layout, colour, Night mode")
        onAllNodesWithText("Scale & Layout, colour, Night mode")[0].performClick()
        waitText("Reset to default")
        assertTrue(onAllNodesWithText("Tablet").fetchSemanticsNodes().isNotEmpty())
        onAllNodesWithText("Night")[0].performScrollTo().performClick()
        onAllNodesWithText("Maroon")[0].performScrollTo().performClick()
        shot("33-phone-display-dialog")
        onAllNodesWithText("Close")[0].performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Reset to default").fetchSemanticsNodes().isEmpty() }
        onAllNodesWithText("Students")[0].performClick()
        waitText("Student Register (2)")
        shot("34-phone-students-night-maroon")
        onAllNodesWithText("Aung Aung")[0].performClick()
        waitText("Classes (2)")
        shot("35-phone-student-night")
        // "Tablet" on a phone held upright keeps the phone layout (no room for sidebar + tables)
        display.update { it.copy(layout = LayoutMode.TABLET) }
        waitForIdle()
        assertTrue(onAllNodesWithText("Scale & Layout").fetchSemanticsNodes().isEmpty())
        display.update { it.copy(layout = LayoutMode.AUTO, night = false, accent = Accent.BLUE) }
        store.close()
    }

    @Test fun tabletPortrait() = runDesktopComposeUiTest(800, 1280) {
        val (store, repo) = repo("tp")
        val display = DisplayState(File(tmp.root, "tp.properties"))
        start(repo, display, MenuActions())
        // tablet layout: sidebar (compact in portrait – icons with small labels) and the wide pages
        waitText("Collection")
        assertTrue(onAllNodesWithText("Scale & Layout").fetchSemanticsNodes().isEmpty())   // compact: icon only
        shot("40-tablet-portrait-dashboard")
        onAllNodesWithText("Payments")[0].performClick()
        waitText("Payments – Payables")
        shot("41-tablet-portrait-payments")
        store.close()
    }

    @Test fun tabletLandscape() = runDesktopComposeUiTest(1280, 800) {
        val (store, repo) = repo("tl")
        val display = DisplayState(File(tmp.root, "tl.properties"))
        display.update { it.copy(night = true, accent = Accent.TEAL) }
        start(repo, display, MenuActions())
        waitText("Scale & Layout")                                                           // full sidebar
        assertTrue(onAllNodesWithText("Version 1.2.0").fetchSemanticsNodes().isNotEmpty())
        shot("42-tablet-landscape-dashboard-night")
        onAllNodesWithText("Students")[0].performClick()
        waitText("Student Register (2)")
        shot("43-tablet-landscape-students-night")
        // the user can force the phone layout on a tablet
        display.update { it.copy(layout = LayoutMode.PHONE) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Scale & Layout").fetchSemanticsNodes().isEmpty() }
        waitText("Payables")
        store.close()
    }

    @Test fun phoneLandscapeForcedTablet() = runDesktopComposeUiTest(892, 412) {
        val (store, repo) = repo("pl")
        val display = DisplayState(File(tmp.root, "pl.properties"))
        display.update { it.copy(layout = LayoutMode.TABLET) }
        start(repo, display, MenuActions())
        waitText("Collection")
        shot("44-phone-landscape-tablet-layout")
        onAllNodesWithText("Students")[0].performClick()
        waitText("Student Register (2)")
        shot("45-phone-landscape-students")
        store.close()
    }
}
