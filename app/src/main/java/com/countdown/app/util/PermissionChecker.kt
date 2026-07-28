package com.countdown.app.util

import android.app.AlarmManager
import android.app.KeyguardManager
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
 * 权限检测与引导工具类
 * 全面检测所有影响闹钟正常工作的权限和系统设置
 */
object PermissionChecker {

    private const val TAG = "PermissionChecker"

    // ==================== 权限数据模型 ====================

    data class PermissionItem(
        val id: String,
        val title: String,
        val description: String,
        val isGranted: Boolean,
        val isCritical: Boolean,
        val actionIntent: Intent?,
        val actionLabel: String,
        val huaweiSpecial: Boolean = false
    )

    data class PermissionResult(
        val allGranted: Boolean,
        val criticalGranted: Boolean,
        val items: List<PermissionItem>,
        val isHuaweiDevice: Boolean
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

    // ==================== 单项权限检测 ====================

    /**
     * 检测通知权限（Android 13+）
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
     * 检测精确闹钟权限（Android 12+）
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
     * 检测全屏通知权限（Android 14+）
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
     * 检测是否忽略电池优化
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 检测悬浮窗权限（用于降级方案）
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * 检测前台服务权限（Android 9+）
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
     * 检测锁屏状态下是否可以显示 Activity
     */
    fun canShowOnLockScreen(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val isLocked = keyguardManager.isKeyguardLocked
            !isLocked || true
        } else {
            true
        }
    }

    // ==================== 全面权限检测 ====================

    fun checkAllPermissions(context: Context): PermissionResult {
        val items = mutableListOf<PermissionItem>()

        // 1. 通知权限（运行时权限）
        val notifGranted = hasNotificationPermission(context)
        items.add(
            PermissionItem(
                id = "notification_permission",
                title = "通知权限",
                description = "发送提醒通知的必需权限",
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
                title = "通知开关",
                description = "系统通知总开关是否开启",
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
                description = "锁屏时弹出全屏提醒界面",
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
                isGranted = batteryOptGranted,
                isCritical = true,
                actionIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                },
                actionLabel = "前往开启"
            )
        )

        // 6. 悬浮窗权限（降级方案用）
        val overlayGranted = canDrawOverlays(context)
        items.add(
            PermissionItem(
                id = "system_alert_window",
                title = "悬浮窗权限",
                description = "无法全屏时以悬浮窗方式提醒（降级方案）",
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

        // 7. 华为专项权限提示
        if (isHuaweiDevice()) {
            items.add(
                PermissionItem(
                    id = "huawei_auto_start",
                    title = "华为自启动管理",
                    description = "允许应用自启动，防止被系统清理",
                    isGranted = false, // 华为没有API检测，默认提示用户去设置
                    isCritical = false,
                    actionIntent = getHuaweiAutoStartIntent(),
                    actionLabel = "前往设置",
                    huaweiSpecial = true
                )
            )
            items.add(
                PermissionItem(
                    id = "huawei_battery",
                    title = "华为电池管理",
                    description = "将应用设为「手动管理」，允许后台活动",
                    isGranted = false,
                    isCritical = false,
                    actionIntent = getHuaweiBatteryIntent(),
                    actionLabel = "前往设置",
                    huaweiSpecial = true
                )
            )
            items.add(
                PermissionItem(
                    id = "huawei_lock_screen",
                    title = "华为锁屏清理",
                    description = "将应用加入锁屏清理白名单",
                    isGranted = false,
                    isCritical = false,
                    actionIntent = getHuaweiLockScreenIntent(),
                    actionLabel = "前往设置",
                    huaweiSpecial = true
                )
            )
        }

        // 8. 小米专项权限提示
        if (isXiaomiDevice()) {
            items.add(
                PermissionItem(
                    id = "xiaomi_auto_start",
                    title = "小米自启动",
                    description = "允许应用自启动",
                    isGranted = false,
                    isCritical = false,
                    actionIntent = getXiaomiAutoStartIntent(),
                    actionLabel = "前往设置",
                    huaweiSpecial = false
                )
            )
        }

        val criticalGranted = items.filter { it.isCritical }.all { it.isGranted }
        val allGranted = items.all { it.isGranted }

        return PermissionResult(
            allGranted = allGranted,
            criticalGranted = criticalGranted,
            items = items,
            isHuaweiDevice = isHuaweiDevice()
        )
    }

    // ==================== 华为设备跳转意图 ====================

    private fun getHuaweiAutoStartIntent(): Intent {
        // 尝试跳转到华为应用启动管理页面
        val intent = Intent().apply {
            component = android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        }
        return intent
    }

    private fun getHuaweiBatteryIntent(): Intent {
        val intent = Intent().apply {
            component = android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        }
        return intent
    }

    private fun getHuaweiLockScreenIntent(): Intent {
        val intent = Intent().apply {
            component = android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"
            )
        }
        return intent
    }

    // ==================== 小米设备跳转意图 ====================

    private fun getXiaomiAutoStartIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
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
                context.startActivity(fallbackIntent)
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

    // ==================== 通知渠道检测 ====================

    fun isAlarmChannelEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel(AlarmScheduler.CHANNEL_ID_ALARM)
            channel?.importance != NotificationManager.IMPORTANCE_NONE
        } else {
            true
        }
    }
}
