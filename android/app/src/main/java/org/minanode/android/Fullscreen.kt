package org.minanode.android

import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.doOnAttach
import androidx.core.view.ViewCompat
import androidx.compose.foundation.layout.WindowInsets as ContentInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union

/** Native controller is read only after attachment, avoiding the earlier WebView startup crash. */
fun applyFullscreen(view: View, enabled: Boolean) {
    view.windowInsetsController?.let { controller ->
        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) controller.hide(WindowInsets.Type.systemBars())
        else controller.show(WindowInsets.Type.systemBars())
    }
    ViewCompat.requestApplyInsets(view)
}

/** In immersive mode, a camera cutout must not reserve the entire former status-bar strip.
 * Keep keyboard clearance and side cutouts (landscape), while transient bars overlay the content.
 */
@Composable
fun appContentInsets(): ContentInsets = if (LocalFullscreen.current)
    ContentInsets.ime.union(ContentInsets.displayCutout.only(WindowInsetsSides.Horizontal))
else ContentInsets.safeDrawing

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
