package com.natasha.local.maintenance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.natasha.local.data.AppDatabase
import java.io.File
import java.util.concurrent.TimeUnit

class MaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return try {
            val db = AppDatabase.get(applicationContext)
            val healthy = db.openHelper.readableDatabase.query("PRAGMA integrity_check").use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == "ok"
            }
            if (!healthy) {
                preferences.edit()
                    .putLong("last_check_at", System.currentTimeMillis())
                    .putBoolean("database_healthy", false)
                    .putString("last_result", "Database integrity check failed")
                    .apply()
                return Result.retry()
            }

            val filesDir = applicationContext.filesDir
            if (!filesDir.exists() && !filesDir.mkdirs()) return Result.retry()

            // Only abandoned temporary imports are eligible for cleanup. Project files,
            // conversations, memories, and model files are never touched by maintenance.
            val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(48)
            val removedStaging = applicationContext.cacheDir.listFiles()
                .orEmpty()
                .filter { it.name.startsWith("project-import-") && it.lastModified() < cutoff }
                .count { it.deleteRecursively() }

            val freeBytes = filesDir.freeSpace
            val lowStorage = freeBytes < LOW_STORAGE_BYTES
            val resultText = buildString {
                append("Database healthy")
                append(" · removed ").append(removedStaging).append(" abandoned import staging folders")
                if (lowStorage) append(" · low storage warning")
            }
            preferences.edit()
                .putLong("last_check_at", System.currentTimeMillis())
                .putBoolean("database_healthy", true)
                .putBoolean("low_storage", lowStorage)
                .putInt("staging_folders_removed", removedStaging)
                .putLong("free_bytes", freeBytes)
                .putString("last_result", resultText)
                .remove("last_error")
                .apply()

            Result.success()
        } catch (failure: Exception) {
            preferences.edit()
                .putLong("last_check_at", System.currentTimeMillis())
                .putString("last_error", failure.message ?: "Unknown maintenance error")
                .putString("last_result", "Maintenance check failed")
                .apply()
            Result.retry()
        }
    }

    companion object {
        private const val PREFERENCES = "forgeai_maintenance"
        private const val LOW_STORAGE_BYTES = 256L * 1024L * 1024L
    }
}
