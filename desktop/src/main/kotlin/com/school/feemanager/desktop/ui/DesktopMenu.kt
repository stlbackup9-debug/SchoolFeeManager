package com.school.feemanager.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import com.school.feemanager.ui.wide.DisplayState
import com.school.feemanager.ui.wide.MenuActions
import com.school.feemanager.ui.wide.NAV_ITEMS
import com.school.feemanager.ui.wide.SidebarMode

/**
 * The window's menu bar. It is created together with the window – a menu bar added later is not laid out
 * by Swing until the window is resized – and its items are enabled once someone is logged in.
 */
@Composable
fun FrameWindowScope.AppMenuBar(actions: MenuActions, display: DisplayState, onOpenDataFolder: () -> Unit, onExit: () -> Unit) {
    val nav = actions.nav
    val on = nav != null
    MenuBar {
        Menu("File", mnemonic = 'F') {
            Item("Collect payment…", enabled = on, onClick = { nav?.collect() }, shortcut = KeyShortcut(Key.P, ctrl = true))
            Item("Register student…", enabled = on && actions.isAdmin, onClick = { nav?.newStudent() }, shortcut = KeyShortcut(Key.N, ctrl = true))
            Separator()
            Item("Import / export, backup & restore…", enabled = on, onClick = { nav?.top("settings"); nav?.settings("data") })
            Item("Open data folder", onClick = onOpenDataFolder)
            Separator()
            Item("Log out", enabled = on, onClick = { actions.logout() })
            Item("Exit", onClick = onExit, shortcut = KeyShortcut(Key.F4, alt = true))
        }
        Menu("View", mnemonic = 'V') {
            NAV_ITEMS.forEach { i -> Item(i.label, enabled = on, onClick = { nav?.top(i.route) }, shortcut = KeyShortcut(i.key, ctrl = true)) }
            Separator()
            Item("Back", enabled = on, onClick = { nav?.back() }, shortcut = KeyShortcut(Key.DirectionLeft, alt = true))
            Separator()
            Item("Scale & layout, colour…", onClick = { actions.showDisplay = true }, shortcut = KeyShortcut(Key.Comma, ctrl = true))
            CheckboxItem("Night mode", checked = com.school.feemanager.ui.wide.School.palette.night, onCheckedChange = { n -> display.update { it.copy(night = n, followSystem = false) } }, shortcut = KeyShortcut(Key.D, ctrl = true, shift = true))
            CheckboxItem("Compact sidebar", checked = display.settings.sidebar == SidebarMode.COMPACT, onCheckedChange = { c -> display.update { it.copy(sidebar = if (c) SidebarMode.COMPACT else SidebarMode.FULL) } })
            Item("Make everything bigger", onClick = { display.zoom(+1) }, shortcut = KeyShortcut(Key.Equals, ctrl = true))
            Item("Make everything smaller", onClick = { display.zoom(-1) }, shortcut = KeyShortcut(Key.Minus, ctrl = true))
            Item("Normal size (100%)", onClick = { display.update { it.copy(scale = 1f, textSize = 1f) } }, shortcut = KeyShortcut(Key.Zero, ctrl = true))
        }
        Menu("Help", mnemonic = 'H') {
            Item("About School Fee Manager", onClick = { actions.showAbout = true })
        }
    }
}

@Composable
fun AboutDialog(version: String, dataFolder: String, onOpenDataFolder: () -> Unit, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("School Fee Manager $version") },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Offline school fee management · all amounts in MMK")
            Text("Data folder: $dataFolder")
            Text("Backups made here can be restored on the Android app and vice versa (Settings → Data).")
            Text("Keyboard: Ctrl+1…6 pages, Ctrl+P collect payment, Ctrl+N register student, Alt+← back, Tab next field.")
        }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    dismissButton = { TextButton(onClick = { onOpenDataFolder(); onDismiss() }) { Text("Open data folder") } },
)

