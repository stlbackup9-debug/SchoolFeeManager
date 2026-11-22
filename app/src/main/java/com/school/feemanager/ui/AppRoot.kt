@file:OptIn(ExperimentalMaterial3Api::class)

package com.school.feemanager.ui

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
import androidx.compose.foundation.text.KeyboardOptions
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
    Tab("payments", "Payments", Icons.Default.CalendarMonth),
    Tab("collection", "Collection", Icons.Default.Payments),
    Tab("reports", "Reports", Icons.Default.Assessment),
    Tab("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun AppRoot(vm: AppViewModel) {
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
        s == null -> Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad -> LoginScreen(vm, data, busy, Modifier.padding(pad)) }
        s.mustChangePassword -> Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { AppTopBar("Change Password") }) { pad ->
            Box(Modifier.padding(pad)) { ChangePasswordForm(vm, forced = true, onDone = {}) }
        }
        else -> MainScaffold(vm, data, s, busy, snackbar)
    }
}

@Composable
private fun LoginScreen(vm: AppViewModel, snap: AppSnapshot, busy: Boolean, modifier: Modifier) {
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Text(snap.settings.schoolName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("School Fee Management System", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { vm.login(user, pass); pass = "" }, enabled = !busy && user.isNotBlank() && pass.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(48.dp)) {
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
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
            AppNavHost(nav, vm, snap, session)
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, vm: AppViewModel, snap: AppSnapshot, session: Session) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = "dashboard") {
        composable("dashboard") { DashboardScreen(snap, onStudent = { nav.navigate("students/$it") }, onReceipt = { nav.navigate("collection/receipt/$it") }) }

        composable("students") {
            StudentsScreen(snap, session, onOpen = { nav.navigate("students/$it") }, onAdd = { nav.navigate("students/edit/-1") })
        }
        composable("students/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
            val id = e.arguments?.getLong("id") ?: -1L
            StudentDetailScreen(
                vm, snap, session, id, onBack = back,
                onEdit = { nav.navigate("students/edit/$id") },
                onCollect = { seq -> nav.navigate("collection/new/$id/$seq") },
                onReceipt = { nav.navigate("collection/receipt/$it") },
            )
        }
        composable("students/edit/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
            val id = e.arguments?.getLong("id") ?: -1L
            StudentEditScreen(vm, snap, if (id < 0) null else id, onBack = back, onSaved = { newId ->
                nav.navigate("students/$newId") { popUpTo("students") }
            })
        }

        composable("payments") { PaymentScheduleScreen(snap, onCollect = { sid, seq -> nav.navigate("collection/new/$sid/$seq") }, onStudent = { nav.navigate("students/$it") }) }

        composable("collection") {
            CollectionScreen(vm, snap, session, onNew = { nav.navigate("collection/new/-1/-1") }, onReceipt = { nav.navigate("collection/receipt/$it") })
        }
        composable(
            "collection/new/{sid}/{seq}",
            listOf(navArgument("sid") { type = NavType.LongType }, navArgument("seq") { type = NavType.IntType }),
        ) { e ->
            val sid = e.arguments?.getLong("sid") ?: -1L
            val seq = e.arguments?.getInt("seq") ?: -1
            CollectPaymentScreen(vm, snap, if (sid < 0) null else sid, if (seq < 0) null else seq, onBack = back, onSaved = { rid ->
                nav.navigate("collection/receipt/$rid") { popUpTo("collection/new/{sid}/{seq}") { inclusive = true } }
            })
        }
        composable("collection/receipt/{rid}", listOf(navArgument("rid") { type = NavType.LongType })) { e ->
            ReceiptScreen(vm, snap, session, e.arguments?.getLong("rid") ?: -1L, onBack = back)
        }

        composable("reports") { ReportsScreen(onOpen = { nav.navigate("reports/${it.name}") }) }
        composable("reports/{type}") { e ->
            val type = ReportType.entries.firstOrNull { it.name == e.arguments?.getString("type") } ?: ReportType.ENROLLMENT
            ReportViewScreen(vm, snap, type, onBack = back)
        }

        composable("settings") { SettingsScreen(vm, snap, session, onOpen = { nav.navigate("settings/$it") }) }
        composable("settings/rates") { FeeRatesScreen(vm, snap, session, onBack = back) }
        composable("settings/school") { SchoolProfileScreen(vm, snap, session, onBack = back) }
        composable("settings/users") { UsersScreen(vm, snap, session, onBack = back) }
        composable("settings/data") { DataScreen(vm, snap, session, onBack = back) }
        composable("settings/password") {
            Column { AppTopBar("Change Password", onBack = back); ChangePasswordForm(vm, forced = false, onDone = back) }
        }
        composable("settings/audit") { AuditLogScreen(vm, onBack = back) }
    }
}
