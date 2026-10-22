package com.example.poswarnus.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Design reference does not specify a dark variant; Compose's default dark scheme is a
// reasonable fallback until one is designed.
private val DarkColorScheme = darkColorScheme()

// Maps POS WarNus brand tokens onto Material3 color-scheme slots (docs/design-reference.md §1).
// Notable slot choices: accentTint -> primaryContainer (selected-item backgrounds), with
// onPrimaryContainer set to the darker accent (not a light-on-dark text color) since the
// tint is used as a light background needing dark emphasis text, per the reference's
// "Accent tint" / "Accent dark" roles. error's onErrorContainer/errorContainer intentionally
// reuse the same red (no separate "on error container" token in the reference).
private val LightColorScheme = lightColorScheme(
    primary = AccentOrange,
    onPrimary = SurfaceWhite,
    primaryContainer = AccentTint,
    onPrimaryContainer = AccentOrangeDark,
    background = BackgroundCream,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundCream,
    onSurfaceVariant = TextSecondary,
    outline = LineStrong,
    outlineVariant = LineDefault,
    error = ErrorRed,
    onErrorContainer = ErrorRed,
    errorContainer = ErrorTint
)

// Flat design: 6dp radius for controls (buttons/inputs/chips), 10dp for cards/dialogs/panels.
// See docs/design-reference.md §1 "Radius Scale".
val PosShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

@Composable
fun POSWarNusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is disabled: the brand color scheme must never be overridden by
    // Android 12+ wallpaper-based dynamic color.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PosShapes,
        content = content
    )
}
