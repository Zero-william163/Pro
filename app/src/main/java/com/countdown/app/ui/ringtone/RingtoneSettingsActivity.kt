package com.countdown.app.ui.ringtone

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.countdown.app.data.CountdownData
import com.countdown.app.ui.theme.CountdownTheme
import com.countdown.app.util.RingtoneManager as AppRingtoneManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 闹钟铃声设置页面
 *
 * 设计风格与 [com.countdown.app.ui.permission.PermissionActivity] 保持一致：
 * Material Design 3 卡片式布局、彩色状态指示。
 *
 * 核心功能：
 * 1. 显示当前铃声名称，支持试听（使用 USAGE_ALARM 音频属性，遵循闹钟音量）
 * 2. 三种铃声来源（单选）：
 *    - 系统默认闹钟铃声（绿色）
 *    - 选择系统铃声（蓝色，打开 RingtoneManager.ACTION_RINGTONE_PICKER）
 *    - 选择本地音频文件（橙色，打开 ACTION_OPEN_DOCUMENT）
 * 3. 使用 ActivityResultContracts 处理选择结果
 * 4. 本地文件铃声通过 takePersistableUriPermission 持久化，跨应用重启/手机重启保持有效
 *
 * 注意：本文件中 `RingtoneManager` 指代 `android.media.RingtoneManager`（系统类），
 * 应用内的工具类通过别名 `AppRingtoneManager` 引用，以避免命名冲突。
 */
class RingtoneSettingsActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, RingtoneSettingsActivity::class.java))
        }
    }

    // 由系统铃声选择器返回的待保存 URI（composable 通过 LaunchedEffect 消费）
    private var pendingSystemRingtoneUri: Uri? by mutableStateOf<Uri?>(null)
    // 由 SAF 文件选择器返回的待保存 URI
    private var pendingLocalFileUri: Uri? by mutableStateOf<Uri?>(null)

    private lateinit var systemRingtoneLauncher: ActivityResultLauncher<Intent>
    private lateinit var openFileLauncher: ActivityResultLauncher<Array<String>>

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ===== 系统铃声选择器结果 =====
        systemRingtoneLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                @Suppress("DEPRECATION")
                val uri = result.data?.getParcelableExtra<Uri>(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI
                )
                pendingSystemRingtoneUri = uri
            }
        }

        // ===== SAF 本地音频文件选择器结果 =====
        openFileLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                pendingLocalFileUri = uri
            }
        }

        setContent {
            CountdownTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RingtoneSettingsScreen(
                        pendingSystemRingtoneUri = pendingSystemRingtoneUri,
                        pendingLocalFileUri = pendingLocalFileUri,
                        onConsumeSystemRingtone = { pendingSystemRingtoneUri = null },
                        onConsumeLocalFile = { pendingLocalFileUri = null },
                        onBack = { finish() },
                        onPickSystemRingtone = {
                            systemRingtoneLauncher.launch(
                                AppRingtoneManager.createSystemRingtonePickerIntent(
                                    RingtoneManager.TYPE_ALARM
                                )
                            )
                        },
                        onPickLocalFile = {
                            openFileLauncher.launch(arrayOf("audio/*"))
                        }
                    )
                }
            }
        }
    }
}

// ==================== 状态颜色定义（与 PermissionActivity 保持一致） ====================

/** 默认铃声 - 绿色 */
private val GreenColor = Color(0xFF4CAF50)
private val GreenBgColor = Color(0xFFE8F5E9)

/** 系统铃声 - 蓝色 */
private val BlueColor = Color(0xFF2196F3)
private val BlueBgColor = Color(0xFFE3F2FD)

/** 本地音频文件 - 橙色 */
private val OrangeColor = Color(0xFFFF9800)
private val OrangeBgColor = Color(0xFFFFF3E0)

/** 停止试听 - 红色 */
private val StopColor = Color(0xFFEF4444)

// ==================== 主屏幕 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneSettingsScreen(
    pendingSystemRingtoneUri: Uri?,
    pendingLocalFileUri: Uri?,
    onConsumeSystemRingtone: () -> Unit,
    onConsumeLocalFile: () -> Unit,
    onBack: () -> Unit,
    onPickSystemRingtone: () -> Unit,
    onPickLocalFile: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 当前铃声信息
    var currentName by remember {
        mutableStateOf(AppRingtoneManager.getCurrentRingtoneName(context))
    }
    var currentType by remember {
        mutableStateOf(AppRingtoneManager.getCurrentRingtoneType(context))
    }
    // 试听状态
    var isPreviewing by remember { mutableStateOf(AppRingtoneManager.isPreviewing()) }
    // Snackbar 消息
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // ===== 处理系统铃声选择结果 =====
    LaunchedEffect(pendingSystemRingtoneUri) {
        val uri = pendingSystemRingtoneUri ?: return@LaunchedEffect
        scope.launch {
            try {
                AppRingtoneManager.saveSystemRingtone(context, uri)
                withContext(Dispatchers.IO) {
                    currentName = AppRingtoneManager.getCurrentRingtoneName(context)
                    currentType = AppRingtoneManager.getCurrentRingtoneType(context)
                }
                snackbarMessage = "铃声已保存"
            } catch (e: Exception) {
                snackbarMessage = "保存失败：${e.message}"
            } finally {
                onConsumeSystemRingtone()
            }
        }
    }

    // ===== 处理本地音频文件选择结果 =====
    LaunchedEffect(pendingLocalFileUri) {
        val uri = pendingLocalFileUri ?: return@LaunchedEffect
        scope.launch {
            try {
                val success = AppRingtoneManager.saveLocalFileRingtone(context, uri)
                withContext(Dispatchers.IO) {
                    currentName = AppRingtoneManager.getCurrentRingtoneName(context)
                    currentType = AppRingtoneManager.getCurrentRingtoneType(context)
                }
                snackbarMessage = if (success) "铃声已保存" else "保存失败：无法持久访问该文件"
            } catch (e: Exception) {
                snackbarMessage = "保存失败：${e.message}"
            } finally {
                onConsumeLocalFile()
            }
        }
    }

    // ===== Snackbar 消息处理 =====
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    // ===== Activity 销毁时停止试听 =====
    DisposableEffect(Unit) {
        onDispose {
            AppRingtoneManager.stopPreview()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "闹钟铃声",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ===== 当前铃声卡片 + 试听按钮 =====
            CurrentRingtoneCard(
                name = currentName,
                isPreviewing = isPreviewing,
                onPreviewToggle = {
                    if (isPreviewing) {
                        AppRingtoneManager.stopPreview()
                        isPreviewing = false
                    } else {
                        val ok = AppRingtoneManager.startPreview(context)
                        isPreviewing = ok
                        if (!ok) {
                            snackbarMessage = "无法播放铃声，请检查铃声设置"
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ===== 铃声来源选择 =====
            SectionTitle("选择铃声来源")

            // a. 系统默认闹钟铃声
            RingtoneOptionCard(
                title = "系统默认闹钟铃声",
                description = "使用系统默认的闹钟铃声，兼容性最佳",
                icon = Icons.Default.Alarm,
                color = GreenColor,
                bgColor = GreenBgColor,
                selected = currentType == CountdownData.RINGTYPE_DEFAULT,
                onClick = {
                    scope.launch {
                        try {
                            AppRingtoneManager.resetToDefault(context)
                            withContext(Dispatchers.IO) {
                                currentName = AppRingtoneManager.getCurrentRingtoneName(context)
                                currentType = AppRingtoneManager.getCurrentRingtoneType(context)
                            }
                            snackbarMessage = "已恢复系统默认闹钟铃声"
                        } catch (e: Exception) {
                            snackbarMessage = "操作失败：${e.message}"
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))

            // b. 选择系统铃声
            RingtoneOptionCard(
                title = "选择系统铃声",
                description = "从系统铃声库中选择闹钟/通知/来电铃声",
                icon = Icons.Default.Notifications,
                color = BlueColor,
                bgColor = BlueBgColor,
                selected = currentType == CountdownData.RINGTYPE_SYSTEM,
                onClick = onPickSystemRingtone
            )
            Spacer(modifier = Modifier.height(10.dp))

            // c. 选择本地音频文件
            RingtoneOptionCard(
                title = "选择本地音频文件",
                description = "从本地选择 MP3、WAV、OGG、FLAC 等音频",
                icon = Icons.Default.MusicNote,
                color = OrangeColor,
                bgColor = OrangeBgColor,
                selected = currentType == CountdownData.RINGTYPE_LOCAL_FILE,
                onClick = onPickLocalFile
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ===== 持久化说明 + 支持格式 =====
            InfoNoteCard()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ==================== 当前铃声卡片 ====================

@Composable
fun CurrentRingtoneCard(
    name: String,
    isPreviewing: Boolean,
    onPreviewToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GreenBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = GreenColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当前铃声",
                        fontSize = 13.sp,
                        color = GreenColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onPreviewToggle,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPreviewing) StopColor else GreenColor,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPreviewing) "停止试听" else "试听",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ==================== 区域标题 ====================

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

// ==================== 铃声来源选项卡片（单选风格） ====================

@Composable
fun RingtoneOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) bgColor else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 0.dp else 2.dp
        ),
        border = if (selected) BorderStroke(2.dp, color) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标（带背景色）
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            // 单选指示器
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = color,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

// ==================== 持久化说明 + 支持格式卡片 ====================

@Composable
fun InfoNoteCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 持久化说明
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "持久化保存",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "铃声设置会在应用重启和手机重启后保持生效，本地文件铃声会自动获取持久访问权限。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 支持格式
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = OrangeColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "支持的音频格式",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "MP3、WAV、OGG、FLAC（取决于系统解码器支持）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}
