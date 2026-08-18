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
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private lateinit var splash: ImageView
    private val splashHandler = Handler(Looper.getMainLooper())
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingGeolocationOrigin: String? = null
    private var pendingGeolocationCallback: GeolocationPermissions.Callback? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private var pendingCameraFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.addJavascriptInterface(FileBridge(), "Web2App")
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
        root.addView(splash, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        webView.loadUrl(BuildConfig.WEB_APP_URL)
        splashHandler.postDelayed({ hideSplash() }, 8000)
    }

    private fun hideSplash() {
        if (splash.visibility != ImageView.VISIBLE) return
        splash.animate().alpha(0f).setDuration(220).withEndAction { splash.visibility = ImageView.GONE }.start()
    }

    private fun hasPermission(permission: String): Boolean =
        android.os.Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun requestWebPermissions(request: PermissionRequest) {
        val needed = mutableListOf<String>()
        if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && !hasPermission(Manifest.permission.CAMERA)) needed += Manifest.permission.CAMERA
        if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) && !hasPermission(Manifest.permission.RECORD_AUDIO)) needed += Manifest.permission.RECORD_AUDIO
        if (needed.isEmpty()) { request.grant(request.resources); return }
        pendingWebPermissionRequest = request
        requestPermissions(needed.toTypedArray(), REQUEST_WEB_PERMISSIONS)
    }

    private fun requestGeolocation(origin: String, callback: GeolocationPermissions.Callback) {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) { callback.invoke(origin, true, false); return }
        pendingGeolocationOrigin = origin
        pendingGeolocationCallback = callback
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION)
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
                if (origin != null && callback != null) callback.invoke(origin, hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION), false)
            }
        }
    }

    private fun openFileChooser(params: WebChromeClient.FileChooserParams): Boolean {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        val accepts = params.acceptTypes.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }
        val wantsImage = accepts.any { it.startsWith("image/") || it == "image/*" }
        val wantsCamera = params.isCaptureEnabled && wantsImage
        if (wantsCamera) return launchCamera()
        filePathCallback = pendingChooserCallback
        val mimeType = when {
            accepts.isEmpty() -> "*/*"
            accepts.size == 1 -> accepts[0]
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
        }
        return try {
            startActivityForResult(intent, REQUEST_FILE_CHOOSER)
            true
        } catch (_: ActivityNotFoundException) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            false
        }
    }

    private var pendingChooserCallback: ValueCallback<Array<Uri>>? = null

    private fun launchCamera(): Boolean {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            return true
        }
        filePathCallback?.onReceiveValue(null)
        val file = File.createTempFile("web2app_camera_${UUID.randomUUID()}", ".jpg", cacheDir)
        val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        pendingCameraFile = file
        pendingCameraUri = uri
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            startActivityForResult(intent, REQUEST_CAMERA_CAPTURE)
            true
        } catch (_: ActivityNotFoundException) {
            file.delete()
            pendingCameraFile = null
            pendingCameraUri = null
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_FILE_CHOOSER -> {
                val callback = filePathCallback
                filePathCallback = null
                if (callback == null) return
                if (resultCode != RESULT_OK || data == null) { callback.onReceiveValue(null); return }
                val uris = mutableListOf<Uri>()
                data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri }
                if (uris.isEmpty()) data.data?.let { uris += it }
                callback.onReceiveValue(uris.toTypedArray().takeIf { it.isNotEmpty() })
            }
            REQUEST_CAMERA_CAPTURE -> {
                val callback = filePathCallback
                val uri = pendingCameraUri
                val file = pendingCameraFile
                pendingCameraUri = null
                pendingCameraFile = null
                filePathCallback = null
                if (resultCode == RESULT_OK && uri != null && file?.exists() == true) callback?.onReceiveValue(arrayOf(uri))
                else { file?.delete(); callback?.onReceiveValue(null) }
            }
            REQUEST_CAMERA -> {
                if (grantResultsSafe(permissions = null) && hasPermission(Manifest.permission.CAMERA)) launchCamera()
                else filePathCallback?.onReceiveValue(null).also { filePathCallback = null }
            }
        }
    }

    private fun grantResultsSafe(permissions: Array<String>?): Boolean = true

    private fun shareUrl(uri: Uri, mimeType: String = "text/plain") {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mimeType; putExtra(Intent.EXTRA_TEXT, uri.toString()) }, "Share with"))
        } catch (_: ActivityNotFoundException) { Toast.makeText(this, "No sharing app available", Toast.LENGTH_SHORT).show() }
    }

    private fun launchExternal(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme in setOf("http", "https")) return false
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: ActivityNotFoundException) { false }
    }

    private inner class FileBridge {
        @JavascriptInterface
        fun saveBase64File(filename: String, mimeType: String, base64: String) {
            try {
                val safeName = filename.substringAfterLast('/').substringAfterLast('\\').ifBlank { "download" }
                val bytes = android.util.Base64.decode(base64.substringAfter(','), android.util.Base64.DEFAULT)
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val target = contentResolver.insert(collection, values) ?: throw IllegalStateException("Unable to create download")
                contentResolver.openOutputStream(target)?.use { it.write(bytes) }
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); contentResolver.update(target, values, null, null)
                runOnUiThread { Toast.makeText(this@MainActivity, "Saved to Downloads", Toast.LENGTH_SHORT).show() }
            } catch (_: Exception) { runOnUiThread { Toast.makeText(this@MainActivity, "Unable to save file", Toast.LENGTH_SHORT).show() } }
        }

        @JavascriptInterface
        fun shareText(text: String) { runOnUiThread { shareUrl(Uri.parse(text), "text/plain") } }
    }

    private inner class AppWebChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) { runOnUiThread { requestWebPermissions(request) } }
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) { runOnUiThread { requestGeolocation(origin, callback) } }
        override fun onShowFileChooser(webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = callback
            pendingChooserCallback = callback
            return openFileChooser(params)
        }
    }

    private inner class AppDownloadListener : DownloadListener {
        override fun onDownloadStart(url: String, userAgent: String, contentDisposition: String, mimetype: String, contentLength: Long) {
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                Toast.makeText(this@MainActivity, "Preparing generated file…", Toast.LENGTH_SHORT).show()
                return
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) { Toast.makeText(this@MainActivity, "This file type cannot be downloaded here.", Toast.LENGTH_SHORT).show(); return }
            try {
                val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle(filename); setDescription("Downloading $filename"); setMimeType(mimetype.takeIf { it.isNotBlank() } ?: "application/octet-stream")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    addRequestHeader("User-Agent", userAgent)
                    CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { addRequestHeader("Cookie", it) }
                }
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this@MainActivity, "Download started", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { Toast.makeText(this@MainActivity, "Unable to start download", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun injectGeneratedDownloadSupport(view: WebView) {
        val js = """
            (function(){
              if(window.__web2appReady)return; window.__web2appReady=true;
              document.addEventListener('click',function(e){
                var a=e.target.closest && e.target.closest('a[download]'); if(!a)return;
                var h=a.href||''; if(!(h.indexOf('blob:')===0||h.indexOf('data:')===0))return;
                e.preventDefault();
                var name=a.download||'download';
                fetch(h).then(function(r){return r.blob()}).then(function(b){
                  var fr=new FileReader(); fr.onloadend=function(){
                    var x=String(fr.result||''); Web2App.saveBase64File(name,b.type||'application/octet-stream',x);
                  }; fr.readAsDataURL(b);
                }).catch(function(){Web2App.saveBase64File(name,'application/octet-stream',h);});
              },true);
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    private inner class AppWebViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) { super.onPageFinished(view, url); injectGeneratedDownloadSupport(view); splashHandler.postDelayed({ hideSplash() }, 150) }
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = handleUrl(request.url)
        @Suppress("DEPRECATION") override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = handleUrl(Uri.parse(url))
        private fun handleUrl(uri: Uri): Boolean {
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase()
            val isWhatsApp = scheme == "whatsapp" || host == "wa.me" || host == "api.whatsapp.com" || host == "web.whatsapp.com" || host == "www.whatsapp.com" || host == "whatsapp.com"
            if (isWhatsApp) return launchExternal(uri)
            if (scheme in setOf("tel", "mailto", "sms", "geo", "maps", "intent")) return launchExternal(uri)
            return false
        }
    }

    @Deprecated("Deprecated in Android API 33")
    override fun onBackPressed() { if (webView.canGoBack()) webView.goBack() else super.onBackPressed() }

    companion object {
        private const val REQUEST_WEB_PERMISSIONS = 4101
        private const val REQUEST_LOCATION = 4102
        private const val REQUEST_FILE_CHOOSER = 4103
        private const val REQUEST_CAMERA = 4104
        private const val REQUEST_CAMERA_CAPTURE = 4105
    }
}
