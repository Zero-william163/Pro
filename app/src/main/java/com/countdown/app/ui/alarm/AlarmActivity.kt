package com.countdown.app.ui.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.countdown.app.service.AlarmService
import com.countdown.app.ui.theme.CountdownTheme
import com.countdown.app.util.DateCalculator
import com.countdown.app.util.NotificationHelper
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 全屏闹钟界面（已重构）
 *
 * 系统级体验要求：
 * - 锁屏状态下直接显示
 * - 自动点亮屏幕
 * - 自动唤醒设备
 * - 保持屏幕常亮
 * - 大按钮易于操作
 * - 关闭逻辑可靠（停止声音、震动、通知、服务）
 */
class AlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EVENT_CONTENT = "event_content"
        const val EXTRA_DAYS_REMAINING = "days_remaining"
        const val EXTRA_TARGET_REACHED = "target_reached"
        private const val TAG = "AlarmActivity"
    }

    private var closeReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "AlarmActivity onCreate")

        // ===== 0. 通知 AlarmService：Activity 已启动（阻止回退机制重复启动）=====
        AlarmService.isAlarmActivityActive = true

        // ===== 1. 窗口属性设置 =====
        setupWindowFlags()

        // ===== 2. 注册关闭广播接收器 =====
        registerCloseReceiver()

        // ===== 3. 获取数据 =====
        val eventContent = intent.getStringExtra(EXTRA_EVENT_CONTENT) ?: ""
        val daysRemaining = intent.getLongExtra(EXTRA_DAYS_REMAINING, 0)
        val targetReached = intent.getBooleanExtra(EXTRA_TARGET_REACHED, false)

        setContent {
            CountdownTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AlarmScreen(
                        eventContent = eventContent,
                        daysRemaining = daysRemaining,
                        targetReached = targetReached,
                        onDismiss = { dismissAlarm() },
                        onSnooze = { snoozeAlarm(eventContent, daysRemaining, targetReached) }
                    )
                }
            }
        }
    }

    /**
     * 设置窗口标志，确保在锁屏时显示、点亮屏幕、保持常亮
     */
    private fun setupWindowFlags() {
        // Android 8.1+ 使用官方 API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // 保持屏幕常亮（所有 API 级别）
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 解除键盘锁（Android 8+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        // 设置布局属性，确保在锁屏和状态栏之上显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
    }

    /**
     * 注册广播接收器，接收关闭指令
     */
    private fun registerCloseReceiver() {
        closeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AlarmService.ACTION_CLOSE_ALARM_ACTIVITY) {
                    Log.d(TAG, "Received close broadcast, finishing activity")
                    finishAndRemoveTask()
                }
            }
        }
        val filter = IntentFilter(AlarmService.ACTION_CLOSE_ALARM_ACTIVITY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(closeReceiver, filter)
        }
    }

    /**
     * 关闭闹钟：停止所有效果并清理
     */
    private fun dismissAlarm() {
        Log.d(TAG, "Dismissing alarm from activity")

        // 1. 停止 AlarmService（声音 + 震动 + 前台服务）
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        ContextCompat.startForegroundService(this, stopIntent)

        // 2. 取消所有闹钟相关通知
        NotificationHelper.cancelAllAlarmNotifications(this)

        // 3. 关闭当前 Activity
        finishAndRemoveTask()
    }

    /**
     * 稍后提醒：5分钟后再次触发
     */
    private fun snoozeAlarm(eventContent: String, daysRemaining: Long, targetReached: Boolean) {
        Log.d(TAG, "Snoozing alarm")

        // 先关闭当前闹钟
        dismissAlarm()

        // 5分钟后再次提醒
        val snoozeTime = System.currentTimeMillis() + 5 * 60 * 1000L
        com.countdown.app.util.AlarmScheduler.scheduleOneShotAlarm(
            this,
            snoozeTime,
            eventContent = eventContent,
            daysRemaining = daysRemaining,
            targetReached = targetReached
        )

        // 显示提示
        NotificationHelper.showSnoozeScheduledNotification(this, eventContent, snoozeTime)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AlarmActivity onDestroy")
        // 通知 AlarmService：Activity 已关闭
        AlarmService.isAlarmActivityActive = false
        closeReceiver?.let { unregisterReceiver(it) }
    }

    override fun onBackPressed() {
        // 禁止返回键关闭，必须点击关闭按钮
        // 这样可以防止误触关闭闹钟
    }
}

// ==================== Compose UI ====================

@Composable
fun AlarmScreen(
    eventContent: String,
    daysRemaining: Long,
    targetReached: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    // 实时更新时间
    var currentTime by remember { mutableStateOf(formatCurrentTime()) }
    var currentDate by remember { mutableStateOf(formatCurrentDate()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = formatCurrentTime()
            currentDate = formatCurrentDate()
        }
    }

    // 入场动画
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        contentVisible = true
    }

    // 脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "alarmPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // 状态文本
    val (bigNumber, unitLabel, statusText, accentColor) = when {
        targetReached -> Quadruple("", "", "目标日期已到达！", Color(0xFF4CAF50))
        daysRemaining == 0L -> Quadruple("0", "天", "就是今天！", Color(0xFFFF9800))
        daysRemaining < 0 -> Quadruple((-daysRemaining).toString(), "天前", "已过去", Color(0xFF2196F3))
        else -> Quadruple(daysRemaining.toString(), "天", "还有", Color(0xFFFF5252))
    }

    val gradientColors = listOf(
        Color(0xFF0D0D1A),
        Color(0xFF1A1A3E),
        Color(0xFF0D0D1A)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ===== 顶部：时间和日期 =====
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { -it / 3 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = currentTime,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = currentDate,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // ===== 中间：事件和倒计时 =====
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(700, delayMillis = 200)) +
                        slideInVertically(tween(700, delayMillis = 200)) { it / 4 }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    // 闹钟图标
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 事件名称
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = accentColor.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = eventContent.ifEmpty { "目标事件" },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (targetReached) {
                        // 目标已到达
                        Text(
                            text = statusText,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            color = accentColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.scale(pulseScale)
                        )
                    } else {
                        // 倒计时显示
                        Text(
                            text = statusText,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = bigNumber,
                            fontSize = 140.sp,
                            fontWeight = FontWeight.Black,
                            color = accentColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 140.sp,
                            modifier = Modifier.scale(pulseScale)
                        )

                        Text(
                            text = unitLabel,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // ===== 底部：操作按钮 =====
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(600, delayMillis = 400))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 关闭按钮（大按钮，主要操作）
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5252),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text(
                            text = "关闭闹钟",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 稍后提醒按钮（次要操作）
                    Button(
                        onClick = onSnooze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text(
                            text = "稍后提醒（5分钟）",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// 辅助数据类
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun formatCurrentTime(): String {
    return java.time.LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault()))
}

private fun formatCurrentDate(): String {
    return java.time.LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE", Locale.getDefault()))
}
