package com.example.batteryguardian

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class BatteryGuardianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Force dark theme only (no follow-system, no light mode)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}
