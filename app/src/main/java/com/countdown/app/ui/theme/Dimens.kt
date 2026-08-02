package com.countdown.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Countdown Design System - Dimension & Spacing System
 *
 * Unified spacing tokens for consistent layout across the app.
 * Based on a 4dp baseline grid.
 */
object Dimens {

    // ===== Spacing =====
    val spacingXs = 4.dp      // Tight spacing: icon-text gap
    val spacingSm = 8.dp      // Small spacing: between related items
    val spacingMd = 12.dp     // Medium spacing: card internal padding
    val spacingLg = 16.dp     // Standard spacing: card padding, section gaps
    val spacingXl = 20.dp     // Large spacing: between sections
    val spacingXxl = 24.dp    // Extra large: major section breaks
    val spacingXxxl = 32.dp   // Hero spacing: top/bottom of screen

    // ===== Card dimensions =====
    val cardPadding = 20.dp
    val cardCorner = 20.dp
    val cardElevation = 2.dp
    val cardElevationPressed = 1.dp

    // ===== Button dimensions =====
    val buttonHeight = 52.dp
    val buttonHeightCompact = 44.dp
    val buttonCorner = 14.dp
    val buttonIconSize = 22.dp

    // ===== Icon sizes =====
    val iconSm = 18.dp
    val iconMd = 22.dp
    val iconLg = 28.dp
    val iconXl = 36.dp

    // ===== Widget dimensions =====
    val widgetPadding = 20.dp
    val widgetCorner = 24.dp

    // ===== Top bar =====
    val topBarHeight = 64.dp
}
