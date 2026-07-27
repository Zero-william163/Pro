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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
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
import com.countdown.app.ui.theme.CountdownTheme
import com.countdown.app.update.UpdateChecker
import com.countdown.app.util.AlarmScheduler
import com.countdown.app.util.DateCalculator
import com.countdown.app.util.NotificationHelper
import com.countdown.app.widget.CountdownWidgetReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val repository = CountdownRepository.getInstance(this)

        setContent {
            val themeMode by repository.countdownDataFlow.collectAsState(initial = CountdownData())
            val darkTheme = when (themeMode.themeMode) {
                CountdownData.THEME_DARK -> true
                CountdownData.THEME_LIGHT -> false
                else -> null // system default
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
                            updateWidgets()
                        }
                    )
                }
            }
        }
    }

    private fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, CountdownWidgetReceiver::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        for (appWidgetId in appWidgetIds) {
            CountdownWidgetReceiver.updateWidget(this, appWidgetManager, appWidgetId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: CountdownRepository,
    onRequestAlarmPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onCheckUpdate: ((Result<UpdateChecker.UpdateInfo>) -> Unit) -> Unit,
    onStartDownload: (String, String) -> Unit,
    onUpdateWidget: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val countdownData by repository.countdownDataFlow.collectAsState(initial = CountdownData())
    var currentTime by remember { mutableStateOf(DateCalculator.formatCurrentTime()) }

    // Update current time every second
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = DateCalculator.formatCurrentTime()
            delay(1000)
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                    IconButton(onClick = {
                        isCheckingUpdate = true
                        onCheckUpdate { result ->
                            isCheckingUpdate = false
                            result.onSuccess { info ->
                                if (info.isNewer) {
                                    showUpdateDialog = info
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("当前已是最新版本")
                                    }
                                }
                            }.onFailure { e ->
                                scope.launch {
                                    snackbarHostState.showSnackbar("检查更新失败: ${e.message}")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main countdown card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = countdownData.eventContent.ifEmpty { "未设置事件" },
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (targetReached) "到达" else if (daysRemaining == 0L) "今天" else "$daysRemaining",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 96.sp,
                        lineHeight = 100.sp
                    )

                    Text(
                        text = if (targetReached) "" else if (daysRemaining <= 0) "天前" else "天",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "目标日期: ${DateCalculator.formatDate(countdownData.targetDate)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // Info cards
            InfoCard(
                icon = Icons.Default.AccessTime,
                title = "当前时间",
                value = currentTime
            )

            InfoCard(
                icon = Icons.Default.NotificationsActive,
                title = "每日提醒",
                value = if (countdownData.reminderEnabled) {
                    "${DateCalculator.formatTime(countdownData.reminderTimeHour, countdownData.reminderTimeMinute)} (${DateCalculator.getNextReminderString(countdownData.reminderTimeHour, countdownData.reminderTimeMinute)})"
                } else "已关闭"
            )

            InfoCard(
                icon = if (countdownData.reminderEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                title = "提醒状态",
                value = if (countdownData.reminderEnabled) "已启用" else "已禁用",
                valueColor = if (countdownData.reminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            // Permission warnings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !AlarmScheduler.canScheduleExactAlarms(context)) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRequestAlarmPermission() },
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
                            text = "需要精确闹钟权限，点击前往设置",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (!NotificationHelper.areNotificationsEnabled(context)) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenNotificationSettings() },
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
                            text = "需要通知权限，点击前往设置",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showSettings = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("编辑设置", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Settings Dialog
    if (showSettings) {
        SettingsDialog(
            data = countdownData,
            onDismiss = { showSettings = false },
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
                }
                showSettings = false
            }
        )
    }

    // Update Dialog
    showUpdateDialog?.let { updateInfo ->
        AlertDialog(
            onDismissRequest = { showUpdateDialog = null },
            title = { Text("发现新版本 v${updateInfo.versionName}") },
            text = {
                Column {
                    Text("当前版本将通过 GitHub 下载更新。", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(updateInfo.releaseNotes, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onStartDownload(updateInfo.downloadUrl, updateInfo.versionName)
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

@Composable
fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = valueColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
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

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑设置", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Event content
                OutlinedTextField(
                    value = eventContent,
                    onValueChange = { eventContent = it },
                    label = { Text("事件内容") },
                    placeholder = { Text("例如：高考、生日、旅行") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Target date
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("目标日期", style = MaterialTheme.typography.bodySmall)
                            Text(
                                DateCalculator.formatDate(targetDate),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reminder time
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("每日提醒时间", style = MaterialTheme.typography.bodySmall)
                            Text(
                                DateCalculator.formatTime(reminderHour, reminderMinute),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reminder enabled
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (reminderEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = null
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

                Spacer(modifier = Modifier.height(12.dp))

                // Theme mode
                var themeExpanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { themeExpanded = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (themeMode) {
                                CountdownData.THEME_DARK -> Icons.Default.DarkMode
                                CountdownData.THEME_LIGHT -> Icons.Default.LightMode
                                else -> Icons.Default.Settings
                            },
                            contentDescription = null
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
                            themeMode = themeMode
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

    // Time Picker
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

    // Date Picker (simplified using dialog with year/month/day)
    if (showDatePicker) {
        var year by remember { mutableIntStateOf(targetDate.year) }
        var month by remember { mutableIntStateOf(targetDate.monthValue) }
        var day by remember { mutableIntStateOf(targetDate.dayOfMonth) }

        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("选择目标日期") },
            text = {
                Column {
                    OutlinedTextField(
                        value = "$year",
                        onValueChange = { year = it.toIntOrNull() ?: year },
                        label = { Text("年") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = "$month",
                        onValueChange = {
                            val m = it.toIntOrNull() ?: month
                            month = m.coerceIn(1, 12)
                        },
                        label = { Text("月 (1-12)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = "$day",
                        onValueChange = {
                            val d = it.toIntOrNull() ?: day
                            day = d.coerceIn(1, 31)
                        },
                        label = { Text("日 (1-31)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        targetDate = LocalDate.of(year, month, day)
                        showDatePicker = false
                    } catch (_: Exception) {
                        Toast.makeText(context, "日期无效", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        )
    }
}
