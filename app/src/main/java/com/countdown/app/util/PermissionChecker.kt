package com.countdown.app.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 权限检测与引导工具类（重构 v3）
 *
 * 核心改进：
 * 1. 多级降级跳转链：每项权限提供按优先级排序的 Intent 候选列表
 *    - 第一优先：直接跳转到对应权限页面
 *    - 第二优先：跳转到当前应用详情页
 *    - 第三优先：跳转到对应系统设置分类页面
 *    - 最后才允许：跳转到系统设置首页
 *
 * 2. 完整的权限信息模型：
 *    - 权限名称、当前状态
 *    - 权限作用说明（description）
 *    - 为什么必须开启（whyRequired）
 *    - 开启后能解决什么问题（solveProblem）
 *    - 操作路径说明（operationPath，当无法直接跳转时显示）
 *
 * 3. Intent 可用性检测：跳转前检查目标 Activity 是否存在
 *
 * 4. 厂商专项适配：华为/荣耀/小米/OPPO/vivo/三星/一加
 */
object PermissionChecker {

    private const val TAG = "PermissionChecker"

    // ==================== 权限确认存储（厂商权限用户手动确认） ====================

    /**
     * 厂商特殊权限的用户确认状态存储
     *
     * 由于华为/小米/OPPO/vivo 等厂商的自启动、后台管理等权限无法通过 Android API 检测，
     * 当用户按照引导开启后，由用户手动确认，存储确认状态。
     * 这不是"是否提示过"的记录，而是"用户确认已开启"的状态。
     */
    private object PermissionConfirmationStore {
        private const val PREFS_NAME = "permission_confirmations"
        private const val KEY_PREFIX = "confirmed_"

        fun isConfirmed(context: Context, permissionId: String): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_PREFIX + permissionId, false)
        }

        fun setConfirmed(context: Context, permissionId: String, confirmed: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_PREFIX + permissionId, confirmed).apply()
            Log.d(TAG, "Permission $permissionId confirmation set to: $confirmed")
        }

        fun clearAll(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }
    }

    /**
     * 检查厂商权限是否已被用户确认
     */
    fun isPermissionConfirmed(context: Context, permissionId: String): Boolean {
        return PermissionConfirmationStore.isConfirmed(context, permissionId)
    }

    /**
     * 设置厂商权限的用户确认状态
     */
    fun setPermissionConfirmed(context: Context, permissionId: String, confirmed: Boolean) {
        PermissionConfirmationStore.setConfirmed(context, permissionId, confirmed)
    }

    // ==================== 权限数据模型 ====================

    /**
     * 单项权限信息
     */
    data class PermissionItem(
        val id: String,
        val title: String,              // 权限名称
        val description: String,        // 权限作用说明
        val whyRequired: String,        // 为什么必须开启
        val solveProblem: String,       // 开启后能解决什么问题
        val isGranted: Boolean,         // 当前是否已开启
        val isCritical: Boolean,        // 是否为关键权限
        val intentCandidates: List<Intent>,  // 按优先级排序的跳转意图列表
        val actionLabel: String,        // 按钮文字
        val vendorSpecial: Boolean = false,  // 是否为厂商专项权限
        val vendorName: String = "",         // 厂商名称（华为/小米等）
        val operationPath: String? = null,   // 手动操作路径（无法直接跳转时显示）
        val iconType: IconType = IconType.GENERAL,  // 图标类型
        val checkable: Boolean = true,       // 是否可通过 API 检测状态（false 表示需用户手动确认）
        val confirmedByUser: Boolean = false, // 用户是否已手动确认开启（仅 checkable=false 时有效）
        val guideId: String = ""              // 关联 PermissionGuideData 中的权限 ID，用于显示详细操作路径
    )

    enum class IconType {
        NOTIFICATION, ALARM, FULL_SCREEN, BATTERY,
        OVERLAY, FOREGROUND_SERVICE, CHANNEL,
        AUTO_START, LOCK_SCREEN, PROTECT_APP, GENERAL
    }

    /**
     * 跳转结果
     */
    sealed class OpenResult {
        /** 直接跳转到对应权限页面 */
        data class DirectJump(val intent: Intent) : OpenResult()
        /** 降级跳转到应用详情页 */
        data class FallbackToAppDetails(val reason: String) : OpenResult()
        /** 降级跳转到系统设置 */
        data class FallbackToSettings(val reason: String) : OpenResult()
        /** 完全无法跳转，需要手动操作 */
        data class Failed(val reason: String, val operationPath: String) : OpenResult()
    }

    data class PermissionResult(
        val allGranted: Boolean,
        val criticalGranted: Boolean,
        val items: List<PermissionItem>,
        val isHuaweiDevice: Boolean,
        val deviceBrand: String,
        val missingCriticalCount: Int
    )

    // ==================== 设备检测 ====================

    fun isHuaweiDevice(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("huawei") || b.contains("huawei") ||
               m.contains("honor") || b.contains("honor")
    }

    fun isHonorDevice(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("honor") || b.contains("honor")
    }

    fun isXiaomiDevice(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("xiaomi") || b.contains("xiaomi") ||
               m.contains("redmi") || b.contains("redmi")
    }

    fun isOppoDevice(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("oppo") || b.contains("oppo") ||
               m.contains("realme") || b.contains("realme") ||
               m.contains("oneplus") || b.contains("oneplus")
    }

    fun isOnePlusDevice(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("oneplus") || b.contains("oneplus")
    }

    fun isVivoDevice(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("vivo") || b.contains("vivo") ||
               m.contains("iqoo") || b.contains("iqoo")
    }

    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.lowercase().contains("samsung")
    }

    fun isPixelDevice(): Boolean {
        return Build.MANUFACTURER.lowercase().contains("google") &&
               Build.BRAND.lowercase().contains("google")
    }

    fun getDeviceBrand(): String {
        return when {
            isHonorDevice() -> "荣耀"
            isHuaweiDevice() -> "华为"
            isOnePlusDevice() -> "一加"
            isOppoDevice() -> "OPPO"
            isVivoDevice() -> "vivo"
            isXiaomiDevice() -> "小米"
            isSamsungDevice() -> "三星"
            isPixelDevice() -> "Pixel"
            else -> Build.BRAND.replaceFirstChar { it.uppercase() }
        }
    }

    // ==================== Intent 可用性检测 ====================

    /**
     * 检查 Intent 是否可以解析到目标 Activity
     */
    fun isIntentAvailable(context: Context, intent: Intent): Boolean {
        return try {
            val pm = context.packageManager
            if (intent.component != null) {
                // 显式 Intent：检查目标 Activity 是否存在
                pm.getActivityInfo(intent.component!!, 0)
                true
            } else {
                val activities = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                activities.isNotEmpty()
            }
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 通用 Intent 构建方法 ====================

    /**
     * 应用详情页 Intent（第二优先降级）
     */
    fun getAppDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 应用通知设置页 Intent
     */
    fun getAppNotificationSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 通知渠道设置页 Intent
     */
    fun getChannelSettingsIntent(context: Context, channelId: String): Intent {
        return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 精确闹钟权限页 Intent
     */
    fun getExactAlarmSettingsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else null
    }

    /**
     * 全屏通知权限页 Intent
     */
    fun getFullScreenIntentSettingsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else null
    }

    /**
     * 忽略电池优化授权页 Intent
     */
    fun getIgnoreBatterySettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 电池优化设置页 Intent（降级）
     */
    fun getBatteryOptimizationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 悬浮窗权限页 Intent
     */
    fun getOverlaySettingsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else null
    }

    // ==================== 厂商专项 Intent ====================

    /**
     * 华为/荣耀 - 应用启动管理
     */
    fun getHuaweiAutoStartIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 华为/荣耀 - 电池管理（手动管理/受保护应用）
     */
    fun getHuaweiBatteryIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 华为/荣耀 - 锁屏清理白名单
     */
    fun getHuaweiLockScreenIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 小米 - 自启动管理
     */
    fun getXiaomiAutoStartIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 小米 - 省电策略（神隐模式）
     */
    fun getXiaomiBatteryIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * OPPO/一加 - 自启动管理
     */
    fun getOppoAutoStartIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * OPPO/一加 - 电池管理
     */
    fun getOppoBatteryIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.FakeActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * vivo - 后台弹窗/自启动管理
     */
    fun getVivoAutoStartIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 三星 - 电池优化
     */
    fun getSamsungBatteryIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // ==================== 单项权限检测 ====================

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else true
    }

    fun hasFullScreenIntentPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.canUseFullScreenIntent()
        } else true
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun hasForegroundServicePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.FOREGROUND_SERVICE
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun isAlarmChannelEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = nm.getNotificationChannel(NotificationHelper.CHANNEL_ID_ALARM)
            channel?.importance != NotificationManager.IMPORTANCE_NONE
        } else true
    }

    // ==================== 全面权限检测 ====================

    fun checkAllPermissions(context: Context): PermissionResult {
        val items = mutableListOf<PermissionItem>()

        // ========== 关键权限（必须开启）==========

        // 1. 通知权限
        items.add(buildNotificationPermission(context))

        // 2. 精确闹钟权限
        items.add(buildExactAlarmPermission(context))

        // 3. 全屏通知权限
        items.add(buildFullScreenIntentPermission(context))

        // 4. 忽略电池优化
        items.add(buildBatteryOptimizationPermission(context))

        // 5. 闹钟通知渠道
        items.add(buildAlarmChannelPermission(context))

        // ========== 建议开启权限 ==========

        // 6. 悬浮窗权限
        items.add(buildOverlayPermission(context))

        // 7. 前台服务权限
        items.add(buildForegroundServicePermission(context))

        // ========== 厂商专项权限 ==========

        // 华为/荣耀
        if (isHuaweiDevice()) {
            items.add(buildHuaweiAutoStart(context))
            items.add(buildHuaweiBattery(context))
            items.add(buildHuaweiLockScreen(context))
        }

        // 小米
        if (isXiaomiDevice()) {
            items.add(buildXiaomiAutoStart(context))
            items.add(buildXiaomiBattery(context))
        }

        // OPPO/一加
        if (isOppoDevice()) {
            items.add(buildOppoAutoStart(context))
        }

        // vivo
        if (isVivoDevice()) {
            items.add(buildVivoAutoStart(context))
        }

        // 三星
        if (isSamsungDevice()) {
            items.add(buildSamsungBattery(context))
        }

        // ========== 应用用户确认状态（厂商权限） ==========
        // 对于无法通过 API 检测的厂商权限，检查用户是否已手动确认
        val finalItems = items.map { item ->
            if (!item.checkable) {
                val confirmed = PermissionConfirmationStore.isConfirmed(context, item.id)
                if (confirmed) {
                    item.copy(isGranted = true, confirmedByUser = true)
                } else {
                    item
                }
            } else {
                item
            }
        }

        // 计算结果
        val criticalItems = finalItems.filter { it.isCritical }
        val criticalGranted = criticalItems.all { it.isGranted }
        val allGranted = finalItems.all { it.isGranted }
        val missingCriticalCount = criticalItems.count { !it.isGranted }

        return PermissionResult(
            allGranted = allGranted,
            criticalGranted = criticalGranted,
            items = finalItems,
            isHuaweiDevice = isHuaweiDevice(),
            deviceBrand = getDeviceBrand(),
            missingCriticalCount = missingCriticalCount
        )
    }

    // ==================== 各权限构建方法 ====================

    private fun buildNotificationPermission(context: Context): PermissionItem {
        val granted = hasNotificationPermission(context) && areNotificationsEnabled(context)
        val intents = mutableListOf<Intent>()

        // 第一优先：应用通知设置页
        intents.add(getAppNotificationSettingsIntent(context))

        // 第二优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "notification_permission",
            title = "通知权限",
            description = "允许应用发送通知栏提醒和横幅通知",
            whyRequired = "闹钟到点时需要通过通知触发提醒，没有此权限所有通知都将被系统拦截",
            solveProblem = "解决闹钟到点后无任何提醒、横幅通知不弹出、通知栏无提示等问题",
            isGranted = granted,
            isCritical = true,
            intentCandidates = intents,
            actionLabel = "立即开启",
            iconType = IconType.NOTIFICATION,
            guideId = "notification_permission"
        )
    }

    private fun buildExactAlarmPermission(context: Context): PermissionItem {
        val granted = hasExactAlarmPermission(context)
        val intents = mutableListOf<Intent>()

        // 第一优先：精确闹钟权限页（Android 12+）
        getExactAlarmSettingsIntent(context)?.let { intents.add(it) }

        // 第二优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "exact_alarm",
            title = "精确闹钟权限",
            description = "允许应用在精确时间点触发闹钟",
            whyRequired = "Android 12+ 系统要求闹钟类应用必须获得此权限才能准时触发",
            solveProblem = "解决闹钟延迟触发、不准确、甚至完全不触发等问题",
            isGranted = granted,
            isCritical = true,
            intentCandidates = intents,
            actionLabel = "立即开启",
            iconType = IconType.ALARM,
            guideId = "exact_alarm"
        )
    }

    private fun buildFullScreenIntentPermission(context: Context): PermissionItem {
        val granted = hasFullScreenIntentPermission(context)
        val intents = mutableListOf<Intent>()

        // 第一优先：全屏通知权限管理页（Android 14+）
        getFullScreenIntentSettingsIntent(context)?.let { intents.add(it) }

        // 第二优先：应用通知设置页
        intents.add(getAppNotificationSettingsIntent(context))

        // 第三优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "full_screen_intent",
            title = "全屏通知权限",
            description = "允许应用在锁屏状态下弹出全屏界面",
            whyRequired = "锁屏时闹钟到点需要通过 FullScreenIntent 直接弹出全屏闹钟界面",
            solveProblem = "解决锁屏状态下闹钟到点后只响铃不弹全屏界面、需要手动解锁才能看到闹钟的问题",
            isGranted = granted,
            isCritical = true,
            intentCandidates = intents,
            actionLabel = "立即开启",
            iconType = IconType.FULL_SCREEN,
            guideId = "full_screen_intent"
        )
    }

    private fun buildBatteryOptimizationPermission(context: Context): PermissionItem {
        val granted = isIgnoringBatteryOptimizations(context)
        val intents = mutableListOf<Intent>()

        // 第一优先：忽略电池优化授权页
        intents.add(getIgnoreBatterySettingsIntent(context))

        // 第二优先：电池优化设置列表页
        intents.add(getBatteryOptimizationSettingsIntent())

        // 第三优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "battery_optimization",
            title = "忽略电池优化",
            description = "允许应用在后台运行时不受电池优化策略限制",
            whyRequired = "系统电池优化会在后台杀死应用，导致闹钟到点时应用已不在运行",
            solveProblem = "解决应用在后台被系统杀死、闹钟不触发、提醒延迟等问题",
            isGranted = granted,
            isCritical = true,
            intentCandidates = intents,
            actionLabel = "立即开启",
            iconType = IconType.BATTERY,
            guideId = "battery_optimization"
        )
    }

    private fun buildAlarmChannelPermission(context: Context): PermissionItem {
        val granted = isAlarmChannelEnabled(context)
        val intents = mutableListOf<Intent>()

        // 第一优先：闹钟通知渠道设置页
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intents.add(getChannelSettingsIntent(context, NotificationHelper.CHANNEL_ID_ALARM))
        }

        // 第二优先：应用通知设置页
        intents.add(getAppNotificationSettingsIntent(context))

        // 第三优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "alarm_channel",
            title = "闹钟通知渠道",
            description = "确保闹钟通知渠道未被用户在系统中禁用",
            whyRequired = "即使通知权限已开启，如果闹钟渠道被单独关闭，闹钟通知仍无法显示",
            solveProblem = "解决通知权限已开启但闹钟通知仍然不显示、不弹横幅的问题",
            isGranted = granted,
            isCritical = true,
            intentCandidates = intents,
            actionLabel = "立即设置",
            iconType = IconType.CHANNEL,
            guideId = "alarm_channel"
        )
    }

    private fun buildOverlayPermission(context: Context): PermissionItem {
        val granted = canDrawOverlays(context)
        val intents = mutableListOf<Intent>()

        // 第一优先：悬浮窗权限管理页
        getOverlaySettingsIntent(context)?.let { intents.add(it) }

        // 第二优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "system_alert_window",
            title = "悬浮窗权限",
            description = "允许应用在其他应用上层显示界面（降级方案）",
            whyRequired = "当全屏通知权限受限时，悬浮窗是显示闹钟界面的降级方案",
            solveProblem = "解决部分设备全屏通知无法弹出时，仍能通过悬浮窗显示闹钟提醒",
            isGranted = granted,
            isCritical = false,
            intentCandidates = intents,
            actionLabel = "立即开启",
            iconType = IconType.OVERLAY,
            guideId = "system_alert_window"
        )
    }

    private fun buildForegroundServicePermission(context: Context): PermissionItem {
        val granted = hasForegroundServicePermission(context)
        val intents = mutableListOf<Intent>()

        // 第一优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "foreground_service",
            title = "前台服务权限",
            description = "允许应用以前台服务方式持续播放闹钟铃声和震动",
            whyRequired = "闹钟响铃期间需要前台服务保持运行，否则系统会中断播放",
            solveProblem = "解决闹钟响铃几秒后自动停止、铃声被系统中断等问题",
            isGranted = granted,
            isCritical = false,
            intentCandidates = intents,
            actionLabel = "前往查看",
            iconType = IconType.FOREGROUND_SERVICE
        )
    }

    // ==================== 厂商专项权限构建 ====================

    private fun buildHuaweiAutoStart(context: Context): PermissionItem {
        val intents = mutableListOf<Intent>()

        // 第一优先：华为应用启动管理
        intents.add(getHuaweiAutoStartIntent())

        // 第二优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "huawei_auto_start",
            title = "应用启动管理",
            description = "允许应用自启动，防止被系统清理后无法恢复",
            whyRequired = "华为系统会在后台清理应用，如果未允许自启动，闹钟到点时应用可能已不在运行",
            solveProblem = "解决华为设备上闹钟不触发、应用被清理后提醒丢失等问题",
            isGranted = false,
            isCritical = false,
            intentCandidates = intents,
            actionLabel = "立即设置",
            vendorSpecial = true,
            vendorName = "华为",
            operationPath = "设置 → 应用 → 应用启动管理 → 找到「目标倒计时」 → 选择「手动管理」 → 开启全部三个开关",
            iconType = IconType.AUTO_START,
            checkable = false,  // 华为未提供公开 API 检测自启动状态
            guideId = "auto_start"
        )
    }

    private fun buildHuaweiBattery(context: Context): PermissionItem {
        val intents = mutableListOf<Intent>()

        // 第一优先：华为电池管理
        intents.add(getHuaweiBatteryIntent())

        // 第二优先：电池优化设置
        intents.add(getBatteryOptimizationSettingsIntent())

        // 第三优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "huawei_battery",
            title = "电池管理（手动管理）",
            description = "将应用设为手动管理，允许后台活动不受限制",
            whyRequired = "华为电池管理会自动限制后台应用活动，导致闹钟服务被冻结",
            solveProblem = "解决华为设备上闹钟到点时应用被冻结、铃声不播放、震动不触发等问题",
            isGranted = isIgnoringBatteryOptimizations(context),
            isCritical = false,
            intentCandidates = intents,
            actionLabel = "立即设置",
            vendorSpecial = true,
            vendorName = "华为",
            operationPath = "设置 → 电池 → 更多电池设置 → 关闭「休眠时始终保持网络连接」→ 应用启动管理 → 手动管理",
            iconType = IconType.BATTERY,
            guideId = "battery_optimization"
        )
    }

    private fun buildHuaweiLockScreen(context: Context): PermissionItem {
        val intents = mutableListOf<Intent>()

        // 第一优先：华为锁屏清理设置
        intents.add(getHuaweiLockScreenIntent())

        // 第二优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "huawei_lock_screen",
            title = "锁屏清理白名单",
            description = "将应用加入锁屏清理白名单，防止锁屏时被清理",
            whyRequired = "华为系统在锁屏时会清理后台应用，如果未加入白名单闹钟将无法触发",
            solveProblem = "解决锁屏状态下闹钟完全不触发、只亮屏不响铃等问题",
            isGranted = false,
            isCritical = false,
            intentCandidates = intents,
            actionLabel = "立即设置",
            vendorSpecial = true,
            vendorName = "华为",
            operationPath = "设置 → 应用 → 应用启动管理 → 找到「目标倒计时」 → 手动管理 → 开启「允许后台活动」",
            iconType = IconType.LOCK_SCREEN,
            checkable = false,  // 华为未提供公开 API 检测锁屏清理白名单
            guideId = "auto_start"
        )
    }

    private fun buildXiaomiAutoStart(context: Context): PermissionItem {
        val intents = mutableListOf<Intent>()

        // 第一优先：小米自启动管理
        intents.add(getXiaomiAutoStartIntent())

        // 第二优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "xiaomi_auto_start",
            title = "自启动管理",
            description = "允许应用自启动，确保被清理后可以自动恢复",
            whyRequired = "MIUI 系统会在后台清理应用，未允许自启动的应用无法在闹钟时间唤醒",
            solveProblem = "解决小米/红米设备上闹钟不触发、应用被杀后提醒丢失等问题",
            isGranted = false,
            isCritical = false,
            intentCandidates = intents,
            actionLabel = "立即设置",
            vendorSpecial = true,
            vendorName = "小米",
            operationPath = "设置 → 应用设置 → 授权管理 → 自启动管理 → 找到「目标倒计时」 → 开启开关",
            iconType = IconType.AUTO_START,
            checkable = false,  // 小米未提供公开 API 检测自启动状态
            guideId = "auto_start"
        )
    }

    private fun buildXiaomiBattery(context: Context): PermissionItem {
        val intents = mutableListOf<Intent>()

        // 第一优先：小米省电策略
        intents.add(getXiaomiBatteryIntent())

        // 第二优先：电池优化设置
        intents.add(getBatteryOptimizationSettingsIntent())

        // 第三优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "xiaomi_battery",
            title = "省电策略",
            description = "将应用省电策略设为「无限制」，允许后台运行",
            whyRequired = "MIUI 省电策略会限制后台应用的网络和 CPU 使用，导致闹钟服务被冻结",
            solveProblem = "解决小米设备上闹钟延迟、到点不响、后台被限制等问题",
            isGranted = isIgnoringBatteryOptimizations(context),
            isCritical = false,
            intentCandidates = intents,
            actionLabel = "立即设置",
            vendorSpecial = true,
            vendorName = "小米",
            operationPath = "设置 → 应用设置 → 应用管理 → 找到「目标倒计时」 → 省电策略 → 选择「无限制」",
            iconType = IconType.BATTERY,
            guideId = "battery_optimization"
        )
    }

    private fun buildOppoAutoStart(context: Context): PermissionItem {
        val intents = mutableListOf<Intent>()

        // 第一优先：OPPO 自启动管理
        intents.add(getOppoAutoStartIntent())

        // 第二优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "oppo_auto_start",
            title = "自启动管理",
            description = "允许应用自启动，确保被清理后可以自动恢复",
            whyRequired = "ColorOS 系统会在后台清理应用，未允许自启动的应用无法在闹钟时间唤醒",
            solveProblem = "解决 OPPO/一加设备上闹钟不触发、应用被杀后提醒丢失等问题",
            isGranted = false,
            isCritical = false,
            intentCandidates = intents,
            actionLabel = "立即设置",
            vendorSpecial = true,
            vendorName = if (isOnePlusDevice()) "一加" else "OPPO",
            operationPath = "设置 → 应用管理 → 自启动管理 → 找到「目标倒计时」 → 开启开关",
            iconType = IconType.AUTO_START,
            checkable = false,  // OPPO 未提供公开 API 检测自启动状态
            guideId = "auto_start"
        )
    }

    private fun buildVivoAutoStart(context: Context): PermissionItem {
        val intents = mutableListOf<Intent>()

        // 第一优先：vivo 后台弹窗管理
        intents.add(getVivoAutoStartIntent())

        // 第二优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "vivo_auto_start",
            title = "后台弹窗管理",
            description = "允许应用在后台弹出界面和自启动",
            whyRequired = "OriginOS/FuntouchOS 会限制后台应用弹出界面，导致全屏闹钟无法显示",
            solveProblem = "解决 vivo/iQOO 设备上闹钟到点不弹全屏界面、后台无法启动等问题",
            isGranted = false,
            isCritical = false,
            intentCandidates = intents,
            actionLabel = "立即设置",
            vendorSpecial = true,
            vendorName = "vivo",
            operationPath = "设置 → 更多设置 → 权限管理 → 后台弹窗 → 找到「目标倒计时」 → 开启开关",
            iconType = IconType.AUTO_START,
            checkable = false,  // vivo 未提供公开 API 检测后台弹窗状态
            guideId = "auto_start"
        )
    }

    private fun buildSamsungBattery(context: Context): PermissionItem {
        val intents = mutableListOf<Intent>()

        // 第一优先：三星电池管理
        intents.add(getSamsungBatteryIntent())

        // 第二优先：电池优化设置
        intents.add(getBatteryOptimizationSettingsIntent())

        // 第三优先：应用详情页
        intents.add(getAppDetailsIntent(context))

        return PermissionItem(
            id = "samsung_battery",
            title = "电池优化",
            description = "将应用排除在电池优化之外",
            whyRequired = "One UI 的电池优化会在后台限制应用运行，导致闹钟服务被中断",
            solveProblem = "解决三星设备上闹钟延迟、到点不响、后台被限制等问题",
            isGranted = isIgnoringBatteryOptimizations(context),
            isCritical = false,
            intentCandidates = intents,
            actionLabel = "立即设置",
            vendorSpecial = true,
            vendorName = "三星",
            operationPath = "设置 → 电池和设备维护 → 电池 → 后台使用限制 → 找到「目标倒计时」 → 设为「不受限制」",
            iconType = IconType.BATTERY,
            guideId = "battery_optimization"
        )
    }

    // ==================== 智能跳转方法 ====================

    /**
     * 尝试打开权限设置页面
     *
     * 降级策略：
     * 1. 依次尝试 intentCandidates 列表中的每个 Intent
     * 2. 如果全部失败，尝试应用详情页
     * 3. 如果连应用详情页都打不开，返回 Failed
     *
     * @return 跳转结果
     */
    fun tryOpenPermission(context: Context, item: PermissionItem): OpenResult {
        // 依次尝试候选 Intent
        for (intent in item.intentCandidates) {
            try {
                val finalIntent = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(finalIntent)
                Log.d(TAG, "Successfully opened settings for ${item.id}")
                return OpenResult.DirectJump(finalIntent)
            } catch (e: Exception) {
                Log.d(TAG, "Intent failed for ${item.id}: ${e.message}, trying next...")
            }
        }

        // 所有候选都失败，尝试应用详情页
        try {
            val appDetails = getAppDetailsIntent(context)
            context.startActivity(appDetails)
            Log.w(TAG, "Fell back to app details for ${item.id}")
            return OpenResult.FallbackToAppDetails("无法直接打开${item.title}设置页面，已跳转到应用详情页")
        } catch (e: Exception) {
            Log.e(TAG, "Even app details failed for ${item.id}", e)
        }

        // 完全失败
        val path = item.operationPath ?: "请前往系统设置手动开启此权限"
        return OpenResult.Failed("无法自动跳转到设置页面", path)
    }

    /**
     * 兼容旧接口：打开权限设置（直接调用 tryOpenPermission，不返回结果）
     */
    fun openPermissionSettings(context: Context, item: PermissionItem) {
        tryOpenPermission(context, item)
    }

    /**
     * 获取缺失关键权限的简短描述列表
     */
    fun getMissingCriticalPermissionDescriptions(result: PermissionResult): List<String> {
        return result.items
            .filter { it.isCritical && !it.isGranted }
            .map { "${it.title}：${it.description}" }
    }
}
