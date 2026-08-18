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
import android.os.Build
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
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var splash: ImageView
    private val splashHandler = Handler(Looper.getMainLooper())
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingGeolocationOrigin: String? = null
    private var pendingGeolocationCallback: GeolocationPermissions.Callback? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private var pendingCameraFile: File? = null
    private var pendingSave: Triple<String, String, ByteArray>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            addJavascriptInterface(FileBridge(), "Web2App")
            webViewClient = AppWebViewClient()
            webChromeClient = AppWebChromeClient()
            setDownloadListener(AppDownloadListener())
        }
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

    private fun hasPermission(permission: String) = Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun requestWebPermissions(request: PermissionRequest) {
        val needed = mutableListOf<String>()
        if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && !hasPermission(Manifest.permission.CAMERA)) needed += Manifest.permission.CAMERA
        if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) && !hasPermission(Manifest.permission.RECORD_AUDIO)) needed += Manifest.permission.RECORD_AUDIO
        if (needed.isEmpty()) request.grant(request.resources)
        else { pendingWebPermissionRequest = request; requestPermissions(needed.toTypedArray(), REQUEST_WEB_PERMISSIONS) }
    }

    private fun requestGeolocation(origin: String, callback: GeolocationPermissions.Callback) {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) callback.invoke(origin, true, false)
        else { pendingGeolocationOrigin = origin; pendingGeolocationCallback = callback; requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCATION) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_WEB_PERMISSIONS -> {
                val request = pendingWebPermissionRequest; pendingWebPermissionRequest = null
                if (request != null) {
                    val cameraOk = !request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) || hasPermission(Manifest.permission.CAMERA)
                    val audioOk = !request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) || hasPermission(Manifest.permission.RECORD_AUDIO)
                    if (cameraOk && audioOk) request.grant(request.resources) else request.deny()
                }
            }
            REQUEST_LOCATION -> {
                val origin = pendingGeolocationOrigin; val callback = pendingGeolocationCallback
                pendingGeolocationOrigin = null; pendingGeolocationCallback = null
                if (origin != null && callback != null) callback.invoke(origin, hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION), false)
            }
            REQUEST_CAMERA -> if (hasPermission(Manifest.permission.CAMERA)) launchCamera() else { filePathCallback?.onReceiveValue(null); filePathCallback = null }
            REQUEST_WRITE_STORAGE -> {
                val save = pendingSave; pendingSave = null
                if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) && save != null) saveLegacy(save.first, save.second, save.third)
                else Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFileChooser(params: WebChromeClient.FileChooserParams): Boolean {
        val callback = filePathCallback ?: return false
        val accepts = params.acceptTypes.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }
        if (params.isCaptureEnabled && accepts.any { it.startsWith("image/") || it == "image/*" }) return launchCamera()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = when { accepts.isEmpty() -> "*/*"; accepts.size == 1 -> accepts[0]; else -> "*/*" }
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
        }
        return try { startActivityForResult(intent, REQUEST_FILE_CHOOSER); true }
        catch (_: ActivityNotFoundException) { callback.onReceiveValue(null); filePathCallback = null; false }
    }

    private fun launchCamera(): Boolean {
        if (!hasPermission(Manifest.permission.CAMERA)) { requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA); return true }
        val file = File.createTempFile("web2app_camera_${UUID.randomUUID()}", ".jpg", cacheDir)
        val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        pendingCameraFile = file; pendingCameraUri = uri
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try { startActivityForResult(intent, REQUEST_CAMERA_CAPTURE); true }
        catch (_: ActivityNotFoundException) { file.delete(); pendingCameraFile = null; pendingCameraUri = null; filePathCallback?.onReceiveValue(null); filePathCallback = null; false }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE_CHOOSER) {
            val callback = filePathCallback; filePathCallback = null
            if (callback == null) return
            if (resultCode != RESULT_OK || data == null) { callback.onReceiveValue(null); return }
            val uris = mutableListOf<Uri>()
            data.clipData?.let { for (i in 0 until it.itemCount) uris += it.getItemAt(i).uri }
            if (uris.isEmpty()) data.data?.let { uris += it }
            callback.onReceiveValue(uris.toTypedArray().takeIf { it.isNotEmpty() }); return
        }
        if (requestCode == REQUEST_CAMERA_CAPTURE) {
            val callback = filePathCallback; val uri = pendingCameraUri; val file = pendingCameraFile
            filePathCallback = null; pendingCameraUri = null; pendingCameraFile = null
            if (resultCode == RESULT_OK && uri != null && file?.exists() == true) callback?.onReceiveValue(arrayOf(uri)) else { file?.delete(); callback?.onReceiveValue(null) }
        }
    }

    private fun shareText(text: String) {
        try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share with")) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "No sharing app available", Toast.LENGTH_SHORT).show() }
    }

    private fun saveGeneratedFile(filename: String, mimeType: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val target = contentResolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: throw IllegalStateException()
                contentResolver.openOutputStream(target)?.use { it.write(bytes) }
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); contentResolver.update(target, values, null, null)
                Toast.makeText(this, "Saved to Downloads", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { Toast.makeText(this, "Unable to save file", Toast.LENGTH_SHORT).show() }
        } else {
            if (!hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { pendingSave = Triple(filename, mimeType, bytes); requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_STORAGE); return }
            saveLegacy(filename, mimeType, bytes)
        }
    }

    private fun saveLegacy(filename: String, mimeType: String, bytes: ByteArray) {
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs(); File(dir, filename).writeBytes(bytes)
            Toast.makeText(this, "Saved to Downloads", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { Toast.makeText(this, "Unable to save file", Toast.LENGTH_SHORT).show() }
    }

    private inner class FileBridge {
        @JavascriptInterface fun saveBase64File(filename: String, mimeType: String, base64: String) {
            try { saveGeneratedFile(filename.substringAfterLast('/').substringAfterLast('\\').ifBlank { "download" }, mimeType, android.util.Base64.decode(base64.substringAfter(','), android.util.Base64.DEFAULT)) }
            catch (_: Exception) { runOnUiThread { Toast.makeText(this@MainActivity, "Invalid generated file", Toast.LENGTH_SHORT).show() } }
        }
        @JavascriptInterface fun shareText(text: String) { runOnUiThread { shareText(text) } }
    }

    private inner class AppWebChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) = runOnUiThread { requestWebPermissions(request) }
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) = runOnUiThread { requestGeolocation(origin, callback) }
        override fun onShowFileChooser(webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            filePathCallback?.onReceiveValue(null); filePathCallback = callback; return openFileChooser(params)
        }
    }

    private inner class AppDownloadListener : DownloadListener {
        override fun onDownloadStart(url: String, userAgent: String, contentDisposition: String, mimetype: String, contentLength: Long) {
            if (url.startsWith("blob:") || url.startsWith("data:")) return
            if (!url.startsWith("http://") && !url.startsWith("https://")) { Toast.makeText(this@MainActivity, "Unsupported download", Toast.LENGTH_SHORT).show(); return }
            try {
                val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setTitle(filename); setDescription("Downloading $filename"); setMimeType(mimetype.ifBlank { "application/octet-stream" })
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
        view.evaluateJavascript("""
            (function(){if(window.__web2appReady)return;window.__web2appReady=true;
            document.addEventListener('click',function(e){var a=e.target.closest&&e.target.closest('a[download]');if(!a)return;var h=a.href||'';if(!(h.indexOf('blob:')===0||h.indexOf('data:')===0))return;e.preventDefault();var n=a.download||'download';
            fetch(h).then(function(r){return r.blob()}).then(function(b){var fr=new FileReader();fr.onloadend=function(){Web2App.saveBase64File(n,b.type||'application/octet-stream',String(fr.result||''));};fr.readAsDataURL(b);}).catch(function(){});},true);})();
        """.trimIndent(), null)
    }

    private fun externalIntent(uri: Uri): Boolean = try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true } catch (_: ActivityNotFoundException) { false }

    private inner class AppWebViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) { super.onPageFinished(view, url); injectGeneratedDownloadSupport(view); splashHandler.postDelayed({ hideSplash() }, 150) }
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = handleUrl(request.url)
        @Suppress("DEPRECATION") override fun shouldOverrideUrlLoading(view: WebView, url: String) = handleUrl(Uri.parse(url))
        private fun handleUrl(uri: Uri): Boolean {
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase()
            val external = scheme in setOf("tel", "mailto", "sms", "geo", "maps", "intent", "whatsapp") || host in setOf("wa.me", "api.whatsapp.com", "whatsapp.com", "www.whatsapp.com")
            return if (external) externalIntent(uri) else false
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
        private const val REQUEST_WRITE_STORAGE = 4106
    }
}
