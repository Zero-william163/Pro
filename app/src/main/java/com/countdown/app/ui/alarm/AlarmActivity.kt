package com.countdown.app.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.countdown.app.service.AlarmService
import com.countdown.app.ui.theme.CountdownTheme
import com.countdown.app.util.DateCalculator
import kotlinx.coroutines.delay

class AlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EVENT_CONTENT = "event_content"
        const val EXTRA_DAYS_REMAINING = "days_remaining"
        const val EXTRA_TARGET_REACHED = "target_reached"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Turn screen on and show over lock screen
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

        // Keep screen on for all API levels
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Dismiss keyguard so the full-screen alarm is visible
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        val eventContent = intent.getStringExtra(EXTRA_EVENT_CONTENT) ?: ""
        val daysRemaining = intent.getLongExtra(EXTRA_DAYS_REMAINING, 0)
        val targetReached = intent.getBooleanExtra(EXTRA_TARGET_REACHED, false)

        setContent {
            CountdownTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AlarmScreen(
                        eventContent = eventContent,
                        daysRemaining = daysRemaining,
                        targetReached = targetReached,
                        onDismiss = { dismissAlarm() }
                    )
                }
            }
        }
    }

    /**
     * Stop the AlarmService (sound + vibration) and remove the activity from the task stack.
     * Note: onDestroy intentionally does NOT stop the service, so that swiping the activity
     * away from recents does not prematurely stop the alarm sound.
     */
    private fun dismissAlarm() {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
        finishAndRemoveTask()
    }

    // onDestroy intentionally does NOT stop AlarmService to prevent accidental alarm cutoff.
}

@Composable
fun AlarmScreen(
    eventContent: String,
    daysRemaining: Long,
    targetReached: Boolean,
    onDismiss: () -> Unit
) {
    // Live-updating current time
    var currentTime by remember { mutableStateOf(DateCalculator.formatCurrentDateTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = DateCalculator.formatCurrentDateTime()
        }
    }

    // Entrance animation state
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        contentVisible = true
    }

    // Pulse animation for the countdown number
    val infiniteTransition = rememberInfiniteTransition(label = "alarmPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Determine the big number and label to display
    val bigNumber: String
    val unitLabel: String
    val statusText: String
    when {
        targetReached -> {
            bigNumber = ""
            unitLabel = ""
            statusText = "目标日期已到达！"
        }
        daysRemaining == 0L -> {
            bigNumber = "0"
            unitLabel = "天"
            statusText = "就是今天！"
        }
        daysRemaining < 0 -> {
            bigNumber = (-daysRemaining).toString()
            unitLabel = "天前"
            statusText = "已过去"
        }
        else -> {
            bigNumber = daysRemaining.toString()
            unitLabel = "天"
            statusText = "还有"
        }
    }

    val gradientColors = listOf(
        Color(0xFF1A1A2E),
        Color(0xFF16213E),
        Color(0xFF0F0F1E)
    )
    val accentColor = Color(0xFFFF6B6B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top: current time
        Text(
            text = currentTime,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        // Middle: event content + countdown number (with pulse animation)
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(animationSpec = tween(500)) +
                slideInVertically(
                    animationSpec = tween(500),
                    initialOffsetY = { it / 4 }
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Event content
                Text(
                    text = "【$eventContent】",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (targetReached) {
                    // Target reached: show status text with pulse
                    Text(
                        text = statusText,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor.copy(alpha = pulseAlpha),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.scale(pulseScale)
                    )
                } else {
                    // Countdown: show status text + big number with pulse
                    Text(
                        text = statusText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Super large countdown number with pulse
                    Text(
                        text = bigNumber,
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor.copy(alpha = pulseAlpha),
                        textAlign = TextAlign.Center,
                        lineHeight = 120.sp,
                        modifier = Modifier.scale(pulseScale)
                    )

                    Text(
                        text = unitLabel,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Bottom: dismiss button
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "关闭提醒",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
