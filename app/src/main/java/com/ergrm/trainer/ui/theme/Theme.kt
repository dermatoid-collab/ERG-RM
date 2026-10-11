package com.ergrm.trainer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// TrainerDay-style accent palette: green/gray/red to signal below/at/above target power, plus the
// other semantic/zone colors used across charts and tiles. These are clinical/semantic signals,
// not decoration, so — exactly like HRV-RM's RangeWithin/RangeAbove/RangeBelow — they stay fixed
// regardless of which ThemeMode is active: a color that means "above target power" must read the
// same no matter what background/palette the rider picked.
val ErgAccent = Color(0xFF3DDC84)
val ErgBelowTarget = Color(0xFF5AA9E6)
val ErgAtTarget = Color(0xFF3DDC84)
val ErgAboveTarget = Color(0xFFE65A5A)
val ErgWarn = Color(0xFFE6C15A)
val ErgDivider = Color(0xFF7FC2F0)
val ErgProgressLine = Color(0xFF4E8FE8)
// Same light blue as CORE's own normal-range color (coreColor's ErgBelowTarget case).
val ErgCadenceLine = ErgBelowTarget
val ErgHrPlus = Color(0xFFE6598A)
// Dedicated, more vivid colors reused across the live chart and history detail: kept separate
// from ErgAboveTarget/ErgAccent (which mean "above target power" and "at target"/theme-primary
// respectively) so brightening the HR trace or the ERG mode tag doesn't shift those other meanings.
val ErgHrLine = Color(0xFFFF0000)
val ErgModeErg = Color(0xFF2ECC71)
// SLO mode's own tag color — distinct from ERG's green and HR+'s pink so the 3-state pill always
// reads unambiguously which mode is active.
val ErgModeSlo = Color(0xFF1ABC9C)
// The CORE sensor's skin-temperature pill value, chosen distinct from ErgHrLine/ErgWarn/
// ZONE_MAX.color — the other three colors in the same pill row — so all three stay tellable apart.
val ErgSkinTemp = Color(0xFFD49494)
// CORE's Heat Training Load tile value — distinct from every other accent already in a tile row
// (red HR, blue cadence, amber warn) so it reads as its own thing.
val ErgHeatLoad = Color(0xFFFF9800)
// The intensity pill's directional triangle (below/above target) — the pill's background used to
// carry this meaning via ErgIntensityUp/Down, but that's now a fixed color instead, so the
// direction lives entirely in this small glyph's color.
val ErgIntensityDownGlyph = Color(0xFF00B3FF)
val ErgIntensityUpGlyph = Color(0xFFFFC233)
// "Live/in-progress" accent: the Interval/Total progress bar fill and the "Now" row's accent
// bar + text, so both read as the same "this is happening right now" signal.
val ErgLiveAccent = Color(0xFF00B3FF)

enum class ThemeMode(val label: String, val dark: Boolean? = null) {
    SYSTEM("System default"),
    LIGHT("Light", false),
    DARK("Dark", true),
    ERG_RM_LEGACY("ERG-RM Legacy", true),
    TOKYO_NIGHT("Tokyo Night", true),
    NORD("Nord", true),
    DRACULA("Dracula", true),
    GRUVBOX_DARK("Gruvbox Dark", true),
    CATPPUCCIN_MOCHA("Catppuccin Mocha", true),
    ONE_DARK("One Dark", true),
    SOLARIZED_DARK("Solarized Dark", true),
    AMOLED("Black (OLED)", true),
    SOLARIZED_LIGHT("Solarized Light", false),
    CATPPUCCIN_LATTE("Catppuccin Latte", false),
    GRUVBOX_LIGHT("Gruvbox Light", false),
}

private class Palette(
    val dark: Boolean,
    val background: Long,
    val surface: Long,
    val surfaceHigh: Long,
    val surfaceHighest: Long,
    val onBackground: Long,
    val onVariant: Long,
    val primary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val secondary: Long,
    val tertiary: Long,
    val outline: Long,
    val outlineVariant: Long,
    val error: Long,
    val errorContainer: Long,
) {
    private fun c(rgb: Long) = Color(0xFF000000 or rgb)

    fun scheme(): ColorScheme {
        // Picked by the primary color's own luminance rather than hardcoded per dark/light, so a
        // light, saturated primary (e.g. legacy's green) still gets readable dark text on it.
        val onPrimary = contrastOn(c(primary))
        return if (dark) {
            darkColorScheme(
                background = c(background),
                surface = c(surface),
                surfaceVariant = c(surfaceHigh),
                surfaceContainer = c(surfaceHigh),
                surfaceContainerHigh = c(surfaceHighest),
                surfaceContainerHighest = c(surfaceHighest),
                surfaceContainerLow = c(surface),
                surfaceContainerLowest = c(background),
                onBackground = c(onBackground),
                onSurface = c(onBackground),
                onSurfaceVariant = c(onVariant),
                primary = c(primary),
                onPrimary = onPrimary,
                primaryContainer = c(primaryContainer),
                onPrimaryContainer = c(onPrimaryContainer),
                secondary = c(secondary),
                tertiary = c(tertiary),
                outline = c(outline),
                outlineVariant = c(outlineVariant),
                error = c(error),
                errorContainer = c(errorContainer),
            )
        } else {
            lightColorScheme(
                background = c(background),
                surface = c(surface),
                surfaceVariant = c(surfaceHigh),
                surfaceContainer = c(surfaceHigh),
                surfaceContainerHigh = c(surfaceHighest),
                surfaceContainerHighest = c(surfaceHighest),
                surfaceContainerLow = c(surface),
                surfaceContainerLowest = c(background),
                onBackground = c(onBackground),
                onSurface = c(onBackground),
                onSurfaceVariant = c(onVariant),
                primary = c(primary),
                onPrimary = onPrimary,
                primaryContainer = c(primaryContainer),
                onPrimaryContainer = c(onPrimaryContainer),
                secondary = c(secondary),
                tertiary = c(tertiary),
                outline = c(outline),
                outlineVariant = c(outlineVariant),
                error = c(error),
                errorContainer = c(errorContainer),
            )
        }
    }

    fun swatches(): List<Color> = listOf(c(background), c(primary), c(secondary), c(tertiary))
}

private fun contrastOn(bg: Color): Color {
    val luminance = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    return if (luminance > 0.5f) Color.Black else Color.White
}

// ERG-RM Legacy reproduces today's original look bit-for-bit: the same background/surface/
// onSurface values ERG-RM has always used, just routed through the new Palette/ColorScheme
// machinery instead of a bespoke darkColorScheme() call.
private val ErgRmLegacyPalette = Palette(
    dark = true,
    background = 0x070D13,
    surface = 0x1B1F26,
    surfaceHigh = 0x20252D,
    surfaceHighest = 0x262C35,
    onBackground = 0xE8EAED,
    onVariant = 0xE8EAED,
    primary = 0x3DDC84,
    primaryContainer = 0x1F5234,
    onPrimaryContainer = 0xE8EAED,
    secondary = 0x5AA9E6,
    tertiary = 0x5AA9E6,
    outline = 0x7FC2F0,
    outlineVariant = 0x20252D,
    error = 0xE65A5A,
    errorContainer = 0x4A1F1E,
)

private val Palettes: Map<ThemeMode, Palette> = mapOf(
    ThemeMode.ERG_RM_LEGACY to ErgRmLegacyPalette,
    ThemeMode.TOKYO_NIGHT to Palette(dark = true, background = 0x1A1B26, surface = 0x1F2335, surfaceHigh = 0x292E42, surfaceHighest = 0x2F3549, onBackground = 0xC0CAF5, onVariant = 0xA9B1D6, primary = 0x5A7BD0, primaryContainer = 0x3D59A1, onPrimaryContainer = 0xC0CAF5, secondary = 0xBB9AF7, tertiary = 0x7DCFFF, outline = 0x565F89, outlineVariant = 0x3B4261, error = 0xF7768E, errorContainer = 0x51283A),
    ThemeMode.NORD to Palette(dark = true, background = 0x2E3440, surface = 0x3B4252, surfaceHigh = 0x434C5E, surfaceHighest = 0x4C566A, onBackground = 0xECEFF4, onVariant = 0xD8DEE9, primary = 0x5E81AC, primaryContainer = 0x4C6A8F, onPrimaryContainer = 0xECEFF4, secondary = 0x88C0D0, tertiary = 0xA3BE8C, outline = 0x616E88, outlineVariant = 0x4C566A, error = 0xBF616A, errorContainer = 0x5B3438),
    ThemeMode.DRACULA to Palette(dark = true, background = 0x282A36, surface = 0x2F3142, surfaceHigh = 0x44475A, surfaceHighest = 0x51556B, onBackground = 0xF8F8F2, onVariant = 0xC9CBE0, primary = 0x7E5FC9, primaryContainer = 0x5B4A99, onPrimaryContainer = 0xF1E9FF, secondary = 0xFF79C6, tertiary = 0x8BE9FD, outline = 0x6272A4, outlineVariant = 0x44475A, error = 0xFF5555, errorContainer = 0x5A2A2F),
    ThemeMode.GRUVBOX_DARK to Palette(dark = true, background = 0x282828, surface = 0x32302F, surfaceHigh = 0x3C3836, surfaceHighest = 0x504945, onBackground = 0xEBDBB2, onVariant = 0xD5C4A1, primary = 0xC4560A, primaryContainer = 0x8F3F08, onPrimaryContainer = 0xFBE9CF, secondary = 0xB8BB26, tertiary = 0x83A598, outline = 0x928374, outlineVariant = 0x504945, error = 0xFB4934, errorContainer = 0x5C2420),
    ThemeMode.CATPPUCCIN_MOCHA to Palette(dark = true, background = 0x1E1E2E, surface = 0x262637, surfaceHigh = 0x313244, surfaceHighest = 0x45475A, onBackground = 0xCDD6F4, onVariant = 0xBAC2DE, primary = 0x7B68D9, primaryContainer = 0x5B4DA8, onPrimaryContainer = 0xE0D8FF, secondary = 0xF5C2E7, tertiary = 0x89DCEB, outline = 0x6C7086, outlineVariant = 0x45475A, error = 0xF38BA8, errorContainer = 0x5A2D3A),
    ThemeMode.ONE_DARK to Palette(dark = true, background = 0x282C34, surface = 0x2C313A, surfaceHigh = 0x353B45, surfaceHighest = 0x3E4451, onBackground = 0xABB2BF, onVariant = 0x9DA5B4, primary = 0x4D78CC, primaryContainer = 0x3A5A9B, onPrimaryContainer = 0xDCE8FF, secondary = 0xC678DD, tertiary = 0x56B6C2, outline = 0x5C6370, outlineVariant = 0x3E4451, error = 0xE06C75, errorContainer = 0x58323A),
    ThemeMode.SOLARIZED_DARK to Palette(dark = true, background = 0x002B36, surface = 0x073642, surfaceHigh = 0x0E4350, surfaceHighest = 0x15505E, onBackground = 0x93A1A1, onVariant = 0x839496, primary = 0x1F7BBF, primaryContainer = 0x14557F, onPrimaryContainer = 0xD6ECFA, secondary = 0x2AA198, tertiary = 0xB58900, outline = 0x586E75, outlineVariant = 0x0E4350, error = 0xDC322F, errorContainer = 0x4A1F1E),
    ThemeMode.AMOLED to Palette(dark = true, background = 0x000000, surface = 0x0A0A0A, surfaceHigh = 0x161616, surfaceHighest = 0x222222, onBackground = 0xE6E1E5, onVariant = 0xCAC4D0, primary = 0xD9426F, primaryContainer = 0x8A1E3A, onPrimaryContainer = 0xFFD9E2, secondary = 0x42D9CC, tertiary = 0x64B5E8, outline = 0x6A6A6A, outlineVariant = 0x2E2E2E, error = 0xFF6B6B, errorContainer = 0x5A1E1E),
    ThemeMode.SOLARIZED_LIGHT to Palette(dark = false, background = 0xFDF6E3, surface = 0xF5EEDA, surfaceHigh = 0xEEE8D5, surfaceHighest = 0xE6DFC8, onBackground = 0x073642, onVariant = 0x586E75, primary = 0x1F7BBF, primaryContainer = 0xBBDDF3, onPrimaryContainer = 0x073642, secondary = 0x2AA198, tertiary = 0xB58900, outline = 0x93A1A1, outlineVariant = 0xD9D2BA, error = 0xDC322F, errorContainer = 0xF8D3CF),
    ThemeMode.CATPPUCCIN_LATTE to Palette(dark = false, background = 0xEFF1F5, surface = 0xE6E9EF, surfaceHigh = 0xDCE0E8, surfaceHighest = 0xCCD0DA, onBackground = 0x4C4F69, onVariant = 0x6C6F85, primary = 0x8839EF, primaryContainer = 0xE2CFFB, onPrimaryContainer = 0x2E1A52, secondary = 0x1E66F5, tertiary = 0x179299, outline = 0x9CA0B0, outlineVariant = 0xBCC0CC, error = 0xD20F39, errorContainer = 0xF8CCD4),
    ThemeMode.GRUVBOX_LIGHT to Palette(dark = false, background = 0xFBF1C7, surface = 0xF2E5BC, surfaceHigh = 0xEBDBB2, surfaceHighest = 0xD5C4A1, onBackground = 0x3C3836, onVariant = 0x504945, primary = 0xAF3A03, primaryContainer = 0xFAD7B5, onPrimaryContainer = 0x5A1F00, secondary = 0x79740E, tertiary = 0x427B58, outline = 0x928374, outlineVariant = 0xD5C4A1, error = 0x9D0006, errorContainer = 0xF4C7C3),
)

fun themeSwatches(mode: ThemeMode): List<Color> = when (mode) {
    ThemeMode.SYSTEM -> listOf(Color(0xFF070D13), Color(0xFF3DDC84), Color(0xFFFFFFFF), Color(0xFF070D13))
    ThemeMode.LIGHT -> lightColorScheme().let { listOf(it.background, it.primary, it.secondary, it.tertiary) }
    ThemeMode.DARK -> darkColorScheme().let { listOf(it.background, it.primary, it.secondary, it.tertiary) }
    else -> Palettes[mode]?.swatches() ?: ErgRmLegacyPalette.swatches()
}

// Currently resolved MaterialTheme colors — used by non-Canvas @Composable call sites that need
// the "surface" family to react to the active ThemeMode. (A Canvas{} draw block is a plain
// DrawScope lambda, not @Composable, so it can't read these directly: hoist a local val from the
// enclosing @Composable body before the Canvas{} call instead — see WorkoutScreen/VitalsScreen/
// HistoryDetailScreen for examples.)
val ErgBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background

val ErgSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surface

val ErgSurface2: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

val ErgOnSurface: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

@Composable
fun ErgRmTheme(mode: ThemeMode = ThemeMode.ERG_RM_LEGACY, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val isDark = mode.dark ?: systemDark
    val colors = when (mode) {
        ThemeMode.SYSTEM -> if (systemDark) darkColorScheme() else lightColorScheme()
        ThemeMode.LIGHT -> lightColorScheme()
        ThemeMode.DARK -> darkColorScheme()
        else -> (Palettes[mode] ?: ErgRmLegacyPalette).scheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
