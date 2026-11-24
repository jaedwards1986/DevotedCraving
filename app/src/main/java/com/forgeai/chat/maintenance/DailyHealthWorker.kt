package com.forgeai.chat.maintenance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.forgeai.chat.data.local.AppDatabase
import com.forgeai.chat.data.preferences.SettingsDataStore
import kotlinx.coroutines.flow.first

/**
 * Runs a local-only integrity/configuration check. No chat text, prompts,
 * API keys, or personal data leave the device.
 */
class DailyHealthWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val database = AppDatabase.getInstance(applicationContext)
        val integrityOk = database.openHelper.writableDatabase
            .query("PRAGMA quick_check").use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
        val settings = SettingsDataStore(applicationContext).settingsFlow.first()
        val settingsOk = settings.baseUrl.isNotBlank() &&
            settings.modelName.isNotBlank() &&
            settings.maxTokens in 64..32768 &&
            settings.temperature in 0f..2f

        val status = when {
            !integrityOk -> "database_integrity_failed"
            !settingsOk -> "settings_validation_failed"
            else -> "healthy"
        }
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .putString(KEY_STATUS, status)
            .apply()

        if (integrityOk && settingsOk) Result.success()
        else Result.failure()
    } catch (error: Exception) {
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .putString(KEY_STATUS, "check_error")
            .putString(KEY_ERROR, error.javaClass.simpleName)
            .apply()
        Result.retry()
    }

    companion object {
        const val PREFS = "forgeai_maintenance"
        const val KEY_LAST_CHECK = "last_health_check"
        const val KEY_STATUS = "health_status"
        const val KEY_ERROR = "health_error"
    }
}
