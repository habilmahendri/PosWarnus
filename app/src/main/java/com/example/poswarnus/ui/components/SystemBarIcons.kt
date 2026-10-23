package com.example.poswarnus.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Sets the status/navigation bar icon appearance while this composable is in the composition and
 * restores whatever was set before when it leaves. Pass null to leave a bar untouched.
 */
@Composable
fun LightSystemBarIcons(lightStatusBar: Boolean? = null, lightNavigationBar: Boolean? = null) {
    val view = LocalView.current
    DisposableEffect(lightStatusBar, lightNavigationBar) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        if (lightStatusBar != null) controller?.isAppearanceLightStatusBars = lightStatusBar
        if (lightNavigationBar != null) controller?.isAppearanceLightNavigationBars = lightNavigationBar
        onDispose {
            if (lightStatusBar != null && previousStatus != null) controller?.isAppearanceLightStatusBars = previousStatus
            if (lightNavigationBar != null && previousNavigation != null) controller?.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
