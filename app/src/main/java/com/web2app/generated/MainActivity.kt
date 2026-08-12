package com.web2app.generated

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.webViewClient = AppWebViewClient()
        webView.loadUrl(BuildConfig.WEB_APP_URL)
        setContentView(webView)
    }

    private inner class AppWebViewClient : WebViewClient() {
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

            // ONLY WhatsApp links are intentionally opened outside the WebView.
            // Every normal HTTP/HTTPS link stays inside the website/app.
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
                val whatsappIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                }
                try {
                    startActivity(whatsappIntent)
                } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }

    @Deprecated("Deprecated in Android API 33")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
