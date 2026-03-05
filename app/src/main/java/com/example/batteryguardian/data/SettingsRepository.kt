package com.example.batteryguardian.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(ModuleSettings.PREFS, Context.MODE_PRIVATE)

    fun read(): ModuleSettings {
        val mode = runCatching {
            HoldMode.valueOf(prefs.getString(ModuleSettings.KEY_MODE, HoldMode.VOLTAGE.name)!!)
        }.getOrElse { HoldMode.VOLTAGE }

        return ModuleSettings(
            enabled = prefs.getBoolean(ModuleSettings.KEY_ENABLED, false),
            mode = mode,
            voltageThresholdMv = prefs.getInt(ModuleSettings.KEY_VOLTAGE_THRESHOLD_MV, 3300),
            voltageConfirmSeconds = prefs.getInt(ModuleSettings.KEY_VOLTAGE_CONFIRM_SECONDS, 10),
            timerMinutes = prefs.getInt(ModuleSettings.KEY_TIMER_MINUTES, 15),
            permanentConfirmed = prefs.getBoolean(ModuleSettings.KEY_PERMANENT_CONFIRMED, false),
            loggingEnabled = prefs.getBoolean(ModuleSettings.KEY_LOGGING_ENABLED, false)
        )
    }

    fun save(settings: ModuleSettings) {
        prefs.edit()
            .putBoolean(ModuleSettings.KEY_ENABLED, settings.enabled)
            .putString(ModuleSettings.KEY_MODE, settings.mode.name)
            .putInt(ModuleSettings.KEY_VOLTAGE_THRESHOLD_MV, settings.voltageThresholdMv.coerceIn(2500, 4500))
            .putInt(ModuleSettings.KEY_VOLTAGE_CONFIRM_SECONDS, settings.voltageConfirmSeconds.coerceIn(0, 120))
            .putInt(ModuleSettings.KEY_TIMER_MINUTES, settings.timerMinutes.coerceIn(1, 180))
            .putBoolean(ModuleSettings.KEY_PERMANENT_CONFIRMED, settings.permanentConfirmed)
            .putBoolean(ModuleSettings.KEY_LOGGING_ENABLED, settings.loggingEnabled)
            .apply()
    }
}
