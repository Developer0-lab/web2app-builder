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
            return handleUrl(request.url.toString())
        }

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return handleUrl(url)
        }

        private fun handleUrl(url: String): Boolean {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return false

            // Keep normal website navigation inside the app.
            if (scheme == "http" || scheme == "https") {
                val appHost = Uri.parse(BuildConfig.WEB_APP_URL).host
                if (uri.host == appHost || uri.host?.endsWith(".$appHost") == true) {
                    return false
                }

                // Common external services should leave the WebView and open
                // with the appropriate Android app/browser.
                if (uri.host == "wa.me" || uri.host?.endsWith("whatsapp.com") == true ||
                    uri.host == "api.whatsapp.com" || uri.host == "maps.google.com" ||
                    uri.host == "www.google.com") {
                    return launchExternal(uri)
                }

                // Other external HTTPS links open in the user's browser.
                return launchExternal(uri)
            }

            // Handle WhatsApp, phone, email, SMS and other Android URL schemes.
            return launchExternal(uri)
        }

        private fun launchExternal(uri: Uri): Boolean {
            return try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                true
            } catch (_: ActivityNotFoundException) {
                // If no app can handle the URL, let WebView try HTTPS URLs.
                uri.scheme == "http" || uri.scheme == "https"
            }
        }
    }

    @Deprecated("Deprecated in Android API 33")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
