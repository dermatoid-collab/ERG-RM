package com.ergrm.trainer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// TrainerDay-style palette: dark background, high-contrast power readout,
// green/gray/red to signal below/at/above target power.
val ErgBackground = Color(0xFF0F1216)
val ErgSurface = Color(0xFF1B1F26)
val ErgSurface2 = Color(0xFF20252D)
val ErgOnSurface = Color(0xFFE8EAED)
val ErgAccent = Color(0xFF3DDC84)
val ErgBelowTarget = Color(0xFF5AA9E6)
val ErgAtTarget = Color(0xFF3DDC84)
val ErgAboveTarget = Color(0xFFE65A5A)
val ErgWarn = Color(0xFFE6C15A)
val ErgDivider = Color(0xFF7FC2F0)

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
