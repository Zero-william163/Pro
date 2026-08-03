package com.countdown.app.update

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 更新模块日志系统。
 *
 * 记录详细的更新检测、下载、切换日志，方便排查问题。
 * 同时输出到 Logcat 和 SharedPreferences（供应用内查看）。
 */
object UpdateLogger {

    private const val TAG = "UpdateModule"
    private const val PREFS_NAME = "update_logs"
    private const val KEY_LOGS = "logs"
    private const val MAX_LOG_ENTRIES = 100

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * 日志级别。
     */
    enum class Level(val priority: Int, val tag: String) {
        DEBUG(Log.DEBUG, "D"),
        INFO(Log.INFO, "I"),
        WARN(Log.WARN, "W"),
        ERROR(Log.ERROR, "E")
    }

    /**
     * 日志条目。
     */
    data class LogEntry(
        val timestamp: String,
        val level: Level,
        val tag: String,
        val message: String
    )

    private val logBuffer = mutableListOf<LogEntry>()
    private val lock = Any()

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String) = log(Level.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message | Exception: ${throwable.javaClass.simpleName}: ${throwable.message}"
        } else message
        log(Level.ERROR, tag, fullMessage)
    }

    private fun log(level: Level, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = LogEntry(timestamp, level, tag, message)

        // 输出到 Logcat
        Log.println(level.priority, "$TAG/$tag", "[$timestamp] $message")

        // 保存到内存缓冲
        synchronized(lock) {
            logBuffer.add(entry)
            if (logBuffer.size > MAX_LOG_ENTRIES) {
                logBuffer.removeAt(0)
            }
        }
    }

    /**
     * 获取所有日志（内存缓冲）。
     */
    fun getLogs(): List<LogEntry> = synchronized(lock) { logBuffer.toList() }

    /**
     * 获取格式化的日志文本。
     */
    fun getFormattedLogs(): String {
        return synchronized(lock) {
            logBuffer.joinToString("\n") { entry ->
                "[${entry.timestamp}] [${entry.level.tag}/${entry.tag}] ${entry.message}"
            }
        }
    }

    /**
     * 持久化日志到 SharedPreferences。
     */
    fun saveToPrefs(context: Context) {
        val formatted = getFormattedLogs()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOGS, formatted)
            .apply()
    }

    /**
     * 从 SharedPreferences 读取持久化日志。
     */
    fun loadFromPrefs(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOGS, "") ?: ""
    }

    /**
     * 清空日志。
     */
    fun clear() {
        synchronized(lock) { logBuffer.clear() }
    }

    // ===== 便捷日志方法 =====

    fun logCheckStart(installedVersion: String) {
        i("CheckUpdate", "===== 开始检查更新 | 当前版本: $installedVersion =====")
    }

    fun logSourceStart(sourceName: String, url: String) {
        i("SourceManager", "尝试更新源: $sourceName | URL: $url")
    }

    fun logSourceSuccess(sourceName: String, remoteVersion: String, latencyMs: Long) {
        i("SourceManager", "更新源成功: $sourceName | 远程版本: $remoteVersion | 耗时: ${latencyMs}ms")
    }

    fun logSourceFailed(sourceName: String, error: String, latencyMs: Long) {
        w("SourceManager", "更新源失败: $sourceName | 错误: $error | 耗时: ${latencyMs}ms")
    }

    fun logSourceSwitch(fromSource: String, toSource: String, reason: String) {
        w("SourceManager", "切换更新源: $fromSource → $toSource | 原因: $reason")
    }

    fun logSpeedTest(source: String, latencyMs: Long, success: Boolean) {
        i("SpeedTest", "测速: $source | 延迟: ${latencyMs}ms | 成功: $success")
    }

    fun logDownloadStart(url: String, sourceName: String) {
        i("Download", "开始下载 | 源: $sourceName | URL: $url")
    }

    fun logDownloadProgress(downloaded: Long, total: Long, percent: Int) {
        d("Download", "下载进度: $percent% (${downloaded}/${total} bytes)")
    }

    fun logDownloadSuccess(filePath: String, totalTimeMs: Long) {
        i("Download", "下载完成 | 路径: $filePath | 总耗时: ${totalTimeMs}ms")
    }

    fun logDownloadFailed(url: String, error: String) {
        e("Download", "下载失败 | URL: $url | 错误: $error")
    }

    fun logDownloadFallback(fromUrl: String, toUrl: String, reason: String) {
        w("Download", "下载切换: $fromUrl → $toUrl | 原因: $reason")
    }

    fun logCheckResult(result: String) {
        i("CheckUpdate", "===== 检查结果: $result =====")
    }

    fun logCacheHit(lastCheckTime: Long, hoursSinceCheck: Float) {
        d("Cache", "缓存命中 | 上次检查: ${dateFormat.format(Date(lastCheckTime))} | 已过: ${"%.1f".format(hoursSinceCheck)}小时")
    }

    fun logCacheMiss(hoursSinceCheck: Float) {
        d("Cache", "缓存未命中 | 已过: ${"%.1f".format(hoursSinceCheck)}小时 | 执行新检查")
    }

    fun logIgnoredVersion(version: String) {
        i("IgnoreVersion", "用户已忽略版本 $version，跳过提醒")
    }
}
