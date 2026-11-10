package com.transitopia.bartwidget.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// Garden T: forest-green train, pale garden sky, and a warm amber sun.
private val GardenLight = lightColorScheme(
    primary = Color(0xFF195B45), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7F5D6), onPrimaryContainer = Color(0xFF123D2E),
    secondary = Color(0xFF52664A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEACC), onSecondaryContainer = Color(0xFF22371C),
    tertiary = Color(0xFF785000), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF4B34D), onTertiaryContainer = Color(0xFF352200),
    background = Color(0xFFF5F8EF), onBackground = Color(0xFF17291F),
    surface = Color(0xFFF5F8EF), onSurface = Color(0xFF17291F),
    surfaceVariant = Color(0xFFE2EADB), onSurfaceVariant = Color(0xFF455347),
    surfaceContainer = Color(0xFFEBF1E3), surfaceContainerHigh = Color(0xFFE5EDDC),
    surfaceContainerHighest = Color(0xFFDFE8D7), surfaceContainerLow = Color(0xFFF0F5E9),
    surfaceContainerLowest = Color.White,
    outline = Color(0xFF68776A), outlineVariant = Color(0xFFBFCBBC),
    inverseSurface = Color(0xFF24362B), inverseOnSurface = Color(0xFFEDF5E7),
    inversePrimary = Color(0xFFA6D9B8), surfaceTint = Color(0xFF195B45)
)

private val GardenDark = darkColorScheme(
    primary = Color(0xFFA6D9B8), onPrimary = Color(0xFF103B2B),
    primaryContainer = Color(0xFF195B45), onPrimaryContainer = Color(0xFFE7F5D6),
    secondary = Color(0xFFBED2AD), onSecondary = Color(0xFF2A3C24),
    secondaryContainer = Color(0xFF3B5132), onSecondaryContainer = Color(0xFFDBEACC),
    tertiary = Color(0xFFF4B34D), onTertiary = Color(0xFF432C00),
    tertiaryContainer = Color(0xFF604100), onTertiaryContainer = Color(0xFFFFDEAB),
    background = Color(0xFF101C16), onBackground = Color(0xFFE3EDE0),
    surface = Color(0xFF101C16), onSurface = Color(0xFFE3EDE0),
    surfaceVariant = Color(0xFF344439), onSurfaceVariant = Color(0xFFC0CEBD),
    surfaceContainer = Color(0xFF1C2B21), surfaceContainerHigh = Color(0xFF26362B),
    surfaceContainerHighest = Color(0xFF304135), surfaceContainerLow = Color(0xFF17251C),
    surfaceContainerLowest = Color(0xFF0B160F),
    outline = Color(0xFF8C9C8A), outlineVariant = Color(0xFF435644),
    inverseSurface = Color(0xFFE3EDE0), inverseOnSurface = Color(0xFF24362B),
    inversePrimary = Color(0xFF195B45), surfaceTint = Color(0xFFA6D9B8)
)

@Composable
fun BartTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) GardenDark else GardenLight) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
