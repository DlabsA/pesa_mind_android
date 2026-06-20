package cc.dlabs.pesamind.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PesaMindTypography = Typography(
    // Display styles (Plus Jakarta Sans)
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 48.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 52.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 36.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 42.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 34.sp,
    ),
    // Headline styles
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 27.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
    ),
    // Body styles
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
    ),
    // Label styles
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 14.sp,
    ),
)

// Currency amount styles (monospace)
val AmountXLarge = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 42.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 48.sp,
)

val AmountMedium = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 20.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 26.sp,
)

val AmountSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 18.sp,
)

