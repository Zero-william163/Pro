package com.countdown.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.countdown.app.data.CountdownData
import com.countdown.app.data.CountdownRepository
import com.countdown.app.service.DownloadService
import com.countdown.app.ui.components.AnimatedEntrance
import com.countdown.app.ui.components.CountdownHeroCard
import com.countdown.app.ui.components.InfoCardItem
import com.countdown.app.ui.permission.PermissionActivity
import com.countdown.app.ui.ringtone.RingtoneSettingsActivity
import com.countdown.app.ui.theme.CountdownTheme
import com.countdown.app.update.UpdateChecker
import com.countdown.app.update.UpdateCheckResult
import com.countdown.app.update.UpdateInfo
import com.countdown.app.update.UpdateLogger
import com.countdown.app.update.UpdatePreferences
import com.countdown.app.util.AlarmScheduler
import com.countdown.app.util.DateCalculator
import com.countdown.app.util.NotificationHelper
import com.countdown.app.util.PermissionChecker
import com.countdown.app.widget.CountdownWidgetReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "通知权限被拒绝，提醒功能可能无法正常工作", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ 请求通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val repository = CountdownRepository.getInstance(this)

        setContent {
            val themeMode by repository.countdownDataFlow.collectAsState(initial = CountdownData())
            val darkTheme = when (themeMode.themeMode) {
                CountdownData.THEME_DARK -> true
                CountdownData.THEME_LIGHT -> false
                else -> null
            }

            CountdownTheme(darkTheme = darkTheme ?: androidx.compose.foundation.isSystemInDarkTheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        repository = repository,
                        onRequestAlarmPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                AlarmScheduler.openExactAlarmSettings(this)
                            }
                        },
                        onOpenNotificationSettings = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            }
                            startActivity(intent)
                        },
                        onOpenPermissionCenter = {
                            PermissionActivity.start(this)
                        },
                        onOpenRingtoneSettings = {
                            RingtoneSettingsActivity.start(this)
                        },
                        onCheckUpdate = { onUpdateCheckResult ->
                            lifecycleScope.launch {
                                val result = UpdateChecker.checkUpdate(this@MainActivity)
                                onUpdateCheckResult(result)
                            }
                        },
                        onCheckUpdateAuto = { onAutoCheckResult ->
                            lifecycleScope.launch {
                                val result = UpdateChecker.checkUpdateAuto(this@MainActivity)
                                onAutoCheckResult(result)
                            }
                        },
                        onStartDownload = { urls, versionName ->
                            val intent = Intent(this, DownloadService::class.java).apply {
                                putExtra(DownloadService.EXTRA_DOWNLOAD_URLS, urls.toTypedArray())
                                putExtra(DownloadService.EXTRA_VERSION_NAME, versionName)
                            }
                            ContextCompat.startForegroundService(this, intent)
                        },
                        onIgnoreVersion = { version ->
                            UpdateChecker.ignoreVersion(this@MainActivity, version)
                        },
                        onUpdateWidget = {
                            CountdownWidgetReceiver.updateAllWidgets(this@MainActivity)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到主界面刷新小组件
        CountdownWidgetReceiver.updateAllWidgets(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: CountdownRepository,
    onRequestAlarmPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenPermissionCenter: () -> Unit,
    onOpenRingtoneSettings: () -> Unit,
    onCheckUpdate: ((Result<UpdateChecker.UpdateResult>) -> Unit) -> Unit,
    onCheckUpdateAuto: ((UpdateCheckResult) -> Unit) -> Unit,
    onStartDownload: (List<String>, String) -> Unit,
    onIgnoreVersion: (String) -> Unit,
    onUpdateWidget: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val countdownData by repository.countdownDataFlow.collectAsState(initial = CountdownData())
    var currentTime by remember { mutableStateOf(DateCalculator.formatCurrentTime()) }

    // 权限检测结果（用于显示顶部提示）
    var permissionResult by remember {
        mutableStateOf(PermissionChecker.checkAllPermissions(context))
    }

    // 重新检测权限
    fun refreshPermissions() {
        permissionResult = PermissionChecker.checkAllPermissions(context)
    }

    // 更新当前时间每秒
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = DateCalculator.formatCurrentTime()
            delay(1000)
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showEditSettings by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf<UpdateChecker.UpdateResult.UpdateAvailable?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showWidgetPrompt by remember { mutableStateOf(false) }

    // ===== 自动检测更新状态 =====
    var autoUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showChangelogDialog by remember { mutableStateOf<UpdateInfo?>(null) }
    val mainLifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    // ===== 启动时自动检测更新（完全静默，仅发现新版本时显示横幅） =====
    LaunchedEffect(Unit) {
        onCheckUpdateAuto { result ->
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    autoUpdateInfo = result.updateInfo
                }
                is UpdateCheckResult.UpToDate -> { /* 静默，不打扰用户 */ }
                is UpdateCheckResult.LocalNewer -> { /* 静默 */ }
                is UpdateCheckResult.Error -> { /* 静默 */ }
            }
        }
    }

    // ===== 应用回到前台时自动检测更新 =====
    DisposableEffect(mainLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
                // 自动检测更新（有6小时缓存，不会频繁请求）
                onCheckUpdateAuto { result ->
                    when (result) {
                        is UpdateCheckResult.UpdateAvailable -> {
                            autoUpdateInfo = result.updateInfo
                        }
                        is UpdateCheckResult.UpToDate -> {
                            autoUpdateInfo = null
                        }
                        is UpdateCheckResult.LocalNewer -> { /* 静默 */ }
                        is UpdateCheckResult.Error -> { /* 静默 */ }
                    }
                }
            }
        }
        mainLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            mainLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ===== 启动时检测桌面小组件（真实检测，不使用 SharedPreferences） =====
    // 仅在应用启动时检测一次：如果桌面没有小组件且提醒已启用，才提示用户添加
    LaunchedEffect(Unit) {
        if (countdownData.reminderEnabled) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, CountdownWidgetReceiver::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds.isEmpty()) {
                showWidgetPrompt = true
            }
        }
    }

    val daysRemaining = DateCalculator.daysRemaining(countdownData.targetDate)
    val targetReached = DateCalculator.isTargetReached(countdownData.targetDate)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("目标倒计时") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    // 权限中心入口
                    IconButton(onClick = onOpenPermissionCenter) {
                        val hasIssues = !permissionResult.criticalGranted
                        Icon(
                            imageVector = if (hasIssues) Icons.Default.Warning else Icons.Default.Security,
                            contentDescription = "权限中心",
                            tint = if (hasIssues) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                    IconButton(onClick = {
                        isCheckingUpdate = true
                        onCheckUpdate { result ->
                            isCheckingUpdate = false
                            result.onSuccess { updateResult ->
                                when (updateResult) {
                                    is UpdateChecker.UpdateResult.UpdateAvailable -> {
                                        showUpdateDialog = updateResult
                                    }
                                    is UpdateChecker.UpdateResult.UpToDate -> {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("当前已是最新版本")
                                        }
                                    }
                                    is UpdateChecker.UpdateResult.LocalNewer -> {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("当前版本高于最新正式版")
                                        }
                                    }
                                }
                            }.onFailure { e ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("检查更新失败，请稍后重试")
                                }
                            }
                        }
                    }) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.SystemUpdate, contentDescription = "检查更新")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        var contentVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { contentVisible = true }
        val contentAlpha by animateFloatAsState(
            targetValue = if (contentVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 600),
            label = "contentAlpha"
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== 权限缺失提示栏（关键） =====
            if (!permissionResult.criticalGranted) {
                PermissionWarningCard(
                    onClick = onOpenPermissionCenter,
                    missingCount = permissionResult.items.count { it.isCritical && !it.isGranted }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ===== 自动检测更新 Banner =====
            autoUpdateInfo?.let { info ->
                UpdateBanner(
                    updateInfo = info,
                    onUpdate = {
                        val urls = info.getAllDownloadUrls()
                        if (urls.isNotEmpty()) {
                            onStartDownload(urls, info.versionName)
                            autoUpdateInfo = null
                            scope.launch { snackbarHostState.showSnackbar("开始下载更新…") }
                        } else {
                            // 下载地址为空，重新检测获取完整信息
                            scope.launch { snackbarHostState.showSnackbar("正在获取下载地址…") }
                            onCheckUpdateAuto { result ->
                                when (result) {
                                    is UpdateCheckResult.UpdateAvailable -> {
                                        val newUrls = result.updateInfo.getAllDownloadUrls()
                                        if (newUrls.isNotEmpty()) {
                                            onStartDownload(newUrls, result.updateInfo.versionName)
                                            autoUpdateInfo = null
                                            scope.launch { snackbarHostState.showSnackbar("开始下载更新…") }
                                        } else {
                                            scope.launch { snackbarHostState.showSnackbar("无法获取下载地址，请稍后重试") }
                                        }
                                    }
                                    else -> {
                                        scope.launch { snackbarHostState.showSnackbar("无法获取下载地址，请稍后重试") }
                                    }
                                }
                            }
                        }
                    },
                    onLater = { autoUpdateInfo = null },
                    onIgnore = {
                        onIgnoreVersion(info.versionName)
                        autoUpdateInfo = null
                        scope.launch { snackbarHostState.showSnackbar("已忽略此版本") }
                    },
                    onViewChangelog = { showChangelogDialog = info }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 华为设备提示
            if (permissionResult.isHuaweiDevice && !permissionResult.allGranted) {
                HuaweiTipCard(onClick = onOpenPermissionCenter)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ===== Hero Countdown Card (visual center) =====
            AnimatedEntrance(visible = contentVisible, delayMillis = 100) {
                CountdownHeroCard(
                    eventContent = countdownData.eventContent,
                    daysRemaining = daysRemaining,
                    targetReached = targetReached,
                    targetDate = DateCalculator.formatDate(countdownData.targetDate),
                    reminderText = if (countdownData.reminderEnabled) {
                        DateCalculator.formatTime(countdownData.reminderTimeHour, countdownData.reminderTimeMinute)
                    } else "未设置",
                    nextReminder = if (countdownData.reminderEnabled) {
                        DateCalculator.getNextReminderString(countdownData.reminderTimeHour, countdownData.reminderTimeMinute)
                    } else ""
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ===== Info Cards =====
            AnimatedEntrance(visible = contentVisible, delayMillis = 200) {
                InfoCardItem(
                    icon = Icons.Default.AccessTime,
                    title = "当前时间",
                    value = currentTime
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedEntrance(visible = contentVisible, delayMillis = 300) {
                InfoCardItem(
                    icon = Icons.Default.NotificationsActive,
                    title = "每日提醒",
                    value = if (countdownData.reminderEnabled) {
                        "${DateCalculator.formatTime(countdownData.reminderTimeHour, countdownData.reminderTimeMinute)} · ${DateCalculator.getNextReminderString(countdownData.reminderTimeHour, countdownData.reminderTimeMinute)}"
                    } else "已关闭",
                    valueColor = if (countdownData.reminderEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedEntrance(visible = contentVisible, delayMillis = 400) {
                InfoCardItem(
                    icon = if (countdownData.reminderEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    title = "提醒状态",
                    value = if (countdownData.reminderEnabled) "已启用" else "已禁用",
                    valueColor = if (countdownData.reminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    iconTint = if (countdownData.reminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ===== 闹钟铃声设置入口 =====
            AnimatedEntrance(visible = contentVisible, delayMillis = 500) {
                InfoCardItem(
                    icon = Icons.Default.Alarm,
                    title = "闹钟铃声",
                    value = com.countdown.app.util.RingtoneManager.getCurrentRingtoneName(context),
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    clickable = true,
                    onClick = onOpenRingtoneSettings
                )
            }

            // 旧版简单权限警告（保留作为兜底）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !AlarmScheduler.canScheduleExactAlarms(context)) {
                Spacer(modifier = Modifier.height(8.dp))
                SimplePermissionCard(
                    text = "需要精确闹钟权限，点击前往设置",
                    onClick = onRequestAlarmPermission
                )
            }

            if (!NotificationHelper.areNotificationsEnabled(context)) {
                Spacer(modifier = Modifier.height(8.dp))
                SimplePermissionCard(
                    text = "需要通知权限，点击前往设置",
                    onClick = onOpenNotificationSettings
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 编辑设置按钮
            AnimatedEntrance(visible = contentVisible, delayMillis = 600) {
                com.countdown.app.ui.components.BrandButton(
                    text = "编辑设置",
                    icon = Icons.Default.Edit,
                    onClick = { showEditSettings = true }
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // ===== 设置页面（齿轮图标） =====
    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false }
        )
    }

    // ===== 编辑设置对话框（编辑设置按钮） =====
    if (showEditSettings) {
        EditSettingsDialog(
            data = countdownData,
            onDismiss = { showEditSettings = false },
            onSave = { newData ->
                scope.launch {
                    repository.saveCountdownData(newData)
                    if (newData.reminderEnabled) {
                        AlarmScheduler.scheduleDailyAlarm(context, newData.reminderTimeHour, newData.reminderTimeMinute)
                    } else {
                        AlarmScheduler.cancelAlarm(context)
                    }
                    onUpdateWidget()
                    snackbarHostState.showSnackbar("设置已保存")
                    refreshPermissions()
                }
                showEditSettings = false
                if (newData.reminderEnabled) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, CountdownWidgetReceiver::class.java)
                    val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
                    if (widgetIds.isEmpty()) {
                        showWidgetPrompt = true
                    }
                }
            }
        )
    }

    // ===== 小组件提示对话框 =====
    if (showWidgetPrompt) {
        AlertDialog(
            onDismissRequest = { showWidgetPrompt = false },
            title = { Text("添加桌面小组件") },
            text = { Text("倒计时提醒已设置完成！建议添加桌面小组件，无需打开应用即可随时查看剩余天数。") },
            confirmButton = {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
                        val componentName = ComponentName(context, CountdownWidgetReceiver::class.java)
                        if (appWidgetManager?.isRequestPinAppWidgetSupported == true) {
                            appWidgetManager.requestPinAppWidget(componentName, null, null)
                        } else {
                            Toast.makeText(context, "请长按桌面空白处，选择「倒计时提醒」小组件", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "请长按桌面空白处，选择「倒计时提醒」小组件", Toast.LENGTH_LONG).show()
                    }
                    showWidgetPrompt = false
                }) {
                    Text("去添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWidgetPrompt = false }) {
                    Text("以后再说")
                }
            }
        )
    }

    // ===== 更新对话框（手动检查触发） =====
    showUpdateDialog?.let { updateInfo ->
        AlertDialog(
            onDismissRequest = { showUpdateDialog = null },
            title = { Text("发现新版本 v${updateInfo.remoteVersion.tagName}") },
            text = {
                Column {
                    Text(
                        "当前版本: ${updateInfo.installedVersion.versionName}\n" +
                        "新版本: ${updateInfo.remoteVersion.tagName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (updateInfo.remoteVersion.releaseNotes.isNotBlank()) {
                        Text(
                            updateInfo.remoteVersion.releaseNotes,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val urls = updateInfo.updateInfo?.getAllDownloadUrls()
                        ?: listOf(updateInfo.remoteVersion.downloadUrl)
                    onStartDownload(urls, updateInfo.remoteVersion.tagName)
                    showUpdateDialog = null
                    scope.launch { snackbarHostState.showSnackbar("开始下载更新…") }
                }) {
                    Text("下载更新")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onIgnoreVersion(updateInfo.remoteVersion.tagName)
                        showUpdateDialog = null
                        scope.launch { snackbarHostState.showSnackbar("已忽略此版本") }
                    }) {
                        Text("忽略")
                    }
                    TextButton(onClick = { showUpdateDialog = null }) {
                        Text("稍后")
                    }
                }
            }
        )
    }

    // ===== 更新日志对话框（从 Banner 查看） =====
    showChangelogDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { showChangelogDialog = null },
            title = { Text("更新日志 v${info.versionName}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        info.releaseNotes.ifBlank { "暂无更新日志" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (info.publishedAt.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "发布时间: ${info.publishedAt}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelogDialog = null }) {
                    Text("关闭")
                }
            }
        )
    }

}

// ==================== 新增 UI 组件 ====================

@Composable
fun PermissionWarningCard(
    onClick: () -> Unit,
    missingCount: Int
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "permCardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(),
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "权限缺失提醒",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "有 $missingCount 项关键权限未开启，提醒功能可能无法正常工作",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun HuaweiTipCard(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "huaweiCardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(),
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "华为设备优化",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "检测到华为设备，建议完成专项设置以确保提醒可靠触发",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SimplePermissionCard(text: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ==================== 更新 Banner 组件 ====================

/**
 * 更新提示 Banner（Material Design 3 风格）。
 *
 * 轻量、美观、不打断用户操作。
 * 显示在首页顶部，用户可以：
 * - 立即更新
 * - 稍后提醒
 * - 忽略本次版本
 * - 查看更新日志
 */
@Composable
fun UpdateBanner(
    updateInfo: UpdateInfo,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onIgnore: () -> Unit,
    onViewChangelog: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "updateBannerScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp,
            pressedElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "发现新版本 v${updateInfo.versionName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "点击立即更新，享受最新功能",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onIgnore) {
                    Text("忽略", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                }
                TextButton(onClick = onViewChangelog) {
                    Text("更新日志")
                }
                TextButton(onClick = onLater) {
                    Text("稍后")
                }
                Button(
                    onClick = onUpdate,
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("立即更新")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // ===== 应用信息 =====
                SettingsSectionTitle("应用信息")

                SettingsRowCard(
                    icon = Icons.Default.Info,
                    iconBgColor = Color(0xFF3B82F6),
                    title = "应用名称",
                    subtitle = "目标倒计时"
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsRowCard(
                    icon = Icons.Default.Tag,
                    iconBgColor = Color(0xFF10B981),
                    title = "当前版本",
                    subtitle = try {
                        val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        }
                        pi.versionName ?: "未知"
                    } catch (e: Exception) { "未知" }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ===== 关于 =====
                SettingsSectionTitle("关于")

                SettingsRowCard(
                    icon = Icons.Default.Info,
                    iconBgColor = Color(0xFF6366F1),
                    title = "关于软件",
                    subtitle = "目标倒计时 - 精准倒计时提醒工具"
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsRowCard(
                    icon = Icons.Default.Person,
                    iconBgColor = Color(0xFF8B5CF6),
                    title = "开发者信息",
                    subtitle = "Zero-william163"
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsRowCard(
                    icon = Icons.Default.Code,
                    iconBgColor = Color(0xFFEC4899),
                    title = "GitHub 仓库",
                    subtitle = "github.com/Zero-william163/Pro"
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsRowCard(
                    icon = Icons.Default.Description,
                    iconBgColor = Color(0xFF0EA5E9),
                    title = "开源协议",
                    subtitle = "MIT License"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("关闭")
            }
        }
    )
}

// ==================== 编辑设置对话框 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSettingsDialog(
    data: CountdownData,
    onDismiss: () -> Unit,
    onSave: (CountdownData) -> Unit
) {
    var eventContent by remember { mutableStateOf(data.eventContent) }
    var targetDate by remember { mutableStateOf(data.targetDate) }
    var reminderHour by remember { mutableIntStateOf(data.reminderTimeHour) }
    var reminderMinute by remember { mutableIntStateOf(data.reminderTimeMinute) }
    var reminderEnabled by remember { mutableStateOf(data.reminderEnabled) }
    var themeMode by remember { mutableIntStateOf(data.themeMode) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑设置", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // ===== 事件名称 =====
                SettingsSectionTitle("事件名称")

                OutlinedTextField(
                    value = eventContent,
                    onValueChange = { eventContent = it },
                    placeholder = { Text("输入事件名称，如：考试、生日、旅行…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ===== 日期与提醒分组 =====
                SettingsSectionTitle("日期与提醒")

                SettingsRowCard(
                    icon = Icons.Default.CalendarToday,
                    iconBgColor = Color(0xFF6366F1),
                    title = "目标日期",
                    subtitle = DateCalculator.formatDate(targetDate),
                    onClick = { showDatePicker = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsRowCard(
                    icon = Icons.Default.AccessTime,
                    iconBgColor = Color(0xFF0EA5E9),
                    title = "每日提醒时间",
                    subtitle = DateCalculator.formatTime(reminderHour, reminderMinute),
                    onClick = { showTimePicker = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 启用每日提醒
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIcon(
                            icon = if (reminderEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            bgColor = if (reminderEnabled) Color(0xFFEC4899) else Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "启用每日提醒",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { reminderEnabled = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ===== 外观分组 =====
                SettingsSectionTitle("外观")

                var themeExpanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clickable { themeExpanded = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIcon(
                            icon = when (themeMode) {
                                CountdownData.THEME_DARK -> Icons.Default.DarkMode
                                CountdownData.THEME_LIGHT -> Icons.Default.LightMode
                                else -> Icons.Default.Settings
                            },
                            bgColor = Color(0xFF8B5CF6)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("主题模式", style = MaterialTheme.typography.bodySmall)
                            Text(
                                when (themeMode) {
                                    CountdownData.THEME_DARK -> "深色模式"
                                    CountdownData.THEME_LIGHT -> "浅色模式"
                                    else -> "跟随系统"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        DropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("跟随系统") },
                                onClick = { themeMode = CountdownData.THEME_SYSTEM; themeExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("浅色模式") },
                                onClick = { themeMode = CountdownData.THEME_LIGHT; themeExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("深色模式") },
                                onClick = { themeMode = CountdownData.THEME_DARK; themeExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        CountdownData(
                            eventContent = eventContent.trim(),
                            targetDate = targetDate,
                            reminderTimeHour = reminderHour,
                            reminderTimeMinute = reminderMinute,
                            reminderEnabled = reminderEnabled,
                            themeMode = themeMode,
                            ringtoneType = data.ringtoneType,
                            ringtoneUri = data.ringtoneUri,
                            ringtoneName = data.ringtoneName
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    // 时间选择器
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = reminderHour,
            initialMinute = reminderMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择提醒时间") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    reminderHour = timePickerState.hour
                    reminderMinute = timePickerState.minute
                    showTimePicker = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 日期选择器
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = targetDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        targetDate = selected
                    }
                    showDatePicker = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ==================== 设置页面辅助组件 ====================

/** 分组标题 */
@Composable
fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/** 彩色圆形图标 */
@Composable
fun SettingsIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bgColor: Color,
    size: Int = 36
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 2).dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((size * 0.55).dp)
        )
    }
}

/** 带彩色圆形图标的设置行卡片 */
@Composable
fun SettingsRowCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    val mod = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    } else {
        Modifier.fillMaxWidth()
    }
    Card(
        modifier = mod,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(icon = icon, bgColor = iconBgColor)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodySmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
