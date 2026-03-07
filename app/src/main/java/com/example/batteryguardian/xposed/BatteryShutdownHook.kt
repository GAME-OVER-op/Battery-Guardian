package com.example.batteryguardian.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class BatteryShutdownHook : IXposedHookLoadPackage {

    private var timerStartMs: Long? = null
    private var lowVoltageSinceMs: Long? = null
    private var shutdownRequested: Boolean = false
    private var receiverRegistered: Boolean = false

    private val testShutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_TEST_SHUTDOWN) return
            val settings = readSettings(context)
            if (!settings.enabled) {
                log(settings.loggingEnabled, "Test shutdown requested, but module is disabled. Ignoring.")
                return
            }
            if (!shutdownRequested) {
                shutdownRequested = true
                clearHoldState()
                requestShutdown(context, settings.loggingEnabled, "BatteryGuardian:Test")
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android") return

        try {
            // Register broadcast receiver as early as possible (BatteryService constructor).
            // This allows triggering a test shutdown even when the battery isn't critical yet.
            runCatching {
                XposedHelpers.findAndHookConstructor(
                    "com.android.server.BatteryService",
                    lpparam.classLoader,
                    Context::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val ctx = param.args.getOrNull(0) as? Context ?: return
                            ensureReceiverRegistered(ctx)
                        }
                    }
                )
            }

            XposedHelpers.findAndHookMethod(
                "com.android.server.BatteryService",
                lpparam.classLoader,
                "shouldShutdownLocked",
                object : XC_MethodHook() {

                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val context = XposedHelpers.getObjectField(param.thisObject, "mContext") as? Context
                            ?: return

                        ensureReceiverRegistered(context)

                        val settings = readSettings(context)
                        if (!settings.enabled) {
                            resetAllState()
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
                            resetAllState()
                            return
                        }

                        // If we're above 0, reset mode-specific state. We only "hold" around 0 and below.
                        if (level > 0) {
                            resetAllState()
                            return
                        }

                        when (settings.parsedMode) {
                            HoldMode.PERMANENT -> {
                                if (!settings.permanentConfirmed) {
                                    log(settings.loggingEnabled, "Permanent mode not confirmed in UI. Allowing shutdown.")
                                    resetAllState()
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
                                    // Some ROMs only attempt shutdown once. If we previously blocked it,
                                    // simply "allowing" may never trigger a shutdown again.
                                    // Therefore, explicitly request shutdown once when the condition is met.
                                    if (!shutdownRequested) {
                                        shutdownRequested = true
                                        clearHoldState()
                                        requestShutdown(context, settings.loggingEnabled, "BatteryGuardian:Timer")
                                    }
                                    param.result = true
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

                                val threshold = settings.voltageThresholdMv.coerceAtLeast(0)
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
                                        if (!shutdownRequested) {
                                            shutdownRequested = true
                                            clearHoldState()
                                            requestShutdown(context, settings.loggingEnabled, "BatteryGuardian:Voltage")
                                        }
                                        param.result = true
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
        clearHoldState()
        shutdownRequested = false
    }

    private fun clearHoldState() {
        timerStartMs = null
        lowVoltageSinceMs = null
    }

    private fun resetAllState() {
        clearHoldState()
        shutdownRequested = false
    }

    private fun ensureReceiverRegistered(context: Context) {
        if (receiverRegistered) return
        runCatching {
            val filter = IntentFilter(ACTION_TEST_SHUTDOWN)

            // Android 13+ requires specifying whether a dynamically registered receiver is exported.
            // We WANT exported here because the request is triggered from the companion app.
            val exportedFlag = runCatching {
                Context::class.java.getField("RECEIVER_EXPORTED").getInt(null)
            }.getOrDefault(0)

            val m = runCatching {
                // Context.registerReceiver(BroadcastReceiver, IntentFilter, int)
                context.javaClass.getMethod("registerReceiver", BroadcastReceiver::class.java, IntentFilter::class.java, Int::class.javaPrimitiveType)
            }.getOrNull()

            if (m != null) {
                m.invoke(context, testShutdownReceiver, filter, exportedFlag)
            } else {
                // Pre-Android 13
                context.registerReceiver(testShutdownReceiver, filter)
            }

            receiverRegistered = true
            XposedBridge.log("BatteryGuardian: test-shutdown receiver registered (exportedFlag=$exportedFlag)")
        }.onFailure {
            XposedBridge.log("BatteryGuardian: receiver register failed: ${it.stackTraceToString()}")
        }
    }

    private fun requestShutdown(context: Context, logging: Boolean, reason: String) {
        runCatching {
            log(logging, "Requesting shutdown (reason=$reason)")
            // PowerManager.shutdown(...) is hidden in the public SDK, so we call the system power service via reflection.
            // 1) Prefer IPowerManager.shutdown(...) (system_server has the permission).
            runCatching {
                val sm = Class.forName("android.os.ServiceManager")
                val getService = sm.getMethod("getService", String::class.java)
                val binder = getService.invoke(null, "power") as android.os.IBinder

                val stub = Class.forName("android.os.IPowerManager\$Stub")
                val asInterface = stub.getMethod("asInterface", android.os.IBinder::class.java)
                val iPowerManager = asInterface.invoke(null, binder)

                val m = iPowerManager.javaClass.getMethod(
                    "shutdown",
                    Boolean::class.javaPrimitiveType,
                    String::class.java,
                    Boolean::class.javaPrimitiveType
                )
                m.invoke(iPowerManager, false, reason, false)
            }.recoverCatching {
                // 2) Fallback: reflect PowerManager.shutdown(...)
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val m = pm.javaClass.getMethod(
                    "shutdown",
                    Boolean::class.javaPrimitiveType,
                    String::class.java,
                    Boolean::class.javaPrimitiveType
                )
                m.invoke(pm, false, reason, false)
            }.getOrThrow()
        }.onFailure {
            XposedBridge.log("BatteryGuardian: shutdown request failed: ${it.stackTraceToString()}")
        }
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

    private companion object {
        const val ACTION_TEST_SHUTDOWN = "com.example.batteryguardian.ACTION_TEST_SHUTDOWN"
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
