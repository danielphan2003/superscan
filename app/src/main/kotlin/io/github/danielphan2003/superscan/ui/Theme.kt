package io.github.danielphan2003.superscan.ui

import android.app.WallpaperManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.danielphan2003.superscan.R

@OptIn(ExperimentalTextApi::class)
private val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = 0.sp, fontFeatureSettings = "tnum",
)

private val SuperscanTypography = Typography(
    displayLarge = type(40, 48, FontWeight.SemiBold),
    displayMedium = type(34, 42, FontWeight.SemiBold),
    displaySmall = type(30, 38, FontWeight.SemiBold),
    headlineLarge = type(28, 36, FontWeight.SemiBold),
    headlineMedium = type(26, 34, FontWeight.SemiBold),
    headlineSmall = type(24, 30, FontWeight.SemiBold),
    titleLarge = type(21, 28, FontWeight.SemiBold),
    titleMedium = type(16, 22, FontWeight.SemiBold),
    titleSmall = type(14, 20, FontWeight.Medium),
    bodyLarge = type(16, 22),
    bodyMedium = type(14, 20),
    bodySmall = type(13, 18),
    labelLarge = type(14, 20, FontWeight.SemiBold),
    labelMedium = type(13, 18, FontWeight.Medium),
    labelSmall = type(12, 16, FontWeight.Medium),
)

// Teal derived from the launcher's #10302A, with neutral instrument surfaces.
private val Dark = darkColorScheme(
    primary = Color(0xFF9AD5C6), onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF234B41), onPrimaryContainer = Color(0xFFB9F1E1),
    secondary = Color(0xFFB5CCC4), onSecondary = Color(0xFF20372F),
    secondaryContainer = Color(0xFF354D44), onSecondaryContainer = Color(0xFFD1E8DF),
    tertiary = Color(0xFFADCBDD), onTertiary = Color(0xFF163545),
    tertiaryContainer = Color(0xFF2D4C5D), onTertiaryContainer = Color(0xFFCAE6F9),
    background = Color(0xFF101313), onBackground = Color(0xFFE4E8E6),
    surface = Color(0xFF101313), onSurface = Color(0xFFE4E8E6),
    surfaceVariant = Color(0xFF343B38), onSurfaceVariant = Color(0xFFA4AEAA),
    surfaceContainerLowest = Color(0xFF0C0F0F), surfaceContainerLow = Color(0xFF171B1A),
    surfaceContainer = Color(0xFF1B1F1E), surfaceContainerHigh = Color(0xFF242928),
    surfaceContainerHighest = Color(0xFF2F3533),
    outline = Color(0xFF76817C), outlineVariant = Color(0xFF414A46),
    error = Color(0xFFFFB4AC), onError = Color(0xFF690005),
    errorContainer = Color(0xFF472321), onErrorContainer = Color(0xFFFFDAD5),
)
private val Light = lightColorScheme(
    primary = Color(0xFF286858), onPrimary = Color.White,
    primaryContainer = Color(0xFFB3EDDA), onPrimaryContainer = Color(0xFF074F40),
    secondary = Color(0xFF4D635A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E8DC), onSecondaryContainer = Color(0xFF354B42),
    tertiary = Color(0xFF466577), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCAE6F9), onTertiaryContainer = Color(0xFF2D4C5D),
    background = Color(0xFFF8FAF8), onBackground = Color(0xFF191D1B),
    surface = Color(0xFFF8FAF8), onSurface = Color(0xFF191D1B),
    surfaceVariant = Color(0xFFDDE5DF), onSurfaceVariant = Color(0xFF59645E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F5F2),
    surfaceContainer = Color(0xFFEEF2EE), surfaceContainerHigh = Color(0xFFE7ECE7),
    surfaceContainerHighest = Color(0xFFE1E7E1),
    outline = Color(0xFF747F78), outlineVariant = Color(0xFFBFCAC2),
    error = Color(0xFFA8322F), onError = Color.White,
    errorContainer = Color(0xFFFFDAD5), onErrorContainer = Color(0xFF410004),
)

@Composable
fun SuperscanTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    // Android supplies generic dynamic colors even without wallpaper colors.
    val hasWallpaperColors = remember(context) {
        runCatching {
            val wallpaper = WallpaperManager.getInstance(context)
            wallpaper.getWallpaperId(WallpaperManager.FLAG_SYSTEM) > 0 &&
                wallpaper.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) != null
        }.getOrDefault(false)
    }
    val scheme = when {
        hasWallpaperColors && dark -> dynamicDarkColorScheme(context)
        hasWallpaperColors -> dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = SuperscanTypography, content = content)
}

object StatusColors {
    val ok: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF98C9B5) else Color(0xFF28634F)
    val warn: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFE5C17D) else Color(0xFF835B16)
    val down: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF2AAA5) else Color(0xFFAD3835)
    val unknown: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
}

data class StatusTone(val foreground: Color, val container: Color, val border: Color)

@Composable
fun statusTone(status: String): StatusTone {
    val foreground = statusColor(status)
    return StatusTone(
        foreground,
        foreground.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surfaceContainer),
        foreground.copy(alpha = 0.30f),
    )
}
