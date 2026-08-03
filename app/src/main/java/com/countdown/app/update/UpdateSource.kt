package com.countdown.app.update

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 更新源接口。
 *
 * 每个更新源实现此接口，返回统一的 UpdateInfo。
 * 支持 GitHub API、jsDelivr CDN、Gitee API 等多种源。
 */
interface UpdateSource {
    /** 源名称 */
    val name: String

    /** 优先级（数字越小优先级越高） */
    val priority: Int

    /** 区域："international" 或 "domestic" */
    val region: String

    /**
     * 从此源获取更新信息。
     *
     * @param client OkHttpClient 实例
     * @return 更新信息，失败返回 null
     */
    fun fetch(client: OkHttpClient): UpdateInfo?
}

// ============================================================
// GitHub Releases API 源（官方源，国际优先）
// ============================================================

/**
 * GitHub Releases API 更新源。
 *
 * 直接请求 GitHub API 获取最新 Release 信息。
 * 国际网络环境下速度最佳，国内可能无法访问。
 */
class GitHubApiSource(
    private val owner: String,
    private val repo: String
) : UpdateSource {

    override val name = "GitHub"
    override val priority = 1
    override val region = "international"

    private val apiUrl get() = "https://api.github.com/repos/$owner/$repo/releases/latest"

    override fun fetch(client: OkHttpClient): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                UpdateLogger.w(name, "API 返回错误码: ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            parseGitHubRelease(body)
        } catch (e: Exception) {
            UpdateLogger.e(name, "请求失败", e)
            null
        }
    }

    private fun parseGitHubRelease(jsonBody: String): UpdateInfo? {
        val json = JSONObject(jsonBody)

        val rawTag = json.optString("tag_name", "")
        val versionName = rawTag.removePrefix("v").removePrefix("V")
        val releaseNotes = json.optString("body", "")
        val publishedAt = json.optString("published_at", "")

        // 查找 APK 下载地址
        var apkUrl = ""
        var apkSize = 0L
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
        }

        if (apkUrl.isBlank()) {
            UpdateLogger.w(name, "Release 中未找到 APK 文件")
            return null
        }

        // 构建多下载源列表
        val downloadUrls = mutableListOf<DownloadSource>()
        downloadUrls.add(DownloadSource("GitHub", apkUrl, "international"))
        // GitHub 代理（国内加速）
        downloadUrls.add(DownloadSource("GitHub Proxy", "https://ghproxy.com/$apkUrl", "domestic"))
        downloadUrls.add(DownloadSource("Mirror", "https://mirror.ghproxy.com/$apkUrl", "domestic"))

        return UpdateInfo(
            versionName = versionName,
            versionCode = estimateVersionCode(versionName),
            versionTag = rawTag,
            releaseNotes = releaseNotes,
            publishedAt = publishedAt,
            apkSize = apkSize,
            sha256 = null,
            downloadUrls = downloadUrls
        )
    }

    private fun estimateVersionCode(versionName: String): Int {
        val parts = versionName.split(".")
        var code = 0
        for (part in parts) {
            code = code * 100 + (part.toIntOrNull() ?: 0)
        }
        return code
    }
}

// ============================================================
// jsDelivr CDN 源（国内镜像，通过 CDN 加速访问 version.json）
// ============================================================

/**
 * jsDelivr CDN 更新源。
 *
 * 通过 jsDelivr CDN 访问仓库中的 version.json 文件。
 * jsDelivr 在国内有 CDN 节点，速度快且稳定。
 *
 * version.json 包含完整的版本信息和所有下载地址，
 * 确保所有源返回的版本信息完全一致。
 */
class JSDelivrSource(
    private val owner: String,
    private val repo: String,
    private val branch: String = "main"
) : UpdateSource {

    override val name = "jsDelivr"
    override val priority = 4
    override val region = "cdn"

    private val jsonUrl get() = "https://cdn.jsdelivr.net/gh/$owner/$repo@$branch/version.json"

    override fun fetch(client: OkHttpClient): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(jsonUrl)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                UpdateLogger.w(name, "CDN 返回错误码: ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            parseVersionJson(body)
        } catch (e: Exception) {
            UpdateLogger.e(name, "请求失败", e)
            null
        }
    }
}

// ============================================================
// Gitee API 源（国内镜像，Gitee Releases API）
// ============================================================

/**
 * Gitee API 更新源。
 *
 * 通过 Gitee API 获取最新 Release 信息。
 * Gitee 是国内代码托管平台，访问速度快且稳定。
 * 需要 Gitee 仓库已镜像并创建了对应的 Release。
 */
class GiteeApiSource(
    private val owner: String,
    private val repo: String
) : UpdateSource {

    override val name = "Gitee"
    override val priority = 5
    override val region = "domestic"

    private val apiUrl get() = "https://gitee.com/api/v5/repos/$owner/$repo/releases/latest"

    override fun fetch(client: OkHttpClient): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                UpdateLogger.w(name, "API 返回错误码: ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            parseGiteeRelease(body)
        } catch (e: Exception) {
            UpdateLogger.e(name, "请求失败", e)
            null
        }
    }

    private fun parseGiteeRelease(jsonBody: String): UpdateInfo? {
        val json = JSONObject(jsonBody)

        val rawTag = json.optString("tag_name", "")
        val versionName = rawTag.removePrefix("v").removePrefix("V")
        val releaseNotes = json.optString("body", "")
        val publishedAt = json.optString("created_at", "")

        // 查找 APK 下载地址
        var apkUrl = ""
        var apkSize = 0L
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
        }

        if (apkUrl.isBlank()) {
            UpdateLogger.w(name, "Release 中未找到 APK 文件")
            return null
        }

        val downloadUrls = mutableListOf<DownloadSource>()
        downloadUrls.add(DownloadSource("Gitee", apkUrl, "domestic"))

        return UpdateInfo(
            versionName = versionName,
            versionCode = estimateVersionCode(versionName),
            versionTag = rawTag,
            releaseNotes = releaseNotes,
            publishedAt = publishedAt,
            apkSize = apkSize,
            sha256 = null,
            downloadUrls = downloadUrls
        )
    }

    private fun estimateVersionCode(versionName: String): Int {
        val parts = versionName.split(".")
        var code = 0
        for (part in parts) {
            code = code * 100 + (part.toIntOrNull() ?: 0)
        }
        return code
    }
}

// ============================================================
// Gitee Raw 源（国内镜像，直接读取 version.json）
// ============================================================

/**
 * Gitee Raw 更新源。
 *
 * 通过 Gitee 的 raw 文件接口直接读取仓库中的 version.json。
 * 比 Gitee API 更稳定（无速率限制），适合国内用户。
 *
 * version.json 包含完整的版本信息和所有下载地址，
 * 确保所有源返回的版本信息完全一致。
 */
class GiteeRawSource(
    private val owner: String,
    private val repo: String,
    private val branch: String = "main"
) : UpdateSource {

    override val name = "Gitee Raw"
    override val priority = 3
    override val region = "domestic"

    private val rawUrl get() = "https://gitee.com/$owner/$repo/raw/$branch/version.json"

    override fun fetch(client: OkHttpClient): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(rawUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                UpdateLogger.w(name, "Raw 文件返回错误码: ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            if (body.isBlank() || body.startsWith("<")) {
                UpdateLogger.w(name, "Raw 文件内容异常，可能是重定向页面")
                return null
            }
            parseVersionJson(body)
        } catch (e: Exception) {
            UpdateLogger.e(name, "请求失败", e)
            null
        }
    }
}

// ============================================================
// version.json 解析工具（jsDelivr 和 Gitee raw 共用）
// ============================================================

/**
 * 解析 version.json 文件内容。
 *
 * version.json 是仓库根目录的版本信息文件，
 * 由 GitHub Actions 在发布 Release 时自动更新。
 */
fun parseVersionJson(jsonBody: String): UpdateInfo? {
    return try {
        val json = JSONObject(jsonBody)

        val versionName = json.optString("versionName", "")
        if (versionName.isBlank()) return null

        val versionCode = json.optInt("versionCode", 0)
        val versionTag = json.optString("versionTag", "v$versionName")
        val releaseNotes = json.optString("releaseNotes", "")
        val publishedAt = json.optString("publishedAt", "")
        val apkSize = json.optLong("apkSize", 0L)
        val sha256 = json.optString("sha256", "").ifBlank { null }

        val downloadUrls = mutableListOf<DownloadSource>()
        val urlsArray = json.optJSONArray("downloadUrls")
        if (urlsArray != null) {
            for (i in 0 until urlsArray.length()) {
                val item = urlsArray.getJSONObject(i)
                val name = item.optString("name", "Unknown")
                val url = item.optString("url", "")
                val region = item.optString("region", "international")
                if (url.isNotBlank()) {
                    downloadUrls.add(DownloadSource(name, url, region))
                }
            }
        }

        if (downloadUrls.isEmpty()) {
            UpdateLogger.w("VersionJson", "version.json 中未找到下载地址")
            return null
        }

        UpdateInfo(
            versionName = versionName,
            versionCode = versionCode,
            versionTag = versionTag,
            releaseNotes = releaseNotes,
            publishedAt = publishedAt,
            apkSize = apkSize,
            sha256 = sha256,
            downloadUrls = downloadUrls
        )
    } catch (e: Exception) {
        UpdateLogger.e("VersionJson", "解析 version.json 失败", e)
        null
    }
}
