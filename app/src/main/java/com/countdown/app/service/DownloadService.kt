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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.countdown.app.MainActivity
import com.countdown.app.R
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

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "countdown_update_download"
        const val EXTRA_DOWNLOAD_URL = "download_url"
        const val EXTRA_VERSION_NAME = "version_name"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_DOWNLOAD_URL) ?: return START_NOT_STICKY
        val versionName = intent.getStringExtra(EXTRA_VERSION_NAME) ?: ""

        startForeground(NOTIFICATION_ID, createProgressNotification(0, versionName))

        serviceScope.launch {
            downloadApk(url, versionName)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private suspend fun downloadApk(url: String, versionName: String) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                showErrorNotification("下载失败: HTTP ${response.code}")
                stopSelf()
                return
            }

            val totalBytes = response.body?.contentLength() ?: -1
            val inputStream = response.body?.byteStream() ?: run {
                showErrorNotification("无法读取下载内容")
                stopSelf()
                return
            }

            val apkFile = File(externalCacheDir, "countdown_update_v$versionName.apk")
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(8192)
                var downloadedBytes = 0L
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    if (totalBytes > 0) {
                        val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                        updateProgress(progress, versionName)
                    }
                }
            }

            showCompleteNotification(apkFile)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            showErrorNotification("下载失败: ${e.message}")
            stopSelf()
        }
    }

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

    private fun createProgressNotification(progress: Int, versionName: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在下载更新 v$versionName")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateProgress(progress: Int, versionName: String) {
        notificationManager.notify(NOTIFICATION_ID, createProgressNotification(progress, versionName))
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
