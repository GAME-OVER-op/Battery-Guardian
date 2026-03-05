package com.example.batteryguardian.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class SettingsProvider : ContentProvider() {

    private lateinit var repository: SettingsRepository

    override fun onCreate(): Boolean {
        context?.let { repository = SettingsRepository(it) }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val settings = repository.read()
        return MatrixCursor(COLUMNS).apply {
            addRow(
                arrayOf(
                    if (settings.enabled) 1 else 0,
                    settings.mode.name,
                    settings.voltageThresholdMv,
                    settings.voltageConfirmSeconds,
                    settings.timerMinutes,
                    if (settings.permanentConfirmed) 1 else 0,
                    if (settings.loggingEnabled) 1 else 0
                )
            )
        }
    }

    override fun getType(uri: Uri): String =
        "vnd.android.cursor.item/vnd.com.example.batteryguardian.settings"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun shutdown() = Unit

    companion object {
        private val COLUMNS = arrayOf(
            "enabled",
            "mode",
            "voltageThresholdMv",
            "voltageConfirmSeconds",
            "timerMinutes",
            "permanentConfirmed",
            "loggingEnabled"
        )
    }
}
