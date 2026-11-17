package org.minanode.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.webkit.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import kotlinx.coroutines.flow.update
import java.io.ByteArrayInputStream

class DashboardActivity : Activity() {
    private var dashboard: DashboardView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppearancePreferences(this).activityTheme())
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        setContentView(root)
        root.doOnAttach { applyFullscreen(it, AppearancePreferences(this).fullscreen()) }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val fullscreen = AppearancePreferences(this).fullscreen()
            val safe = insets.getInsets(if (fullscreen) WindowInsetsCompat.Type.ime()
                else WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.setPadding(if (fullscreen) maxOf(safe.left, cutout.left) else safe.left, safe.top,
                if (fullscreen) maxOf(safe.right, cutout.right) else safe.right, safe.bottom)
            WindowInsetsCompat.CONSUMED
        }
        dashboard = DashboardView(this) { finish() }.also { root.addView(it) }
    }
    override fun onDestroy() { dashboard?.dispose(); super.onDestroy() }
}

class DashboardView(context: android.content.Context, private val onBack: () -> Unit) : FrameLayout(context) {
    private var web: WebView? = null
    private val container: FrameLayout = this
    private var dark = AppearancePreferences(context).dark()
    private var disposed = false
    private fun local(uri: Uri) = uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port == NodeApi.PORT
    init { openDashboard() }
    fun setAppearance(value: Boolean) {
        if (value == dark) return
        dark = value
        applyWebTheme()
    }
    private fun applyWebTheme() {
        val theme = if (dark) "dark" else "light"
        web?.takeIf { it.url?.let { url -> local(Uri.parse(url)) } == true }?.evaluateJavascript(
            """(function(){try{var t="$theme";localStorage.setItem("minanode-theme",t);var r=document.documentElement;r.classList.toggle("dark",t==="dark");r.classList.toggle("light",t==="light");}catch(e){}})();""", null)
    }
    fun dispose() { disposed = true; releaseWebView() }
    @SuppressLint("SetJavaScriptEnabled") // Only our bundled dashboard at the node's exact local origin.
    private fun openDashboard() {
        releaseWebView()
        container.removeAllViews()
        try {
            val page = WebView(context)
            var themeApplied = false
            web = page
            page.apply {
                setBackgroundColor(if (dark) Color.rgb(8, 13, 19) else Color.WHITE)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportMultipleWindows(false)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (view !== web || view == null || url == null || !local(Uri.parse(url)) || themeApplied) return
                        themeApplied = true
                        // Fixed literals only, on the exact trusted node origin. No JavaScript bridge.
                        applyWebTheme()
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                        if (local(request.url)) return false
                        if (request.isForMainFrame && request.url.scheme in listOf("https", "http")) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        }
                        return true
                    }
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                        if (local(request.url)) return null
                        return WebResourceResponse("text/plain", "UTF-8", 403, "External resource blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }

                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail): Boolean {
                        if (view === web) showFailure(context.getString(R.string.dashboard_renderer_error), null)
                        return true // Acknowledge the renderer loss instead of terminating the app process.
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame && view === web) {
                            showFailure(context.getString(R.string.dashboard_api_error), null)
                        }
                    }
                }
            }
            container.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            page.loadUrl("http://127.0.0.1:${NodeApi.PORT}/")
        } catch (error: Exception) {
            showFailure(context.getString(R.string.dashboard_open_error, error.javaClass.simpleName), error)
        }
    }

    private fun recordFailure(stage: String, error: Throwable?) {
        // Stack frames identify the failure without copying exception messages,
        // which may contain URLs or arbitrary data, into the exported log.
        val diagnostic = "[app] Dashboard $stage · ${error?.javaClass?.simpleName ?: "WebView unavailable"}\n" +
            error?.stackTrace?.take(16)?.joinToString("\n").orEmpty()
        runCatching {
            val files = NodeFiles(context)
            files.append(diagnostic)
            NodeState.flow.update { it.copy(log = files.tail()) }
        }
    }

    private fun showFailure(message: String, error: Exception?) {
        if (disposed) return
        recordFailure("opening/rendering", error)
        releaseWebView()
        container.removeAllViews()
        val padding = (24 * resources.displayMetrics.density).toInt()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val foreground = if (dark) Color.WHITE else Color.rgb(24, 20, 32)
        body.addView(TextView(context).apply { text = context.getString(R.string.dashboard_unavailable); textSize = 22f; setTextColor(foreground) })
        body.addView(TextView(context).apply { text = message; textSize = 16f; setTextColor(foreground) })
        body.addView(Button(context).apply { text = context.getString(R.string.retry); setOnClickListener { openDashboard() } })
        body.addView(Button(context).apply { text = context.getString(R.string.back_to_node); setOnClickListener { onBack() } })
        container.addView(body)
    }

    private fun releaseWebView() {
        val old = web ?: return
        web = null
        runCatching { (old.parent as? ViewGroup)?.removeView(old) }
        runCatching { old.destroy() }
    }

}
