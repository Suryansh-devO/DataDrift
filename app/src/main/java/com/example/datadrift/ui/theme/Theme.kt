package com.example.datadrift.ui.theme

// ============================================================
// DataDrift - Yellow + Black Theme
// ============================================================

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ============================================================
// DARK THEME
// ============================================================

private val DarkColorScheme =
    darkColorScheme(

        primary =
            DataDriftYellow,

        secondary =
            DataDriftYellowLight,

        tertiary =
            DataDriftYellowDark,

        background =
            DataDriftBlack,

        surface =
            DataDriftBlackCard,

        surfaceVariant =
            DataDriftDarkGrey,

        onPrimary =
            DataDriftBlack,

        onSecondary =
            DataDriftBlack,

        onTertiary =
            DataDriftBlack,

        onBackground =
            DataDriftWhite,

        onSurface =
            DataDriftWhite,

        onSurfaceVariant =
            DataDriftWhiteSoft
    )


// ============================================================
// LIGHT THEME
// ============================================================

private val LightColorScheme =
    lightColorScheme(

        primary =
            DataDriftYellowDark,

        secondary =
            DataDriftYellow,

        tertiary =
            DataDriftYellowLight,

        background =
            DataDriftWhite,

        surface =
            DataDriftWhiteSoft,

        surfaceVariant =
            DataDriftGrey,

        onPrimary =
            DataDriftBlack,

        onSecondary =
            DataDriftBlack,

        onTertiary =
            DataDriftBlack,

        onBackground =
            DataDriftBlack,

        onSurface =
            DataDriftBlack,

        onSurfaceVariant =
            DataDriftBlackSoft
    )


// ============================================================
// DATADRIFT THEME
// ============================================================

@Composable
fun DataDriftTheme(

    darkTheme: Boolean =
        isSystemInDarkTheme(),

    content: @Composable () -> Unit

) {

    val colorScheme =
        if (darkTheme) {

            DarkColorScheme

        } else {

            LightColorScheme
        }

    MaterialTheme(

        colorScheme =
            colorScheme,

        typography =
            Typography,

        content =
            content
    )
}