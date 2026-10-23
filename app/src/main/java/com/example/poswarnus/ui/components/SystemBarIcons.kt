package com.example.poswarnus.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Sets the status/navigation bar icon colours. Driven from one place (the nav graph, by route) so
 * overlapping screens during a crossfade cannot fight over the value; nothing is restored.
 */
@Composable
fun SystemBarIcons(lightStatusBarIcons: Boolean, lightNavigationBarIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        // isAppearanceLight* = true means DARK icons.
        controller.isAppearanceLightStatusBars = !lightStatusBarIcons
        controller.isAppearanceLightNavigationBars = !lightNavigationBarIcons
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
