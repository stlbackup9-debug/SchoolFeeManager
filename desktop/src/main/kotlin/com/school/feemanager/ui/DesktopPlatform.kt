package com.school.feemanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.system.exitProcess

/** The main window, used as the owner of file dialogs. */
val LocalAwtFrame = staticCompositionLocalOf<Frame?> { null }

class LocalFileRef(val file: File) : FileRef {
    override val name: String get() = file.name
    override fun openInput(): InputStream = file.inputStream()
    override fun openOutput(): OutputStream = file.outputStream()
}

object DesktopPlatform : PlatformBridge {
    override val isDesktop: Boolean get() = true

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", true)

    /**
     * Opens the document in the default browser (Edge on Windows 11), which shows its print dialog –
     * choose a printer or "Save as PDF".
     */
    override fun printHtml(html: String, jobName: String, isReport: Boolean) {
        val dir = File(System.getProperty("java.io.tmpdir"), "SchoolFeeManager").apply { mkdirs() }
        dir.listFiles { f -> f.name.endsWith(".html") && System.currentTimeMillis() - f.lastModified() > 86_400_000 }?.forEach { it.delete() }
        val safe = jobName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(60)
        val f = File(dir, "$safe-${System.currentTimeMillis()}.html")
        val auto = "<script>window.addEventListener('load', function () { setTimeout(function () { window.print(); }, 400); });</script>"
        val withTitle = html.replaceFirst("<head>", "<head><title>${HtmlTitle.escape(jobName)}</title>")
        f.writeText(if (withTitle.contains("</body>")) withTitle.replace("</body>", "$auto</body>") else withTitle + auto, Charsets.UTF_8)
        openInSystem(f)
    }

    fun openInSystem(f: File) {
        Thread {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(f.toURI())
                else if (isWindows) ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", f.absolutePath).start()
                else ProcessBuilder("xdg-open", f.absolutePath).start()
            } catch (e: Exception) {
                System.err.println("Cannot open ${f.absolutePath}: ${e.message}")
            }
        }.apply { isDaemon = true }.start()
    }

    /** Starts the same program again (same Java runtime and arguments) and exits this one. */
    override fun restartApp() {
        try {
            val info = ProcessHandle.current().info()
            val cmd = info.command().orElse(null)
            if (cmd != null) ProcessBuilder(listOf(cmd) + info.arguments().orElse(emptyArray()).toList()).inheritIO().start()
        } catch (e: Exception) {
            System.err.println("Restart failed: ${e.message}")
        }
        exitProcess(0)
    }
}

private object HtmlTitle {
    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

private val EXTENSIONS = mapOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
    "text/csv" to "csv", "text/comma-separated-values" to "csv", "application/csv" to "csv",
    "application/pdf" to "pdf",
)

private fun showDialog(owner: Frame?, title: String, save: Boolean, suggested: String?, extensions: List<String>): File? {
    val d = FileDialog(owner, title, if (save) FileDialog.SAVE else FileDialog.LOAD)
    d.directory = LastDir.dir?.absolutePath ?: System.getProperty("user.home")
    if (save) d.file = suggested
    else if (extensions.isNotEmpty()) {
        d.file = extensions.joinToString(";") { "*.$it" }  // Windows: filter shown in the dialog
        d.setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", true) } }
    }
    d.isVisible = true
    val name = d.file ?: return null
    val dir = d.directory ?: return null
    var f = File(dir, name)
    if (save && suggested != null) {
        val ext = suggested.substringAfterLast('.', "")
        if (ext.isNotEmpty() && !f.name.endsWith(".$ext", true)) f = File(dir, "$name.$ext")
    }
    LastDir.dir = f.parentFile
    return f
}

private object LastDir { var dir: File? = null }

@Composable
fun rememberFileOpener(onResult: (FileRef) -> Unit): (Array<String>) -> Unit {
    val owner = LocalAwtFrame.current
    val callback by rememberUpdatedState(onResult)
    return remember(owner) {
        { types ->
            // "*/*" (backup restore) = no filter; otherwise only the known document types (.xlsx, .csv)
            val exts = if ("*/*" in types) emptyList() else types.mapNotNull { EXTENSIONS[it] }.distinct()
            showDialog(owner, "Open", save = false, suggested = null, extensions = exts)?.let { callback(LocalFileRef(it)) }
        }
    }
}

@Composable
fun rememberFileSaver(mimeType: String, onResult: (FileRef) -> Unit): (String) -> Unit {
    val owner = LocalAwtFrame.current
    val callback by rememberUpdatedState(onResult)
    return remember(owner, mimeType) {
        { suggested -> showDialog(owner, "Save as", save = true, suggested = suggested, extensions = emptyList())?.let { callback(LocalFileRef(it)) } }
    }
}

@Composable
fun PlatformVerticalScrollbar(state: androidx.compose.foundation.lazy.LazyListState, modifier: androidx.compose.ui.Modifier) =
    androidx.compose.foundation.VerticalScrollbar(androidx.compose.foundation.rememberScrollbarAdapter(state), modifier)

@Composable
fun PlatformHorizontalScrollbar(state: androidx.compose.foundation.ScrollState, modifier: androidx.compose.ui.Modifier) =
    androidx.compose.foundation.HorizontalScrollbar(androidx.compose.foundation.rememberScrollbarAdapter(state), modifier)
