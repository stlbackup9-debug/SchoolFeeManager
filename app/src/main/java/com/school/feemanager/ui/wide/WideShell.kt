package com.school.feemanager.ui.wide

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.school.feemanager.data.AppSnapshot
import com.school.feemanager.ui.AppNav
import com.school.feemanager.ui.AppNavHost
import com.school.feemanager.ui.AppViewModel
import com.school.feemanager.ui.Session

data class NavItem(val route: String, val label: String, val icon: ImageVector, val key: Key)

val NAV_ITEMS = listOf(
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

/** Tablet / Windows frame after login: school sidebar on the left, the page on the right. */
@Composable
fun WideShell(
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

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val mode = LocalDisplay.current?.settings?.sidebar ?: SidebarMode.AUTO
    val compact = when (mode) { SidebarMode.COMPACT -> true; SidebarMode.FULL -> false; SidebarMode.AUTO -> maxWidth < 1000.dp }
    Row(Modifier.fillMaxSize()) {
        Sidebar(snap, session, route, logo, version, compact, onSelect = { nav.top(it) }, onLogout = vm::logout, onDisplay = { actions?.showDisplay = true })
        Box(Modifier.weight(1f).fillMaxHeight().background(School.Background)) {
            Column(Modifier.fillMaxSize()) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = School.Gold)
                AppNavHost(nav, vm, snap, session, DesktopScreens)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        }
    }
    }
}

@Composable
private fun Sidebar(snap: AppSnapshot, session: Session, route: String, logo: Painter?, version: String, compact: Boolean, onSelect: (String) -> Unit, onLogout: () -> Unit, onDisplay: () -> Unit) {
    val display = LocalDisplay.current
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
        Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
        // the list scrolls when the screen is short (phone in landscape, large scale)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        NAV_ITEMS.forEach { item ->
            val selected = route == item.route || route.startsWith(item.route + "/")
            val bg = if (selected) School.SidebarSelected else Color.Transparent
            val fg = if (selected) School.SidebarSelectedText else on
            if (compact) Column(
                Modifier.padding(horizontal = 8.dp, vertical = 3.dp).fillMaxWidth().height(60.dp)
                    .background(bg, RoundedCornerShape(10.dp)).clickable { onSelect(item.route) },
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(item.icon, contentDescription = null, tint = if (selected) School.SidebarSelectedText else School.Gold, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(3.dp))
                Text(item.label, color = fg, fontSize = 11.sp, maxLines = 1, softWrap = false, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            } else Row(
                Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth().height(54.dp)
                    .background(bg, RoundedCornerShape(10.dp)).clickable { onSelect(item.route) }.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, contentDescription = item.label, tint = if (selected) School.SidebarSelectedText else School.Gold, modifier = Modifier.size(22.dp))
                if (!compact) {
                    Spacer(Modifier.width(16.dp))
                    Text(item.label, color = fg, fontSize = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        }
        // Scale & Layout + night mode
        val night = School.palette.night
        val nightButton: @Composable () -> Unit = {
            if (display != null) Box(
                Modifier.size(44.dp).background(on.copy(alpha = 0.1f), RoundedCornerShape(10.dp)).clickable { display.update { it.copy(night = !night, followSystem = false) } },
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
