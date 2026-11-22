package com.school.feemanager

import android.app.Application
import com.school.feemanager.data.AppDatabase
import com.school.feemanager.data.FeeRepository

/** Simple manual dependency container (no DI framework needed for a single-module app). */
class FeeApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: FeeRepository by lazy { FeeRepository(this, database) }
}
