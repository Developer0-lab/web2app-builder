package com.web2app.generated

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private lateinit var splash: ImageView
    private val splashHandler = Handler(Looper.getMainLooper())
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingGeolocationOrigin: String? = null
    private var pendingGeolocationCallback: GeolocationPermissions.Callback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = FrameLayout(this)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.webViewClient = AppWebViewClient()
        webView.webChromeClient = AppWebChromeClient()
        webView.setDownloadListener(AppDownloadListener())
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

    private fun hasPermission(permission: String): Boolean =
        android.os.Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun requestWebPermissions(request: PermissionRequest) {
        val needed = mutableListOf<String>()
        if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && !hasPermission(Manifest.permission.CAMERA)) {
            needed += Manifest.permission.CAMERA
        }
        if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (needed.isEmpty()) {
            request.grant(request.resources)
            return
        }
        pendingWebPermissionRequest = request
        requestPermissions(needed.toTypedArray(), REQUEST_WEB_PERMISSIONS)
    }

    private fun requestGeolocation(origin: String, callback: GeolocationPermissions.Callback) {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            callback.invoke(origin, true, false)
            return
        }
        pendingGeolocationOrigin = origin
        pendingGeolocationCallback = callback
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_WEB_PERMISSIONS -> {
                val request = pendingWebPermissionRequest
                pendingWebPermissionRequest = null
                if (request != null) {
                    val cameraOk = !request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) || hasPermission(Manifest.permission.CAMERA)
                    val audioOk = !request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) || hasPermission(Manifest.permission.RECORD_AUDIO)
                    if (cameraOk && audioOk) request.grant(request.resources) else request.deny()
                }
            }
            REQUEST_LOCATION -> {
                val origin = pendingGeolocationOrigin
                val callback = pendingGeolocationCallback
                pendingGeolocationOrigin = null
                pendingGeolocationCallback = null
                if (origin != null && callback != null) {
                    callback.invoke(origin, grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED, false)
                }
            }
        }
    }

    private inner class AppWebChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            runOnUiThread { requestWebPermissions(request) }
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            runOnUiThread { requestGeolocation(origin, callback) }
        }
    }

    private inner class AppDownloadListener : DownloadListener {
        override fun onDownloadStart(
            url: String,
            userAgent: String,
            contentDisposition: String,
            mimetype: String,
            contentLength: Long
        ) {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(this@MainActivity, "This file type cannot be downloaded here.", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle(filename)
                    setDescription("Downloading $filename")
                    setMimeType(mimetype.takeIf { it.isNotBlank() } ?: "application/octet-stream")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    addRequestHeader("User-Agent", userAgent)
                    val cookies = CookieManager.getInstance().getCookie(url)
                    if (!cookies.isNullOrBlank()) addRequestHeader("Cookie", cookies)
                }
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                Toast.makeText(this@MainActivity, "Download started", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, "Unable to start download", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class AppWebViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            super.onPageFinished(view, url)
            splashHandler.postDelayed({ hideSplash() }, 150)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = handleUrl(request.url)

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = handleUrl(Uri.parse(url))

        private fun handleUrl(uri: Uri): Boolean {
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase()
            val isWhatsApp = scheme == "whatsapp" || host == "wa.me" || host == "api.whatsapp.com" ||
                host == "web.whatsapp.com" || host == "www.whatsapp.com" || host == "whatsapp.com"
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

    companion object {
        private const val REQUEST_WEB_PERMISSIONS = 4101
        private const val REQUEST_LOCATION = 4102
    }
}
