package org.minanode.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import java.io.ByteArrayInputStream

class DashboardActivity : Activity() {
    private var web: WebView? = null
    private fun local(uri: Uri) = uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port == NodeApi.PORT

    @SuppressLint("SetJavaScriptEnabled") // Only our bundled dashboard at the node's exact local origin.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        setContentView(web)
    }

    override fun onDestroy() { web?.destroy(); web = null; super.onDestroy() }
}
