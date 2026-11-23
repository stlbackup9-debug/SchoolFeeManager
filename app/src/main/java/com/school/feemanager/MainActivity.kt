package com.school.feemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.feemanager.ui.AndroidPlatform
import com.school.feemanager.ui.AppRoot
import com.school.feemanager.ui.AppViewModel
import com.school.feemanager.ui.LocalPlatform
import com.school.feemanager.ui.theme.SchoolFeeTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        viewModelFactory { initializer { AppViewModel((this[APPLICATION_KEY] as FeeApp).repository) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val platform = AndroidPlatform(this)
        setContent {
            CompositionLocalProvider(LocalPlatform provides platform) {
                SchoolFeeTheme {
                    AppRoot(vm)
                }
            }
        }
    }
}
