package com.countdown.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.countdown.app.update.UpdateLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * APK 下载服务（商业级多源下载）。
 *
 * 功能：
 * - 支持多个下载源，自动测速选择最快的
 * - 下载失败自动切换到下一个源
 * - 前台通知显示下载进度
 * - 下载完成自动触发安装
 * - 全面的异常处理，永不崩溃
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "countdown_update_download"

        const val EXTRA_DOWNLOAD_URL = "download_url"
        const val EXTRA_DOWNLOAD_URLS = "download_urls"
        const val EXTRA_VERSION_NAME = "version_name"

        // 下载重试次数（降低以避免在死源上浪费时间）
        private const val MAX_RETRIES = 2

        // 测速超时（秒）
        private const val SPEED_TEST_TIMEOUT = 10L

        // 下载超时
        private const val CONNECT_TIMEOUT = 60L
        private const val READ_TIMEOUT = 300L
        private const val WRITE_TIMEOUT = 60L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val versionName = intent?.getStringExtra(EXTRA_VERSION_NAME) ?: ""

        val urls = intent?.getStringArrayExtra(EXTRA_DOWNLOAD_URLS)?.toList()
        val singleUrl = intent?.getStringExtra(EXTRA_DOWNLOAD_URL)

        val downloadUrls = when {
            urls != null && urls.isNotEmpty() -> urls
            singleUrl != null && singleUrl.isNotBlank() -> listOf(singleUrl)
            else -> {
                UpdateLogger.e(TAG, "未提供下载地址")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, createProgressNotification(0, versionName, "准备下载…"))

        serviceScope.launch {
            downloadWithMultiSource(downloadUrls, versionName)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * 多源下载：测速 → 选择最快 → 下载 → 失败则切换源。
     */
    private suspend fun downloadWithMultiSource(urls: List<String>, versionName: String) {
        UpdateLogger.i(TAG, "开始多源下载 | 版本: $versionName | 源数量: ${urls.size}")
        urls.forEachIndexed { index, url ->
            UpdateLogger.i(TAG, "  源${index + 1}: $url")
        }

        // Step 1: 测速，选择可用的源并排序
        val sortedUrls = speedTestAndSort(urls)
        UpdateLogger.i(TAG, "测速完成，优先级排序:")
        sortedUrls.forEachIndexed { index, url ->
            UpdateLogger.i(TAG, "  ${index + 1}. $url")
        }

        if (sortedUrls.isEmpty()) {
            UpdateLogger.e(TAG, "所有源测速均失败，使用原始顺序尝试下载")
        }

        // 如果测速全部失败，使用原始 URL 列表作为 fallback
        val urlsToTry = if (sortedUrls.isNotEmpty()) sortedUrls else urls

        // Step 2: 依次尝试下载
        for ((index, url) in urlsToTry.withIndex()) {
            val sourceName = "源${index + 1}"
            UpdateLogger.logDownloadStart(url, sourceName)

            val success = tryDownload(url, versionName, sourceName)
            if (success) {
                return
            }

            // 下载失败，切换到下一个源
            if (index < urlsToTry.size - 1) {
                UpdateLogger.logDownloadFallback(url, urlsToTry[index + 1], "下载失败")
                updateProgressNotification(0, versionName, "切换下载源…")
            }
        }

        // 所有源都失败
        UpdateLogger.e(TAG, "所有下载源均失败")
        showErrorNotification("所有下载源均失败，请检查网络后重试")
        stopSelf()
    }

    /**
     * 测速并排序下载地址。
     *
     * 使用 GET + Range: bytes=0-0 请求（比 HEAD 更可靠，部分服务器不支持 HEAD）。
     * 只返回测速成功的源，按延迟排序。
     * 如果全部失败，返回空列表（调用方会 fallback 到原始顺序）。
     */
    private fun speedTestAndSort(urls: List<String>): List<String> {
        val client = OkHttpClient.Builder()
            .connectTimeout(SPEED_TEST_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(SPEED_TEST_TIMEOUT, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val results = urls.map { url ->
            val startTime = System.currentTimeMillis()
            try {
                // 使用 GET + Range 代替 HEAD，兼容性更好
                val request = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-0")
                    .header("Accept", "application/octet-stream, application/vnd.android.package-archive, */*")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val latency = System.currentTimeMillis() - startTime
                // 200 或 206 都算成功
                val success = response.isSuccessful || response.code == 206
                response.close()
                UpdateLogger.logSpeedTest(url, latency, success)
                Triple(url, latency, success)
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                UpdateLogger.logSpeedTest(url, latency, false)
                UpdateLogger.w(TAG, "测速失败: $url | ${e.message}")
                Triple(url, latency, false)
            }
        }

        // 只返回成功的源，按延迟排序
        val successList = results.filter { it.third }
            .sortedBy { it.second }
            .map { it.first }

        return successList
    }

    /**
     * 尝试从单个 URL 下载 APK。
     *
     * @return true 成功，false 失败
     */
    private fun tryDownload(url: String, versionName: String, sourceName: String): Boolean {
        var retryCount = 0

        while (retryCount < MAX_RETRIES) {
            try {
                if (retryCount > 0) {
                    UpdateLogger.i(TAG, "重试下载 ($retryCount/$MAX_RETRIES) | 源: $sourceName")
                    updateProgressNotification(0, versionName, "重试中($retryCount/$MAX_RETRIES)…")
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                    .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .retryOnConnectionFailure(true)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/octet-stream, application/vnd.android.package-archive, */*")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    UpdateLogger.e(TAG, "HTTP ${response.code} | 源: $sourceName")
                    response.close()
                    retryCount++
                    continue
                }

                val totalBytes = response.body?.contentLength() ?: -1
                val inputStream = response.body?.byteStream()
                if (inputStream == null) {
                    UpdateLogger.e(TAG, "无法读取下载内容 | 源: $sourceName")
                    response.close()
                    retryCount++
                    continue
                }

                // 清理旧的 APK 文件
                val cacheDir = externalCacheDir ?: cacheDir
                val apkFile = File(cacheDir, "countdown_update_v$versionName.apk")

                // 断点续传：如果文件已存在且大小匹配，跳过下载
                if (apkFile.exists() && totalBytes > 0 && apkFile.length() == totalBytes) {
                    UpdateLogger.i(TAG, "文件已完整缓存，跳过下载 | 源: $sourceName")
                    response.close()
                    showCompleteNotification(apkFile)
                    UpdateLogger.logDownloadSuccess(apkFile.absolutePath, 0)
                    stopSelf()
                    return true
                }

                // 如果文件存在但大小不匹配，删除旧文件
                if (apkFile.exists() && totalBytes > 0 && apkFile.length() != totalBytes) {
                    UpdateLogger.i(TAG, "清理不完整的缓存文件 | 源: $sourceName | 缓存: ${apkFile.length()}, 预期: $totalBytes")
                    apkFile.delete()
                }

                updateProgressNotification(0, versionName, "正在下载($sourceName)…")
                val startTime = System.currentTimeMillis()

                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloadedBytes = 0L
                    var bytesRead: Int
                    var lastProgressUpdate = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 500 || progress == 100) {
                                updateProgressNotification(progress, versionName, "$progress%")
                                UpdateLogger.logDownloadProgress(downloadedBytes, totalBytes, progress)
                                lastProgressUpdate = now
                            }
                        } else {
                            // 未知总大小时，显示已下载大小
                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 1000) {
                                val mb = downloadedBytes / 1024 / 1024
                                updateProgressNotification(0, versionName, "已下载 ${mb}MB ($sourceName)")
                                lastProgressUpdate = now
                            }
                        }
                    }
                }

                response.close()

                // 验证文件大小
                if (totalBytes > 0 && apkFile.length() != totalBytes) {
                    UpdateLogger.e(TAG, "文件大小不匹配 | 源: $sourceName | 预期: $totalBytes, 实际: ${apkFile.length()}")
                    apkFile.delete()
                    retryCount++
                    continue
                }

                // 验证文件非空
                if (apkFile.length() == 0L) {
                    UpdateLogger.e(TAG, "下载文件为空 | 源: $sourceName")
                    apkFile.delete()
                    retryCount++
                    continue
                }

                val totalTime = System.currentTimeMillis() - startTime
                UpdateLogger.logDownloadSuccess(apkFile.absolutePath, totalTime)
                showCompleteNotification(apkFile)
                stopSelf()
                return true

            } catch (e: Exception) {
                val errorMsg = e.message ?: e.javaClass.simpleName
                UpdateLogger.logDownloadFailed(url, errorMsg)
                UpdateLogger.e(TAG, "下载异常 | 源: $sourceName | 错误: $errorMsg")

                // 区分超时和其他错误
                when {
                    errorMsg.contains("timeout", ignoreCase = true) -> {
                        UpdateLogger.w(TAG, "连接超时，切换到下一个源 | 源: $sourceName")
                        return false // 超时直接切换源，不重试
                    }
                    errorMsg.contains("connect", ignoreCase = true) -> {
                        UpdateLogger.w(TAG, "连接失败，切换到下一个源 | 源: $sourceName")
                        return false // 连接失败直接切换源
                    }
                    else -> {
                        retryCount++
                        if (retryCount >= MAX_RETRIES) {
                            return false
                        }
                    }
                }
            }
        }

        return false
    }

    // ===== 通知相关 =====

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "应用更新下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示应用更新下载进度"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createProgressNotification(progress: Int, versionName: String, contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在下载更新 v$versionName")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0 && contentText.contains("准备"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateProgressNotification(progress: Int, versionName: String, contentText: String) {
        notificationManager.notify(NOTIFICATION_ID, createProgressNotification(progress, versionName, contentText))
    }

    private fun showCompleteNotification(apkFile: File) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                FileProvider.getUriForFile(this@DownloadService, "${packageName}.fileprovider", apkFile),
                "application/vnd.android.package-archive"
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("下载完成")
            .setContentText("点击安装更新")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showErrorNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("下载失败")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }
}
