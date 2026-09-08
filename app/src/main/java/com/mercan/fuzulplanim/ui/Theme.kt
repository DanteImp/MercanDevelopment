package com.mercan.fuzulplanim.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF183F5F)
val Blue = Color(0xFF0B63CE)
val Teal = Color(0xFF0F766E)
val Green = Color(0xFF178447)
val Red = Color(0xFFC0362C)
val Amber = Color(0xFFB7791F)
val Ink = Color(0xFF1F2937)
val Muted = Color(0xFF6B7280)
val SurfaceSoft = Color(0xFFF4F7FB)
val Border = Color(0xFFE3E8EF)

private val AppColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color.White,
    tertiary = Green,
    error = Red,
    background = SurfaceSoft,
    surface = Color.White,
    onSurface = Ink,
    outline = Border
)

@Composable
fun FuzulTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, content = content)
}
