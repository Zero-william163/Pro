package com.countdown.app.ui.permission

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.countdown.app.ui.theme.CountdownTheme
import com.countdown.app.util.PermissionChecker
import com.countdown.app.util.PermissionGuideData
import kotlinx.coroutines.delay

/**
 * 权限中心页面（重构版）
 *
 * 设计参考：支付宝、微信、华为健康、美团等成熟 Android 应用
 *
 * 核心特性：
 * 1. Material Design 3 卡片式布局
 * 2. 权限状态一目了然（绿色=已开启，橙色=建议开启，红色=未开启）
 * 3. 每项权限显示完整信息：
 *    - 权限名称
 *    - 当前状态
 *    - 权限作用说明
 *    - 为什么必须开启
 *    - 开启后能解决什么问题
 * 4. 多级降级跳转：优先直达对应权限页面，无法直达时逐级降级
 * 5. 厂商专项适配：华为/荣耀/小米/OPPO/vivo/三星/一加
 * 6. 自动检测 + 返回自动刷新
 * 7. 跳转失败时显示手动操作路径
 */
class PermissionActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PermissionActivity::class.java))
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CountdownTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PermissionScreen(onBack = { finish() })
                }
            }
        }
    }
}

// ==================== 状态颜色定义 ====================

/** 已开启 - 绿色 */
private val GreenColor = Color(0xFF4CAF50)
private val GreenBgColor = Color(0xFFE8F5E9)

/** 未开启（关键权限）- 红色 */
private val RedColor = Color(0xFFEF4444)
private val RedBgColor = Color(0xFFFFEBEE)

/** 建议开启（非关键权限未开启）- 橙色 */
private val OrangeColor = Color(0xFFFF9800)
private val OrangeBgColor = Color(0xFFFFF3E0)

/** 待确认（无法通过 API 检测的厂商权限）- 蓝色 */
private val BlueColor = Color(0xFF2196F3)
private val BlueBgColor = Color(0xFFE3F2FD)

// ==================== 主屏幕 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 权限检测结果
    var permissionResult by remember {
        mutableStateOf(PermissionChecker.checkAllPermissions(context))
    }

    // 跳转失败的对话框状态
    var failedItem by remember { mutableStateOf<PermissionChecker.PermissionItem?>(null) }

    // 用户确认对话框状态（厂商权限「我已开启」）
    var confirmItem by remember { mutableStateOf<PermissionChecker.PermissionItem?>(null) }

    // 详细操作指南对话框状态
    var guideDialogItem by remember { mutableStateOf<PermissionChecker.PermissionItem?>(null) }

    // Snackbar 消息
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // 重新检测权限
    fun refreshPermissions() {
        permissionResult = PermissionChecker.checkAllPermissions(context)
    }

    // 生命周期监听：从设置页面返回时自动刷新权限状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Snackbar 消息处理
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    // 跳转失败对话框
    failedItem?.let { item ->
        OpenResultDialog(
            title = item.title,
            operationPath = item.operationPath ?: "请前往系统设置手动开启此权限",
            onDismiss = { failedItem = null }
        )
    }

    // 用户确认对话框（厂商权限「我已开启」）
    confirmItem?.let { item ->
        ConfirmPermissionDialog(
            title = item.title,
            onConfirm = {
                PermissionChecker.setPermissionConfirmed(context, item.id, true)
                refreshPermissions()
                snackbarMessage = "${item.title} 已标记为已开启"
                confirmItem = null
            },
            onDismiss = { confirmItem = null }
        )
    }

    // 详细操作指南对话框
    guideDialogItem?.let { item ->
        PermissionGuideDialog(
            item = item,
            deviceBrand = permissionResult.deviceBrand,
            onDismiss = { guideDialogItem = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "权限中心",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { PermissionHelpActivity.start(context) }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "权限帮助")
                    }
                    IconButton(onClick = { refreshPermissions() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
            // ===== 顶部状态总览卡片 =====
            StatusHeaderCard(
                criticalGranted = permissionResult.criticalGranted,
                allGranted = permissionResult.allGranted,
                missingCriticalCount = permissionResult.missingCriticalCount,
                deviceBrand = permissionResult.deviceBrand,
                hasVendorItems = permissionResult.items.any { it.vendorSpecial }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ===== 关键权限区域 =====
            val criticalItems = permissionResult.items.filter { it.isCritical && !it.vendorSpecial }
            if (criticalItems.isNotEmpty()) {
                SectionTitle("关键权限（必须开启）")
                criticalItems.forEachIndexed { index, item ->
                    PermissionCard(
                        item = item,
                        onActionClick = {
                            val result = PermissionChecker.tryOpenPermission(context, item)
                            when (result) {
                                is PermissionChecker.OpenResult.DirectJump -> {
                                    // 直接跳转成功，返回时自动刷新
                                }
                                is PermissionChecker.OpenResult.FallbackToAppDetails -> {
                                    snackbarMessage = result.reason
                                }
                                is PermissionChecker.OpenResult.FallbackToSettings -> {
                                    snackbarMessage = result.reason
                                }
                                is PermissionChecker.OpenResult.Failed -> {
                                    failedItem = item
                                }
                            }
                        },
                        onViewGuide = { guideDialogItem = item },
                        delayMillis = index * 80
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // ===== 建议开启权限区域 =====
            val recommendedItems = permissionResult.items.filter { !it.isCritical && !it.vendorSpecial }
            if (recommendedItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionTitle("建议开启权限")
                recommendedItems.forEachIndexed { index, item ->
                    PermissionCard(
                        item = item,
                        onActionClick = {
                            val result = PermissionChecker.tryOpenPermission(context, item)
                            when (result) {
                                is PermissionChecker.OpenResult.DirectJump -> {
                                    // 直接跳转成功
                                }
                                is PermissionChecker.OpenResult.FallbackToAppDetails -> {
                                    snackbarMessage = result.reason
                                }
                                is PermissionChecker.OpenResult.FallbackToSettings -> {
                                    snackbarMessage = result.reason
                                }
                                is PermissionChecker.OpenResult.Failed -> {
                                    failedItem = item
                                }
                            }
                        },
                        onViewGuide = { guideDialogItem = item },
                        delayMillis = (criticalItems.size + index) * 80
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // ===== 厂商专项权限区域 =====
            val vendorItems = permissionResult.items.filter { it.vendorSpecial }
            if (vendorItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                VendorSectionHeader(
                    vendorName = permissionResult.deviceBrand,
                    vendorItemCount = vendorItems.size
                )
                Spacer(modifier = Modifier.height(12.dp))

                vendorItems.forEachIndexed { index, item ->
                    PermissionCard(
                        item = item,
                        onActionClick = {
                            val result = PermissionChecker.tryOpenPermission(context, item)
                            when (result) {
                                is PermissionChecker.OpenResult.DirectJump -> {
                                    // 直接跳转成功
                                }
                                is PermissionChecker.OpenResult.FallbackToAppDetails -> {
                                    snackbarMessage = result.reason
                                }
                                is PermissionChecker.OpenResult.FallbackToSettings -> {
                                    snackbarMessage = result.reason
                                }
                                is PermissionChecker.OpenResult.Failed -> {
                                    failedItem = item
                                }
                            }
                        },
                        onConfirmClick = {
                            confirmItem = item
                        },
                        onResetConfirm = {
                            PermissionChecker.setPermissionConfirmed(context, item.id, false)
                            refreshPermissions()
                            snackbarMessage = "${item.title} 已重置，请重新确认"
                        },
                        onViewGuide = { guideDialogItem = item },
                        delayMillis = (criticalItems.size + recommendedItems.size + index) * 80
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ===== 底部刷新按钮 =====
            OutlinedRefreshButton(onClick = { refreshPermissions() })

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ==================== 顶部状态总览卡片 ====================

@Composable
fun StatusHeaderCard(
    criticalGranted: Boolean,
    allGranted: Boolean,
    missingCriticalCount: Int,
    deviceBrand: String,
    hasVendorItems: Boolean
) {
    val cardColor by animateColorAsState(
        targetValue = when {
            criticalGranted -> GreenBgColor
            else -> RedBgColor
        },
        animationSpec = tween(500),
        label = "statusCardColor"
    )
    val iconColor = if (criticalGranted) GreenColor else RedColor

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (criticalGranted) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when {
                    allGranted -> "所有权限已开启"
                    criticalGranted -> "关键权限已开启"
                    else -> "$missingCriticalCount 项关键权限未开启"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = iconColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when {
                    allGranted && hasVendorItems -> "建议完成${deviceBrand}专项设置以获得最佳体验"
                    allGranted -> "您的提醒功能应该可以正常工作"
                    criticalGranted && hasVendorItems -> "建议完成${deviceBrand}专项设置以获得最佳体验"
                    criticalGranted -> "关键权限已就绪，建议开启其余权限"
                    else -> "请开启以下关键权限，否则提醒可能无法正常工作"
                },
                fontSize = 14.sp,
                color = iconColor.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
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

// ==================== 厂商专项区域标题 ====================

@Composable
fun VendorSectionHeader(vendorName: String, vendorItemCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = Color(0xFFFF6D00),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "${vendorName}设备专项设置",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )
                Text(
                    text = "${vendorName}手机有额外的后台管理策略，建议完成以下 $vendorItemCount 项设置以确保提醒可靠",
                    fontSize = 13.sp,
                    color = Color(0xFFBF360C),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ==================== 权限卡片（核心组件） ====================

@Composable
fun PermissionCard(
    item: PermissionChecker.PermissionItem,
    onActionClick: () -> Unit,
    onConfirmClick: () -> Unit = {},
    onResetConfirm: () -> Unit = {},
    onViewGuide: () -> Unit = {},
    delayMillis: Int
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }
    ) {
        PermissionCardContent(
            item = item,
            onActionClick = onActionClick,
            onConfirmClick = onConfirmClick,
            onResetConfirm = onResetConfirm,
            onViewGuide = onViewGuide
        )
    }
}

@Composable
fun PermissionCardContent(
    item: PermissionChecker.PermissionItem,
    onActionClick: () -> Unit,
    onConfirmClick: () -> Unit = {},
    onResetConfirm: () -> Unit = {},
    onViewGuide: () -> Unit = {}
) {
    // 状态颜色
    // 绿色 = 已开启（API 检测）或 已确认（用户手动确认）
    // 红色 = 未开启 + 关键权限
    // 橙色 = 未开启 + 非关键权限（建议开启）
    // 蓝色 = 无法通过 API 检测且未确认（待用户手动确认）
    val statusColor = when {
        item.isGranted -> GreenColor
        !item.checkable -> BlueColor
        item.isCritical -> RedColor
        else -> OrangeColor
    }
    val statusBgColor = when {
        item.isGranted -> GreenBgColor
        !item.checkable -> BlueBgColor
        item.isCritical -> RedBgColor
        else -> OrangeBgColor
    }
    val statusText = when {
        item.isGranted && item.confirmedByUser -> "已确认"
        item.isGranted -> "已开启"
        !item.checkable -> "待确认"
        item.isCritical -> "未开启"
        else -> "建议开启"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ===== 第一行：图标 + 标题 + 状态/按钮 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 权限图标（带背景色）
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(statusBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getPermissionIcon(item.iconType),
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (item.isCritical) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "必需",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = RedColor,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(RedBgColor)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (item.vendorSpecial && item.vendorName.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.vendorName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE65100),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFFFF3E0))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = item.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                // 右侧：状态标签或立即开启按钮
                if (item.isGranted) {
                    // 已开启/已确认：显示绿色状态标签
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GreenBgColor)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = statusText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GreenColor
                            )
                        }
                        // 用户确认的权限可以重新检测
                        if (item.confirmedByUser) {
                            TextButton(
                                onClick = onResetConfirm,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 4.dp, vertical = 0.dp
                                )
                            ) {
                                Text("重新检测", fontSize = 11.sp)
                            }
                        }
                    }
                } else if (!item.checkable) {
                    // 待确认（厂商权限）：显示"前往设置"按钮 + "我已开启"按钮
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Button(
                            onClick = onActionClick,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 14.dp,
                                vertical = 6.dp
                            ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BlueColor,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = item.actionLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        TextButton(
                            onClick = onConfirmClick,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 4.dp, vertical = 0.dp
                            )
                        ) {
                            Text("我已开启", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    // 未开启：显示立即开启按钮
                    Button(
                        onClick = onActionClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 14.dp,
                            vertical = 6.dp
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                item.isCritical -> RedColor
                                else -> OrangeColor
                            },
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = item.actionLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ===== 为什么必须开启 =====
            Spacer(modifier = Modifier.height(10.dp))
            InfoRow(
                icon = Icons.Default.Warning,
                iconColor = Color(0xFFFF8F00),
                label = "为什么必须开启",
                content = item.whyRequired
            )

            // ===== 开启后能解决什么问题 =====
            Spacer(modifier = Modifier.height(6.dp))
            InfoRow(
                icon = Icons.Default.CheckCircle,
                iconColor = GreenColor.copy(alpha = 0.7f),
                label = "开启后解决",
                content = item.solveProblem
            )

            // ===== 手动操作路径（厂商专项权限且未确认时显示）=====
            if (item.vendorSpecial && !item.isGranted && item.operationPath != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!item.checkable) BlueBgColor else Color(0xFFFFF8E1))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFFF8F00),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "操作路径",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE65100)
                            )
                            Text(
                                text = item.operationPath,
                                fontSize = 12.sp,
                                color = Color(0xFF5D4037),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // ===== 查看详细教程按钮 =====
            if (item.guideId.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onViewGuide,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp, vertical = 0.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "查看详细教程",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ==================== 信息行组件 ====================

@Composable
fun InfoRow(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    content: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = "$label：",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = content,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 17.sp
            )
        }
    }
}

// ==================== 图标映射 ====================

fun getPermissionIcon(iconType: PermissionChecker.IconType): ImageVector {
    return when (iconType) {
        PermissionChecker.IconType.NOTIFICATION -> Icons.Default.Notifications
        PermissionChecker.IconType.ALARM -> Icons.Default.Alarm
        PermissionChecker.IconType.FULL_SCREEN -> Icons.Default.Fullscreen
        PermissionChecker.IconType.BATTERY -> Icons.Default.BatteryFull
        PermissionChecker.IconType.OVERLAY -> Icons.Default.Layers
        PermissionChecker.IconType.FOREGROUND_SERVICE -> Icons.Default.PlayArrow
        PermissionChecker.IconType.CHANNEL -> Icons.Default.NotificationsActive
        PermissionChecker.IconType.AUTO_START -> Icons.Default.PowerSettingsNew
        PermissionChecker.IconType.LOCK_SCREEN -> Icons.Default.Lock
        PermissionChecker.IconType.PROTECT_APP -> Icons.Default.Shield
        PermissionChecker.IconType.GENERAL -> Icons.Default.Security
    }
}

// ==================== 跳转失败对话框 ====================

@Composable
fun OpenResultDialog(
    title: String,
    operationPath: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = OrangeColor
            )
        },
        title = {
            Text(
                text = "无法直接打开「$title」",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "由于当前系统限制，无法直接打开该权限页面，请按照以下步骤开启：",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFF8E1))
                        .padding(12.dp)
                ) {
                    Text(
                        text = operationPath,
                        fontSize = 13.sp,
                        color = Color(0xFF5D4037),
                        lineHeight = 20.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

// ==================== 底部刷新按钮 ====================

@Composable
fun OutlinedRefreshButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("重新检测权限状态")
    }
}

// ==================== 用户确认对话框（厂商权限） ====================

@Composable
fun ConfirmPermissionDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = GreenColor
            )
        },
        title = {
            Text(
                text = "确认「$title」已开启",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "您是否已在系统设置中开启「$title」？\n\n" +
                        "由于该权限为厂商特殊权限，无法通过系统 API 自动检测。\n" +
                        "确认后将标记为已开启。如需重新检测，可点击「重新检测」按钮。",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenColor,
                    contentColor = Color.White
                )
            ) {
                Text("已开启", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("未开启")
            }
        }
    )
}

// ==================== 详细操作指南对话框 ====================

@Composable
fun PermissionGuideDialog(
    item: PermissionChecker.PermissionItem,
    deviceBrand: String,
    onDismiss: () -> Unit
) {
    // 从 PermissionGuideData 获取引导数据
    val guide = if (item.guideId.isNotEmpty()) {
        PermissionGuideData.getGuideByPermissionId(item.guideId)
    } else null

    // 获取当前品牌对应的引导（如果没有则使用通用引导）
    val brandGuide = guide?.let { g ->
        // 尝试匹配检测到的品牌
        val matchedBrand = PermissionGuideData.ALL_BRANDS.find { brand ->
            deviceBrand.contains(brand, ignoreCase = true) || brand.contains(deviceBrand, ignoreCase = true)
        }
        matchedBrand?.let { g.brandGuides[it] } ?: g.genericGuide
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = guide?.permissionName ?: item.title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (guide != null && brandGuide != null) {
                    // 品牌标识
                    Text(
                        text = "操作路径（${brandGuide.brandName}）",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 步骤列表
                    brandGuide.steps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            // 步骤编号圆圈
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // 步骤内容
                            Column(modifier = Modifier.weight(1f)) {
                                if (step.iconHint.isNotEmpty()) {
                                    Text(
                                        text = step.iconHint,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = step.stepText,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        // 步骤之间的箭头
                        if (index < brandGuide.steps.size - 1) {
                            Row(
                                modifier = Modifier
                                    .padding(start = 10.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // 注意事项
                    if (brandGuide.notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFF8E1))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFFF8F00),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = brandGuide.notes,
                                    fontSize = 12.sp,
                                    color = Color(0xFF5D4037),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 为什么需要
                    GuideDialogInfoSection(
                        label = "为什么需要此权限",
                        content = guide.whyRequired,
                        color = MaterialTheme.colorScheme.primary,
                        bgColor = MaterialTheme.colorScheme.primaryContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 不开启的后果
                    GuideDialogInfoSection(
                        label = "不开启会导致",
                        content = guide.consequenceIfDisabled,
                        color = RedColor,
                        bgColor = RedBgColor
                    )
                } else {
                    // 没有引导数据
                    Text(
                        text = "暂无此权限的详细操作指南。\n\n请点击「立即开启」按钮跳转到系统设置页面，或点击顶部「权限帮助」查看所有品牌教程。",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun GuideDialogInfoSection(
    label: String,
    content: String,
    color: Color,
    bgColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 17.sp
            )
        }
    }
}
