package cc.dlabs.pesamind.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun PesaMindTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = PesaMindTeal,
            background = BackgroundDark,
            surface = SurfaceDark,
        )
    } else {
        lightColorScheme(
            primary = PesaMindTeal,
            background = BackgroundLight,
            surface = SurfaceLight,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
