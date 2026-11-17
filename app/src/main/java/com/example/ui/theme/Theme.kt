package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudyverseLightColors = lightColorScheme(
    primary = GryffindorScarletLight,
    onPrimary = OnGryffindorScarletLight,
    primaryContainer = GryffindorScarletContainerLight,
    onPrimaryContainer = OnGryffindorScarletContainerLight,
    secondary = GryffindorGoldLight,
    onSecondary = OnGryffindorGoldLight,
    secondaryContainer = GryffindorGoldContainerLight,
    onSecondaryContainer = OnGryffindorGoldContainerLight,
    tertiary = GryffindorBurgundyLight,
    onTertiary = OnGryffindorBurgundyLight,
    tertiaryContainer = GryffindorBurgundyContainerLight,
    onTertiaryContainer = OnGryffindorBurgundyContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = DangerCoral
)

private val StudyverseDarkColors = darkColorScheme(
    primary = GryffindorScarletDark,
    onPrimary = OnGryffindorScarletDark,
    primaryContainer = GryffindorScarletContainerDark,
    onPrimaryContainer = OnGryffindorScarletContainerDark,
    secondary = GryffindorGoldDark,
    onSecondary = OnGryffindorGoldDark,
    secondaryContainer = GryffindorGoldContainerDark,
    onSecondaryContainer = OnGryffindorGoldContainerDark,
    tertiary = GryffindorBurgundyDark,
    onTertiary = OnGryffindorBurgundyDark,
    tertiaryContainer = GryffindorBurgundyContainerDark,
    onTertiaryContainer = OnGryffindorBurgundyContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = DangerCoral
)

val StudyverseShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun StudyverseTheme(
    darkModeOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val isDark = darkModeOverride ?: isSystemInDarkTheme()
    val colorScheme = if (isDark) StudyverseDarkColors else StudyverseLightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StudyverseTypography,
        shapes = StudyverseShapes,
        content = content
    )
}
