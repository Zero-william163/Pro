package com.countdown.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.countdown.app.ui.theme.BrandIndigo
import com.countdown.app.ui.theme.BrandSky
import com.countdown.app.ui.theme.BrandTeal
import com.countdown.app.ui.theme.GradientEnd
import com.countdown.app.ui.theme.GradientStart
import kotlinx.coroutines.delay

/**
 * Countdown Design System - Reusable UI Components
 *
 * All components follow Material Design 3 guidelines and use the
 * app's design tokens (colors, shapes, typography, dimensions).
 */

// ==================== Hero Countdown Card ====================

/**
 * The main hero card displaying the super-large countdown number.
 *
 * This is the visual center of the home screen.
 * Layout: Event name → "还有" → super-large number → "天" → divider → info row
 *
 * Features a gradient background with the countdown number as focal point.
 */
@Composable
fun CountdownHeroCard(
    eventContent: String,
    daysRemaining: Long,
    targetReached: Boolean,
    targetDate: String,
    reminderText: String,
    nextReminder: String = "",
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = if (isDarkTheme) {
                            listOf(GradientStartDark, GradientEndDark)
                        } else {
                            listOf(GradientStart, GradientEnd)
                        }
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(28.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ===== Top: Event name =====
                Text(
                    text = eventContent.ifEmpty { "目标" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ===== "还有" label above the number =====
                Text(
                    text = when {
                        targetReached -> "目标已到达"
                        daysRemaining == 0L -> "就是"
                        daysRemaining < 0 -> "已过去"
                        else -> "还有"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                // ===== Super-large countdown number (visual center) =====
                AnimatedCountdownNumber(
                    days = daysRemaining,
                    targetReached = targetReached
                )

                Spacer(modifier = Modifier.height(2.dp))

                // ===== "天" label below the number =====
                Text(
                    text = when {
                        targetReached -> ""
                        daysRemaining == 0L -> "今天"
                        daysRemaining < 0 -> "天"
                        else -> "天"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ===== Divider =====
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.25f))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ===== Bottom: Info row (target date / reminder / next reminder) =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Target date
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "目标日期",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = targetDate,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // Reminder time
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "提醒时间",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = reminderText,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // Next reminder
                    if (nextReminder.isNotBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "下次提醒",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = nextReminder,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.95f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== Animated Countdown Number ====================

/**
 * Displays the countdown number with a count-up animation on appear.
 * Uses displayLarge typography for maximum visual impact.
 */
@Composable
fun AnimatedCountdownNumber(
    days: Long,
    targetReached: Boolean
) {
    var displayValue by remember { mutableIntStateOf(0) }

    LaunchedEffect(days) {
        if (targetReached || days <= 0) {
            displayValue = days.toInt()
        } else {
            // Animate from 0 to target
            val target = days.toInt()
            val steps = minOf(target, 30)
            if (steps > 0) {
                val stepValue = target / steps
                for (i in 0..steps) {
                    displayValue = if (i == steps) target else stepValue * i
                    delay(30)
                }
            } else {
                displayValue = target
            }
        }
    }

    val displayText = when {
        targetReached -> "到达"
        days == 0L -> "今天"
        days < 0 -> "${-displayValue}"
        else -> "$displayValue"
    }

    Text(
        text = displayText,
        style = MaterialTheme.typography.displayLarge,
        fontSize = 80.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center,
        letterSpacing = (-2).sp
    )
}

// ==================== Brand Card ====================

/**
 * Standard card component with consistent styling.
 * Uses design tokens for shape, elevation, and padding.
 */
@Composable
fun BrandCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    elevation: androidx.compose.ui.unit.Dp = 2.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

// ==================== Info Card Item ====================

/**
 * Icon + Title + Value info card.
 * Used for displaying information rows on the home screen.
 */
@Composable
fun InfoCardItem(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    clickable: Boolean = false,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cardScale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .then(if (clickable) Modifier.clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple()
            ) { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with background circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    color = valueColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (clickable) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ==================== Brand Button ====================

/**
 * Primary action button with gradient background, scale animation, and ripple.
 * Unified design for all primary actions in the app.
 */
@Composable
fun BrandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(),
                enabled = enabled,
                onClick = onClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (enabled) 4.dp else 0.dp,
            pressedElevation = 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = if (enabled) {
                            if (isDarkTheme) {
                                listOf(GradientStartDark, GradientEndDark)
                            } else {
                                listOf(GradientStart, GradientEnd)
                            }
                        } else {
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== Section Title ====================

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

// ==================== Animated Entrance ====================

/**
 * Wraps content with a staggered fade-in + slide-up entrance animation.
 */
@Composable
fun AnimatedEntrance(
    visible: Boolean,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 400, delayMillis = delayMillis)
        ) + slideInVertically(
            animationSpec = tween(durationMillis = 400, delayMillis = delayMillis),
            initialOffsetY = { it / 4 }
        ) + scaleIn(
            animationSpec = tween(durationMillis = 400, delayMillis = delayMillis),
            initialScale = 0.95f
        ),
        content = { content() }
    )
}

// ==================== Status Chip ====================

@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ==================== Utility ====================

/**
 * Calculate luminance of a color for dark/light theme detection.
 */
private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

// Dark gradient colors (imported locally to avoid circular dependency)
private val GradientStartDark = Color(0xFF3730A3)
private val GradientEndDark = Color(0xFF6D28D9)
