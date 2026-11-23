package com.school.feemanager.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.school.feemanager.data.FeeRepository
import com.school.feemanager.data.JdbcFeeStore
import com.school.feemanager.ui.AppRoot
import com.school.feemanager.ui.AppViewModel
import com.school.feemanager.ui.DesktopPlatform
import com.school.feemanager.ui.LocalAwtFrame
import com.school.feemanager.ui.LocalPlatform
import com.school.feemanager.desktop.ui.AboutDialog
import com.school.feemanager.desktop.ui.AppMenuBar
import com.school.feemanager.desktop.ui.DesktopShell
import com.school.feemanager.desktop.ui.DisplaySettingsDialog
import com.school.feemanager.desktop.ui.DisplayState
import com.school.feemanager.desktop.ui.LocalDisplay
import com.school.feemanager.desktop.ui.MenuActions
import com.school.feemanager.desktop.ui.SchoolDesktopTheme
import com.school.feemanager.ui.LocalDesktop
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JOptionPane
import kotlin.system.exitProcess

const val APP_NAME = "School Fee Manager"
const val APP_VERSION = "1.1.0"

/** Where the database lives: %APPDATA%\SchoolFeeManager on Windows, ~/.schoolfeemanager elsewhere. Not inside the program folder, so updates and uninstalling keep the data. */
fun dataDir(): File {
    System.getProperty("schoolfees.dataDir")?.let { return File(it) }
    val appData = System.getenv("APPDATA")
    return if (!appData.isNullOrBlank()) File(appData, "SchoolFeeManager") else File(System.getProperty("user.home"), ".schoolfeemanager")
}

fun main() {
    // Menus and message boxes in the Windows style (the screens themselves are drawn by the app).
    try { javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName()) } catch (_: Exception) {}
    val repo = try {
        FeeRepository(JdbcFeeStore(File(dataDir(), "school_fees.db")))
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(null, "Cannot open the database in ${dataDir()}:\n${e.message}", APP_NAME, JOptionPane.ERROR_MESSAGE)
        exitProcess(1)
    }
    val icon = DesktopPlatform::class.java.getResourceAsStream("/icon.png")?.use { BitmapPainter(ImageIO.read(it).toComposeImageBitmap()) }
    application {
        val vm = remember { AppViewModel(repo) }
        val actions = remember { MenuActions() }
        val display = remember { DisplayState(File(dataDir(), "display.properties")) }
        val openData = { DesktopPlatform.openInSystem(dataDir()) }
        Window(
            onCloseRequest = ::exitApplication,
            title = APP_NAME,
            icon = icon,
            state = rememberWindowState(size = DpSize(1400.dp, 900.dp)),
        ) {
            window.minimumSize = java.awt.Dimension(1100, 680)
            AppMenuBar(actions, display, onOpenDataFolder = openData, onExit = ::exitApplication)
            CompositionLocalProvider(LocalPlatform provides DesktopPlatform, LocalAwtFrame provides window, LocalDesktop provides true, LocalDisplay provides display) {
                SchoolDesktopTheme {
                    DesktopFrame {
                        AppRoot(vm) { v, snap, session, busy, snackbar ->
                            DesktopShell(actions, v, snap, session, busy, snackbar, icon, APP_VERSION)
                        }
                        if (actions.showAbout) AboutDialog(APP_VERSION, dataDir().absolutePath, openData) { actions.showAbout = false }
                        if (actions.showDisplay) DisplaySettingsDialog(display) { actions.showDisplay = false }
                    }
                }
            }
        }
    }
}

/** The app's own lifecycle / ViewModel store owner (always resumed while the window is open). */
private class AppOwner : LifecycleOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
}

/** Provides the lifecycle / ViewModel owners the shared screens expect, and Windows-style Tab / Shift+Tab between fields. */
@Composable
fun DesktopFrame(content: @Composable () -> Unit) {
    val owner = remember { AppOwner() }
    val focus = LocalFocusManager.current
    CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalViewModelStoreOwner provides owner) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onPreviewKeyEvent { e ->
                if (e.key == Key.Tab && e.type == KeyEventType.KeyDown && !e.isCtrlPressed && !e.isAltPressed) {
                    focus.moveFocus(if (e.isShiftPressed) FocusDirection.Previous else FocusDirection.Next); true
                } else false
            },
        ) { content() }
    }
}
