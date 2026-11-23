package com.school.feemanager.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuBar
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.school.feemanager.data.AppSnapshot
import com.school.feemanager.ui.AppNav
import com.school.feemanager.ui.AppNavHost
import com.school.feemanager.ui.AppViewModel
import com.school.feemanager.ui.Session

private data class NavItem(val route: String, val label: String, val icon: ImageVector, val key: Key)

private val ITEMS = listOf(
    NavItem("dashboard", "Dashboard", Icons.Default.Dashboard, Key.One),
    NavItem("students", "Students", Icons.Default.People, Key.Two),
    NavItem("payments", "Payments", Icons.Default.CalendarMonth, Key.Three),
    NavItem("collection", "Collection", Icons.Default.Payments, Key.Four),
    NavItem("reports", "Reports", Icons.Default.Assessment, Key.Five),
    NavItem("settings", "Settings", Icons.Default.Settings, Key.Six),
)

/** What the menu bar can do; filled in by the shell after login (the menu bar itself exists from the start). */
class MenuActions {
    var nav by mutableStateOf<AppNav?>(null)
    var isAdmin by mutableStateOf(false)
    var logout: () -> Unit = {}
    var showAbout by mutableStateOf(false)
    var showDisplay by mutableStateOf(false)
}

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
            ITEMS.forEach { i -> Item(i.label, enabled = on, onClick = { nav?.top(i.route) }, shortcut = KeyShortcut(i.key, ctrl = true)) }
            Separator()
            Item("Back", enabled = on, onClick = { nav?.back() }, shortcut = KeyShortcut(Key.DirectionLeft, alt = true))
            Separator()
            Item("Scale & layout, colour…", onClick = { actions.showDisplay = true }, shortcut = KeyShortcut(Key.Comma, ctrl = true))
            CheckboxItem("Night mode", checked = display.settings.night, onCheckedChange = { n -> display.update { it.copy(night = n) } }, shortcut = KeyShortcut(Key.D, ctrl = true, shift = true))
            CheckboxItem("Compact sidebar", checked = display.settings.compactSidebar, onCheckedChange = { c -> display.update { it.copy(compactSidebar = c) } })
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

/** Windows frame after login: school sidebar on the left, the page on the right. */
@Composable
fun DesktopShell(
    actions: MenuActions?,
    vm: AppViewModel, snap: AppSnapshot, session: Session, busy: Boolean, snackbar: SnackbarHostState,
    logo: Painter?, version: String,
) {
    val controller = rememberNavController()
    val nav = remember(controller) { AppNav(controller) }
    val entry by controller.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    if (actions != null) DisposableEffect(nav, session.isAdmin) {
        actions.nav = nav; actions.isAdmin = session.isAdmin; actions.logout = vm::logout
        onDispose { actions.nav = null; actions.isAdmin = false }
    }

    Row(Modifier.fillMaxSize()) {
        Sidebar(snap, session, route, logo, version, onSelect = { nav.top(it) }, onLogout = vm::logout, onDisplay = { actions?.showDisplay = true })
        Box(Modifier.weight(1f).fillMaxHeight().background(School.Background)) {
            Column(Modifier.fillMaxSize()) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = School.Gold)
                AppNavHost(nav, vm, snap, session, DesktopScreens)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun Sidebar(snap: AppSnapshot, session: Session, route: String, logo: Painter?, version: String, onSelect: (String) -> Unit, onLogout: () -> Unit, onDisplay: () -> Unit) {
    val display = LocalDisplay.current
    val compact = display?.settings?.compactSidebar == true
    val on = School.OnSidebar
    Column(Modifier.width(if (compact) 84.dp else 240.dp).fillMaxHeight().background(School.Sidebar)) {
        // school name + logo
        Row(Modifier.fillMaxWidth().padding(horizontal = if (compact) 18.dp else 22.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            if (logo != null) Image(logo, contentDescription = snap.settings.schoolName, modifier = Modifier.size(48.dp))
            if (!compact) {
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(snap.settings.schoolName, color = on, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                    Text("School Fee Manager", color = on.copy(alpha = 0.75f), fontSize = 14.sp)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = if (compact) 14.dp else 22.dp).background(School.Gold.copy(alpha = 0.85f)))
        Spacer(Modifier.height(18.dp))
        ITEMS.forEach { item ->
            val selected = route == item.route || route.startsWith(item.route + "/")
            val bg = if (selected) School.SidebarSelected else Color.Transparent
            val fg = if (selected) School.SidebarSelectedText else on
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth().height(54.dp)
                    .background(bg, RoundedCornerShape(10.dp)).clickable { onSelect(item.route) }.padding(horizontal = if (compact) 0.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
            ) {
                Icon(item.icon, contentDescription = item.label, tint = if (selected) School.SidebarSelectedText else School.Gold, modifier = Modifier.size(22.dp))
                if (!compact) {
                    Spacer(Modifier.width(16.dp))
                    Text(item.label, color = fg, fontSize = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // Scale & Layout + night mode
        val night = display?.settings?.night == true
        val nightButton: @Composable () -> Unit = {
            if (display != null) Box(
                Modifier.size(44.dp).background(on.copy(alpha = 0.1f), RoundedCornerShape(10.dp)).clickable { display.update { it.copy(night = !night) } },
                contentAlignment = Alignment.Center,
            ) { Icon(if (night) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = if (night) "Day mode" else "Night mode", tint = School.Gold, modifier = Modifier.size(20.dp)) }
        }
        if (compact) Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(44.dp).background(on.copy(alpha = 0.1f), RoundedCornerShape(10.dp)).clickable(onClick = onDisplay), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DisplaySettings, contentDescription = "Scale & Layout", tint = School.Gold, modifier = Modifier.size(20.dp))
            }
            nightButton()
        } else Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(44.dp).background(on.copy(alpha = 0.1f), RoundedCornerShape(10.dp)).clickable(onClick = onDisplay).padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.DisplaySettings, contentDescription = null, tint = School.Gold, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(7.dp)); Text("Scale & Layout", color = on, fontSize = 14.sp, maxLines = 1, softWrap = false)
            }
            nightButton()
        }
        HorizontalDivider(color = on.copy(alpha = 0.15f))
        if (compact) Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out", tint = on, modifier = Modifier.size(22.dp).clickable(onClick = onLogout))
        } else Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
            Text("${session.display} · ${session.role.label}", color = on, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("Log out", color = on, fontSize = 15.sp, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable(onClick = onLogout))
            Spacer(Modifier.height(8.dp))
            Text("Version $version", color = on.copy(alpha = 0.55f), fontSize = 12.sp)
        }
    }
}
