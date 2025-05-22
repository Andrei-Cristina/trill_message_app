package org.message.trill.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


val primaryLight = Color(0xFF415F91)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFD6E3FF)
val onPrimaryContainerLight = Color(0xFF284777)
val secondaryLight = Color(0xFF565F71)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFDAE2F9)
val onSecondaryContainerLight = Color(0xFF3E4759)
val tertiaryLight = Color(0xFF705575)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFFAD8FD)
val onTertiaryContainerLight = Color(0xFF573E5C)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF93000A)
val backgroundLight = Color(0xFFF9F9FF)
val onBackgroundLight = Color(0xFF191C20)
val surfaceLight = Color(0xFFF9F9FF)
val onSurfaceLight = Color(0xFF191C20)


val primaryDark = Color(0xFFAAC7FF)
val onPrimaryDark = Color(0xFF0A305F)
val primaryContainerDark = Color(0xFF284777)
val onPrimaryContainerDark = Color(0xFFD6E3FF)
val secondaryDark = Color(0xFFBEC6DC)
val onSecondaryDark = Color(0xFF283141)
val secondaryContainerDark = Color(0xFF3E4759)
val onSecondaryContainerDark = Color(0xFFDAE2F9)
val tertiaryDark = Color(0xFFDDBCE0)
val onTertiaryDark = Color(0xFF3F2844)
val tertiaryContainerDark = Color(0xFF573E5C)
val onTertiaryContainerDark = Color(0xFFFAD8FD)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF111318)
val onBackgroundDark = Color(0xFFE2E2E9)
val surfaceDark = Color(0xFF111318)
val onSurfaceDark = Color(0xFFE2E2E9)


private val AppLightColorPalette = lightColors(
    primary = primaryLight,
    primaryVariant = primaryContainerLight,
    secondary = tertiaryLight,
    secondaryVariant = tertiaryContainerLight,
    background = backgroundLight,
    surface = surfaceLight,
    error = errorLight,
    onPrimary = onPrimaryLight,
    onSecondary = onTertiaryLight,
    onBackground = onBackgroundLight,
    onSurface = onSurfaceLight,
    onError = onErrorLight
)

private val AppDarkColorPalette = darkColors(
    primary = primaryDark,
    primaryVariant = primaryContainerDark,
    secondary = tertiaryDark,
    secondaryVariant = tertiaryContainerDark,
    background = backgroundDark,
    surface = surfaceDark,
    error = errorDark,
    onPrimary = onPrimaryDark,
    onSecondary = onTertiaryDark,
    onBackground = onBackgroundDark,
    onSurface = onSurfaceDark,
    onError = onErrorDark
)

val AppTypography = Typography()

@Composable
fun TrillAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        AppDarkColorPalette
    } else {
        AppLightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = AppTypography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}