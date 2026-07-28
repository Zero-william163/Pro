package com.countdown.app.ui.permission

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.countdown.app.ui.theme.CountdownTheme
import com.countdown.app.util.PermissionChecker
import kotlinx.coroutines.delay

/**
 * 权限中心页面
 *
 * 设计参考：支付宝、微信、华为健康等成熟 App 的权限引导页面
 * - Material Design 3 卡片式布局
 * - 权限状态一目了然（绿色 = 已开启，橙色/红色 = 未开启）
 * - 每项权限都有说明和立即开启按钮
 * - 华为设备专项适配提示
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // 权限检测结果
    var permissionResult by remember {
        mutableStateOf(PermissionChecker.checkAllPermissions(context))
    }

    // 重新检测权限
    fun refreshPermissions() {
        permissionResult = PermissionChecker.checkAllPermissions(context)
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ===== 顶部状态卡片 =====
            StatusHeaderCard(
                criticalGranted = permissionResult.criticalGranted,
                allGranted = permissionResult.allGranted,
                isHuawei = permissionResult.isHuaweiDevice
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ===== 关键权限区域 =====
            Text(
                text = "关键权限（必须开启）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            val criticalItems = permissionResult.items.filter { it.isCritical }
            criticalItems.forEachIndexed { index, item ->
                PermissionCard(
                    item = item,
                    onActionClick = {
                        PermissionChecker.openPermissionSettings(context, item)
                    },
                    delayMillis = index * 100
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ===== 其他权限区域 =====
            val otherItems = permissionResult.items.filter { !it.isCritical && !it.huaweiSpecial }
            if (otherItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "其他权限（建议开启）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )

                otherItems.forEachIndexed { index, item ->
                    PermissionCard(
                        item = item,
                        onActionClick = {
                            PermissionChecker.openPermissionSettings(context, item)
                        },
                        delayMillis = (criticalItems.size + index) * 100
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // ===== 华为专项提示 =====
            val huaweiItems = permissionResult.items.filter { it.huaweiSpecial }
            if (huaweiItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                // 华为提示卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3E0)
                    )
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
                                text = "华为设备专项设置",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                            Text(
                                text = "华为手机有额外的后台管理策略，建议完成以下设置以确保提醒可靠",
                                fontSize = 13.sp,
                                color = Color(0xFFBF360C),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                huaweiItems.forEachIndexed { index, item ->
                    PermissionCard(
                        item = item,
                        onActionClick = {
                            PermissionChecker.openPermissionSettings(context, item)
                        },
                        delayMillis = (criticalItems.size + otherItems.size + index) * 100
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ===== 刷新按钮 =====
            Button(
                onClick = { refreshPermissions() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("刷新权限状态")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StatusHeaderCard(criticalGranted: Boolean, allGranted: Boolean, isHuawei: Boolean) {
    val cardColor by animateColorAsState(
        targetValue = if (criticalGranted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
        animationSpec = tween(500),
        label = "cardColor"
    )
    val iconColor = if (criticalGranted) Color(0xFF2E7D32) else Color(0xFFEF6C00)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (criticalGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (criticalGranted) {
                    if (allGranted) "所有权限已开启" else "关键权限已开启"
                } else "关键权限未完全开启",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = iconColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (criticalGranted) {
                    if (isHuawei) "建议完成华为专项设置以获得最佳体验" else "您的提醒功能应该可以正常工作"
                } else "请开启以下关键权限，否则提醒可能无法正常工作",
                fontSize = 14.sp,
                color = iconColor.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun PermissionCard(
    item: PermissionChecker.PermissionItem,
    onActionClick: () -> Unit,
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
        val statusColor = if (item.isGranted) Color(0xFF4CAF50) else Color(0xFFFF9800)
        val statusBgColor = if (item.isGranted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 状态指示圆点
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )

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
                                    color = Color(0xFFD32F2F),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFFEBEE))
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

                    // 状态标签
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusBgColor)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (item.isGranted) "已开启" else "未开启",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }
                }

                // 操作按钮（未开启时显示）
                if (!item.isGranted) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onActionClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = item.actionLabel,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
