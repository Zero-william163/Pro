package com.countdown.app.update

import android.content.Context
import android.content.SharedPreferences

/**
 * 更新模块偏好设置。
 *
 * 管理：
 * - 上次检查时间（用于 6 小时缓存策略）
 * - 忽略的版本号
 * - 缓存的更新信息（避免重复请求）
 */
class UpdatePreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_IGNORED_VERSION = "ignored_version"
        private const val KEY_CACHED_VERSION_NAME = "cached_version_name"
        private const val KEY_CACHED_VERSION_CODE = "cached_version_code"
        private const val KEY_CACHED_RELEASE_NOTES = "cached_release_notes"
        private const val KEY_CACHED_DOWNLOAD_URLS = "cached_download_urls"
        private const val KEY_CACHED_PUBLISHED_AT = "cached_published_at"

        // 缓存有效时间：6 小时
        const val CACHE_DURATION_MS = 6 * 60 * 60 * 1000L

        @Volatile
        private var instance: UpdatePreferences? = null

        fun getInstance(context: Context): UpdatePreferences {
            return instance ?: synchronized(this) {
                instance ?: UpdatePreferences(context.applicationContext).also { instance = it }
            }
        }
    }

    // ===== 上次检查时间 =====

    /**
     * 获取上次检查时间戳。
     */
    fun getLastCheckTime(): Long = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)

    /**
     * 更新最后检查时间为当前时间。
     */
    fun updateLastCheckTime() {
        prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()
    }

    /**
     * 检查是否需要更新（距上次检查是否超过缓存时间）。
     */
    fun shouldCheckUpdate(): Boolean {
        val lastCheck = getLastCheckTime()
        if (lastCheck == 0L) return true
        val elapsed = System.currentTimeMillis() - lastCheck
        return elapsed >= CACHE_DURATION_MS
    }

    /**
     * 获取距上次检查的小时数。
     */
    fun hoursSinceLastCheck(): Float {
        val lastCheck = getLastCheckTime()
        if (lastCheck == 0L) return Float.MAX_VALUE
        return (System.currentTimeMillis() - lastCheck) / (1000f * 60 * 60)
    }

    // ===== 忽略版本 =====

    /**
     * 获取被忽略的版本号。
     */
    fun getIgnoredVersion(): String? = prefs.getString(KEY_IGNORED_VERSION, null)

    /**
     * 忽略指定版本。
     */
    fun ignoreVersion(version: String) {
        prefs.edit().putString(KEY_IGNORED_VERSION, version).apply()
    }

    /**
     * 检查指定版本是否被忽略。
     */
    fun isVersionIgnored(version: String): Boolean {
        val ignored = getIgnoredVersion() ?: return false
        return ignored == version
    }

    /**
     * 清除忽略版本记录。
     */
    fun clearIgnoredVersion() {
        prefs.edit().remove(KEY_IGNORED_VERSION).apply()
    }

    // ===== 缓存更新信息 =====

    /**
     * 缓存更新信息。
     */
    fun cacheUpdateInfo(info: UpdateInfo) {
        prefs.edit().apply {
            putString(KEY_CACHED_VERSION_NAME, info.versionName)
            putInt(KEY_CACHED_VERSION_CODE, info.versionCode)
            putString(KEY_CACHED_RELEASE_NOTES, info.releaseNotes)
            putString(KEY_CACHED_DOWNLOAD_URLS, info.getAllDownloadUrls().joinToString("\n"))
            putString(KEY_CACHED_PUBLISHED_AT, info.publishedAt)
        }.apply()
    }

    /**
     * 获取缓存的下载地址列表。
     */
    fun getCachedDownloadUrls(): List<String> {
        val urls = prefs.getString(KEY_CACHED_DOWNLOAD_URLS, "") ?: ""
        return if (urls.isBlank()) emptyList() else urls.split("\n")
    }

    /**
     * 获取缓存的版本名。
     */
    fun getCachedVersionName(): String? = prefs.getString(KEY_CACHED_VERSION_NAME, null)

    /**
     * 清除缓存。
     */
    fun clearCache() {
        prefs.edit().apply {
            remove(KEY_CACHED_VERSION_NAME)
            remove(KEY_CACHED_VERSION_CODE)
            remove(KEY_CACHED_RELEASE_NOTES)
            remove(KEY_CACHED_DOWNLOAD_URLS)
            remove(KEY_CACHED_PUBLISHED_AT)
        }.apply()
    }
}
