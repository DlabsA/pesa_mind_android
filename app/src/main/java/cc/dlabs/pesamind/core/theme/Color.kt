package cc.dlabs.pesamind.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ============================================================================
// BRAND PRIMITIVES
// ============================================================================
object BrandColors {
    val Forest900 = Color(0xFF003934)  // Primary dark
    val Forest800 = Color(0xFF004D45)  // Hover
    val Forest700 = Color(0xFF006B5E)  // Focus rings
    val Forest100 = Color(0xFFE6F0EF)  // Pale tint surface

    val Lime500 = Color(0xFF9FE870)    // Accent
    val Lime400 = Color(0xFFB8F093)    // Accent hover
    val Lime200 = Color(0xFFDDF7C9)    // Pale lime tint

    val CanvasWarm = Color(0xFFF8F9F0) // Light background
    val CanvasDark = Color(0xFF0D1410) // Dark background
}

// ============================================================================
// LIGHT MODE SEMANTIC TOKENS
// ============================================================================
object LightColors {
    // Surfaces
    val Background = Color(0xFFF8F9F0)
    val Surface = Color(0xFFF8F9F0)
    val SurfaceRaised = Color(0xFFF0F4F2)

    // Borders
    val Border = Color(0xFFD4DBD9)
    val BorderStrong = Color(0xFFA3B0AE)

    // Primary (Forest Green in light mode)
    val Primary = Color(0xFF003934)
    val PrimaryHover = Color(0xFF004D45)
    val PrimaryForeground = Color(0xFFF8F9F0)

    // Accent (Lime in light mode)
    val Accent = Color(0xFF9FE870)
    val AccentHover = Color(0xFFB8F093)
    val AccentForeground = Color(0xFF003934)

    // Text
    val TextPrimary = Color(0xFF0D1410)
    val TextSecondary = Color(0xFF3D524F)
    val TextMuted = Color(0xFF6B8280)

    // Financial Semantics
    val Income = Color(0xFF1D9E75)
    val IncomeBg = Color(0xFFE1F5EE)
    val IncomeFg = Color(0xFF0E4A25)

    val Expense = Color(0xFFD85A30)
    val ExpenseBg = Color(0xFFFDECEA)
    val ExpenseFg = Color(0xFF7B1A14)

    val Savings = Color(0xFF378ADD)
    val SavingsBg = Color(0xFFEAEEEE)
    val SavingsFg = Color(0xFF2E3D3C)

    val Warning = Color(0xFFD97706)
    val WarningBg = Color(0xFFFEF3E2)
}

// ============================================================================
// DARK MODE SEMANTIC TOKENS
// ============================================================================
object DarkColors {
    // Surfaces
    val Background = Color(0xFF0D1117)
    val Surface = Color(0xFF161B22)
    val SurfaceRaised = Color(0xFF21262D)

    // Borders
    val Border = Color(0xFF223130)            // Subtle edge definition for Surface cards
    val BorderStrong = Color(0xFF344B49)      // Pronounced definition for inputs / active outlines

    // Primary (Lime in dark mode — INTENTIONAL FLIP!)
    val Primary = Color(0xFF9FE870)           // Actionable elements / FABs / Toggles
    val PrimaryHover = Color(0xFFB8F093)      // Hover / Pressed state feedback
    val PrimaryForeground = Color(0xFF003934) // High-contrast text ON top of Primary color

    // Accent (Forest in dark mode — complement)
    val Accent = Color(0xFF003934)
    val AccentHover = Color(0xFF004D45)
    val AccentForeground = Color(0xFF9FE870)

    // Text
    val TextPrimary = Color(0xFFE8F0EE)
    val TextSecondary = Color(0xFF9BB8B2)
    val TextMuted = Color(0xFF5A7D77)

    // Financial Semantics (adjusted for dark mode visibility)
    val Income = Color(0xFF1D9E75)
    val IncomeBg = Color(0xFFE1F5EE)
    val IncomeFg = Color(0xFF86EFAC)

    val Expense = Color(0xFFD85A30)
    val ExpenseBg = Color(0xFF2C0F0E)
    val ExpenseFg = Color(0xFFFCA5A5)

    val Savings = Color(0XFF378ADD)
    val SavingsBg = Color(0xFF1A2523)
    val SavingsFg = Color(0xFFB8CAC7)

    val Warning = Color(0xFFFBBF24)
    val WarningBg = Color(0xFF2C1F08)
}

// ============================================================================
// SPACING TOKENS (in dp)
// ============================================================================
object Spacing {
    const val Space1 = 4
    const val Space2 = 8
    const val Space3 = 12
    const val Space4 = 16
    const val Space5 = 20
    const val Space6 = 24
    const val Space8 = 32
    const val Space10 = 40
    const val Space12 = 48
}

// ============================================================================
// RADIUS TOKENS (in dp)
// ============================================================================
object Radius {
    const val Small = 6
    const val Medium = 10
    const val Large = 16
    const val ExtraLarge = 20
    const val XXLarge = 28
    const val Full = 9999
}

// ============================================================================
// DYNAMIC COLOR HELPERS (Theme-aware)
// ============================================================================

/**
 * Get the primary color dynamically based on current theme (light/dark mode)
 * Use this in @Composable functions instead of hardcoded PesaMindTeal
 */
@Composable
fun getPrimaryColor(): Color = MaterialTheme.colorScheme.primary


/**
 * Get the tertiary (income) color dynamically based on current theme
 */
@Composable
fun getTertiaryColor(): Color = MaterialTheme.colorScheme.tertiary

/**
 * Get the error (expense) color dynamically based on current theme
 */
@Composable
fun getErrorColor(): Color = MaterialTheme.colorScheme.error

/**
 * Get the surface color dynamically based on current theme
 */
@Composable
fun getSurfaceColor(): Color = MaterialTheme.colorScheme.surface

/**
 * Get the background color dynamically based on current theme
 */
@Composable
fun getBackgroundColor(): Color = MaterialTheme.colorScheme.background

// ============================================================================
// DEPRECATED LEGACY COLOR EXPORTS (DO NOT USE IN NEW CODE)
// ============================================================================
// These are kept only for backward compatibility
// Use the @Composable functions above or MaterialTheme.colorScheme directly

@Deprecated(
    "Use getPrimaryColor() @Composable function instead for theme-aware color",
    level = DeprecationLevel.WARNING
)
val PesaMindTeal = LightColors.Primary

@Deprecated(
    "Use getTertiaryColor() @Composable function instead",
    level = DeprecationLevel.WARNING
)
val PesaMindGreen = LightColors.Income

@Deprecated(
    "Use getPrimaryColor() @Composable function instead",
    level = DeprecationLevel.WARNING
)
val PesaMindNavy = LightColors.Primary

@Deprecated(
    "Use getTertiaryColor() @Composable function instead",
    level = DeprecationLevel.WARNING
)
val IncomeGreen = LightColors.Income

@Deprecated(
    "Use getTertiaryColor() @Composable function instead",
    level = DeprecationLevel.WARNING
)
val PesaMindGreen_Income = LightColors.Income

@Deprecated(
    "Use getErrorColor() @Composable function instead",
    level = DeprecationLevel.WARNING
)
val ExpenseRed = LightColors.Expense

@Deprecated(
    "Use getBackgroundColor() @Composable function instead",
    level = DeprecationLevel.WARNING
)
val BackgroundLight = LightColors.Background

@Deprecated(
    "Use MaterialTheme.colorScheme.onSurfaceVariant instead",
    level = DeprecationLevel.WARNING
)
val TextSecondary = LightColors.TextSecondary
val ChipActive     = Color(0xFF378ADD)
val NetPos  = Color(0xFF639922)
val NetNeg  = Color(0xFFE24B4A)
val IncomeText = Color(0xFF085041)