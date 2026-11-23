package com.school.feemanager

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.feemanager.ui.AndroidPlatform
import com.school.feemanager.ui.AppViewModel
import com.school.feemanager.ui.LocalPlatform
import com.school.feemanager.ui.wide.LocalDisplay
import com.school.feemanager.ui.wide.MenuActions
import com.school.feemanager.ui.wide.School
import com.school.feemanager.ui.wide.SchoolApp
import com.school.feemanager.ui.wide.SchoolTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        viewModelFactory { initializer { AppViewModel((this[APPLICATION_KEY] as FeeApp).repository) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val app = application as FeeApp
        val platform = AndroidPlatform(this)
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        setContent {
            val actions = remember { MenuActions() }
            CompositionLocalProvider(LocalPlatform provides platform, LocalDisplay provides app.display) {
                SchoolTheme {
                    // status bar: white icons on the school colour; navigation bar icons follow day / night
                    val night = School.palette.night
                    LaunchedEffect(night) {
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                            navigationBarStyle = if (night) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                        )
                    }
                    SchoolApp(vm, app.display, actions, painterResource(R.drawable.ic_launcher_foreground), version)
                }
            }
        }
    }
}
