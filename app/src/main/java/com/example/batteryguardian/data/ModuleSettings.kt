package com.example.batteryguardian.data

enum class HoldMode {
    VOLTAGE,
    TIMER,
    PERMANENT
}

data class ModuleSettings(
    val enabled: Boolean = false,
    val mode: HoldMode = HoldMode.VOLTAGE,
    // Voltage mode
    val voltageThresholdMv: Int = 3300,
    val voltageConfirmSeconds: Int = 10,
    // Timer mode
    val timerMinutes: Int = 15,
    // Permanent mode safety confirmation (UI-gated, module still obeys it)
    val permanentConfirmed: Boolean = false,
    val loggingEnabled: Boolean = false
) {
    companion object {
        const val PREFS = "module_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_MODE = "mode"
        const val KEY_VOLTAGE_THRESHOLD_MV = "voltage_threshold_mv"
        const val KEY_VOLTAGE_CONFIRM_SECONDS = "voltage_confirm_seconds"
        const val KEY_TIMER_MINUTES = "timer_minutes"
        const val KEY_PERMANENT_CONFIRMED = "permanent_confirmed"
        const val KEY_LOGGING_ENABLED = "logging_enabled"
    }
}
