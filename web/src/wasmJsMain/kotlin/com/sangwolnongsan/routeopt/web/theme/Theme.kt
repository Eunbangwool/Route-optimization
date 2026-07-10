package com.sangwolnongsan.routeopt.web.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sangwolnongsan.routeopt.web.resources.Res
import com.sangwolnongsan.routeopt.web.resources.pretendard_regular
import org.jetbrains.compose.resources.Font

private val Light = lightColorScheme(
    primary = Color(0xFF1E6FEB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE7FF),
    onPrimaryContainer = Color(0xFF0A2E6B),
    secondary = Color(0xFF0E9488),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDF3EE),
    onSecondaryContainer = Color(0xFF033F3A),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = Color(0xFFF3F6FB),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEBF0F7),
    onSurfaceVariant = Color(0xFF4C5B72),
    outline = Color(0xFFC7D2E0),
    outlineVariant = Color(0xFFE1E8F1),
    error = Color(0xFFDC2626),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5C9BFF),
    onPrimary = Color(0xFF00204C),
    primaryContainer = Color(0xFF15386E),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF00201D),
    secondaryContainer = Color(0xFF0F423D),
    onSecondaryContainer = Color(0xFFA5F3E9),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF3A2A00),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF141E2C),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1E2A3B),
    onSurfaceVariant = Color(0xFF9CACC4),
    outline = Color(0xFF33415A),
    outlineVariant = Color(0xFF223148),
    error = Color(0xFFF87171),
)

/** 둥근 모서리 셰이프 스케일 — 카드/버튼/칩에 일관 적용. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Pretendard 한글 폰트 패밀리 (regular 번들, Bold 는 합성). */
@Composable
private fun pretendard(): FontFamily = FontFamily(
    Font(Res.font.pretendard_regular, FontWeight.Normal),
)

/** Material3 Typography 전 스타일에 폰트 적용. */
private fun typographyWith(family: FontFamily): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = family),
        displayMedium = d.displayMedium.copy(fontFamily = family),
        displaySmall = d.displaySmall.copy(fontFamily = family),
        headlineLarge = d.headlineLarge.copy(fontFamily = family),
        headlineMedium = d.headlineMedium.copy(fontFamily = family),
        headlineSmall = d.headlineSmall.copy(fontFamily = family),
        titleLarge = d.titleLarge.copy(fontFamily = family),
        titleMedium = d.titleMedium.copy(fontFamily = family),
        titleSmall = d.titleSmall.copy(fontFamily = family),
        bodyLarge = d.bodyLarge.copy(fontFamily = family),
        bodyMedium = d.bodyMedium.copy(fontFamily = family),
        bodySmall = d.bodySmall.copy(fontFamily = family),
        labelLarge = d.labelLarge.copy(fontFamily = family),
        labelMedium = d.labelMedium.copy(fontFamily = family),
        labelSmall = d.labelSmall.copy(fontFamily = family),
    )
}

@Composable
fun RouteOptTheme(content: @Composable () -> Unit) {
    val family = pretendard()
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = typographyWith(family),
        shapes = AppShapes,
    ) {
        // 명시적 fontFamily 없는 Text 도 한글이 나오도록 기본 텍스트 스타일에 폰트 주입
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = family),
        ) {
            content()
        }
    }
}
