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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.school.feemanager.data.FeeRepository
import com.school.feemanager.data.JdbcFeeStore
import com.school.feemanager.desktop.DesktopFrame
import com.school.feemanager.domain.FeeChoice
import com.school.feemanager.domain.FeeKind
import com.school.feemanager.domain.FeeOption
import com.school.feemanager.domain.PaymentTerm
import com.school.feemanager.ui.AppViewModel
import com.school.feemanager.ui.LocalPlatform
import com.school.feemanager.ui.PlatformBridge
import com.school.feemanager.ui.wide.Accent
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.imageio.ImageIO

/** Fee Rates: "Fees for all students" per class and student choices; enrollment form: training / book choices and custom payment term. */
@OptIn(ExperimentalTestApi::class)
class FeeChoicesUiTest {
    @get:Rule val tmp = TemporaryFolder()
    private val shots = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }
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
    private fun ComposeUiTest.click(text: String, substring: Boolean = false) {
        val n = onAllNodesWithText(text, substring = substring)[0]
        n.assertExists()
        try { n.performScrollTo() } catch (_: AssertionError) {}   // not inside a scrolling list
        // semantic click: a mouse click on a button that opens another page would leave the test pointer "pressed"
        // (a button that opens another page is gone when the test looks it up again after the action)
        try { n.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) } catch (e: AssertionError) { if (e.message?.contains("no existing nodes") != true) throw e }
    }

    private fun seeded(): Pair<JdbcFeeStore, FeeRepository> {
        val store = JdbcFeeStore(File(tmp.root, "c.db"))
        val repo = FeeRepository(store)
        runBlocking {
            repo.ensureSeeded()
            repo.setPassword(repo.snapshot().first().users.single().id, "admin123", "t", forceChange = false)
            val c = repo.snapshot().first().card
            // KG (the first class, preselected in the forms) offers choices; G-1 does not take the fees for all students
            repo.saveRates(c.copy(classes = c.classes.map {
                when (it.className) {
                    "KG" -> it.copy(options = listOf(FeeOption(FeeKind.TRAINING, "Advanced", 300000), FeeOption(FeeKind.BOOK, "Workbook", 15000), FeeOption(FeeKind.BOOK, "Dictionary", 8000)))
                    "G-1" -> it.copy(commonFees = false)
                    else -> it
                }
            }), "t")
        }
        return store to repo
    }

    private fun ComposeUiTest.start(repo: FeeRepository, layout: LayoutMode) {
        val display = DisplayState(File(tmp.root, "d-$layout.properties"))
        display.update { it.copy(layout = layout) }
        val vm = AppViewModel(repo)
        setContent {
            CompositionLocalProvider(LocalPlatform provides touch, LocalDisplay provides display) {
                SchoolTheme { DesktopFrame { SchoolApp(vm, display, MenuActions(), null, "1.3.0") } }
            }
        }
        waitText("Log in")
        onNode(hasSetTextAction() and hasText("Username")).performTextInput("admin")
        onNode(hasSetTextAction() and hasText("Password")).performTextInput("admin123")
        click("Log in")
        waitText("Total Students Enrolled")
    }

    @Test fun feeRatesTable() = runDesktopComposeUiTest(1500, 950) {
        val (store, repo) = seeded()
        start(repo, LayoutMode.TABLET)
        click("Settings")
        waitText("Fee rates")
        click("Fee rates")
        waitText("Fees by class")
        waitText("1 training, 2 book")
        shot("50-fee-rates-fees-for-all-and-choices")
        click("1 training, 2 book")
        waitText("KG – fee choices")
        click("Add training choice")
        shot("51-fee-choices-dialog")
        // the new row is blank → OK disabled; remove it again by cancelling
        click("Cancel")
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("KG – fee choices").fetchSemanticsNodes().isEmpty() }
        store.close()
    }

    @Test fun enrollmentWithChoicesAndCustomTerm() = runDesktopComposeUiTest(1100, 1500) {
        val (store, repo) = seeded()
        start(repo, LayoutMode.TABLET)
        click("Students")
        waitText("Student Register")
        click("+  Register student")
        waitText("Training fee")
        onNode(hasSetTextAction() and hasText("Student name *")).performTextInput("Hnin Hnin")
        // book: add the workbook; training: Advanced; term: Custom 6 installments every 2 months
        click("Workbook – 15,000", substring = true)
        click("Standard – ", substring = true)
        waitText("Advanced – 300,000")
        click("Advanced – 300,000", substring = true)
        click("Payment term")
        waitText("Custom installments")
        click("Custom installments")
        waitText("Installments")
        onNode(hasSetTextAction() and hasText("Installments")).performTextReplacement("6")
        onNode(hasSetTextAction() and hasText("Every … months")).performTextReplacement("2")
        waitText("6-Inst every 2 months: 6 payments")
        shot("52-register-choices-custom-term")
        click("Save")
        waitUntil(timeoutMillis = 5_000) { runBlocking { repo.snapshot().first().ledgers.isNotEmpty() } }
        val e = runBlocking { repo.snapshot().first() }.ledgers.single().enrollments.single()
        assertEquals(FeeChoice("Advanced", true, listOf("Workbook")), e.enrollment.choice)
        assertEquals(PaymentTerm.CUSTOM, e.enrollment.term); assertEquals(6, e.enrollment.termParts); assertEquals(2, e.enrollment.termEvery)
        // KG: 300,000 advanced + 200,000 instrument + 20,000 standard book + 15,000 workbook + 100,000 entrance
        assertEquals(635000L, e.net)
        assertEquals(6, e.lines.size)
        waitText("Training: Advanced")
        shot("53-student-with-choices")
        store.close()
    }
}
