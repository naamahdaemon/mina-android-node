package org.minanode.android

import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.doOnAttach

/** Native controller is read only after attachment, avoiding the earlier WebView startup crash. */
fun applyFullscreen(view: View, enabled: Boolean) {
    view.windowInsetsController?.let { controller ->
        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) controller.hide(WindowInsets.Type.systemBars())
        else controller.show(WindowInsets.Type.systemBars())
    }
}

@Composable
fun FullscreenDialogWindow() {
    val view = LocalView.current
    val fullscreen = LocalFullscreen.current
    DisposableEffect(view, fullscreen) {
        view.doOnAttach {
            val decor = (it.parent as? DialogWindowProvider)?.window?.decorView ?: it
            decor.doOnAttach { attached -> applyFullscreen(attached, fullscreen) }
        }
        onDispose { }
    }
}
