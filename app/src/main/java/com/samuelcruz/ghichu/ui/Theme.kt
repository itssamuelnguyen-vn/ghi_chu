package com.samuelcruz.ghichu.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.samuelcruz.ghichu.R
import com.samuelcruz.ghichu.core.Prefs
import com.samuelcruz.ghichu.core.S

/** Be Vietnam Pro: an open-source typeface drawn for Vietnamese (licenses/BeVietnamPro-OFL.txt). */
val BeVietnamPro = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
    Font(R.font.be_vietnam_pro_extrabold, FontWeight.ExtraBold),
)

private fun TextStyle.be(weight: FontWeight? = null) = copy(fontFamily = BeVietnamPro, fontWeight = weight ?: fontWeight)

private val typography = Typography().run {
    copy(
        displayLarge = displayLarge.be(FontWeight.ExtraBold),
        displayMedium = displayMedium.be(FontWeight.ExtraBold),
        displaySmall = displaySmall.be(FontWeight.ExtraBold),
        headlineLarge = headlineLarge.be(FontWeight.Bold),
        headlineMedium = headlineMedium.be(FontWeight.Bold),
        headlineSmall = headlineSmall.be(FontWeight.Bold),
        titleLarge = titleLarge.be(FontWeight.Bold),
        titleMedium = titleMedium.be(FontWeight.SemiBold),
        titleSmall = titleSmall.be(FontWeight.SemiBold),
        bodyLarge = bodyLarge.be(),
        bodyMedium = bodyMedium.be(),
        bodySmall = bodySmall.be(),
        labelLarge = labelLarge.be(FontWeight.SemiBold),
        labelMedium = labelMedium.be(FontWeight.SemiBold),
        labelSmall = labelSmall.be(FontWeight.Medium),
    )
}

private val Light = lightColorScheme(
    primary = Color(0xFFB45309),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFDE9C4),
    onPrimaryContainer = Color(0xFF452400),
    secondary = Color(0xFF8A5A1F),
    secondaryContainer = Color(0xFFF7E6CF),
    onSecondaryContainer = Color(0xFF3A2405),
    tertiary = Color(0xFF0F766E),
    background = Color(0xFFFAF8F4),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFAF8F4),
    onSurface = Color(0xFF1F1B16),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFDF9),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF3EFE8),
    surfaceContainerHighest = Color(0xFFEBE6DD),
    onSurfaceVariant = Color(0xFF6E665B),
    outline = Color(0xFFCFC6B8),
    outlineVariant = Color(0xFFE8E1D5),
    error = Color(0xFFDC2626),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFBBF24),
    onPrimary = Color(0xFF3B2300),
    primaryContainer = Color(0xFF5C3A00),
    onPrimaryContainer = Color(0xFFFDE9C4),
    secondary = Color(0xFFE6BE8A),
    secondaryContainer = Color(0xFF3D2E1C),
    onSecondaryContainer = Color(0xFFF7E6CF),
    tertiary = Color(0xFF5EEAD4),
    background = Color(0xFF121110),
    onBackground = Color(0xFFEDE7DF),
    surface = Color(0xFF121110),
    onSurface = Color(0xFFEDE7DF),
    surfaceContainerLowest = Color(0xFF0D0C0B),
    surfaceContainerLow = Color(0xFF1A1816),
    surfaceContainer = Color(0xFF1D1B19),
    surfaceContainerHigh = Color(0xFF262320),
    surfaceContainerHighest = Color(0xFF302C28),
    onSurfaceVariant = Color(0xFFB3AA9E),
    outline = Color(0xFF5A534A),
    outlineVariant = Color(0xFF34302B),
    error = Color(0xFFF87171),
)

/** The warm header/brand gradient (lock screen, empty states, the sync card). */
val BrandBrush = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706), Color(0xFFB45309)))

/** Whether the app is drawn dark (the user's choice in Settings, or the phone's setting). */
val LocalIsDark = staticCompositionLocalOf { false }

/** Size factor of note text, from Settings. */
val LocalNoteScale = staticCompositionLocalOf { 1f }

fun noteScaleOf(fontSize: Int) = when (fontSize) {
    0 -> 0.9f
    2 -> 1.15f
    3 -> 1.3f
    else -> 1f
}

/** [theme] is a [Prefs] THEME_ value. */
@Composable
fun GhiChuTheme(theme: Int, fontSize: Int, content: @Composable () -> Unit) {
    val dark = when (theme) {
        Prefs.THEME_LIGHT -> false
        Prefs.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalIsDark provides dark, LocalNoteScale provides noteScaleOf(fontSize)) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = typography) {
            // Text/icons outside a Surface (screens drawn on a plain Box) would otherwise default to black,
            // unreadable in dark mode. Like a base `color` on <body> in CSS.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
        }
    }
}

/**
 * Note background colours: index 0 is "none" (the normal card colour). Soft pastels in light mode, deep muted
 * tones in dark mode, so text stays readable in both.
 */
object NoteColors {
    private val light = listOf(
        0x00000000L, 0xFFFAD4CF, 0xFFFBE3C4, 0xFFFFF1B3, 0xFFDDF2C9, 0xFFCDEFE6,
        0xFFD3E6F8, 0xFFDCDDF7, 0xFFEADBF7, 0xFFF9D7E7, 0xFFEADFD3, 0xFFE7E7EA,
    )
    private val dark = listOf(
        0x00000000L, 0xFF5C2B29, 0xFF614A19, 0xFF5E5718, 0xFF345920, 0xFF16504B,
        0xFF2D555E, 0xFF283A66, 0xFF42275E, 0xFF5B2245, 0xFF442F19, 0xFF3C3F43,
    )
    val count = light.size

    fun name(i: Int): String = S.colorNames.getOrElse(i) { "" }

    /** Card/editor background for colour [i]; [fallback] for "none". */
    fun of(i: Int, dark: Boolean, fallback: Color): Color =
        if (i <= 0 || i >= count) fallback else Color((if (dark) this.dark else light)[i])

    @Composable
    @ReadOnlyComposable
    fun background(i: Int): Color = of(i, LocalIsDark.current, MaterialTheme.colorScheme.surfaceContainer)
}
