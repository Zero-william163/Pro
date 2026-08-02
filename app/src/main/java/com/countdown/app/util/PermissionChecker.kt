package com.countdown.app.util

import android.app.AlarmManager
import android.app.NotificationManager
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
 * 权限检测与引导工具类（重构 v2）
 *
 * 全面检测所有影响闹钟正常工作的权限和系统设置：
 * ① 通知权限（POST_NOTIFICATIONS）
 * ② 精确闹钟权限（SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM）
 * ③ 全屏通知权限（Full Screen Intent 相关权限）
 * ④ 忽略电池优化
 * ⑤ 后台运行权限
 * ⑥ 自启动权限（设备支持时）
 * ⑦ 锁屏显示权限
 * ⑧ 华为后台保护
 * ⑨ 华为应用启动管理
 * ⑩ 其他会影响闹钟正常工作的权限
 *
 * 每次启动应用时自动检测，缺失权限时主动提示用户。
 */
object PermissionChecker {

    private const val TAG = "PermissionChecker"

    // ==================== 权限数据模型 ====================

    data class PermissionItem(
        val id: String,
        val title: String,
        val description: String,
        val benefit: String,
        val isGranted: Boolean,
        val isCritical: Boolean,
        val actionIntent: Intent?,
        val actionLabel: String,
        val huaweiSpecial: Boolean = false,
        val actionGuide: String? = null
    )

    data class PermissionResult(
        val allGranted: Boolean,
        val criticalGranted: Boolean,
        val items: List<PermissionItem>,
        val isHuaweiDevice: Boolean,
        val missingCriticalCount: Int
    )

    // ==================== 设备检测 ====================

    fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("huawei") ||
               brand.contains("huawei") ||
               manufacturer.contains("honor") ||
               brand.contains("honor")
    }

    fun isXiaomiDevice(): Boolean {
        return Build.MANUFACTURER.lowercase().contains("xiaomi") ||
               Build.BRAND.lowercase().contains("xiaomi") ||
               Build.MANUFACTURER.lowercase().contains("redmi") ||
               Build.BRAND.lowercase().contains("redmi")
    }

    fun isOppoDevice(): Boolean {
        return Build.MANUFACTURER.lowercase().contains("oppo") ||
               Build.BRAND.lowercase().contains("oppo") ||
               Build.MANUFACTURER.lowercase().contains("realme") ||
               Build.BRAND.lowercase().contains("realme")
    }

    fun isVivoDevice(): Boolean {
        return Build.MANUFACTURER.lowercase().contains("vivo") ||
               Build.BRAND.lowercase().contains("vivo") ||
               Build.MANUFACTURER.lowercase().contains("iqoo") ||
               Build.BRAND.lowercase().contains("iqoo")
    }

    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.lowercase().contains("samsung")
    }

    // ==================== 单项权限检测 ====================

    /**
     * ① 检测通知权限（Android 13+ 运行时权限）
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * 检测通知是否被用户在系统设置中关闭
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * ② 检测精确闹钟权限（Android 12+）
     */
    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * ③ 检测全屏通知权限（Android 14+）
     */
    fun hasFullScreenIntentPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.canUseFullScreenIntent()
        } else {
            true
        }
    }

    /**
     * ④ 检测是否忽略电池优化
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * ⑤ 检测悬浮窗权限（用于降级方案）
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * ⑥ 检测前台服务权限（Android 9+）
     */
    fun hasForegroundServicePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.FOREGROUND_SERVICE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * ⑦ 检测前台服务特殊类型权限（Android 14+）
     */
    fun hasForegroundServiceMediaPlaybackPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * ⑧ 检测通知渠道是否被禁用
     */
    fun isAlarmChannelEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID_ALARM)
            channel?.importance != NotificationManager.IMPORTANCE_NONE
        } else {
            true
        }
    }

    // ==================== 全面权限检测 ====================

    fun checkAllPermissions(context: Context): PermissionResult {
        val items = mutableListOf<PermissionItem>()

        // ===== 关键权限（必须开启）=====

        // 1. 通知权限（运行时权限）
        val notifGranted = hasNotificationPermission(context)
        items.add(
            PermissionItem(
                id = "notification_permission",
                title = "通知权限",
                description = "发送提醒通知的必需权限",
                benefit = "开启后，到点时可以收到通知栏提醒和横幅提醒",
                isGranted = notifGranted,
                isCritical = true,
                actionIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else null,
                actionLabel = "前往开启"
            )
        )

        // 2. 通知开关（用户在系统设置中是否关闭了通知）
        val notifEnabled = areNotificationsEnabled(context)
        items.add(
            PermissionItem(
                id = "notification_enabled",
                title = "通知总开关",
                description = "系统通知总开关是否开启",
                benefit = "开启后，应用可以显示所有类型的通知",
                isGranted = notifEnabled,
                isCritical = true,
                actionIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                },
                actionLabel = "前往开启"
            )
        )

        // 3. 精确闹钟权限
        val exactAlarmGranted = hasExactAlarmPermission(context)
        items.add(
            PermissionItem(
                id = "exact_alarm",
                title = "精确闹钟权限",
                description = "在准确时间触发提醒的必需权限",
                benefit = "开启后，提醒会在设定的时间准时触发，不会延迟",
                isGranted = exactAlarmGranted,
                isCritical = true,
                actionIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                } else null,
                actionLabel = "前往开启"
            )
        )

        // 4. 全屏通知权限
        val fullScreenGranted = hasFullScreenIntentPermission(context)
        items.add(
            PermissionItem(
                id = "full_screen_intent",
                title = "全屏通知权限",
                description = "锁屏时弹出全屏提醒界面的权限",
                benefit = "开启后，锁屏状态下到点会自动弹出全屏闹钟界面",
                isGranted = fullScreenGranted,
                isCritical = true,
                actionIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                } else null,
                actionLabel = "前往开启"
            )
        )

        // 5. 忽略电池优化
        val batteryOptGranted = isIgnoringBatteryOptimizations(context)
        items.add(
            PermissionItem(
                id = "battery_optimization",
                title = "忽略电池优化",
                description = "防止系统限制后台运行导致提醒失效",
                benefit = "开启后，系统不会在后台杀死应用，确保提醒准时触发",
                isGranted = batteryOptGranted,
                isCritical = true,
                actionIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                },
                actionLabel = "前往开启"
            )
        )

        // 6. 闹钟通知渠道
        val alarmChannelEnabled = isAlarmChannelEnabled(context)
        items.add(
            PermissionItem(
                id = "alarm_channel",
                title = "闹钟通知渠道",
                description = "确保闹钟通知渠道未被禁用",
                benefit = "开启后，闹钟通知可以正常显示并触发全屏界面",
                isGranted = alarmChannelEnabled,
                isCritical = true,
                actionIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_ID_ALARM)
                    }
                } else null,
                actionLabel = "前往设置"
            )
        )

        // ===== 建议开启权限 =====

        // 7. 悬浮窗权限（降级方案用）
        val overlayGranted = canDrawOverlays(context)
        items.add(
            PermissionItem(
                id = "system_alert_window",
                title = "悬浮窗权限",
                description = "无法全屏时以悬浮窗方式提醒（降级方案）",
                benefit = "开启后，即使全屏通知权限受限，也能以悬浮窗方式提醒",
                isGranted = overlayGranted,
                isCritical = false,
                actionIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                } else null,
                actionLabel = "前往开启"
            )
        )

        // 8. 前台服务权限
        val fgServiceGranted = hasForegroundServicePermission(context)
        items.add(
            PermissionItem(
                id = "foreground_service",
                title = "前台服务权限",
                description = "允许应用在前台播放闹钟声音和震动",
                benefit = "开启后，闹钟响起时可以持续播放声音和震动",
                isGranted = fgServiceGranted,
                isCritical = false,
                actionIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                },
                actionLabel = "前往查看"
            )
        )

        // ===== 华为专项权限 =====
        if (isHuaweiDevice()) {
            // 华为自启动管理
            items.add(
                PermissionItem(
                    id = "huawei_auto_start",
                    title = "华为自启动管理",
                    description = "允许应用自启动，防止被系统清理",
                    benefit = "开启后，应用可以在开机或被清理后自动启动，确保提醒不丢失",
                    isGranted = false, // 华为没有 API 检测，默认提示用户去设置
                    isCritical = false,
                    actionIntent = getHuaweiAutoStartIntent(),
                    actionLabel = "前往设置",
                    huaweiSpecial = true,
                    actionGuide = "在应用启动管理中找到「目标倒计时」，开启自启动开关"
                )
            )

            // 华为电池管理（手动管理）
            items.add(
                PermissionItem(
                    id = "huawei_battery",
                    title = "华为电池管理",
                    description = "将应用设为「手动管理」，允许后台活动",
                    benefit = "开启后，系统不会自动限制应用后台运行，确保提醒准时触发",
                    isGranted = false,
                    isCritical = false,
                    actionIntent = getHuaweiBatteryIntent(),
                    actionLabel = "前往设置",
                    huaweiSpecial = true,
                    actionGuide = "在电池管理中找到「目标倒计时」，设为手动管理并允许后台活动"
                )
            )

            // 华为锁屏清理白名单
            items.add(
                PermissionItem(
                    id = "huawei_lock_screen",
                    title = "华为锁屏清理",
                    description = "将应用加入锁屏清理白名单",
                    benefit = "开启后，锁屏状态下系统不会清理应用，确保锁屏时可以弹出全屏提醒",
                    isGranted = false,
                    isCritical = false,
                    actionIntent = getHuaweiLockScreenIntent(),
                    actionLabel = "前往设置",
                    huaweiSpecial = true,
                    actionGuide = "在锁屏清理白名单中添加「目标倒计时」"
                )
            )

            // 华为后台保护
            items.add(
                PermissionItem(
                    id = "huawei_protect_app",
                    title = "华为应用保护",
                    description = "将应用加入受保护应用列表",
                    benefit = "开启后，系统会在后台保护应用运行，防止被意外清理",
                    isGranted = false,
                    isCritical = false,
                    actionIntent = getHuaweiProtectAppIntent(),
                    actionLabel = "前往设置",
                    huaweiSpecial = true,
                    actionGuide = "在受保护应用中开启「目标倒计时」"
                )
            )
        }

        // ===== 小米专项权限 =====
        if (isXiaomiDevice()) {
            items.add(
                PermissionItem(
                    id = "xiaomi_auto_start",
                    title = "小米自启动",
                    description = "允许应用自启动",
                    benefit = "开启后，应用可以在被清理后自动启动，确保提醒不丢失",
                    isGranted = false,
                    isCritical = false,
                    actionIntent = getXiaomiAutoStartIntent(),
                    actionLabel = "前往设置",
                    actionGuide = "在自启动管理中开启「目标倒计时」"
                )
            )

            items.add(
                PermissionItem(
                    id = "xiaomi_battery",
                    title = "小米省电策略",
                    description = "将应用设为无限制",
                    benefit = "开启后，系统不会限制应用后台运行",
                    isGranted = false,
                    isCritical = false,
                    actionIntent = getXiaomiBatteryIntent(),
                    actionLabel = "前往设置",
                    actionGuide = "在省电策略中将「目标倒计时」设为无限制"
                )
            )
        }

        // ===== OPPO/Realme 专项权限 =====
        if (isOppoDevice()) {
            items.add(
                PermissionItem(
                    id = "oppo_auto_start",
                    title = "OPPO自启动",
                    description = "允许应用自启动",
                    benefit = "开启后，应用可以在被清理后自动启动",
                    isGranted = false,
                    isCritical = false,
                    actionIntent = getOppoAutoStartIntent(),
                    actionLabel = "前往设置",
                    actionGuide = "在自启动管理中开启「目标倒计时」"
                )
            )
        }

        // ===== Vivo/IQOO 专项权限 =====
        if (isVivoDevice()) {
            items.add(
                PermissionItem(
                    id = "vivo_auto_start",
                    title = "Vivo自启动",
                    description = "允许应用自启动",
                    benefit = "开启后，应用可以在被清理后自动启动",
                    isGranted = false,
                    isCritical = false,
                    actionIntent = getVivoAutoStartIntent(),
                    actionLabel = "前往设置",
                    actionGuide = "在自启动管理中开启「目标倒计时」"
                )
            )
        }

        // ===== Samsung 专项权限 =====
        if (isSamsungDevice()) {
            items.add(
                PermissionItem(
                    id = "samsung_battery",
                    title = "三星电池优化",
                    description = "将应用排除在电池优化之外",
                    benefit = "开启后，系统不会在后台限制应用运行",
                    isGranted = isIgnoringBatteryOptimizations(context),
                    isCritical = false,
                    actionIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    actionLabel = "前往设置",
                    actionGuide = "在电池优化中将「目标倒计时」设为不优化"
                )
            )
        }

        // 计算结果
        val criticalItems = items.filter { it.isCritical }
        val criticalGranted = criticalItems.all { it.isGranted }
        val allGranted = items.all { it.isGranted }
        val missingCriticalCount = criticalItems.count { !it.isGranted }

        return PermissionResult(
            allGranted = allGranted,
            criticalGranted = criticalGranted,
            items = items,
            isHuaweiDevice = isHuaweiDevice(),
            missingCriticalCount = missingCriticalCount
        )
    }

    // ==================== 华为设备跳转意图 ====================

    private fun getHuaweiAutoStartIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun getHuaweiBatteryIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun getHuaweiLockScreenIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun getHuaweiProtectAppIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // ==================== 小米设备跳转意图 ====================

    private fun getXiaomiAutoStartIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun getXiaomiBatteryIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // ==================== OPPO 设备跳转意图 ====================

    private fun getOppoAutoStartIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // ==================== Vivo 设备跳转意图 ====================

    private fun getVivoAutoStartIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // ==================== 通用跳转方法 ====================

    fun openPermissionSettings(context: Context, item: PermissionItem) {
        if (item.actionIntent != null) {
            try {
                val intent = item.actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open specific settings for ${item.id}, falling back to app settings", e)
                // 降级到应用信息页面
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    Log.e(TAG, "Even fallback settings failed", e2)
                }
            }
        } else {
            // 没有特定意图，打开应用设置
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
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
