package com.countdown.app.update

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 更新源管理器（智能地理检测）。
 *
 * 核心机制：
 * 1. 启动时快速探测网络环境（国内/国际）
 *    - 尝试连接 Gitee（国内）和 GitHub（国际），哪个先响应就用哪个区域
 *    - 超时 3 秒，不影响启动速度
 * 2. 根据探测结果选择源优先级：
 *    - 国内用户：Gitee Raw → Gitee API → jsDelivr CDN → GitHub API
 *    - 国际用户：GitHub API → jsDelivr CDN → Gitee Raw → Gitee API
 * 3. 按优先级依次尝试，任一源成功即返回
 * 4. 所有源失败则返回 null
 *
 * APK 下载同样根据地理区域排序：
 * - 国内用户：Gitee → GH Proxy → GH Fast → GitHub
 * - 国际用户：GitHub → GH Proxy → GH Fast → Gitee
 */
class UpdateSourceManager {

    private val sourceConnectTimeout = 15L // 秒
    private val sourceReadTimeout = 20L    // 秒
    private val probeTimeout = 3L          // 地理探测超时（秒）

    /** 国内更新源（按优先级排序） */
    private val domesticSources: List<UpdateSource>

    /** 国际更新源（按优先级排序） */
    private val internationalSources: List<UpdateSource>

    /** 当前正在使用的源索引 */
    private val currentIndex = AtomicInteger(0)

    /** 缓存的网络环境检测结果 */
    @Volatile
    private var isDomestic: Boolean? = null

    init {
        domesticSources = listOf(
            GiteeRawSource("zero-william163", "Pro", "main"),   // 国内直读 version.json
            GiteeApiSource("zero-william163", "Pro"),            // Gitee API
            JSDelivrSource("Zero-william163", "Pro", "main"),    // CDN（有国内节点）
            GitHubApiSource("Zero-william163", "Pro")            // 国际源（兜底）
        )

        internationalSources = listOf(
            GitHubApiSource("Zero-william163", "Pro"),            // 官方源（国际最快）
            JSDelivrSource("Zero-william163", "Pro", "main"),    // CDN 加速
            GiteeRawSource("zero-william163", "Pro", "main"),    // 国内源（兜底）
            GiteeApiSource("zero-william163", "Pro")             // Gitee API
        )

        UpdateLogger.i("SourceManager", "已注册 ${domesticSources.size} 个国内源 + ${internationalSources.size} 个国际源")
    }

    /**
     * 探测网络环境：国内还是国际。
     *
     * 同时向 Gitee 和 GitHub 发起极小请求，哪个先响应就判定为对应区域。
     * 结果会缓存，避免重复探测。
     *
     * @return true=国内, false=国际
     */
    private fun probeNetworkRegion(): Boolean {
        // 如果已经探测过，直接返回缓存结果
        isDomestic?.let { return it }

        val probeClient = OkHttpClient.Builder()
            .connectTimeout(probeTimeout, TimeUnit.SECONDS)
            .readTimeout(probeTimeout, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        // 并行探测 Gitee 和 GitHub
        val giteeFuture = Thread {
            try {
                val request = Request.Builder()
                    .url("https://gitee.com")
                    .head()
                    .build()
                probeClient.newCall(request).execute().close()
                isDomestic = true
            } catch (_: Exception) {
                // Gitee 不可达
            }
        }

        val githubFuture = Thread {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com")
                    .head()
                    .build()
                probeClient.newCall(request).execute().close()
                if (isDomestic == null) isDomestic = false
            } catch (_: Exception) {
                // GitHub 不可达
            }
        }

        giteeFuture.start()
        githubFuture.start()

        // 等待探测完成（最多 probeTimeout*2 秒）
        giteeFuture.join(probeTimeout * 1000 * 2)
        githubFuture.join(probeTimeout * 1000 * 2)

        // 如果都没响应，默认按国内处理（Gitee 兜底）
        val result = isDomestic ?: true
        isDomestic = result

        UpdateLogger.i("SourceManager", "网络环境探测结果: ${if (result) "国内" else "国际"}")
        return result
    }

    /**
     * 根据网络环境获取排序后的更新源列表。
     */
    private fun getSortedSources(): List<UpdateSource> {
        val domestic = probeNetworkRegion()
        val sources = if (domestic) domesticSources else internationalSources

        UpdateLogger.i("SourceManager", "使用${if (domestic) "国内" else "国际"}源优先级: ${sources.joinToString { it.name }}")
        return sources
    }

    /**
     * 创建配置好超时的 OkHttpClient。
     */
    fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(sourceConnectTimeout, TimeUnit.SECONDS)
            .readTimeout(sourceReadTimeout, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 从多个更新源获取更新信息。
     *
     * 根据网络环境选择源优先级，依次尝试直到成功。
     *
     * @return Pair<UpdateInfo, 源名称> 或 null
     */
    fun fetchUpdateInfo(): Pair<UpdateInfo, String>? {
        val client = createClient()
        val sources = getSortedSources()
        val errors = mutableListOf<SourceError>()

        for ((index, source) in sources.withIndex()) {
            val startTime = System.currentTimeMillis()
            UpdateLogger.logSourceStart(source.name, source.region)

            try {
                val info = source.fetch(client)
                val latency = System.currentTimeMillis() - startTime

                if (info != null) {
                    currentIndex.set(index)
                    UpdateLogger.logSourceSuccess(source.name, info.versionName, latency)

                    // 根据网络环境排序下载地址
                    val sortedInfo = sortDownloadUrlsByRegion(info)
                    return Pair(sortedInfo, source.name)
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
     * 根据网络环境排序下载地址。
     *
     * 国内用户：国内源 → CDN → 国际源
     * 国际用户：国际源 → CDN → 国内源
     */
    private fun sortDownloadUrlsByRegion(info: UpdateInfo): UpdateInfo {
        val domestic = isDomestic ?: true
        val mergedUrls = mutableListOf<DownloadSource>()
        val seenUrls = mutableSetOf<String>()

        // 按区域优先级排序
        val regionPriority = if (domestic) {
            listOf("domestic", "cdn", "international")
        } else {
            listOf("international", "cdn", "domestic")
        }

        for (region in regionPriority) {
            for (source in info.downloadUrls) {
                if (source.region == region && source.url !in seenUrls) {
                    mergedUrls.add(source)
                    seenUrls.add(source.url)
                }
            }
        }

        // 补充未分类的源
        for (source in info.downloadUrls) {
            if (source.url !in seenUrls) {
                mergedUrls.add(source)
                seenUrls.add(source.url)
            }
        }

        return info.copy(downloadUrls = mergedUrls)
    }

    /**
     * 对下载源进行测速。
     *
     * 对每个下载 URL 发起请求，测量响应时间。
     * 返回按延迟排序的测速结果列表。
     *
     * @param downloadUrls 待测速的下载源列表
     * @return 按延迟排序的测速结果（成功的在前）
     */
    fun speedTest(downloadUrls: List<DownloadSource>): List<SpeedTestResult> {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val results = mutableListOf<SpeedTestResult>()

        for (source in downloadUrls) {
            val startTime = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url(source.url)
                    .header("Range", "bytes=0-0")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val latency = System.currentTimeMillis() - startTime
                val success = response.isSuccessful || response.code == 206

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
        val sources = if (isDomestic == true) domesticSources else internationalSources
        val index = currentIndex.get()
        return if (index < sources.size) sources[index].name else "Unknown"
    }

    /**
     * 获取所有已注册的源名称列表。
     */
    fun getAllSourceNames(): List<String> {
        val domestic = isDomestic ?: true
        return if (domestic) domesticSources.map { it.name } else internationalSources.map { it.name }
    }

    /**
     * 获取所有源的描述信息。
     */
    fun getSourceDescriptions(): List<String> {
        val domestic = isDomestic ?: true
        val sources = if (domestic) domesticSources else internationalSources
        val regionLabel = if (domestic) "国内优先" else "国际优先"
        return sources.map { "$regionLabel - ${it.name} (${it.region}, 优先级: ${it.priority})" }
    }

    /**
     * 获取当前网络环境。
     */
    fun getNetworkRegion(): String {
        return when (isDomestic) {
            true -> "国内"
            false -> "国际"
            null -> "未检测"
        }
    }
}
