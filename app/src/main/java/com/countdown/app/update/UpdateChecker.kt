package com.countdown.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用更新检查器（商业级多源架构）。
 *
 * 功能：
 * - 多更新源检测（GitHub → jsDelivr → Gitee），自动故障转移
 * - 6 小时缓存策略，避免频繁请求
 * - 自动检测 vs 手动检测，行为不同
 * - 忽略版本功能
 * - 详细日志记录
 * - 全面的异常处理，永不崩溃
 *
 * 使用方式：
 *   // 自动检测（有缓存，静默）
 *   val result = UpdateChecker.checkUpdateAuto(context)
 *
 *   // 手动检测（忽略缓存，提示结果）
 *   val result = UpdateChecker.checkUpdateManual(context)
 *
 *   // 忽略版本
 *   UpdateChecker.ignoreVersion(context, "1.2.0")
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private val sourceManager = UpdateSourceManager()

    // ===== 兼容旧代码的数据模型（保留向后兼容） =====

    data class InstalledVersion(
        val versionName: String,
        val versionCode: Long,
        val semanticVersion: SemanticVersion?
    )

    data class RemoteVersion(
        val tagName: String,
        val releaseName: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val publishedAt: String,
        val semanticVersion: SemanticVersion?
    )

    /** 旧版结果类型（兼容） */
    sealed class UpdateResult {
        data class UpdateAvailable(
            val remoteVersion: RemoteVersion,
            val installedVersion: InstalledVersion,
            val updateInfo: UpdateInfo? = null,
            val sourceName: String = "Unknown"
        ) : UpdateResult()

        data class UpToDate(val installedVersion: InstalledVersion) : UpdateResult()
        data class LocalNewer(
            val installedVersion: InstalledVersion,
            val remoteVersion: RemoteVersion
        ) : UpdateResult()
    }

    // ===== 公共 API =====

    /**
     * 自动检测更新（每次启动都做真实网络检测，不使用缓存）。
     *
     * 适用场景：应用启动、回到前台。
     * 行为：
     * - 每次都发起真实网络请求，确保获取最新版本信息
     * - 静默检测，不弹窗
     * - 有新版本时返回 UpdateAvailable（由 UI 决定是否显示 Banner）
     * - 已是最新版时静默返回 UpToDate
     * - 被忽略的版本不返回 UpdateAvailable
     *
     * @param context 应用上下文
     * @return UpdateCheckResult
     */
    suspend fun checkUpdateAuto(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val prefs = UpdatePreferences.getInstance(context)
        val installedVersion = getInstalledVersion(context)

        UpdateLogger.logCheckStart(installedVersion.versionName)

        // 清除旧缓存，确保每次都做真实检测
        prefs.clearCache()

        // 执行实际检查
        val result = performCheck(installedVersion, context)

        // 更新最后检查时间
        prefs.updateLastCheckTime()

        // 检查忽略版本
        if (result is UpdateCheckResult.UpdateAvailable) {
            if (prefs.isVersionIgnored(result.updateInfo.versionName)) {
                UpdateLogger.logIgnoredVersion(result.updateInfo.versionName)
                return@withContext UpdateCheckResult.UpToDate(
                    installedVersion.versionName,
                    result.updateInfo.versionName,
                    result.sourceName
                )
            }
        }

        UpdateLogger.logCheckResult(result::class.simpleName ?: "Unknown")
        UpdateLogger.saveToPrefs(context)
        result
    }

    /**
     * 手动检测更新（忽略缓存）。
     *
     * 适用场景：用户点击"检查更新"按钮。
     * 行为：
     * - 忽略缓存，立即重新检测
     * - 清除忽略版本记录（用户主动检查说明想要更新）
     * - 返回详细结果供 UI 提示
     *
     * @param context 应用上下文
     * @return Result<UpdateResult>（兼容旧 API）
     */
    suspend fun checkUpdate(context: Context): Result<UpdateResult> = withContext(Dispatchers.IO) {
        val installedVersion = getInstalledVersion(context)
        UpdateLogger.logCheckStart(installedVersion.versionName)
        UpdateLogger.i(TAG, "手动检测更新（忽略缓存）")

        val checkResult = performCheck(installedVersion, context)

        // 更新最后检查时间
        UpdatePreferences.getInstance(context).updateLastCheckTime()

        // 转换为旧版 Result 格式（兼容现有 UI 代码）
        val result = when (checkResult) {
            is UpdateCheckResult.UpdateAvailable -> {
                val remote = RemoteVersion(
                    tagName = checkResult.updateInfo.versionName,
                    releaseName = checkResult.updateInfo.versionTag,
                    downloadUrl = checkResult.updateInfo.getPrimaryDownloadUrl() ?: "",
                    releaseNotes = checkResult.updateInfo.releaseNotes,
                    publishedAt = checkResult.updateInfo.publishedAt,
                    semanticVersion = SemanticVersion.parse(checkResult.updateInfo.versionName)
                )
                Result.success(UpdateResult.UpdateAvailable(
                    remoteVersion = remote,
                    installedVersion = installedVersion,
                    updateInfo = checkResult.updateInfo,
                    sourceName = checkResult.sourceName
                ))
            }
            is UpdateCheckResult.UpToDate -> {
                Result.success(UpdateResult.UpToDate(installedVersion))
            }
            is UpdateCheckResult.LocalNewer -> {
                val remote = RemoteVersion(
                    tagName = checkResult.remoteVersionName,
                    releaseName = "",
                    downloadUrl = "",
                    releaseNotes = "",
                    publishedAt = "",
                    semanticVersion = SemanticVersion.parse(checkResult.remoteVersionName)
                )
                Result.success(UpdateResult.LocalNewer(installedVersion, remote))
            }
            is UpdateCheckResult.Error -> {
                UpdateLogger.e(TAG, "所有更新源失败: ${checkResult.message}")
                UpdateLogger.saveToPrefs(context)
                Result.failure(Exception(checkResult.message))
            }
        }

        UpdateLogger.logCheckResult(result::class.simpleName ?: "Unknown")
        UpdateLogger.saveToPrefs(context)
        result
    }

    // ===== 忽略版本 API =====

    /**
     * 忽略指定版本。
     */
    fun ignoreVersion(context: Context, version: String) {
        UpdatePreferences.getInstance(context).ignoreVersion(version)
        UpdateLogger.i(TAG, "用户忽略版本: $version")
    }

    /**
     * 检查版本是否被忽略。
     */
    fun isVersionIgnored(context: Context, version: String): Boolean {
        return UpdatePreferences.getInstance(context).isVersionIgnored(version)
    }

    /**
     * 清除忽略版本记录。
     */
    fun clearIgnoredVersion(context: Context) {
        UpdatePreferences.getInstance(context).clearIgnoredVersion()
        UpdateLogger.i(TAG, "清除忽略版本记录")
    }

    // ===== 获取多源下载地址 =====

    /**
     * 获取下载地址列表（用于 DownloadService 多源下载）。
     *
     * 如果有缓存的更新信息，返回所有下载地址。
     * 否则返回空列表。
     */
    fun getDownloadUrls(context: Context): List<DownloadSource> {
        val prefs = UpdatePreferences.getInstance(context)
        val cachedUrls = prefs.getCachedDownloadUrls()
        if (cachedUrls.isNotEmpty()) {
            return cachedUrls.mapIndexed { index, url ->
                DownloadSource("Source${index + 1}", url, if (index == 0) "international" else "domestic")
            }
        }
        return emptyList()
    }

    // ===== 内部方法 =====

    /**
     * 从缓存重建完整的 UpdateInfo（包含下载地址）。
     *
     * 缓存中存储了版本名、下载地址等信息，
     * 此方法将它们重新组装为 UpdateInfo 对象。
     */
    private fun buildCachedUpdateInfo(prefs: UpdatePreferences, versionName: String): UpdateInfo {
        val cachedUrls = prefs.getCachedDownloadUrls()
        val downloadSources = cachedUrls.mapIndexed { index, url ->
            DownloadSource("Source${index + 1}", url, if (index == 0) "domestic" else "international")
        }
        return UpdateInfo(
            versionName = versionName,
            versionCode = 0,
            versionTag = "v$versionName",
            releaseNotes = "",
            publishedAt = "",
            apkSize = 0,
            sha256 = null,
            downloadUrls = downloadSources
        )
    }

    /**
     * 执行实际的更新检查（从多源获取版本信息）。
     */
    private fun performCheck(
        installed: InstalledVersion,
        context: Context
    ): UpdateCheckResult {
        val fetchResult = sourceManager.fetchUpdateInfo()

        if (fetchResult == null) {
            return UpdateCheckResult.Error(
                message = "暂时无法检查更新，请稍后重试",
                errors = emptyList()
            )
        }

        val (updateInfo, sourceName) = fetchResult
        return compareVersions(installed, updateInfo, sourceName)
    }

    /**
     * 比较本地版本与远程版本。
     */
    private fun compareVersions(
        installed: InstalledVersion,
        remoteVersionName: String,
        sourceName: String
    ): UpdateCheckResult {
        val installedSemVer = installed.semanticVersion
        val remoteSemVer = SemanticVersion.parse(remoteVersionName)

        if (installedSemVer != null && remoteSemVer != null) {
            val comparison = remoteSemVer.compareTo(installedSemVer)
            return when {
                comparison > 0 -> {
                    // 远程版本更高，有更新可用
                    // 但需要 UpdateInfo 才能返回完整信息
                    // 这里返回一个简单的 UpdateAvailable，由调用方补充
                    UpdateCheckResult.UpdateAvailable(
                        updateInfo = UpdateInfo(
                            versionName = remoteVersionName,
                            versionCode = 0,
                            versionTag = "v$remoteVersionName",
                            releaseNotes = "",
                            publishedAt = "",
                            apkSize = 0,
                            sha256 = null,
                            downloadUrls = emptyList()
                        ),
                        installedVersionName = installed.versionName,
                        sourceName = sourceName
                    )
                }
                comparison == 0 -> UpdateCheckResult.UpToDate(
                    installed.versionName, remoteVersionName, sourceName
                )
                else -> UpdateCheckResult.LocalNewer(
                    installed.versionName, remoteVersionName, sourceName
                )
            }
        }

        // 语义化版本解析失败，回退到字符串比较
        UpdateLogger.w(TAG, "语义化版本解析失败，回退到字符串比较")
        return when {
            remoteVersionName == installed.versionName -> UpdateCheckResult.UpToDate(
                installed.versionName, remoteVersionName, sourceName
            )
            else -> UpdateCheckResult.UpdateAvailable(
                updateInfo = UpdateInfo(
                    versionName = remoteVersionName,
                    versionCode = 0,
                    versionTag = "v$remoteVersionName",
                    releaseNotes = "",
                    publishedAt = "",
                    apkSize = 0,
                    sha256 = null,
                    downloadUrls = emptyList()
                ),
                installedVersionName = installed.versionName,
                sourceName = sourceName
            )
        }
    }

    /**
     * 比较版本（带完整 UpdateInfo）。
     */
    private fun compareVersions(
        installed: InstalledVersion,
        updateInfo: UpdateInfo,
        sourceName: String
    ): UpdateCheckResult {
        val installedSemVer = installed.semanticVersion
        val remoteSemVer = SemanticVersion.parse(updateInfo.versionName)

        if (installedSemVer != null && remoteSemVer != null) {
            val comparison = remoteSemVer.compareTo(installedSemVer)
            return when {
                comparison > 0 -> UpdateCheckResult.UpdateAvailable(
                    updateInfo = updateInfo,
                    installedVersionName = installed.versionName,
                    sourceName = sourceName
                )
                comparison == 0 -> UpdateCheckResult.UpToDate(
                    installed.versionName, updateInfo.versionName, sourceName
                )
                else -> UpdateCheckResult.LocalNewer(
                    installed.versionName, updateInfo.versionName, sourceName
                )
            }
        }

        // 回退到字符串比较
        return when {
            updateInfo.versionName == installed.versionName -> UpdateCheckResult.UpToDate(
                installed.versionName, updateInfo.versionName, sourceName
            )
            else -> UpdateCheckResult.UpdateAvailable(
                updateInfo = updateInfo,
                installedVersionName = installed.versionName,
                sourceName = sourceName
            )
        }
    }

    /**
     * 通过 PackageManager 读取当前安装版本。
     */
    private fun getInstalledVersion(context: Context): InstalledVersion {
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            UpdateLogger.e(TAG, "无法读取当前版本信息", e)
            return InstalledVersion("0.0.0", 0L, SemanticVersion(0, 0, 0))
        }

        val versionName = packageInfo.versionName ?: "0.0.0"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val semanticVersion = SemanticVersion.parse(versionName)

        UpdateLogger.i(TAG, "当前安装版本: versionName=$versionName, versionCode=$versionCode, semantic=$semanticVersion")

        return InstalledVersion(versionName, versionCode, semanticVersion)
    }

    /**
     * 获取更新源管理器（供外部使用）。
     */
    fun getSourceManager(): UpdateSourceManager = sourceManager
}
