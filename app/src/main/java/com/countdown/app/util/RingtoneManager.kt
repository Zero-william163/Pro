package com.countdown.app.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.countdown.app.data.CountdownData
import com.countdown.app.data.CountdownRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 闹钟铃声管理器
 *
 * 核心功能：
 * 1. 系统铃声选择（闹钟/通知/来电铃声）
 * 2. 本地音频文件选择（通过 SAF / ACTION_OPEN_DOCUMENT）
 * 3. 长期持久化（takePersistableUriPermission，跨重启/升级保持有效）
 * 4. 铃声试听（使用 USAGE_ALARM 音频属性，遵循闹钟音量）
 * 5. 铃声失效检测与自动回退（URI 无效/文件删除/权限失效时恢复默认）
 *
 * 支持的本地音频格式：MP3、WAV、OGG、FLAC（取决于系统解码器支持）
 */
object RingtoneManager {

    private const val TAG = "RingtoneManager"

    // SAF 文件选择请求码
    const val REQUEST_CODE_OPEN_AUDIO_FILE = 5001
    // 系统铃声选择器请求码
    const val REQUEST_CODE_SYSTEM_RINGTONE = 5002

    // 支持的音频 MIME 类型
    val SUPPORTED_AUDIO_MIME_TYPES = arrayOf(
        "audio/mpeg",           // MP3
        "audio/mp3",
        "audio/wav",            // WAV
        "audio/wave",
        "audio/x-wav",
        "audio/ogg",            // OGG
        "audio/x-ogg",
        "application/ogg",
        "audio/flac",           // FLAC
        "audio/x-flac",
        "audio/aac",            // AAC
        "audio/x-m4a",          // M4A
        "audio/mp4",            // MP4 audio
        "audio/*"               // 通配符，让系统选择器过滤所有音频
    )

    // ==================== 铃声选择 Intent ====================

    /**
     * 创建 SAF 文件选择 Intent（用于选择本地音频文件）
     *
     * 使用 ACTION_OPEN_DOCUMENT 而非 ACTION_GET_CONTENT，
     * 因为前者支持 takePersistableUriPermission，可跨重启保持访问权限。
     */
    fun createOpenAudioFileIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            // 请求持久化读取权限
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
    }

    /**
     * 创建系统铃声选择 Intent
     *
     * @param ringtoneType RingtoneManager.TYPE_ALARM / TYPE_NOTIFICATION / TYPE_RINGTONE
     */
    fun createSystemRingtonePickerIntent(ringtoneType: Int): Intent {
        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringtoneType)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟铃声")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            // 预选当前铃声
            val currentUri = getCurrentRingtoneUriForPicker()
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
        }
    }

    /**
     * 获取当前铃声 URI（用于铃声选择器预选）
     */
    private fun getCurrentRingtoneUriForPicker(): Uri? {
        return runBlocking(Dispatchers.IO) {
            val context = AppContextHolder.getContext()
            if (context != null) {
                val data = CountdownRepository.getInstance(context).countdownDataFlow.first()
                when (data.ringtoneType) {
                    CountdownData.RINGTYPE_DEFAULT -> null
                    CountdownData.RINGTYPE_SYSTEM -> {
                        if (data.ringtoneUri.isNotEmpty()) Uri.parse(data.ringtoneUri) else null
                    }
                    CountdownData.RINGTYPE_LOCAL_FILE -> {
                        if (data.ringtoneUri.isNotEmpty()) Uri.parse(data.ringtoneUri) else null
                    }
                    else -> null
                }
            } else null
        }
    }

    // ==================== 持久化 URI 权限 ====================

    /**
     * 对 SAF 返回的 URI 获取持久化读取权限
     *
     * 这是确保铃声在应用关闭、手机重启、应用升级后仍然可访问的关键步骤。
     * 必须在 onActivityResult / ActivityResultCallback 中立即调用。
     *
     * @return true 如果权限获取成功
     */
    fun takePersistableUriPermission(context: Context, uri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            Log.d(TAG, "Persistable URI permission taken for: $uri")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to take persistable URI permission for $uri", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error taking persistable URI permission for $uri", e)
            false
        }
    }

    /**
     * 检查是否仍持有指定 URI 的持久化权限
     */
    fun hasPersistableUriPermission(context: Context, uri: Uri): Boolean {
        return try {
            val permissions = context.contentResolver.persistedUriPermissions
            permissions.any { it.uri == uri && it.isReadPermission }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking persistable URI permission", e)
            false
        }
    }

    // ==================== 保存铃声选择 ====================

    /**
     * 保存系统铃声选择
     *
     * @param uri 系统铃声 URI（来自 RingtoneManager.ACTION_RINGTONE_PICKER）
     */
    suspend fun saveSystemRingtone(context: Context, uri: Uri?) {
        val name = getRingtoneName(context, uri) ?: "系统铃声"
        val uriStr = uri?.toString() ?: ""
        CountdownRepository.getInstance(context).saveRingtone(
            type = CountdownData.RINGTYPE_SYSTEM,
            uri = uriStr,
            name = name
        )
        Log.d(TAG, "Saved system ringtone: $name ($uriStr)")
    }

    /**
     * 保存本地音频文件选择
     *
     * @param uri 文件 URI（来自 ACTION_OPEN_DOCUMENT）
     * @return true 如果保存成功（包括获取持久化权限）
     */
    suspend fun saveLocalFileRingtone(context: Context, uri: Uri): Boolean {
        // 1. 获取持久化读取权限
        val permissionGranted = takePersistableUriPermission(context, uri)
        if (!permissionGranted) {
            Log.e(TAG, "Failed to get persistable permission for $uri")
            return false
        }

        // 2. 验证文件可读
        if (!isUriReadable(context, uri)) {
            Log.e(TAG, "URI is not readable: $uri")
            return false
        }

        // 3. 获取文件名
        val name = getFileNameFromUri(context, uri) ?: "本地音频文件"

        // 4. 保存到 DataStore
        CountdownRepository.getInstance(context).saveRingtone(
            type = CountdownData.RINGTYPE_LOCAL_FILE,
            uri = uri.toString(),
            name = name
        )
        Log.d(TAG, "Saved local file ringtone: $name (${uri})")
        return true
    }

    /**
     * 恢复默认铃声
     */
    suspend fun resetToDefault(context: Context) {
        CountdownRepository.getInstance(context).saveRingtone(
            type = CountdownData.RINGTYPE_DEFAULT,
            uri = "",
            name = "系统默认闹钟铃声"
        )
        Log.d(TAG, "Ringtone reset to default")
    }

    // ==================== 获取当前铃声 ====================

    /**
     * 获取当前铃声 URI（用于播放）
     *
     * 如果用户设置了自定义铃声且 URI 有效，返回自定义 URI。
     * 如果自定义铃声失效（文件删除/权限失效/URI 无效），自动回退到默认闹钟铃声。
     * 如果用户未设置自定义铃声，返回系统默认闹钟铃声 URI。
     *
     * @param autoFallbackIfInvalid 如果为 true，当自定义铃声失效时自动重置为默认
     * @return 铃声 URI，永远不会返回 null
     */
    fun getAlarmRingtoneUri(context: Context, autoFallbackIfInvalid: Boolean = true): Uri {
        val data = CountdownRepository.getInstance(context).getCountdownDataSync()

        return when (data.ringtoneType) {
            CountdownData.RINGTYPE_DEFAULT -> {
                getDefaultAlarmUri()
            }
            CountdownData.RINGTYPE_SYSTEM -> {
                if (data.ringtoneUri.isNotEmpty()) {
                    val uri = Uri.parse(data.ringtoneUri)
                    if (isUriReadable(context, uri)) {
                        uri
                    } else {
                        Log.w(TAG, "System ringtone URI invalid, falling back to default")
                        if (autoFallbackIfInvalid) {
                            runBlocking { resetToDefault(context) }
                        }
                        getDefaultAlarmUri()
                    }
                } else {
                    getDefaultAlarmUri()
                }
            }
            CountdownData.RINGTYPE_LOCAL_FILE -> {
                if (data.ringtoneUri.isNotEmpty()) {
                    val uri = Uri.parse(data.ringtoneUri)
                    if (isUriReadable(context, uri)) {
                        uri
                    } else {
                        Log.w(TAG, "Local file ringtone URI invalid, falling back to default")
                        if (autoFallbackIfInvalid) {
                            runBlocking { resetToDefault(context) }
                        }
                        getDefaultAlarmUri()
                    }
                } else {
                    getDefaultAlarmUri()
                }
            }
            else -> getDefaultAlarmUri()
        }
    }

    /**
     * 获取系统默认闹钟铃声 URI
     */
    fun getDefaultAlarmUri(): Uri {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: Uri.parse("content://settings/system/alarm_alert")
    }

    /**
     * 获取当前铃声显示名称
     */
    fun getCurrentRingtoneName(context: Context): String {
        val data = CountdownRepository.getInstance(context).getCountdownDataSync()
        return when (data.ringtoneType) {
            CountdownData.RINGTYPE_DEFAULT -> "系统默认闹钟铃声"
            CountdownData.RINGTYPE_SYSTEM -> {
                if (data.ringtoneUri.isNotEmpty()) {
                    getRingtoneName(context, Uri.parse(data.ringtoneUri)) ?: data.ringtoneName
                } else {
                    "系统默认闹钟铃声"
                }
            }
            CountdownData.RINGTYPE_LOCAL_FILE -> {
                if (data.ringtoneUri.isNotEmpty()) {
                    getFileNameFromUri(context, Uri.parse(data.ringtoneUri)) ?: data.ringtoneName
                } else {
                    "系统默认闹钟铃声"
                }
            }
            else -> "系统默认闹钟铃声"
        }
    }

    /**
     * 获取当前铃声类型
     */
    fun getCurrentRingtoneType(context: Context): Int {
        return CountdownRepository.getInstance(context).getCountdownDataSync().ringtoneType
    }

    // ==================== URI 验证 ====================

    /**
     * 检查 URI 是否可读（文件是否存在、权限是否有效）
     */
    fun isUriReadable(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { 
                // 尝试打开输入流，如果能打开说明文件存在且有权限
                it.available()
            }
            true
        } catch (e: Exception) {
            Log.d(TAG, "URI not readable: $uri - ${e.message}")
            false
        }
    }

    /**
     * 检查当前铃声是否有效
     */
    fun isCurrentRingtoneValid(context: Context): Boolean {
        val data = CountdownRepository.getInstance(context).getCountdownDataSync()
        return when (data.ringtoneType) {
            CountdownData.RINGTYPE_DEFAULT -> true
            CountdownData.RINGTYPE_SYSTEM,
            CountdownData.RINGTYPE_LOCAL_FILE -> {
                if (data.ringtoneUri.isEmpty()) {
                    true // 空URI时使用默认，视为有效
                } else {
                    isUriReadable(context, Uri.parse(data.ringtoneUri))
                }
            }
            else -> true
        }
    }

    // ==================== 铃声信息获取 ====================

    /**
     * 从系统 RingtoneManager 获取铃声名称
     */
    private fun getRingtoneName(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        return try {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.getTitle(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting ringtone name", e)
            null
        }
    }

    /**
     * 从文件 URI 获取文件名
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            result = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting file name from URI", e)
            }
        }
        if (result == null) {
            result = uri.lastPathSegment
        }
        return result
    }

    // ==================== 铃声试听 ====================

    private var previewMediaPlayer: MediaPlayer? = null

    /**
     * 开始试听当前铃声
     *
     * 使用 USAGE_ALARM 音频属性，遵循闹钟音量而非媒体音量。
     * 铃声循环播放直到调用 stopPreview()。
     *
     * @return true 如果开始播放成功
     */
    fun startPreview(context: Context): Boolean {
        // 先停止之前的播放
        stopPreview()

        return try {
            val uri = getAlarmRingtoneUri(context, autoFallbackIfInvalid = false)
            Log.d(TAG, "Starting preview for URI: $uri")

            previewMediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
            Log.d(TAG, "Preview started successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start preview", e)
            // 尝试使用默认铃声
            try {
                val defaultUri = getDefaultAlarmUri()
                previewMediaPlayer = MediaPlayer().apply {
                    setDataSource(context, defaultUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                Log.d(TAG, "Preview started with default ringtone")
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Default ringtone preview also failed", e2)
                false
            }
        }
    }

    /**
     * 停止试听
     */
    fun stopPreview() {
        try {
            previewMediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.reset()
                mp.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping preview", e)
        }
        previewMediaPlayer = null
        Log.d(TAG, "Preview stopped")
    }

    /**
     * 是否正在试听
     */
    fun isPreviewing(): Boolean {
        return try {
            previewMediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 试听指定 URI（不保存，仅播放）
     */
    fun previewUri(context: Context, uri: Uri): Boolean {
        stopPreview()
        return try {
            previewMediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to preview URI: $uri", e)
            false
        }
    }

    // ==================== 系统铃声列表 ====================

    /**
     * 获取系统铃声列表
     *
     * @param type RingtoneManager.TYPE_ALARM / TYPE_NOTIFICATION / TYPE_RINGTONE
     * @return 铃声条目列表（URI + 名称）
     */
    fun getSystemRingtoneList(context: Context, type: Int): List<RingtoneEntry> {
        val entries = mutableListOf<RingtoneEntry>()
        try {
            val manager = RingtoneManager(context).apply {
                setType(type)
            }
            val cursor = manager.cursor
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                    val uri = manager.getRingtoneUri(cursor.position)
                    entries.add(RingtoneEntry(uri, title))
                } while (cursor.moveToNext())
            }
            cursor?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching system ringtone list", e)
        }
        return entries
    }

    /**
     * 系统铃声条目
     */
    data class RingtoneEntry(val uri: Uri, val title: String)
}

// ==================== Application Context Holder ====================

/**
 * Application Context 持有者
 * 用于在非 Activity 场景下获取 Context
 */
object AppContextHolder {
    @Volatile
    private var appContext: Context? = null

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    fun getContext(): Context? = appContext
}
