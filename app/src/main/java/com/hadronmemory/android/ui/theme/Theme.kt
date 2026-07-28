package com.hadronmemory.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The Hadron accent, shared with hadron-ios' AccentColor asset: #219EBC in
 * light, #8ECAE6 in dark. Deliberately not Material You dynamic colour — the
 * brand should read the same on every device and next to the iOS app.
 */
private val HadronBlue = Color(0xFF219EBC)
private val HadronBlueLight = Color(0xFF8ECAE6)

private val LightColors = lightColorScheme(
    primary = HadronBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8E7F6),
    onPrimaryContainer = Color(0xFF002E3A),
    secondary = Color(0xFF4C626B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE6F0),
    onSecondaryContainer = Color(0xFF071E26),
    tertiary = Color(0xFF5B5B7E),
    onTertiary = Color.White,
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDBE4E8),
    onSurfaceVariant = Color(0xFF3F484C),
    // Menus, dialogs and sheets sit on the surface-container roles. Left at
    // their Material baseline they come out lavender-tinted, which reads as a
    // second, unrelated brand — keep the whole neutral ramp blue-grey.
    surfaceDim = Color(0xFFD8DBDD),
    surfaceBright = Color(0xFFFBFCFE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F5F7),
    surfaceContainer = Color(0xFFEDF0F2),
    surfaceContainerHigh = Color(0xFFE7EAEC),
    surfaceContainerHighest = Color(0xFFE1E4E6),
    inverseSurface = Color(0xFF2E3132),
    inverseOnSurface = Color(0xFFEFF1F3),
    inversePrimary = Color(0xFF8ECAE6),
    outline = Color(0xFF6F797D),
    outlineVariant = Color(0xFFBFC8CC),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = HadronBlueLight,
    onPrimary = Color(0xFF003544),
    primaryContainer = Color(0xFF004E61),
    onPrimaryContainer = Color(0xFFB8E7F6),
    secondary = Color(0xFFB3CAD5),
    onSecondary = Color(0xFF1E333C),
    secondaryContainer = Color(0xFF354A53),
    onSecondaryContainer = Color(0xFFCFE6F0),
    tertiary = Color(0xFFC3C3EB),
    onTertiary = Color(0xFF2C2C4E),
    background = Color(0xFF191C1E),
    onBackground = Color(0xFFE1E3E5),
    surface = Color(0xFF191C1E),
    onSurface = Color(0xFFE1E3E5),
    surfaceVariant = Color(0xFF3F484C),
    onSurfaceVariant = Color(0xFFBFC8CC),
    surfaceDim = Color(0xFF191C1E),
    surfaceBright = Color(0xFF35383A),
    surfaceContainerLowest = Color(0xFF0F1113),
    surfaceContainerLow = Color(0xFF1D2022),
    surfaceContainer = Color(0xFF212426),
    surfaceContainerHigh = Color(0xFF2C2E31),
    surfaceContainerHighest = Color(0xFF37393C),
    inverseSurface = Color(0xFFE1E3E5),
    inverseOnSurface = Color(0xFF2E3132),
    inversePrimary = Color(0xFF00677F),
    outline = Color(0xFF899297),
    outlineVariant = Color(0xFF3F484C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun HadronTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
