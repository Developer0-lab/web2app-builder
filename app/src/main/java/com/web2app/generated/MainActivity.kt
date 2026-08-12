package com.web2app.generated

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl(BuildConfig.WEB_APP_URL)
        setContentView(webView)
    }

    override fun onBackPressed() {
        val view = findViewById<WebView>(android.R.id.content)
        if (view.canGoBack()) view.goBack() else super.onBackPressed()
    }
}
