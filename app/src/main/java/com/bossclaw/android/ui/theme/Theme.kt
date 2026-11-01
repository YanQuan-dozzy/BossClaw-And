package com.bossclaw.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object BossClawColors {
    val Teal = Color(0xFF0B7A75)
    val TealDeep = Color(0xFF09635F)
    val TealBright = Color(0xFF17B3A6)
    val TealGlow = Color(0xFF7DE7DA)
    val TealSoft = Color(0xFFD8EFEC)
    val Amber = Color(0xFFC4782E)
    val AmberBright = Color(0xFFE0A467)
    val AmberSoft = Color(0xFFFBEBD8)
    val AmberText = Color(0xFF8A4F14)
    val Ink = Color(0xFF1B2422)
    val InkSoft = Color(0xFF46544F)
    val Paper = Color(0xFFF6F4EE)
    val PaperDeep = Color(0xFFEFECE2)
    val Card = Color(0xFFFFFFFF)
    val Line = Color(0xFFE6E1D5)
    val Muted = Color(0xFF5F6E68)
    val Danger = Color(0xFFB3443A)
    val Success = Color(0xFF2E8B57)
    val Info = Color(0xFF3E6E8E)

    val DarkBackground = Color(0xFF0F1614)
    val DarkSurface = Color(0xFF17211F)
    val DarkSurfaceHigh = Color(0xFF1E2A27)
    val DarkLine = Color(0xFF2A3835)
    val DarkText = Color(0xFFE9F1ED)
}

private val LightScheme = lightColorScheme(
    primary = BossClawColors.Teal,
    onPrimary = Color.White,
    primaryContainer = BossClawColors.TealSoft,
    onPrimaryContainer = Color(0xFF063B38),
    secondary = BossClawColors.Amber,
    onSecondary = Color.White,
    secondaryContainer = BossClawColors.AmberSoft,
    onSecondaryContainer = Color(0xFF5C3510),
    tertiary = BossClawColors.Info,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3EDF3),
    onTertiaryContainer = Color(0xFF17323F),
    background = BossClawColors.Paper,
    onBackground = BossClawColors.Ink,
    surface = BossClawColors.Card,
    onSurface = BossClawColors.Ink,
    surfaceVariant = Color(0xFFEFEBE2),
    onSurfaceVariant = BossClawColors.Muted,
    outline = BossClawColors.Line,
    outlineVariant = Color(0xFFECE7DC),
    error = BossClawColors.Danger,
    onError = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF8F3),
    surfaceContainer = Color(0xFFEFEBE2),
    surfaceContainerHigh = Color(0xFFE7E2D6),
    surfaceContainerHighest = Color(0xFFDFD9CB),
    inverseSurface = BossClawColors.Ink,
    inverseOnSurface = BossClawColors.Paper,
    inversePrimary = Color(0xFF5FD1C8),
    scrim = Color.Black
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5FD1C8),
    onPrimary = Color(0xFF00312D),
    primaryContainer = Color(0xFF10423E),
    onPrimaryContainer = Color(0xFFB5EDE7),
    secondary = Color(0xFFE0A467),
    onSecondary = Color(0xFF3D2408),
    secondaryContainer = Color(0xFF4B3314),
    onSecondaryContainer = Color(0xFFF7DFC2),
    tertiary = Color(0xFF8FB6CE),
    onTertiary = Color(0xFF0B2837),
    tertiaryContainer = Color(0xFF24404F),
    onTertiaryContainer = Color(0xFFCFE4EF),
    background = BossClawColors.DarkBackground,
    onBackground = BossClawColors.DarkText,
    surface = BossClawColors.DarkSurface,
    onSurface = BossClawColors.DarkText,
    surfaceVariant = Color(0xFF1E2A27),
    onSurfaceVariant = Color(0xFF93A39D),
    outline = BossClawColors.DarkLine,
    outlineVariant = Color(0xFF22302C),
    error = Color(0xFFE58C82),
    onError = Color(0xFF3B0A06),
    surfaceContainerLowest = Color(0xFF0B110F),
    surfaceContainerLow = Color(0xFF131C1A),
    surfaceContainer = Color(0xFF1A2523),
    surfaceContainerHigh = Color(0xFF1E2A27),
    surfaceContainerHighest = Color(0xFF25332F),
    inverseSurface = BossClawColors.DarkText,
    inverseOnSurface = BossClawColors.DarkBackground,
    inversePrimary = BossClawColors.Teal,
    scrim = Color.Black
)

/** 品牌渐变，统一在这里定义，避免各页面色调漂移。 */
object BossClawGradients {

    private val heroLight = listOf(Color(0xFF0C5F5A), Color(0xFF10877F), Color(0xFF37A99E))
    private val heroDark = listOf(Color(0xFF0B2B29), Color(0xFF10544F), Color(0xFF167C74))

    fun hero(dark: Boolean): Brush = Brush.linearGradient(
        colors = if (dark) heroDark else heroLight,
        start = Offset.Zero,
        end = Offset(1200f, 780f)
    )

    fun quota(dark: Boolean): Brush = Brush.horizontalGradient(
        colors = if (dark) {
            listOf(Color(0xFF7DE7DA), Color(0xFFF0C48A))
        } else {
            listOf(Color(0xFF9CF2E7), Color(0xFFFFE2B8))
        }
    )

    fun sheen(): Brush = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)
    )

    /** 卡片顶部微光：让纯色卡片有一点材质感，不靠更重的描边来区分层级。 */
    fun cardSheen(dark: Boolean): Brush = Brush.verticalGradient(
        colors = if (dark) {
            listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)
        } else {
            listOf(Color.White.copy(alpha = 0.62f), Color.Transparent)
        }
    )
}

/** 动效规格：统一时长与曲线，让页面切换、数值变化保持同一种节奏。 */
object BossClawMotion {
    const val FAST = 180
    const val PRESS = 120
    const val BASE = 300
    const val SLOW = 520

    val Standard: Easing = FastOutSlowInEasing
    val EmphasizedDecel: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccel: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> settle(): SpringSpec<T> =
        spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    fun <T> snappy(): SpringSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
}

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 39.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.5.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.5.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.5.sp,
        letterSpacing = 0.2.sp
    )
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** 当前主题是否为深色，供渐变、插画等需要手动配色的组件读取。 */
val LocalBossClawDark = staticCompositionLocalOf { false }

@Composable
fun BossClawTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalBossClawDark provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
