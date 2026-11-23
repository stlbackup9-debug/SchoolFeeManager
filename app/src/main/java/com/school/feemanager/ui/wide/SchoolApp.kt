package com.school.feemanager.ui.wide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.school.feemanager.ui.AppRoot
import com.school.feemanager.ui.AppViewModel
import com.school.feemanager.ui.LocalDesktop
import com.school.feemanager.ui.LocalOpenDisplaySettings

/** Smallest screen that gets the sidebar + tables layout (a 7-inch tablet or larger, or a big phone in landscape). */
val WIDE_MIN_WIDTH = 600.dp
val WIDE_MIN_HEIGHT = 480.dp
/** Below this width the tablet layout is not used even when chosen in Scale & Layout. */
val TABLET_FORCED_MIN_WIDTH = 480.dp

/**
 * The whole app (Android phone, Android tablet and Windows). It chooses the layout from the screen size –
 * phones: bottom navigation and card lists; tablets and PCs: school sidebar and wide tables – unless the user
 * fixed it in Scale & Layout. Rotating a tablet or resizing the window switches layout at once.
 */
@Composable
fun SchoolApp(vm: AppViewModel, display: DisplayState, actions: MenuActions, logo: Painter?, version: String, alwaysWide: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = alwaysWide || when (display.settings.layout) {
            LayoutMode.PHONE -> false
            // forced tablet layout still needs room for the sidebar and a table (e.g. a phone turned sideways)
            LayoutMode.TABLET -> maxWidth >= TABLET_FORCED_MIN_WIDTH
            LayoutMode.AUTO -> maxWidth >= WIDE_MIN_WIDTH && maxHeight >= WIDE_MIN_HEIGHT
        }
        CompositionLocalProvider(LocalDesktop provides wide, LocalOpenDisplaySettings provides { actions.showDisplay = true }) {
            // Wide layout fills the screen edge to edge; keep it clear of the status / navigation bars (no insets on Windows).
            Box(if (wide) Modifier.fillMaxSize().background(School.Sidebar).windowInsetsPadding(WindowInsets.safeDrawing) else Modifier.fillMaxSize()) {
                AppRoot(vm, shell = if (wide) { v, snap, session, busy, snackbar -> WideShell(actions, v, snap, session, busy, snackbar, logo, version) } else null)
            }
            if (actions.showDisplay) DisplaySettingsDialog(display) { actions.showDisplay = false }
        }
    }
}
