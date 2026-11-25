package com.driver.notes

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Light = lightColorScheme(
    primary = Color(0xFFE65100), onPrimary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2), onSecondaryContainer = Color(0xFF3E2000)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFFB74D), onPrimary = Color(0xFF3E2000),
    primaryContainer = Color(0xFF7A3E00), onPrimaryContainer = Color(0xFFFFDDB8),
    secondaryContainer = Color(0xFF5A3A12), onSecondaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF0E0E0F), surface = Color(0xFF0E0E0F),
    surfaceContainerHighest = Color(0xFF26262A), surfaceContainer = Color(0xFF1B1B1E)
)

/** mode: 0 = theo hệ thống, 1 = sáng, 2 = tối */
@Composable
fun AppTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
