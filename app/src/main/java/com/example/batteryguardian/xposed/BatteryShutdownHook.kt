package com.example.batteryguardian.xposed

import android.content.Context
import android.net.Uri
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class BatteryShutdownHook : IXposedHookLoadPackage {

    private var timerStartMs: Long? = null
    private var lowVoltageSinceMs: Long? = null

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android") return

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.server.BatteryService",
                lpparam.classLoader,
                "shouldShutdownLocked",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as? Context
                            ?: return

                        val settings = readSettings(context)
                        if (!settings.enabled) {
                            resetState()
                            return
                        }

                        // Try to read current battery level from BatteryService internals.
                        val batteryProps = runCatching {
                            XposedHelpers.getObjectField(param.thisObject, "mHealthInfo")
                        }.getOrNull() ?: runCatching {
                            XposedHelpers.getObjectField(param.thisObject, "mBatteryProps")
                        }.getOrNull()

                        val level = readIntField(batteryProps, "batteryLevel", "mBatteryLevel") ?: 0

                        // Absolute safety floor: if we ever see <= -20, do not block shutdown.
                        if (level <= -20) {
                            log(settings.loggingEnabled, "Safety floor reached (level=$level). Allowing shutdown.")
                            resetState()
                            return
                        }

                        // If we're above 0, reset mode-specific state. We only "hold" around 0 and below.
                        if (level > 0) {
                            resetState()
                            return
                        }

                        when (settings.parsedMode) {
                            HoldMode.PERMANENT -> {
                                if (!settings.permanentConfirmed) {
                                    log(settings.loggingEnabled, "Permanent mode not confirmed in UI. Allowing shutdown.")
                                    resetState()
                                    return
                                }
                                log(settings.loggingEnabled, "Permanent hold active. Blocking shutdown. level=$level")
                                param.result = false
                            }

                            HoldMode.TIMER -> {
                                val now = System.currentTimeMillis()
                                val start = timerStartMs ?: now.also { timerStartMs = it }
                                val elapsedMs = now - start
                                val limitMs = settings.timerMinutes.coerceIn(1, 180) * 60_000L

                                if (elapsedMs < limitMs) {
                                    log(
                                        settings.loggingEnabled,
                                        "Timer hold: blocking shutdown. level=$level elapsed=${elapsedMs / 1000}s limit=${limitMs / 1000}s"
                                    )
                                    param.result = false
                                } else {
                                    log(
                                        settings.loggingEnabled,
                                        "Timer expired: allowing shutdown. level=$level elapsed=${elapsedMs / 1000}s"
                                    )
                                    resetState()
                                }
                            }

                            HoldMode.VOLTAGE -> {
                                val voltageMv =
                                    readIntField(batteryProps, "batteryVoltage", "mBatteryVoltage")
                                        ?: runCatching { XposedHelpers.getIntField(param.thisObject, "mBatteryVoltage") }
                                            .getOrNull()

                                if (voltageMv == null) {
                                    // If we cannot read voltage, be conservative: keep holding (like original behavior),
                                    // but still respect the -20 safety floor above.
                                    log(settings.loggingEnabled, "Voltage unavailable. Blocking shutdown. level=$level")
                                    param.result = false
                                    return
                                }

                                val threshold = settings.voltageThresholdMv.coerceIn(2500, 4500)
                                val confirmMs = settings.voltageConfirmSeconds.coerceIn(0, 120) * 1000L

                                val now = System.currentTimeMillis()
                                if (voltageMv <= threshold) {
                                    val since = lowVoltageSinceMs ?: now.also { lowVoltageSinceMs = it }
                                    val lowForMs = now - since

                                    if (lowForMs >= confirmMs) {
                                        log(
                                            settings.loggingEnabled,
                                            "Voltage threshold reached: allowing shutdown. V=${voltageMv}mV <= ${threshold}mV for ${lowForMs}ms"
                                        )
                                        resetState()
                                    } else {
                                        log(
                                            settings.loggingEnabled,
                                            "Voltage low but not confirmed yet: blocking shutdown. V=${voltageMv}mV <= ${threshold}mV for ${lowForMs}ms need=${confirmMs}ms"
                                        )
                                        param.result = false
                                    }
                                } else {
                                    // Voltage above threshold: keep holding and reset confirmation timer.
                                    lowVoltageSinceMs = null
                                    log(
                                        settings.loggingEnabled,
                                        "Voltage above threshold: blocking shutdown. V=${voltageMv}mV > ${threshold}mV"
                                    )
                                    param.result = false
                                }
                            }
                        }
                    }
                }
            )

            XposedBridge.log("BatteryGuardian: hook installed for BatteryService.shouldShutdownLocked")
        } catch (t: Throwable) {
            XposedBridge.log("BatteryGuardian hook error: ${t.stackTraceToString()}")
        }
    }

    private fun resetState() {
        timerStartMs = null
        lowVoltageSinceMs = null
    }

    private fun readSettings(context: Context): HookSettings {
        val uri = Uri.parse("content://com.example.batteryguardian.settings/module")
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    HookSettings(
                        enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) == 1,
                        mode = cursor.getString(cursor.getColumnIndexOrThrow("mode")) ?: HoldMode.VOLTAGE.name,
                        voltageThresholdMv = cursor.getInt(cursor.getColumnIndexOrThrow("voltageThresholdMv")),
                        voltageConfirmSeconds = cursor.getInt(cursor.getColumnIndexOrThrow("voltageConfirmSeconds")),
                        timerMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("timerMinutes")),
                        permanentConfirmed = cursor.getInt(cursor.getColumnIndexOrThrow("permanentConfirmed")) == 1,
                        loggingEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("loggingEnabled")) == 1
                    )
                } else {
                    HookSettings()
                }
            } ?: HookSettings()
        }.getOrElse {
            XposedBridge.log("BatteryGuardian settings read error: ${it.stackTraceToString()}")
            HookSettings()
        }
    }

    private fun log(enabled: Boolean, msg: String) {
        if (enabled) XposedBridge.log("BatteryGuardian: $msg")
    }

    private fun readIntField(obj: Any?, vararg names: String): Int? {
        if (obj == null) return null
        for (n in names) {
            runCatching { return XposedHelpers.getIntField(obj, n) }.getOrNull()
        }
        return null
    }

    private data class HookSettings(
        val enabled: Boolean = false,
        val mode: String = HoldMode.VOLTAGE.name,
        val voltageThresholdMv: Int = 3300,
        val voltageConfirmSeconds: Int = 10,
        val timerMinutes: Int = 15,
        val permanentConfirmed: Boolean = false,
        val loggingEnabled: Boolean = false
    ) {
        val parsedMode: HoldMode
            get() = runCatching { HoldMode.valueOf(mode) }.getOrElse { HoldMode.VOLTAGE }
    }
}

private enum class HoldMode {
    VOLTAGE,
    TIMER,
    PERMANENT
}
