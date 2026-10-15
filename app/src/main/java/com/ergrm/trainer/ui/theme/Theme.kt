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
    // From SUNW-RM (same author's weather widget app): its 5 community dev palettes not already
    // above, plus its own 12 custom single-hue brand themes, carried over under their SUNW-RM
    // names/spelling.
    ROSE_PINE("Rosé Pine", true),
    EVERFOREST("Everforest", true),
    NIGHT_OWL("Night Owl", true),
    PALENIGHT("Palenight", true),
    AYU_MIRAGE("Ayu Mirage", true),
    VINO("Vino", true),
    METEOBLUE("MeteoBlue", true),
    TURCHESE("Turchese", true),
    VIOLA("Viola", true),
    CORALLO("Corallo", true),
    LIME("Lime", true),
    ARDESIA("Ardesia", false),
    SALVIA("Salvia", false),
    DUNA("Duna", false),
    ILMETEO("ilMeteo", false),
    AZZURRO("Azzurro", false),
    GIALLO("Giallo", false),
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
    // From SUNW-RM's Appearance.kt — background/surface/text/textSecondary/dim carried over
    // directly (pageBackground/cardBackground/text/textSecondary/dim there); primary/secondary/
    // tertiary/error derived from its highSun/blueHour/goldenHour/sky-ramp fields for the 5
    // community dev palettes below, or brightened from the theme's own surface color for its 12
    // single-hue brand themes (SUNW's highSun/blueHour don't reliably stay in those themes' own
    // hue family). Not visually verified on-device yet.
    ThemeMode.ROSE_PINE to Palette(dark = true, background = 0x12101B, surface = 0x1F1D2E, surfaceHigh = 0x252334, surfaceHighest = 0x2C2A3B, onBackground = 0xE0DEF4, onVariant = 0x908CAA, primary = 0xF6C177, primaryContainer = 0x9F7E54, onPrimaryContainer = 0xE0DEF4, secondary = 0x9CCFD8, tertiary = 0xEB6F92, outline = 0x6E6A86, outlineVariant = 0x46445A, error = 0xEB6F92, errorContainer = 0x743B51),
    ThemeMode.EVERFOREST to Palette(dark = true, background = 0x272E33, surface = 0x343F44, surfaceHigh = 0x394347, surfaceHighest = 0x3E484B, onBackground = 0xD3C6AA, onVariant = 0xA7A08B, primary = 0xDBBC7F, primaryContainer = 0x978662, onPrimaryContainer = 0xD3C6AA, secondary = 0x7FBBB3, tertiary = 0xE69875, outline = 0x859289, outlineVariant = 0x5C6866, error = 0xE67E80, errorContainer = 0x7D5256),
    ThemeMode.NIGHT_OWL to Palette(dark = true, background = 0x01111D, surface = 0x0B2942, surfaceHigh = 0x112E47, surfaceHighest = 0x18354D, onBackground = 0xD6DEEB, onVariant = 0x8AA3BF, primary = 0xECC48D, primaryContainer = 0x938062, onPrimaryContainer = 0xD6DEEB, secondary = 0x82AAFF, tertiary = 0xF78C6C, outline = 0x5F7E97, outlineVariant = 0x35546C, error = 0xEF5350, errorContainer = 0x6C2F34),
    ThemeMode.PALENIGHT to Palette(dark = true, background = 0x232635, surface = 0x32364A, surfaceHigh = 0x363A4F, surfaceHighest = 0x3B3F54, onBackground = 0xBFC7E8, onVariant = 0x9AA3CC, primary = 0xFFCB6B, primaryContainer = 0xAB8C56, onPrimaryContainer = 0xBFC7E8, secondary = 0x82AAFF, tertiary = 0xF78C6C, outline = 0x676E95, outlineVariant = 0x4C5270, error = 0xF07178, errorContainer = 0x7F4853),
    ThemeMode.AYU_MIRAGE to Palette(dark = true, background = 0x171B24, surface = 0x2A3142, surfaceHigh = 0x2F3646, surfaceHighest = 0x353B4A, onBackground = 0xCCCAC2, onVariant = 0x9C9A93, primary = 0xFFD173, primaryContainer = 0xA78C55, onPrimaryContainer = 0xCCCAC2, secondary = 0x73D0FF, tertiary = 0xFFAD66, outline = 0x707A8C, outlineVariant = 0x4D5667, error = 0xF28779, errorContainer = 0x7A4C4A),
    ThemeMode.VINO to Palette(dark = true, background = 0x1F0F13, surface = 0x3A1D25, surfaceHigh = 0x3F232A, surfaceHighest = 0x462931, onBackground = 0xF1DADA, onVariant = 0xC9A5A8, primary = 0x9C8E92, primaryContainer = 0x6C5E62, onPrimaryContainer = 0xF1DADA, secondary = 0x7A9CE0, tertiary = 0xF29A5E, outline = 0x8A6469, outlineVariant = 0x624047, error = 0xE8665F, errorContainer = 0x793635),
    ThemeMode.METEOBLUE to Palette(dark = true, background = 0x11264D, surface = 0x1B3F78, surfaceHigh = 0x22457C, surfaceHighest = 0x2A4B81, onBackground = 0xFFFFFF, onVariant = 0xBCD0F0, primary = 0x8D9FBC, primaryContainer = 0x5E7192, onPrimaryContainer = 0xFFFFFF, secondary = 0x4DA3FF, tertiary = 0xFFA94D, outline = 0x7F9BCB, outlineVariant = 0x4D6DA2, error = 0xFF6B8A, errorContainer = 0x7C4568),
    ThemeMode.TURCHESE to Palette(dark = true, background = 0x00535B, surface = 0x006F79, surfaceHigh = 0x08737D, surfaceHighest = 0x117882, onBackground = 0xFFFFFF, onVariant = 0xCFF3F6, primary = 0x80B7BC, primaryContainer = 0x4F9197, onPrimaryContainer = 0xFFFFFF, secondary = 0x38C6D9, tertiary = 0xFFB74D, outline = 0x6FB5BC, outlineVariant = 0x38929A, error = 0xFF6F91, errorContainer = 0x736073),
    ThemeMode.VIOLA to Palette(dark = true, background = 0x3A157F, surface = 0x5527B8, surfaceHigh = 0x5A2DBA, surfaceHighest = 0x6035BD, onBackground = 0xFFFFFF, onVariant = 0xDCCCFF, primary = 0xAA93DC, primaryContainer = 0x7F63B9, onPrimaryContainer = 0xFFFFFF, secondary = 0x5CC8FF, tertiary = 0xFF9F5A, outline = 0x9C86E0, outlineVariant = 0x7856CC, error = 0xFF5FA2, errorContainer = 0x93368F),
    ThemeMode.CORALLO to Palette(dark = true, background = 0x85201B, surface = 0xA82A24, surfaceHigh = 0xAB302A, surfaceHighest = 0xAE3731, onBackground = 0xFFF1EF, onVariant = 0xFFCFC9, primary = 0xD49492, primaryContainer = 0xB66865, onPrimaryContainer = 0xFFF1EF, secondary = 0x7FB6FF, tertiary = 0xFFC857, outline = 0xE08E86, outlineVariant = 0xC45C55, error = 0xFFB3A7, errorContainer = 0xBC625A),
    ThemeMode.LIME to Palette(dark = true, background = 0x0B3D16, surface = 0x14602A, surfaceHigh = 0x1B6530, surfaceHighest = 0x226A36, onBackground = 0xF0FFE8, onVariant = 0xBDEBB0, primary = 0x8AB094, primaryContainer = 0x5A8464, onPrimaryContainer = 0xF0FFE8, secondary = 0x4DD0E1, tertiary = 0xFFB74D, outline = 0x6DB760, outlineVariant = 0x408C45, error = 0xFF8A80, errorContainer = 0x796046),
    ThemeMode.ARDESIA to Palette(dark = false, background = 0xDDE3EA, surface = 0xCBD3DD, surfaceHigh = 0xC6CED8, surfaceHighest = 0xC0C8D2, onBackground = 0x1F2937, onVariant = 0x475569, primary = 0x98A3B2, primaryContainer = 0xE2E5E9, onPrimaryContainer = 0x1F2937, secondary = 0x2563EB, tertiary = 0xEA580C, outline = 0x8793A3, outlineVariant = 0xA9B3C0, error = 0xDC2626, errorContainer = 0xF9D8D8),
    ThemeMode.SALVIA to Palette(dark = false, background = 0xDCE4D6, surface = 0xC9D6C1, surfaceHigh = 0xC4D1BC, surfaceHighest = 0xBECBB6, onBackground = 0x22301F, onVariant = 0x425A3C, primary = 0x9AA993, primaryContainer = 0xE3E7E1, onPrimaryContainer = 0x22301F, secondary = 0x2F6F8F, tertiary = 0xC2571A, outline = 0x8A9A84, outlineVariant = 0xAAB8A2, error = 0xB23A48, errorContainer = 0xF1DCDE),
    ThemeMode.DUNA to Palette(dark = false, background = 0xE8DDC8, surface = 0xD9CBB0, surfaceHigh = 0xD4C6AC, surfaceHighest = 0xCFC1A7, onBackground = 0x3A2F1E, onVariant = 0x5E4F37, primary = 0xAA9B80, primaryContainer = 0xE7E3DB, onPrimaryContainer = 0x3A2F1E, secondary = 0x2F6B8A, tertiary = 0xC0561A, outline = 0x9A8B70, outlineVariant = 0xBAAB90, error = 0xB03A2E, errorContainer = 0xF1DCD9),
    ThemeMode.ILMETEO to Palette(dark = false, background = 0xFFD79A, surface = 0xFFC066, surfaceHigh = 0xF9BB63, surfaceHighest = 0xF1B55F, onBackground = 0x2B1500, onVariant = 0x5C3A10, primary = 0xCA9451, primaryContainer = 0xF0E1CE, onPrimaryContainer = 0x2B1500, secondary = 0x1E6FB8, tertiary = 0xE65100, outline = 0xB8864A, outlineVariant = 0xDCA358, error = 0xD32F2F, errorContainer = 0xF7DADA),
    ThemeMode.AZZURRO to Palette(dark = false, background = 0xCDEBFF, surface = 0x9ED4FF, surfaceHigh = 0x99CFF9, surfaceHighest = 0x94C8F2, onBackground = 0x06203D, onVariant = 0x1F4A75, primary = 0x78A9D6, primaryContainer = 0xD9E7F4, onPrimaryContainer = 0x06203D, secondary = 0x0A6CE0, tertiary = 0xE85D04, outline = 0x6C9BC8, outlineVariant = 0x85B8E4, error = 0xD6293E, errorContainer = 0xF8D8DC),
    ThemeMode.GIALLO to Palette(dark = false, background = 0xFFF0A8, surface = 0xFFE066, surfaceHigh = 0xF9DA63, surfaceHighest = 0xF1D45F, onBackground = 0x2A2000, onVariant = 0x5C4A00, primary = 0xC6AC3C, primaryContainer = 0xEFE8C8, onPrimaryContainer = 0x2A2000, secondary = 0x1E70C8, tertiary = 0xE65100, outline = 0xB39B2E, outlineVariant = 0xD9BE4A, error = 0xD32F2F, errorContainer = 0xF7DADA),
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
