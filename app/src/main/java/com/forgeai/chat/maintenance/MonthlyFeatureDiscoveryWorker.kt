package com.forgeai.chat.maintenance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.forgeai.chat.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Checks the public ForgeAI release feed monthly and records an update
 * suggestion. It never downloads, installs, or changes app code automatically.
 */
class MonthlyFeatureDiscoveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        return try {
            val request = Request.Builder()
                .url(RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "ForgeAI-FeatureDiscovery")
                .get()
                .build()

            val release = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return if (response.code >= 500 || response.code == 429) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
                JSONObject(response.body?.string().orEmpty())
            }

            val latestTag = release.optString("tag_name")
            val releaseUrl = release.optString("html_url")
            val publishedAt = release.optString("published_at")
            if (latestTag.isBlank() || releaseUrl.isBlank()) return Result.retry()

            val installed = BuildConfig.VERSION_NAME.removePrefix("v")
            val latest = latestTag.removePrefix("v")
            val isNewer = latest != installed
            applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_DISCOVERY, System.currentTimeMillis())
                .putString(KEY_LATEST_TAG, latestTag)
                .putString(KEY_RELEASE_URL, releaseUrl)
                .putString(KEY_PUBLISHED_AT, publishedAt)
                .putBoolean(KEY_UPDATE_AVAILABLE, isNewer)
                .apply()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val RELEASE_URL =
            "https://api.github.com/repos/jaedwards1986/ForgeAI/releases/latest"
        const val PREFS = "forgeai_maintenance"
        const val KEY_LAST_DISCOVERY = "last_feature_discovery"
        const val KEY_LATEST_TAG = "latest_release_tag"
        const val KEY_RELEASE_URL = "latest_release_url"
        const val KEY_PUBLISHED_AT = "latest_release_published_at"
        const val KEY_UPDATE_AVAILABLE = "update_available"
    }
}
