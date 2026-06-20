package cc.dlabs.pesamind.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun PesaMindTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            // Primary (Lime in dark mode)
            primary = DarkColors.Primary,
            onPrimary = DarkColors.PrimaryForeground,
            primaryContainer = DarkColors.PrimaryHover,
            onPrimaryContainer = DarkColors.PrimaryForeground,

            // Secondary (Forest in dark mode)
            secondary = DarkColors.Accent,
            onSecondary = DarkColors.AccentForeground,
            secondaryContainer = DarkColors.AccentHover,
            onSecondaryContainer = DarkColors.AccentForeground,

            // Tertiary (Income)
            tertiary = DarkColors.Income,
            onTertiary = Color.White,
            tertiaryContainer = DarkColors.IncomeBg,
            onTertiaryContainer = DarkColors.IncomeFg,


            // Background & Surface
            background = DarkColors.Background,
            onBackground = DarkColors.TextPrimary,

            surface = DarkColors.Surface,
            onSurface = DarkColors.TextPrimary,
            surfaceVariant = DarkColors.SurfaceRaised,
            onSurfaceVariant = DarkColors.TextSecondary,

            // Outline
            outline = DarkColors.Border,
            outlineVariant = DarkColors.BorderStrong,

            // Error (Expense)
            error = DarkColors.Expense,
            onError = Color.White,
            errorContainer = DarkColors.ExpenseBg,
            onErrorContainer = DarkColors.ExpenseFg,
        )
    } else {
        lightColorScheme(
            // Primary (Forest in light mode)
            primary = LightColors.Primary,
            onPrimary = LightColors.PrimaryForeground,
            primaryContainer = LightColors.PrimaryHover,
            onPrimaryContainer = LightColors.PrimaryForeground,

            // Secondary (Lime in light mode)
            secondary = LightColors.Accent,
            onSecondary = LightColors.AccentForeground,
            secondaryContainer = LightColors.AccentHover,
            onSecondaryContainer = LightColors.AccentForeground,

            // Tertiary (Income)
            tertiary = LightColors.Income,
            onTertiary = Color.White,
            tertiaryContainer = LightColors.IncomeBg,
            onTertiaryContainer = LightColors.IncomeFg,

            // Background & Surface
            background = LightColors.Background,
            onBackground = LightColors.TextPrimary,

            surface = LightColors.Surface,
            onSurface = LightColors.TextPrimary,
            surfaceVariant = LightColors.SurfaceRaised,
            onSurfaceVariant = LightColors.TextSecondary,

            // Outline
            outline = LightColors.Border,
            outlineVariant = LightColors.BorderStrong,

            // Error (Expense)
            error = LightColors.Expense,
            onError = Color.White,
            errorContainer = LightColors.ExpenseBg,
            onErrorContainer = LightColors.ExpenseFg,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
