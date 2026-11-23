@file:OptIn(ExperimentalMaterial3Api::class)

package com.school.feemanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.school.feemanager.data.AppSnapshot
import com.school.feemanager.domain.ReportType

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("dashboard", "Dashboard", Icons.Default.Dashboard),
    Tab("students", "Students", Icons.Default.People),
    Tab("payments", "Payables", Icons.Default.CalendarMonth),
    Tab("collection", "Collection", Icons.Default.Payments),
    Tab("reports", "Reports", Icons.Default.Assessment),
    Tab("settings", "Settings", Icons.Default.Settings),
)

/** Navigation actions shared by every screen (phone and Windows). */
class AppNav(val controller: NavHostController) {
    /**
     * Opens a main section. Clicking the section you are already in (e.g. Settings while on Fee rates)
     * goes back to its main page; switching sections keeps where you were in each.
     */
    fun top(route: String) {
        val current = controller.currentDestination?.route.orEmpty()
        if (current == route) return
        if (current.startsWith("$route/") && controller.popBackStack(route, inclusive = false)) return
        controller.navigate(route) {
            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = !current.startsWith("$route/")
        }
    }
    fun student(id: Long) = controller.navigate("students/$id")
    fun newStudent() = controller.navigate("students/edit/-1")
    /** Collect payment; [items] are payable keys "enrollmentId:seq" (empty = choose). */
    fun collect(studentId: Long? = null, items: List<String> = emptyList()) = controller.navigate("collection/new/${studentId ?: -1}?items=${items.joinToString(",")}")
    fun receipt(id: Long) = controller.navigate("collection/receipt/$id")
    fun report(type: ReportType) = controller.navigate("reports/${type.name}")
    fun settings(sub: String) = controller.navigate("settings/$sub")
    fun back() = controller.popBackStack()
}

/**
 * The five top-level pages. The phone uses [MobileScreens]; the Windows app supplies wide,
 * table-based layouts. Everything else (forms, student page, receipt, report view, settings) is shared.
 */
interface TopScreens {
    @Composable fun Dashboard(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav)
    @Composable fun Students(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav)
    @Composable fun Payables(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav)
    @Composable fun Collection(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav)
    @Composable fun Reports(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav)
    /** Settings → Fee rates (phone: one card per class; Windows: an editable table). */
    @Composable fun FeeRates(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav) =
        PageFrame { FeeRatesScreen(vm, snap, session, onBack = { nav.back() }) }
}

object MobileScreens : TopScreens {
    @Composable override fun Dashboard(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav) =
        DashboardScreen(snap, onStudent = nav::student, onReceipt = nav::receipt)
    @Composable override fun Students(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav) =
        StudentsScreen(snap, session, onOpen = nav::student, onAdd = nav::newStudent)
    @Composable override fun Payables(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav) =
        PayablesScreen(snap, onPay = { nav.collect(null, it) }, onStudent = nav::student)
    @Composable override fun Collection(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav) =
        CollectionScreen(vm, snap, session, onNew = { nav.collect() }, onReceipt = nav::receipt)
    @Composable override fun Reports(vm: AppViewModel, snap: AppSnapshot, session: Session, nav: AppNav) =
        ReportsScreen(onOpen = nav::report)
}

/** The app frame after login: bottom navigation on the phone; the Windows app passes its own (sidebar + menu bar). */
typealias AppShell = @Composable (vm: AppViewModel, snap: AppSnapshot, session: Session, busy: Boolean, snackbar: SnackbarHostState) -> Unit

@Composable
fun AppRoot(vm: AppViewModel, shell: AppShell? = null) {
    val session by vm.session.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    val s = session
    val data = snap
    when {
        data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        s == null -> Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad -> LoginFrame(Modifier.padding(pad)) { LoginScreen(vm, data, busy, Modifier) } }
        s.mustChangePassword -> Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { if (!LocalDesktop.current) AppTopBar("Change Password") }) { pad ->
            LoginFrame(Modifier.padding(pad)) {
                Column {
                    if (LocalDesktop.current) Text("Change Password", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp))
                    ChangePasswordForm(vm, forced = true, onDone = {})
                }
            }
        }
        else -> if (shell != null) shell(vm, data, s, busy, snackbar) else MainScaffold(vm, data, s, busy, snackbar)
    }
}

/** On Windows the login sits in a card on the school-blue background; on the phone it fills the screen. */
@Composable
private fun LoginFrame(modifier: Modifier, content: @Composable () -> Unit) {
    if (!LocalDesktop.current) { Box(modifier) { content() }; return }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 460.dp).padding(24.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(8.dp)) {
            Box(Modifier.padding(12.dp)) { content() }
        }
    }
}

@Composable
private fun LoginScreen(vm: AppViewModel, snap: AppSnapshot, busy: Boolean, modifier: Modifier) {
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    Box(if (LocalDesktop.current) modifier else modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Text(snap.settings.schoolName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("School Fee Management System", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val focus = LocalFocusManager.current
            val canLogin = !busy && user.isNotBlank() && pass.isNotEmpty()
            val doLogin = { if (canLogin) { vm.login(user, pass); pass = "" } }
            // Enter moves to the password, Enter there logs in (phone keyboard and PC keyboard)
            OutlinedTextField(user, { user = it.replace("\n", "").replace("\t", "") }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }))
            OutlinedTextField(
                pass, { pass = it.replace("\n", "").replace("\t", "") }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { doLogin() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = doLogin, enabled = canLogin, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Log in")
            }
            if (snap.users.size == 1 && snap.users[0].mustChangePassword) {
                Text("First use: log in with admin / admin123, then set a new password.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel, snap: AppSnapshot, session: Session, busy: Boolean, snackbar: SnackbarHostState) {
    val nav = rememberNavController()
    val appNav = remember(nav) { AppNav(nav) }
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                TABS.forEach { t ->
                    val selected = current == t.route || (current?.startsWith(t.route + "/") == true)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { appNav.top(t.route) },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            AppNavHost(appNav, vm, snap, session)
        }
    }
}

/** On Windows, form-style pages are kept to a readable width in the middle of the window. */
@Composable
fun PageFrame(content: @Composable () -> Unit) {
    if (!LocalDesktop.current) { content(); return }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxHeight().widthIn(max = 1100.dp)) { content() }
    }
}

@Composable
fun AppNavHost(nav: AppNav, vm: AppViewModel, snap: AppSnapshot, session: Session, screens: TopScreens = MobileScreens) {
    val c = nav.controller
    val back: () -> Unit = { c.popBackStack() }
    NavHost(c, startDestination = "dashboard") {
        composable("dashboard") { screens.Dashboard(vm, snap, session, nav) }
        composable("students") { screens.Students(vm, snap, session, nav) }
        composable("students/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
            val id = e.arguments?.getLong("id") ?: -1L
            PageFrame {
                StudentDetailScreen(
                    vm, snap, session, id, onBack = back,
                    onEdit = { c.navigate("students/edit/$id") },
                    onAddClass = { c.navigate("students/$id/class/-1") },
                    onEditClass = { eid -> c.navigate("students/$id/class/$eid") },
                    onPay = { items -> nav.collect(id, items) },
                    onReceipt = nav::receipt,
                )
            }
        }
        composable("students/edit/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
            val id = e.arguments?.getLong("id") ?: -1L
            PageFrame {
                StudentEditScreen(vm, snap, if (id < 0) null else id, onBack = back, onSaved = { newId ->
                    c.navigate("students/$newId") { popUpTo("students") }
                })
            }
        }
        composable(
            "students/{id}/class/{eid}",
            listOf(navArgument("id") { type = NavType.LongType }, navArgument("eid") { type = NavType.LongType }),
        ) { e ->
            val id = e.arguments?.getLong("id") ?: -1L
            val eid = e.arguments?.getLong("eid") ?: -1L
            PageFrame { EnrollmentEditScreen(vm, snap, id, if (eid < 0) null else eid, onBack = back, onSaved = back) }
        }

        composable("payments") { screens.Payables(vm, snap, session, nav) }
        composable("collection") { screens.Collection(vm, snap, session, nav) }
        composable(
            "collection/new/{sid}?items={items}",
            listOf(navArgument("sid") { type = NavType.LongType }, navArgument("items") { type = NavType.StringType; defaultValue = "" }),
        ) { e ->
            val sid = e.arguments?.getLong("sid") ?: -1L
            val items = e.arguments?.getString("items").orEmpty().split(",").filter { it.isNotBlank() }
            PageFrame {
                CollectPaymentScreen(vm, snap, if (sid < 0) null else sid, items, onBack = back, onSaved = { rid ->
                    c.navigate("collection/receipt/$rid") { popUpTo("collection/new/{sid}?items={items}") { inclusive = true } }
                })
            }
        }
        composable("collection/receipt/{rid}", listOf(navArgument("rid") { type = NavType.LongType })) { e ->
            PageFrame { ReceiptScreen(vm, snap, session, e.arguments?.getLong("rid") ?: -1L, onBack = back) }
        }

        composable("reports") { screens.Reports(vm, snap, session, nav) }
        composable("reports/{type}") { e ->
            val type = ReportType.entries.firstOrNull { it.name == e.arguments?.getString("type") } ?: ReportType.ENROLLMENT
            ReportViewScreen(vm, snap, type, onBack = back)
        }

        composable("settings") { PageFrame { SettingsScreen(vm, snap, session, onOpen = nav::settings) } }
        composable("settings/rates") { screens.FeeRates(vm, snap, session, nav) }
        composable("settings/school") { PageFrame { SchoolProfileScreen(vm, snap, session, onBack = back) } }
        composable("settings/users") { PageFrame { UsersScreen(vm, snap, session, onBack = back) } }
        composable("settings/data") { PageFrame { DataScreen(vm, snap, session, onBack = back) } }
        composable("settings/password") {
            PageFrame { Column { AppTopBar("Change Password", onBack = back); ChangePasswordForm(vm, forced = false, onDone = back) } }
        }
        composable("settings/audit") { PageFrame { AuditLogScreen(vm, onBack = back) } }
    }
}
