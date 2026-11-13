package com.nextspot.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/** Text styles from docs/design/DESIGN.md section 3. Sizes are the 2560x1440 Figma values (sp). */
class NsTextStyles(private val family: FontFamily) {
    private fun s(size: Int, line: Int, w: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = w)

    val logo = s(72, 88, FontWeight.Bold)
    val nowQuestion = s(42, 54, FontWeight.Bold)
    val title = s(36, 44, FontWeight.Bold)
    val yesNo = s(32, 40, FontWeight.Bold)
    val button = s(30, 44, FontWeight.Bold)
    val caption = s(28, 40)
    val step = s(26, 32)
    val stepCurrent = s(26, 32, FontWeight.Bold)
    val status = s(26, 32, FontWeight.Bold)
    val body = s(24, 28)
    val bodyBold = s(24, 28, FontWeight.Bold)
    val tag = s(22, 24, FontWeight.Bold)
    val small = s(20, 24, FontWeight.Medium)
    val bigPoint = s(48, 56, FontWeight.Bold)

    /** Keep Korean words whole when a line wraps (like CSS word-break: keep-all). */
    val keepAll = LineBreak(
        strategy = LineBreak.Strategy.HighQuality,
        strictness = LineBreak.Strictness.Strict,
        wordBreak = LineBreak.WordBreak.Phrase,
    )
}

internal val LocalNsType = staticCompositionLocalOf { NsTextStyles(FontFamily.Default) }

/** Use inside composables: `Text("..", style = NsType.title)`. Set up by [NsTheme]. */
val NsType: NsTextStyles
    @Composable @ReadOnlyComposable get() = LocalNsType.current

/**
 * Asta Sans (the Pleos system font), looked up by name at runtime so the app still builds and runs
 * if the font files are missing (then the default font is used).
 * Files: app/src/main/res/font/asta_sans_regular / _medium / _bold .ttf  (or a single asta_sans.ttf)
 */
@Composable
internal fun rememberNsFontFamily(): FontFamily {
    val ctx = LocalContext.current
    return remember(ctx) {
        fun res(vararg names: String): Int = names.firstNotNullOfOrNull { n ->
            ctx.resources.getIdentifier(n, "font", ctx.packageName).takeIf { it != 0 }
        } ?: 0
        val regular = res("asta_sans_regular", "astasans_regular", "asta_sans")
        val fonts = listOf(
            regular to FontWeight.Normal,
            res("asta_sans_medium", "astasans_medium").takeIf { it != 0 }.let { it ?: regular } to FontWeight.Medium,
            res("asta_sans_bold", "astasans_bold").takeIf { it != 0 }.let { it ?: regular } to FontWeight.Bold,
        ).filter { it.first != 0 }.map { (id, w) -> Font(id, w) }
        if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
    }
}

/** Material3 text styles (used by the Car A / Car B screens) with the Pleos font applied. */
internal fun nsMaterialTypography(f: FontFamily): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = f),
        displayMedium = d.displayMedium.copy(fontFamily = f),
        displaySmall = d.displaySmall.copy(fontFamily = f),
        headlineLarge = d.headlineLarge.copy(fontFamily = f),
        headlineMedium = d.headlineMedium.copy(fontFamily = f),
        headlineSmall = d.headlineSmall.copy(fontFamily = f),
        titleLarge = d.titleLarge.copy(fontFamily = f),
        titleMedium = d.titleMedium.copy(fontFamily = f),
        titleSmall = d.titleSmall.copy(fontFamily = f),
        bodyLarge = d.bodyLarge.copy(fontFamily = f),
        bodyMedium = d.bodyMedium.copy(fontFamily = f),
        bodySmall = d.bodySmall.copy(fontFamily = f),
        labelLarge = d.labelLarge.copy(fontFamily = f),
        labelMedium = d.labelMedium.copy(fontFamily = f),
        labelSmall = d.labelSmall.copy(fontFamily = f),
    )
}
