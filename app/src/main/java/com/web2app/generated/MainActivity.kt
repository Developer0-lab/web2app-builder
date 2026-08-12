package com.web2app.generated

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private lateinit var splash: ImageView
    private val splashHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = FrameLayout(this)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.webViewClient = AppWebViewClient()
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))

        splash = ImageView(this).apply {
            setBackgroundColor(Color.rgb(7, 17, 31))
            setImageResource(com.web2app.generated.R.drawable.web2app_splash)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Loading"
        }
        root.addView(splash, FrameLayout.LayoutParams(-1, -1).apply { gravity = Gravity.CENTER })
        setContentView(root)

        webView.loadUrl(BuildConfig.WEB_APP_URL)
        splashHandler.postDelayed({ hideSplash() }, 8000)
    }

    private fun hideSplash() {
        if (splash.visibility != View.VISIBLE) return
        splash.animate().alpha(0f).setDuration(220).withEndAction {
            splash.visibility = View.GONE
        }.start()
    }

    private inner class AppWebViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            splashHandler.postDelayed({ hideSplash() }, 150)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return handleUrl(request.url)
        }

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return handleUrl(Uri.parse(url))
        }

        private fun handleUrl(uri: Uri): Boolean {
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase()
            val isWhatsApp =
                scheme == "whatsapp" ||
                host == "wa.me" ||
                host == "api.whatsapp.com" ||
                host == "web.whatsapp.com" ||
                host == "www.whatsapp.com" ||
                host == "whatsapp.com"
            return if (isWhatsApp) launchWhatsApp(uri) else false
        }

        private fun launchWhatsApp(uri: Uri): Boolean {
            return try {
                val whatsappIntent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") }
                try { startActivity(whatsappIntent) } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
                true
            } catch (_: ActivityNotFoundException) { false }
        }
    }

    @Deprecated("Deprecated in Android API 33")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
