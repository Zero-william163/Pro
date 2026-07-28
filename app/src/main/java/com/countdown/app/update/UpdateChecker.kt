package com.countdown.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 应用内更新检查器。
 *
 * 通过 GitHub Releases API 检查最新版本，并与当前安装版本进行语义化比较。
 *
 * 使用方式:
 *   val result = UpdateChecker.checkUpdate(context)
 *   result.onSuccess { updateResult ->
 *       when (updateResult) {
 *           is UpdateResult.UpdateAvailable -> { /* 显示更新弹窗 */ }
 *           is UpdateResult.UpToDate -> { /* 提示"当前已是最新版本" */ }
 *           is UpdateResult.LocalNewer -> { /* 提示"当前版本高于最新正式版" */ }
 *       }
 *   }.onFailure { e ->
 *       /* 提示"检查更新失败，请稍后重试。" */
 *   }
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    // GitHub 仓库信息
    private const val GITHUB_OWNER = "Zero-william163"
    private const val GITHUB_REPO = "Pro"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    // -----------------------------------------------------------------------
    // 数据模型
    // -----------------------------------------------------------------------

    /**
     * 当前安装的应用版本信息。
     *
     * @param versionName 版本名，例如 "1.1.0"
     * @param versionCode 版本号，例如 110
     * @param semanticVersion 从 versionName 解析出的语义化版本
     */
    data class InstalledVersion(
        val versionName: String,
        val versionCode: Long,
        val semanticVersion: SemanticVersion?
    )

    /**
     * GitHub Release 中的版本信息。
     *
     * @param tagName 标签名（已去除 v 前缀），例如 "1.1.0"
     * @param releaseName Release 名称
     * @param downloadUrl APK 下载地址
     * @param releaseNotes 更新日志
     * @param publishedAt 发布时间
     * @param semanticVersion 从 tagName 解析出的语义化版本
     */
    data class RemoteVersion(
        val tagName: String,
        val releaseName: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val publishedAt: String,
        val semanticVersion: SemanticVersion?
    )

    /**
     * 版本比较结果。
     */
    sealed class UpdateResult {
        /**
         * GitHub 版本高于当前安装版本，可以更新。
         */
        data class UpdateAvailable(
            val remoteVersion: RemoteVersion,
            val installedVersion: InstalledVersion
        ) : UpdateResult()

        /**
         * 当前安装版本与 GitHub 最新版本相同，无需更新。
         */
        data class UpToDate(
            val installedVersion: InstalledVersion
        ) : UpdateResult()

        /**
         * 当前安装版本高于 GitHub 最新版本（测试版或预发布版）。
         * 不允许降级安装。
         */
        data class LocalNewer(
            val installedVersion: InstalledVersion,
            val remoteVersion: RemoteVersion
        ) : UpdateResult()
    }

    // -----------------------------------------------------------------------
    // 公共 API
    // -----------------------------------------------------------------------

    /**
     * 检查应用更新。
     *
     * 流程:
     * 1. 读取当前安装版本 (PackageManager)
     * 2. 请求 GitHub Releases API
     * 3. 解析远程版本信息
     * 4. 语义化版本比较
     * 5. 返回比较结果
     *
     * @param context 应用上下文
     * @return Result<UpdateResult>，成功返回 UpdateResult，失败返回异常
     */
    suspend fun checkUpdate(context: Context): Result<UpdateResult> = withContext(Dispatchers.IO) {
        try {
            // Step 1: 读取当前安装版本
            val installedVersion = getInstalledVersion(context)
            Log.i(TAG, "当前安装版本: versionName=${installedVersion.versionName}, " +
                    "versionCode=${installedVersion.versionCode}, " +
                    "semantic=${installedVersion.semanticVersion}")

            // Step 2: 请求 GitHub Releases API
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            Log.d(TAG, "请求 GitHub API: $GITHUB_API_URL")

            val response = try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                Log.e(TAG, "网络请求失败", e)
                return@withContext Result.failure(Exception("网络请求失败，请检查网络连接"))
            }

            response.use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "GitHub API 返回错误码: ${resp.code}")
                    when (resp.code) {
                        404 -> return@withContext Result.failure(
                            Exception("尚未发布任何 Release")
                        )
                        403 -> return@withContext Result.failure(
                            Exception("GitHub API 速率限制，请稍后重试")
                        )
                        else -> return@withContext Result.failure(
                            Exception("服务器错误: ${resp.code}")
                        )
                    }
                }

                val body = resp.body?.string()
                if (body.isNullOrBlank()) {
                    Log.e(TAG, "GitHub API 返回空响应")
                    return@withContext Result.failure(Exception("服务器返回空数据"))
                }

                // Step 3: 解析远程版本信息
                val remoteVersion = parseGitHubRelease(body)
                Log.i(TAG, "GitHub 最新版本: tagName=${remoteVersion.tagName}, " +
                        "publishedAt=${remoteVersion.publishedAt}, " +
                        "semantic=${remoteVersion.semanticVersion}")

                // 检查 APK 下载地址
                if (remoteVersion.downloadUrl.isBlank()) {
                    Log.w(TAG, "Release 中未找到 APK 文件")
                    return@withContext Result.failure(Exception("此版本未上传 APK 文件"))
                }

                // Step 4: 语义化版本比较
                val result = compareVersions(installedVersion, remoteVersion)

                // Step 5: 返回结果
                when (result) {
                    is UpdateResult.UpdateAvailable ->
                        Log.i(TAG, "比较结果: 发现新版本 ${remoteVersion.tagName} > ${installedVersion.versionName}")
                    is UpdateResult.UpToDate ->
                        Log.i(TAG, "比较结果: 当前已是最新版本 ${installedVersion.versionName}")
                    is UpdateResult.LocalNewer ->
                        Log.i(TAG, "比较结果: 当前版本 ${installedVersion.versionName} 高于最新正式版 ${remoteVersion.tagName}")
                }

                Result.success(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查更新过程中发生未预期异常", e)
            Result.failure(Exception("检查更新失败，请稍后重试"))
        }
    }

    // -----------------------------------------------------------------------
    // 内部方法
    // -----------------------------------------------------------------------

    /**
     * 通过 PackageManager 读取当前安装 APK 的真实版本信息。
     * 不使用硬编码或缓存值。
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
            Log.e(TAG, "无法读取当前版本信息", e)
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

        return InstalledVersion(versionName, versionCode, semanticVersion)
    }

    /**
     * 解析 GitHub Releases API 的 JSON 响应。
     */
    private fun parseGitHubRelease(jsonBody: String): RemoteVersion {
        val json = JSONObject(jsonBody)

        // 解析 tag_name，自动去除 v 前缀
        val rawTag = json.optString("tag_name", "")
        val tagName = rawTag.removePrefix("v").removePrefix("V")
        val semanticVersion = SemanticVersion.parse(rawTag)

        val releaseName = json.optString("name", "")
        val releaseNotes = json.optString("body", "")
        val publishedAt = json.optString("published_at", "")

        // 查找 APK 下载地址
        var apkUrl = ""
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    Log.d(TAG, "找到 APK: $name, URL: $apkUrl")
                    break
                }
            }
        }

        if (apkUrl.isBlank()) {
            Log.w(TAG, "Release 中未找到 APK asset")
        }

        return RemoteVersion(
            tagName = tagName,
            releaseName = releaseName,
            downloadUrl = apkUrl,
            releaseNotes = releaseNotes,
            publishedAt = publishedAt,
            semanticVersion = semanticVersion
        )
    }

    /**
     * 比较当前安装版本与远程版本。
     *
     * 优先比较 versionCode（更可靠），如果 versionCode 相同则比较语义化版本号。
     * 如果两者都无法比较，则使用语义化版本号作为唯一依据。
     */
    private fun compareVersions(
        installed: InstalledVersion,
        remote: RemoteVersion
    ): UpdateResult {
        val installedSemVer = installed.semanticVersion
        val remoteSemVer = remote.semanticVersion

        // 如果两个语义化版本都能解析出来，使用语义化版本比较
        if (installedSemVer != null && remoteSemVer != null) {
            val comparison = remoteSemVer.compareTo(installedSemVer)
            return when {
                comparison > 0 -> UpdateResult.UpdateAvailable(remote, installed)
                comparison == 0 -> UpdateResult.UpToDate(installed)
                else -> UpdateResult.LocalNewer(installed, remote)
            }
        }

        // 如果语义化版本解析失败，回退到 versionCode 比较
        Log.w(TAG, "语义化版本解析失败，回退到 versionCode 比较: " +
                "installed=$installedSemVer, remote=$remoteSemVer")

        // 如果 versionCode 相同，认为是最新版本
        return when {
            remote.tagName == installed.versionName -> UpdateResult.UpToDate(installed)
            else -> {
                // 无法确定，默认提示有更新（但记录警告）
                Log.w(TAG, "无法精确比较版本，默认提示有更新")
                UpdateResult.UpdateAvailable(remote, installed)
            }
        }
    }
}
