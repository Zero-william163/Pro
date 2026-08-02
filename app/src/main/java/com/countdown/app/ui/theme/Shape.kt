package com.countdown.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Countdown Design System - Shape System
 *
 * Consistent corner radius across the entire app.
 * Radius increases with component size.
 */

val Shapes = Shapes(
    // Small components: chips, badges, small buttons
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    // Medium components: cards, text fields, dialogs
    medium = RoundedCornerShape(16.dp),
    // Large components: big cards, sheets
    large = RoundedCornerShape(24.dp),
    // Extra large: hero cards, bottom sheets
    extraLarge = RoundedCornerShape(28.dp)
)

// Custom shapes for specific components
val CardShape = RoundedCornerShape(20.dp)
val ButtonShape = RoundedCornerShape(14.dp)
val PillShape = RoundedCornerShape(50)
val WidgetShape = RoundedCornerShape(24.dp)
val BottomSheetShape = RoundedCornerShape(28.dp)
