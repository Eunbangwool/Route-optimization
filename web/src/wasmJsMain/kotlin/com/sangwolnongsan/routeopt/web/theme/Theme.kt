package com.sangwolnongsan.routeopt.web.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun RouteOptTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
