package com.countdown.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    // GitHub repo info - will be configured at runtime
    private const val DEFAULT_OWNER = "Zero-william163"
    private const val DEFAULT_REPO = "Pro"
    private const val GITHUB_API_URL = "https://api.github.com/repos"

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        val releaseNotes: String,
        val isNewer: Boolean
    )

    suspend fun checkUpdate(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val currentVersionCode = getCurrentVersionCode(context)
            val currentVersionName = getCurrentVersionName(context)

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("$GITHUB_API_URL/$DEFAULT_OWNER/$DEFAULT_REPO/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .header("Authorization", "token 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")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Network error: ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "").removePrefix("v")
            val releaseNotes = json.optString("body", "")

            // Find APK asset
            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            if (apkUrl.isEmpty()) {
                return@withContext Result.failure(Exception("No APK found in release"))
            }

            // Parse version code from tag (e.g., "1.0.0" -> 1000000 rough compare)
            val remoteVersionCode = parseVersionCode(tagName)
            val isNewer = remoteVersionCode > currentVersionCode ||
                    (remoteVersionCode == currentVersionCode && tagName != currentVersionName)

            Result.success(
                UpdateInfo(
                    versionName = tagName,
                    versionCode = remoteVersionCode,
                    downloadUrl = apkUrl,
                    releaseNotes = releaseNotes,
                    isNewer = isNewer
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            Result.failure(e)
        }
    }

    private fun getCurrentVersionCode(context: Context): Int {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun parseVersionCode(versionName: String): Int {
        val parts = versionName.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return major * 10000 + minor * 100 + patch
    }
}
