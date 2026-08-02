package com.countdown.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Countdown Design System - Color Palette
 *
 * Brand identity: Indigo primary + Sky secondary + Teal accent
 * Designed for a modern, premium tool-app aesthetic.
 */

// ==================== Brand Primary (Indigo) ====================
val BrandIndigo = Color(0xFF4F46E5)
val BrandIndigoLight = Color(0xFF818CF8)
val BrandIndigoDark = Color(0xFF3730A3)
val BrandIndigoContainer = Color(0xFFE0E2FF)
val BrandIndigoContainerDark = Color(0xFF3730A3)

// ==================== Brand Secondary (Sky Blue) ====================
val BrandSky = Color(0xFF0284C7)
val BrandSkyLight = Color(0xFF38BDF8)
val BrandSkyDark = Color(0xFF075985)
val BrandSkyContainer = Color(0xFFBAE6FD)
val BrandSkyContainerDark = Color(0xFF075985)

// ==================== Brand Tertiary (Teal) ====================
val BrandTeal = Color(0xFF0D9488)
val BrandTealLight = Color(0xFF2DD4BF)
val BrandTealDark = Color(0xFF115E59)
val BrandTealContainer = Color(0xFF99F6E4)
val BrandTealContainerDark = Color(0xFF115E59)

// ==================== Status Colors ====================
val BrandSuccess = Color(0xFF10B981)
val BrandSuccessLight = Color(0xFF6EE7B7)
val BrandSuccessDark = Color(0xFF047857)
val BrandSuccessContainer = Color(0xFFD1FAE5)
val BrandSuccessContainerDark = Color(0xFF065F46)

val BrandWarning = Color(0xFFF59E0B)
val BrandWarningLight = Color(0xFFFCD34D)
val BrandWarningDark = Color(0xFFB45309)
val BrandWarningContainer = Color(0xFFFEF3C7)
val BrandWarningContainerDark = Color(0xFF78350F)

val BrandError = Color(0xFFDC2626)
val BrandErrorLight = Color(0xFFFCA5A5)
val BrandErrorDark = Color(0xFF991B1B)
val BrandErrorContainer = Color(0xFFFEE2E2)
val BrandErrorContainerDark = Color(0xFF7F1D1D)

val BrandInfo = Color(0xFF3B82F6)
val BrandInfoLight = Color(0xFF93C5FD)
val BrandInfoDark = Color(0xFF1E40AF)
val BrandInfoContainer = Color(0xFFDBEAFE)
val BrandInfoContainerDark = Color(0xFF1E3A8A)

// ==================== Neutral Palette ====================
val NeutralBgLight = Color(0xFFFAFAFF)
val NeutralBgDark = Color(0xFF0F0F14)
val NeutralSurfaceLight = Color(0xFFFFFFFF)
val NeutralSurfaceDark = Color(0xFF1A1A22)
val NeutralSurfaceVariantLight = Color(0xFFEFEEF7)
val NeutralSurfaceVariantDark = Color(0xFF2D2D38)
val NeutralOutlineLight = Color(0xFF71717A)
val NeutralOutlineDark = Color(0xFF938F99)
val NeutralOutlineVariantLight = Color(0xFFD4D4D8)
val NeutralOutlineVariantDark = Color(0xFF49454F)

// ==================== Gradient Colors ====================
val GradientStart = Color(0xFF4F46E5)
val GradientEnd = Color(0xFF7C3AED)
val GradientStartDark = Color(0xFF3730A3)
val GradientEndDark = Color(0xFF6D28D9)

// Widget gradient
val WidgetGradientStart = Color(0xFF4F46E5)
val WidgetGradientEnd = Color(0xFF0EA5E9)
val WidgetGradientStartDark = Color(0xFF312E81)
val WidgetGradientEndDark = Color(0xFF0C4A6E)

// ==================== Extended Colors (non-Material 3) ====================
// Used for permission status indicators
val StatusGreen = Color(0xFF22C55E)
val StatusGreenBg = Color(0xFFF0FDF4)
val StatusGreenBgDark = Color(0xFF052E16)

val StatusOrange = Color(0xFFF97316)
val StatusOrangeBg = Color(0xFFFFF7ED)
val StatusOrangeBgDark = Color(0xFF431407)

val StatusRed = Color(0xFFEF4444)
val StatusRedBg = Color(0xFFFEF2F2)
val StatusRedBgDark = Color(0xFF450A0A)

val StatusBlue = Color(0xFF3B82F6)
val StatusBlueBg = Color(0xFFEFF6FF)
val StatusBlueBgDark = Color(0xFF172554)

// ==================== Light Color Scheme ====================
val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = BrandIndigoContainer,
    onPrimaryContainer = Color(0xFF1A1B4B),
    secondary = BrandSky,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = BrandSkyContainer,
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = BrandTeal,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = BrandTealContainer,
    onTertiaryContainer = Color(0xFF134E4A),
    error = BrandError,
    onError = Color(0xFFFFFFFF),
    errorContainer = BrandErrorContainer,
    onErrorContainer = Color(0xFF7F1D1D),
    background = NeutralBgLight,
    onBackground = Color(0xFF1A1A22),
    surface = NeutralSurfaceLight,
    onSurface = Color(0xFF1A1A22),
    surfaceVariant = NeutralSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF49454F),
    outline = NeutralOutlineLight,
    outlineVariant = NeutralOutlineVariantLight,
    inverseSurface = Color(0xFF2D2D38),
    inverseOnSurface = Color(0xFFF1F0FA),
    inversePrimary = Color(0xFFC7D0FF),
    scrim = Color(0xFF000000)
)

// ==================== Dark Color Scheme ====================
val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFFC7D0FF),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = BrandIndigoContainerDark,
    onPrimaryContainer = Color(0xFFE0E2FF),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF0C4A6E),
    secondaryContainer = BrandSkyContainerDark,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF134E4A),
    tertiaryContainer = BrandTealContainerDark,
    onTertiaryContainer = Color(0xFF99F6E4),
    error = BrandErrorLight,
    onError = Color(0xFF7F1D1D),
    errorContainer = BrandErrorDark,
    onErrorContainer = Color(0xFFFEE2E2),
    background = NeutralBgDark,
    onBackground = Color(0xFFE6E1E9),
    surface = NeutralSurfaceDark,
    onSurface = Color(0xFFE6E1E9),
    surfaceVariant = NeutralSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = NeutralOutlineDark,
    outlineVariant = NeutralOutlineVariantDark,
    inverseSurface = Color(0xFFE6E1E9),
    inverseOnSurface = Color(0xFF1A1A22),
    inversePrimary = BrandIndigo,
    scrim = Color(0xFF000000)
)
