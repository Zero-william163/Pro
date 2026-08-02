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
                        onStartDownload = { url, versionName ->
                            val intent = Intent(this, DownloadService::class.java).apply {
                                putExtra(DownloadService.EXTRA_DOWNLOAD_URL, url)
                                putExtra(DownloadService.EXTRA_VERSION_NAME, versionName)
                            }
                            ContextCompat.startForegroundService(this, intent)
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
    onStartDownload: (String, String) -> Unit,
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

    // 从设置页面返回时自动刷新权限状态
    val mainLifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(mainLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        mainLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            mainLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf<UpdateChecker.UpdateResult.UpdateAvailable?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showWidgetPrompt by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

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
                    icon = Icons.Default.CalendarToday,
                    title = "目标日期",
                    value = DateCalculator.formatDate(countdownData.targetDate),
                    iconTint = MaterialTheme.colorScheme.primary,
                    clickable = true,
                    onClick = { showDatePicker = true }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedEntrance(visible = contentVisible, delayMillis = 250) {
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
                    valueColor = if (countdownData.reminderEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    clickable = true,
                    onClick = { showTimePicker = true }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedEntrance(visible = contentVisible, delayMillis = 400) {
                InfoCardItem(
                    icon = if (countdownData.reminderEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    title = "提醒状态",
                    value = if (countdownData.reminderEnabled) "已启用（点击关闭）" else "已禁用（点击开启）",
                    valueColor = if (countdownData.reminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    iconTint = if (countdownData.reminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    clickable = true,
                    onClick = {
                        val newEnabled = !countdownData.reminderEnabled
                        scope.launch {
                            repository.saveReminderEnabled(newEnabled)
                            if (newEnabled) {
                                AlarmScheduler.scheduleDailyAlarm(context, countdownData.reminderTimeHour, countdownData.reminderTimeMinute)
                            } else {
                                AlarmScheduler.cancelAlarm(context)
                            }
                            onUpdateWidget()
                            refreshPermissions()
                        }
                    }
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

            Spacer(modifier = Modifier.height(10.dp))

            // ===== 主题模式 =====
            AnimatedEntrance(visible = contentVisible, delayMillis = 550) {
                var themeExpanded by remember { mutableStateOf(false) }
                Box {
                    InfoCardItem(
                        icon = when (countdownData.themeMode) {
                            CountdownData.THEME_DARK -> Icons.Default.DarkMode
                            CountdownData.THEME_LIGHT -> Icons.Default.LightMode
                            else -> Icons.Default.Settings
                        },
                        title = "主题模式",
                        value = when (countdownData.themeMode) {
                            CountdownData.THEME_DARK -> "深色模式"
                            CountdownData.THEME_LIGHT -> "浅色模式"
                            else -> "跟随系统"
                        },
                        iconTint = MaterialTheme.colorScheme.secondary,
                        clickable = true,
                        onClick = { themeExpanded = true }
                    )
                    DropdownMenu(
                        expanded = themeExpanded,
                        onDismissRequest = { themeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("跟随系统") },
                            onClick = {
                                scope.launch { repository.saveThemeMode(CountdownData.THEME_SYSTEM) }
                                themeExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("浅色模式") },
                            onClick = {
                                scope.launch { repository.saveThemeMode(CountdownData.THEME_LIGHT) }
                                themeExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("深色模式") },
                            onClick = {
                                scope.launch { repository.saveThemeMode(CountdownData.THEME_DARK) }
                                themeExpanded = false
                            }
                        )
                    }
                }
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

            // 关于按钮
            AnimatedEntrance(visible = contentVisible, delayMillis = 600) {
                com.countdown.app.ui.components.BrandButton(
                    text = "关于",
                    icon = Icons.Default.Info,
                    onClick = { showSettings = true }
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // ===== 设置对话框（仅关于页面） =====
    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false },
            onCheckUpdate = {
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
                    }.onFailure {
                        scope.launch {
                            snackbarHostState.showSnackbar("检查更新失败，请稍后重试")
                        }
                    }
                }
            },
            isCheckingUpdate = isCheckingUpdate
        )
    }

    // ===== 日期选择器 =====
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = countdownData.targetDate
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
                        scope.launch {
                            repository.saveTargetDate(selected)
                            onUpdateWidget()
                        }
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

    // ===== 时间选择器 =====
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = countdownData.reminderTimeHour,
            initialMinute = countdownData.reminderTimeMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择提醒时间") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.saveReminderTime(timePickerState.hour, timePickerState.minute)
                        if (countdownData.reminderEnabled) {
                            AlarmScheduler.scheduleDailyAlarm(context, timePickerState.hour, timePickerState.minute)
                        }
                        onUpdateWidget()
                    }
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

    // ===== 更新对话框 =====
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
                    onStartDownload(
                        updateInfo.remoteVersion.downloadUrl,
                        updateInfo.remoteVersion.tagName
                    )
                    showUpdateDialog = null
                    scope.launch { snackbarHostState.showSnackbar("开始下载更新…") }
                }) {
                    Text("下载更新")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = null }) {
                    Text("稍后")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onCheckUpdate: () -> Unit,
    isCheckingUpdate: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于", style = MaterialTheme.typography.headlineSmall) },
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
                    subtitle = "1.6.7"
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ===== 更新 =====
                SettingsSectionTitle("更新")

                SettingsRowCard(
                    icon = Icons.Default.SystemUpdate,
                    iconBgColor = Color(0xFFEC4899),
                    title = "检查更新",
                    subtitle = if (isCheckingUpdate) "正在检查…" else "点击检查新版本",
                    onClick = onCheckUpdate
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
