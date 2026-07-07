package com.sangwolnongsan.routeopt.web.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.sangwolnongsan.routeopt.web.resources.Res
import com.sangwolnongsan.routeopt.web.resources.pretendard_regular
import org.jetbrains.compose.resources.Font

private val Brand = Color(0xFF1E6FEB)
private val BrandDark = Color(0xFF3B82F6)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Color(0xFF0EA5A4),
    background = Color(0xFFF6F8FB),
    surface = Color.White,
    error = Color(0xFFDC2626),
)

private val Dark = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color(0xFF0B1220),
    secondary = Color(0xFF2DD4BF),
    background = Color(0xFF0B1220),
    surface = Color(0xFF16202E),
    error = Color(0xFFF87171),
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
    ) {
        // 명시적 fontFamily 없는 Text 도 한글이 나오도록 기본 텍스트 스타일에 폰트 주입
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = family),
        ) {
            content()
        }
    }
}
