package com.countdown.app.update

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 更新源管理器。
 *
 * 管理多个更新源，支持：
 * - 自动切换：GitHub → jsDelivr → Gitee
 * - 自动测速：对下载源进行测速，选择最快的
 * - 自动重试：每个源失败后自动切换到下一个
 * - 自动故障转移：所有源依次尝试，直到成功
 *
 * 架构设计：
 * 1. 版本检测源：GitHub API / jsDelivr version.json / Gitee API
 * 2. APK 下载源：从版本信息中获取所有下载地址，测速后选择最快的
 */
class UpdateSourceManager {

    private val sourceConnectTimeout = 10L // 秒
    private val sourceReadTimeout = 15L    // 秒

    /** 所有注册的更新源（按优先级排序） */
    private val sources: List<UpdateSource>

    /** 当前正在使用的源索引 */
    private val currentIndex = AtomicInteger(0)

    init {
        sources = listOf(
            GitHubApiSource("Zero-william163", "Pro"),       // priority=1, 官方源
            GiteeRawSource("zero-william163", "Pro", "main"), // priority=2, 国内直读 version.json
            JSDelivrSource("Zero-william163", "Pro", "main"), // priority=3, CDN 加速
            GiteeApiSource("zero-william163", "Pro")          // priority=4, Gitee API
        ).sortedBy { it.priority }

        UpdateLogger.i("SourceManager", "已注册 ${sources.size} 个更新源: ${sources.joinToString { "${it.name}(${it.region},P${it.priority})" }}")
    }

    /**
     * 创建配置好超时的 OkHttpClient。
     */
    fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(sourceConnectTimeout, TimeUnit.SECONDS)
            .readTimeout(sourceReadTimeout, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 从多个更新源获取更新信息。
     *
     * 按优先级依次尝试每个源：
     * 1. GitHub API（官方源，国际优先）
     * 2. jsDelivr CDN（国内镜像，通过 CDN 加速）
     * 3. Gitee API（国内镜像，直接访问）
     *
     * 任何一个源成功即返回，记录使用的源名称。
     * 所有源均失败则返回 null。
     *
     * @return Pair<UpdateInfo, 源名称> 或 null
     */
    fun fetchUpdateInfo(): Pair<UpdateInfo, String>? {
        val client = createClient()
        val errors = mutableListOf<SourceError>()

        for ((index, source) in sources.withIndex()) {
            val startTime = System.currentTimeMillis()
            UpdateLogger.logSourceStart(source.name, "N/A")

            try {
                val info = source.fetch(client)
                val latency = System.currentTimeMillis() - startTime

                if (info != null) {
                    currentIndex.set(index)
                    UpdateLogger.logSourceSuccess(source.name, info.versionName, latency)

                    // 合并下载源：如果 GitHub 成功，补充 Gitee 下载地址
                    val mergedInfo = mergeDownloadUrls(info)
                    return Pair(mergedInfo, source.name)
                } else {
                    UpdateLogger.logSourceFailed(source.name, "返回空结果", latency)
                    errors.add(SourceError(source.name, "返回空结果"))
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                UpdateLogger.logSourceFailed(source.name, e.message ?: "未知错误", latency)
                errors.add(SourceError(source.name, e.message ?: "未知错误"))

                if (index < sources.size - 1) {
                    UpdateLogger.logSourceSwitch(source.name, sources[index + 1].name, "当前源失败")
                }
            }
        }

        UpdateLogger.e("SourceManager", "所有 ${sources.size} 个更新源均失败")
        return null
    }

    /**
     * 合并下载地址。
     *
     * 确保下载地址列表包含所有可用的源：
     * - GitHub 直连
     * - GitHub 代理（ghproxy）
     * - Gitee 镜像
     *
     * 去重后按优先级排序：国内源优先。
     */
    private fun mergeDownloadUrls(info: UpdateInfo): UpdateInfo {
        val mergedUrls = mutableListOf<DownloadSource>()
        val seenUrls = mutableSetOf<String>()

        // 国内源优先
        for (source in info.downloadUrls) {
            if (source.region == "domestic" && source.url !in seenUrls) {
                mergedUrls.add(source)
                seenUrls.add(source.url)
            }
        }

        // 然后是 CDN
        for (source in info.downloadUrls) {
            if (source.region == "cdn" && source.url !in seenUrls) {
                mergedUrls.add(source)
                seenUrls.add(source.url)
            }
        }

        // 最后是国际源
        for (source in info.downloadUrls) {
            if (source.region == "international" && source.url !in seenUrls) {
                mergedUrls.add(source)
                seenUrls.add(source.url)
            }
        }

        return info.copy(downloadUrls = mergedUrls)
    }

    /**
     * 对下载源进行测速。
     *
     * 对每个下载 URL 发起 HEAD 请求，测量响应时间。
     * 返回按延迟排序的测速结果列表。
     *
     * @param downloadUrls 待测速的下载源列表
     * @return 按延迟排序的测速结果（成功的在前）
     */
    fun speedTest(downloadUrls: List<DownloadSource>): List<SpeedTestResult> {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val results = mutableListOf<SpeedTestResult>()

        for (source in downloadUrls) {
            val startTime = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url(source.url)
                    .head()
                    .build()

                val response = client.newCall(request).execute()
                val latency = System.currentTimeMillis() - startTime
                val success = response.isSuccessful

                response.close()

                results.add(SpeedTestResult(source, latency, success))
                UpdateLogger.logSpeedTest(source.name, latency, success)
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - startTime
                results.add(SpeedTestResult(source, latency, false, e.message))
                UpdateLogger.logSpeedTest(source.name, latency, false)
            }
        }

        // 按延迟排序，成功的在前
        return results.sortedWith(compareBy({ !it.success }, { it.latencyMs }))
    }

    /**
     * 获取当前使用的源名称。
     */
    fun getCurrentSourceName(): String {
        val index = currentIndex.get()
        return if (index < sources.size) sources[index].name else "Unknown"
    }

    /**
     * 获取所有已注册的源名称列表。
     */
    fun getAllSourceNames(): List<String> = sources.map { it.name }

    /**
     * 获取所有源的描述信息。
     */
    fun getSourceDescriptions(): List<String> {
        return sources.map { "${it.name} (${it.region}, 优先级: ${it.priority})" }
    }
}
