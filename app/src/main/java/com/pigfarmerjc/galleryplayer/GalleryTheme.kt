package com.pigfarmerjc.galleryplayer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Charcoal = Color(0xFF101114)
private val CharcoalElevated = Color(0xFF191B20)
private val CharcoalSurface = Color(0xFF22252C)
private val IceBlue = Color(0xFF9CCBFF)
private val IceBlueBright = Color(0xFFD6E8FF)
private val Ink = Color(0xFFE9EEF6)
private val MutedInk = Color(0xFF9BA6B6)
private val WarmError = Color(0xFFFFB4AB)

private val GalleryDarkColors = darkColorScheme(
    primary = IceBlue,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF174A76),
    onPrimaryContainer = IceBlueBright,
    secondary = Color(0xFFB9C8DB),
    onSecondary = Color(0xFF233140),
    background = Charcoal,
    onBackground = Ink,
    surface = CharcoalElevated,
    onSurface = Ink,
    surfaceVariant = CharcoalSurface,
    onSurfaceVariant = MutedInk,
    error = WarmError,
    onError = Color(0xFF690005)
)

private val GalleryLightColors = lightColorScheme(
    primary = Color(0xFF175A91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E5FF),
    onPrimaryContainer = Color(0xFF001D34),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF181B20),
    surface = Color.White,
    onSurface = Color(0xFF181B20),
    surfaceVariant = Color(0xFFE4E8EF),
    onSurfaceVariant = Color(0xFF434A54)
)

private val GalleryShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
)

@Composable
fun GalleryPlayerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) GalleryDarkColors else GalleryLightColors,
        typography = Typography(),
        shapes = GalleryShapes,
        content = content
    )
}
