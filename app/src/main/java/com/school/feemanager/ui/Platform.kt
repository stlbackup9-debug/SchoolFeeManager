package com.school.feemanager.ui

import androidx.compose.runtime.staticCompositionLocalOf
import java.io.InputStream
import java.io.OutputStream

/** A file the user picked in a system file dialog (Android: a content Uri, Windows: a file). */
interface FileRef {
    val name: String
    fun openInput(): InputStream
    fun openOutput(): OutputStream
}

/** Things the screens need from the operating system. */
interface PlatformBridge {
    /** Prints an HTML document (the user can also choose "Save as PDF"). */
    fun printHtml(html: String, jobName: String, isReport: Boolean)

    /** Restarts the app, e.g. after a backup was restored. */
    fun restartApp()

    /** True on Windows / PC (keyboard shortcuts, menu bar). */
    val isDesktop: Boolean get() = false
}

/** Opens the Scale & Layout dialog (set by the host app; null = not available). */
val LocalOpenDisplaySettings = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

val LocalPlatform = staticCompositionLocalOf<PlatformBridge> { error("PlatformBridge not provided") }

/*
 * Each platform also provides (same signatures, in AndroidPlatform.kt / DesktopPlatform.kt):
 *   @Composable fun rememberFileOpener(onResult: (FileRef) -> Unit): (mimeTypes: Array<String>) -> Unit
 *   @Composable fun rememberFileSaver(mimeType: String, onResult: (FileRef) -> Unit): (suggestedName: String) -> Unit
 *   @Composable fun PlatformVerticalScrollbar(state: LazyListState, modifier: Modifier)     (Windows: scrollbar, touch: nothing)
 *   @Composable fun PlatformHorizontalScrollbar(state: ScrollState, modifier: Modifier)
 */
