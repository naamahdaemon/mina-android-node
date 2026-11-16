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
    private var web: WebView? = null
    private lateinit var container: FrameLayout
    private fun local(uri: Uri) = uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port == NodeApi.PORT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8, 13, 19)) }
        setContentView(container)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Resize the WebView's actual viewport. Padding inside the WebView would
        // leave the dashboard's fixed header underneath Android's status bar.
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            if (view.paddingLeft != safe.left || view.paddingTop != safe.top ||
                view.paddingRight != safe.right || view.paddingBottom != safe.bottom) {
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            }
            WindowInsetsCompat.CONSUMED
        }
        // A detached view has no WindowInsetsController. In particular, do not
        // construct the AndroidX controller and dereference it during onCreate.
        container.doOnAttach { view ->
            runCatching {
                view.windowInsetsController?.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
                ViewCompat.requestApplyInsets(view)
            }.onFailure { recordFailure("system bars", it) }
        }
        openDashboard()
    }

    @SuppressLint("SetJavaScriptEnabled") // Only our bundled dashboard at the node's exact local origin.
    private fun openDashboard() {
        releaseWebView()
        container.removeAllViews()
        try {
            val page = WebView(this)
            web = page
            page.apply {
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

                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail): Boolean {
                        if (view === web) showFailure("Le moteur WebView s’est arrêté. Tu peux réessayer ou revenir au nœud.", null)
                        return true // Acknowledge the renderer loss instead of terminating the app process.
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame && view === web) {
                            showFailure("L’API locale n’est pas encore disponible. Attendre le démarrage du nœud puis réessayer.", null)
                        }
                    }
                }
            }
            container.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            page.loadUrl("http://127.0.0.1:${NodeApi.PORT}/")
        } catch (error: Exception) {
            showFailure("Impossible d’ouvrir le dashboard (${error.javaClass.simpleName}). Le diagnostic est dans l’onglet Journal.", error)
        }
    }

    private fun recordFailure(stage: String, error: Throwable?) {
        // Stack frames identify the failure without copying exception messages,
        // which may contain URLs or arbitrary data, into the exported log.
        val diagnostic = "[app] Dashboard $stage · ${error?.javaClass?.simpleName ?: "WebView indisponible"}\n" +
            error?.stackTrace?.take(16)?.joinToString("\n").orEmpty()
        runCatching {
            val files = NodeFiles(this)
            files.append(diagnostic)
            NodeState.flow.update { it.copy(log = files.tail()) }
        }
    }

    private fun showFailure(message: String, error: Exception?) {
        if (isFinishing || isDestroyed) return
        recordFailure("opening/rendering", error)
        releaseWebView()
        container.removeAllViews()
        val padding = (24 * resources.displayMetrics.density).toInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        body.addView(TextView(this).apply { text = "Dashboard indisponible"; textSize = 22f; setTextColor(Color.WHITE) })
        body.addView(TextView(this).apply { text = message; textSize = 16f; setTextColor(Color.WHITE) })
        body.addView(Button(this).apply { text = "Réessayer"; setOnClickListener { openDashboard() } })
        body.addView(Button(this).apply { text = "Retour au nœud"; setOnClickListener { finish() } })
        container.addView(body)
    }

    private fun releaseWebView() {
        val old = web ?: return
        web = null
        runCatching { (old.parent as? ViewGroup)?.removeView(old) }
        runCatching { old.destroy() }
    }

    override fun onDestroy() { releaseWebView(); super.onDestroy() }
}
