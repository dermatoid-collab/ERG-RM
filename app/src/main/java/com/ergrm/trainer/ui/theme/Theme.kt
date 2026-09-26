package com.ergrm.trainer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// TrainerDay-style palette: dark background, high-contrast power readout,
// green/gray/red to signal below/at/above target power.
val ErgBackground = Color(0xFF070D13)
val ErgSurface = Color(0xFF070D13)
val ErgSurface2 = Color(0xFF0D1822)
val ErgOnSurface = Color(0xFFE8EAED)
val ErgAccent = Color(0xFF3DDC84)
val ErgBelowTarget = Color(0xFF5AA9E6)
val ErgAtTarget = Color(0xFF3DDC84)
val ErgAboveTarget = Color(0xFFE65A5A)
val ErgWarn = Color(0xFFE6C15A)
val ErgDivider = Color(0xFF7FC2F0)
val ErgProgressLine = Color(0xFF4E8FE8)
val ErgCadenceLine = Color(0xFF3A6BB5)
val ErgHrPlus = Color(0xFFE6598A)
// Dedicated, more vivid colors reused across the live chart and history detail: kept separate
// from ErgAboveTarget/ErgAccent (which mean "above target power" and "at target"/theme-primary
// respectively) so brightening the HR trace or the ERG mode tag doesn't shift those other meanings.
val ErgHrLine = Color(0xFFFC2121)
val ErgModeErg = Color(0xFF2ECC71)
// The CORE sensor's skin-temperature pill value, chosen distinct from ErgHrLine/ErgWarn/
// ZONE_MAX.color — the other three colors in the same pill row — so all three stay tellable apart.
val ErgSkinTemp = Color(0xFFFFB5D5)
// Tile/pill icon tints from the UI redesign spec sheet: blue for time/target-ish metrics
// (also reused for the "Now" row accent), green for cadence, pink for HR/CORE, amber for
// watts/HSI — distinct from the value-text colors above, which still signal zone/deviation.
val ErgIconBlue = Color(0xFF00B3FF)
val ErgIconGreen = Color(0xFF00E676)
val ErgIconPink = Color(0xFFFF2D6D)
val ErgIconAmber = Color(0xFFFFC233)

private val ErgColorScheme = darkColorScheme(
    background = ErgBackground,
    surface = ErgSurface,
    onSurface = ErgOnSurface,
    primary = ErgAccent,
    onPrimary = Color.Black,
    secondary = ErgBelowTarget,
)

@Composable
fun ErgRmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ErgColorScheme,
        content = content,
    )
}
