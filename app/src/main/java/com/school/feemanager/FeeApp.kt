package com.school.feemanager

import android.app.Application
import com.school.feemanager.data.AppDatabase
import com.school.feemanager.data.FeeRepository
import com.school.feemanager.data.RoomFeeStore
import com.school.feemanager.ui.wide.DisplaySettings
import com.school.feemanager.ui.wide.DisplayState

/** Simple manual dependency container (no DI framework needed for a single-module app). */
class FeeApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: FeeRepository by lazy { FeeRepository(RoomFeeStore(this, database)) }

    /** Scale & Layout, colour, night mode (this device only; Mode follows the phone's dark mode by default). */
    val display: DisplayState by lazy { DisplayState(java.io.File(filesDir, "display.properties"), DisplaySettings(followSystem = true)) }
}
