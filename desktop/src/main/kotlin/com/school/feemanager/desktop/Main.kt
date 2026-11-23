package com.school.feemanager.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.school.feemanager.ui.theme.SchoolFeeTheme
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
    val repo = try {
        FeeRepository(JdbcFeeStore(File(dataDir(), "school_fees.db")))
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(null, "Cannot open the database in ${dataDir()}:\n${e.message}", APP_NAME, JOptionPane.ERROR_MESSAGE)
        exitProcess(1)
    }
    val icon = DesktopPlatform::class.java.getResourceAsStream("/icon.png")?.use { BitmapPainter(ImageIO.read(it).toComposeImageBitmap()) }
    application {
        val vm = remember { AppViewModel(repo) }
        Window(
            onCloseRequest = ::exitApplication,
            title = "$APP_NAME $APP_VERSION",
            icon = icon,
            state = rememberWindowState(size = DpSize(1180.dp, 860.dp)),
        ) {
            window.minimumSize = java.awt.Dimension(420, 560)
            CompositionLocalProvider(LocalPlatform provides DesktopPlatform, LocalAwtFrame provides window) {
                SchoolFeeTheme { DesktopFrame { AppRoot(vm) } }
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

/** The screens are designed for a phone/tablet width; on a wide window they are centred. */
@Composable
fun DesktopFrame(content: @Composable () -> Unit) {
    val owner = remember { AppOwner() }
    CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalViewModelStoreOwner provides owner) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxHeight().widthIn(max = 1000.dp).background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}
