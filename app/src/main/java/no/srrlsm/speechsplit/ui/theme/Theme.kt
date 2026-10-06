package no.srrlsm.speechsplit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

/** Short way to reach the palette from any composable: `AppTheme.colors.textSub`. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

/**
 * A complete Material colour scheme means Buttons, TextFields, Cards, Dialogs and Dividers
 * pick up the right colours by themselves, in both light and dark mode.
 */
@Composable
fun SpeechSplitTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val c = if (darkTheme) DarkAppColors else LightAppColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            secondary = c.accent, onSecondary = c.onAccent,
            secondaryContainer = c.surfaceHigh, onSecondaryContainer = c.textMain,
            background = c.bg, onBackground = c.textMain,
            surface = c.surface, onSurface = c.textMain,
            surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.textSub,
            surfaceContainerLowest = c.bg, surfaceContainerLow = c.surface,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface,
            surfaceContainerHighest = c.surface,
            outline = c.divider, outlineVariant = c.divider,
            error = c.over, onError = c.onOver,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            secondary = c.accent, onSecondary = c.onAccent,
            secondaryContainer = c.surfaceHigh, onSecondaryContainer = c.textMain,
            background = c.bg, onBackground = c.textMain,
            surface = c.surface, onSurface = c.textMain,
            surfaceVariant = c.surfaceHigh, onSurfaceVariant = c.textSub,
            surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface,
            surfaceContainerHighest = c.surface,
            outline = c.divider, outlineVariant = c.divider,
            error = c.over, onError = c.onOver,
        )
    }
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
