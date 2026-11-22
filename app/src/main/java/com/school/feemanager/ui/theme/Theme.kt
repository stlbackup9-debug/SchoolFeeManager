package com.school.feemanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF1F4E79)
val Blue = Color(0xFF2E75B6)
val LightBlue = Color(0xFFDDEBF7)
val Surface = Color(0xFFF5F8FC)
val Green = Color(0xFF2E7D32)
val Amber = Color(0xFFB26A00)
val Red = Color(0xFFC62828)
val Grey = Color(0xFF6B7280)

private val colors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = LightBlue,
    onPrimaryContainer = Navy,
    secondary = Blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F1FA),
    onSecondaryContainer = Navy,
    background = Surface,
    surface = Color.White,
    surfaceVariant = Color(0xFFEFF3F8),
    onSurfaceVariant = Color(0xFF44505C),
    outline = Color(0xFFC3CCD6),
    error = Red,
)

private val typography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun SchoolFeeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
