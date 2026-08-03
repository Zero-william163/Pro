package com.countdown.app.update

/**
 * 统一更新信息模型。
 *
 * 无论从哪个更新源获取，都解析为此统一格式，
 * 确保所有源返回的版本信息完全一致。
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val versionTag: String,
    val releaseNotes: String,
    val publishedAt: String,
    val apkSize: Long,
    val sha256: String?,
    val downloadUrls: List<DownloadSource>
) {
    /**
     * 获取首选下载地址（第一个可用的）
     */
    fun getPrimaryDownloadUrl(): String? = downloadUrls.firstOrNull()?.url

    /**
     * 获取所有下载地址列表
     */
    fun getAllDownloadUrls(): List<String> = downloadUrls.map { it.url }
}

/**
 * 下载源信息。
 *
 * @param name 源名称，如 "GitHub"、"GitHub Proxy"、"Gitee"
 * @param url 下载地址
 * @param region 区域，"international" 或 "domestic" 或 "cdn"
 */
data class DownloadSource(
    val name: String,
    val url: String,
    val region: String
)

/**
 * 更新检测结果。
 */
sealed class UpdateCheckResult {
    /**
     * 发现新版本。
     */
    data class UpdateAvailable(
        val updateInfo: UpdateInfo,
        val installedVersionName: String,
        val sourceName: String
    ) : UpdateCheckResult()

    /**
     * 当前已是最新版本。
     */
    data class UpToDate(
        val installedVersionName: String,
        val remoteVersionName: String,
        val sourceName: String
    ) : UpdateCheckResult()

    /**
     * 当前版本高于远程版本（不允许降级）。
     */
    data class LocalNewer(
        val installedVersionName: String,
        val remoteVersionName: String,
        val sourceName: String
    ) : UpdateCheckResult()

    /**
     * 检查失败，所有源均不可用。
     */
    data class Error(
        val message: String,
        val errors: List<SourceError> = emptyList()
    ) : UpdateCheckResult()
}

/**
 * 单个更新源的错误信息。
 */
data class SourceError(
    val sourceName: String,
    val error: String
)

/**
 * 测速结果。
 */
data class SpeedTestResult(
    val source: DownloadSource,
    val latencyMs: Long,
    val success: Boolean,
    val errorMsg: String? = null
)
