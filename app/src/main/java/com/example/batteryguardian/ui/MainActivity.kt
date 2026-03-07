package com.example.batteryguardian.ui

import android.os.BatteryManager
import android.os.Bundle
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.batteryguardian.R
import com.example.batteryguardian.data.HoldMode
import com.example.batteryguardian.data.ModuleSettings
import com.example.batteryguardian.data.SettingsRepository
import com.example.batteryguardian.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = SettingsRepository(this)

        if (showDisclaimerIfNeeded()) return
        initAfterDisclaimer()
    }

    override fun onResume() {
        super.onResume()
        bindBatteryInfo()
    }

    private fun initAfterDisclaimer() {
        setupUi()
        bindBatteryInfo()
        loadSettings()
    }

    private fun showDisclaimerIfNeeded(): Boolean {
        val prefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)) return false

        val content = LayoutInflater.from(this).inflate(R.layout.dialog_disclaimer, null)
        val agree = content.findViewById<android.widget.CheckBox>(R.id.disclaimerAgree)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.disclaimer_title))
            .setView(content)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.disclaimer_accept), null)
            .setNegativeButton(getString(R.string.disclaimer_decline)) { _, _ ->
                finishAffinity()
            }
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = false

            agree.setOnCheckedChangeListener { _, isChecked ->
                positive.isEnabled = isChecked
            }

            positive.setOnClickListener {
                if (!agree.isChecked) return@setOnClickListener
                prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, true).apply()
                dialog.dismiss()
                initAfterDisclaimer()
            }
        }

        dialog.show()
        return true
    }

    private fun setupUi() = with(binding) {

        testShutdownButton.setOnClickListener {
            showTestShutdownDialog()
        }

        modeGroup.setOnCheckedChangeListener { _, _ ->
            updateModeVisibility()
        }

        saveButton.setOnClickListener {
            val mode = selectedMode()

            // Validate permanent confirmation
            val permanentOk = mode != HoldMode.PERMANENT || permanentConfirm.isChecked
            if (!permanentOk) {
                infoText.text = getString(R.string.permanent_confirm_required)
                return@setOnClickListener
            }

            val settings = ModuleSettings(
                enabled = switchEnabled.isChecked,
                mode = mode,
                voltageThresholdMv = voltageInput.text?.toString()?.toIntOrNull() ?: 3300,
                voltageConfirmSeconds = confirmInput.text?.toString()?.toIntOrNull() ?: 10,
                timerMinutes = timerInput.text?.toString()?.toIntOrNull() ?: 15,
                permanentConfirmed = permanentConfirm.isChecked,
                loggingEnabled = switchLogging.isChecked
            )
            repository.save(settings)
            infoText.text = getString(R.string.saved_message)
            updateModeVisibility()
        }
    }

    private fun showTestShutdownDialog() {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_test_shutdown, null)
        val agree = content.findViewById<android.widget.CheckBox>(R.id.testShutdownAgree)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.test_shutdown_title))
            .setView(content)
            .setCancelable(true)
            .setPositiveButton(getString(R.string.test_shutdown_confirm), null)
            .setNegativeButton(getString(R.string.test_shutdown_cancel)) { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = false
            agree.setOnCheckedChangeListener { _, isChecked ->
                positive.isEnabled = isChecked
            }
            positive.setOnClickListener {
                if (!agree.isChecked) return@setOnClickListener
                // Send broadcast to system_server (receiver registered by the LSPosed module)
                sendBroadcast(Intent(ACTION_TEST_SHUTDOWN).setPackage("android"))
                Toast.makeText(this, getString(R.string.test_shutdown_sent), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun loadSettings() {
        val settings = repository.read()
        binding.switchEnabled.isChecked = settings.enabled
        binding.switchLogging.isChecked = settings.loggingEnabled

        when (settings.mode) {
            HoldMode.VOLTAGE -> binding.modeVoltage.isChecked = true
            HoldMode.TIMER -> binding.modeTimer.isChecked = true
            HoldMode.PERMANENT -> binding.modePermanent.isChecked = true
        }

        binding.voltageInput.setText(settings.voltageThresholdMv.toString())
        binding.confirmInput.setText(settings.voltageConfirmSeconds.toString())
        binding.timerInput.setText(settings.timerMinutes.toString())
        binding.permanentConfirm.isChecked = settings.permanentConfirmed

        updateModeVisibility()
    }

    private fun updateModeVisibility() = with(binding) {
        val mode = selectedMode()

        val showVoltage = mode == HoldMode.VOLTAGE
        voltageLayout.visibility = if (showVoltage) View.VISIBLE else View.GONE
        confirmLayout.visibility = if (showVoltage) View.VISIBLE else View.GONE

        val showTimer = mode == HoldMode.TIMER
        timerLayout.visibility = if (showTimer) View.VISIBLE else View.GONE

        val showPermanent = mode == HoldMode.PERMANENT
        permanentWarning.visibility = if (showPermanent) View.VISIBLE else View.GONE
        permanentConfirm.visibility = if (showPermanent) View.VISIBLE else View.GONE

        // If leaving permanent mode, don't require confirmation to save.
        if (!showPermanent) {
            permanentConfirm.isChecked = false
        }
    }

    private fun selectedMode(): HoldMode = when (binding.modeGroup.checkedRadioButtonId) {
        R.id.modeTimer -> HoldMode.TIMER
        R.id.modePermanent -> HoldMode.PERMANENT
        else -> HoldMode.VOLTAGE
    }

    private fun bindBatteryInfo() {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val intent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val voltageText = if (voltageMv > 0) " • ${voltageMv}mV" else ""

        binding.currentBatteryValue.text = getString(R.string.current_battery_value, level, voltageText)
    }

    private companion object {
        const val PREFS_UI = "battery_guardian_ui"
        const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"
        const val ACTION_TEST_SHUTDOWN = "com.example.batteryguardian.ACTION_TEST_SHUTDOWN"
    }

}
