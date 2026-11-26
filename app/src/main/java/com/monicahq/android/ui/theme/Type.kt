package com.monicahq.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// The design system uses the system sans (Roboto on Android): no bundled font.
// Sizes are in sp so they follow the person's font size setting.
private val Default = Typography()

internal val MonicaTypography =
    Default.copy(
        headlineMedium =
            Default.headlineMedium.copy(
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.03).em,
            ),
        bodyLarge =
            Default.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        bodySmall =
            Default.bodySmall.copy(
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        labelLarge =
            Default.labelLarge.copy(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
    )

/** The "Monica" wordmark next to the logo. */
internal val WordmarkStyle =
    TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.03).em,
    )
