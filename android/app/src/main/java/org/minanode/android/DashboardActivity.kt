package org.minanode.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayInputStream

class DashboardActivity : Activity() {
    private var web: WebView? = null
    private fun local(uri: Uri) = uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port == NodeApi.PORT

    @SuppressLint("SetJavaScriptEnabled") // Only our bundled dashboard at the node's exact local origin.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val container = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8, 13, 19)) }
        // Resize the WebView's actual viewport. Padding inside the WebView would
        // leave the dashboard's fixed header underneath Android's status bar.
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            WindowInsetsCompat.CONSUMED
        }
        WindowCompat.getInsetsController(window, container).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    if (local(request.url)) return false
                    if (request.isForMainFrame && request.url.scheme in listOf("https", "http")) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    }
                    return true
                }
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                    if (local(request.url)) return null
                    return WebResourceResponse("text/plain", "UTF-8", 403, "External resource blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
            }
            loadUrl("http://127.0.0.1:${NodeApi.PORT}/")
        }
        container.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(container)
        ViewCompat.requestApplyInsets(container)
    }

    override fun onDestroy() { web?.destroy(); web = null; super.onDestroy() }
}
