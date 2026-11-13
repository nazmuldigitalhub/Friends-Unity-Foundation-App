package org.friendsunityfoundation.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val HOME_URL = "https://friendsunityfoundation.org"
        private const val OFFLINE_URL = "file:///android_asset/offline.html"
        private const val JS_INTERFACE_NAME = "Android"
    }

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var webView: WebView

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var showingOfflinePage = false

    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback
            filePathCallback = null
            callback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeRefreshLayout = findViewById(R.id.swipe_refresh)
        webView = findViewById(R.id.web_view)

        configureWebView()
        configureSwipeRefresh()
        configureBackNavigation()

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            loadHome()
        } else {
            showingOfflinePage = webView.url?.startsWith(OFFLINE_URL) == true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.background))

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.addJavascriptInterface(WebAppInterface(), JS_INTERFACE_NAME)
        webView.webViewClient = AppWebViewClient()
        webView.webChromeClient = AppWebChromeClient()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloadFile(url, userAgent, contentDisposition, mimeType)
        }

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    private fun configureSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(R.color.brand_primary, R.color.brand_accent)
        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ -> webView.canScrollVertically(-1) }
        swipeRefreshLayout.setOnRefreshListener {
            when {
                showingOfflinePage && isNetworkAvailable() -> loadHome()
                showingOfflinePage -> {
                    swipeRefreshLayout.isRefreshing = false
                    showToast(R.string.no_internet)
                }
                else -> webView.reload()
            }
        }
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun loadHome() {
        if (!isNetworkAvailable()) {
            showOfflinePage()
            return
        }

        if (showingOfflinePage) {
            webView.clearHistory()
        }

        showingOfflinePage = false
        webView.loadUrl(HOME_URL)
    }

    private fun showOfflinePage() {
        swipeRefreshLayout.isRefreshing = false
        if (showingOfflinePage) return

        showingOfflinePage = true
        webView.loadUrl(OFFLINE_URL)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun handleUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false

        return when (scheme) {
            "http", "https", "file", "about", "javascript", "data", "blob" -> false
            else -> {
                openExternal(uri)
                true
            }
        }
    }

    private fun openExternal(uri: Uri) {
        val intent = if ("intent".equals(uri.scheme, ignoreCase = true)) {
            try {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } catch (exception: Exception) {
                null
            }
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }

        if (intent == null) {
            showToast(R.string.no_app_found)
            return
        }

        try {
            startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            showToast(R.string.no_app_found)
        } catch (exception: Exception) {
            showToast(R.string.no_app_found)
        }
    }

    private fun downloadFile(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            showToast(R.string.download_failed)
            return
        }

        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
                addRequestHeader("User-Agent", userAgent ?: webView.settings.userAgentString)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setTitle(fileName)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadManager =
                getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: run {
                    showToast(R.string.download_failed)
                    return
                }

            downloadManager.enqueue(request)
            showToast(R.string.download_started)
        } catch (exception: Exception) {
            showToast(R.string.download_failed)
        }
    }

    private fun showToast(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
    }

    private inner class AppWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            val uri = request?.url ?: return false
            return handleUri(uri)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            swipeRefreshLayout.isRefreshing = false

            if (url != null && url.startsWith(OFFLINE_URL)) {
                view?.clearHistory()
            }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)

            if (request?.isForMainFrame == true) {
                showOfflinePage()
            }
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            handler?.cancel()
            swipeRefreshLayout.isRefreshing = false
            showToast(R.string.secure_connection_failed)
        }
    }

    private inner class AppWebChromeClient : WebChromeClient() {

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: WebChromeClient.FileChooserParams?
        ): Boolean {
            this@MainActivity.filePathCallback?.onReceiveValue(null)
            this@MainActivity.filePathCallback = filePathCallback

            val intent = fileChooserParams?.createIntent()
                ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }

            return try {
                fileChooserLauncher.launch(intent)
                true
            } catch (exception: ActivityNotFoundException) {
                this@MainActivity.filePathCallback = null
                filePathCallback?.onReceiveValue(null)
                showToast(R.string.no_file_picker)
                false
            }
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            if (resultMsg == null) return false

            val popupWebView = WebView(this@MainActivity)
            popupWebView.settings.javaScriptEnabled = true
            popupWebView.settings.domStorageEnabled = true
            popupWebView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    popup: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false

                    if (!handleUri(uri)) {
                        this@MainActivity.webView.loadUrl(uri.toString())
                    }

                    popupWebView.post { popupWebView.destroy() }
                    return true
                }
            }

            CookieManager.getInstance().setAcceptThirdPartyCookies(popupWebView, true)

            val transport = resultMsg.obj as? WebView.WebViewTransport ?: run {
                popupWebView.destroy()
                return false
            }

            transport.webView = popupWebView
            resultMsg.sendToTarget()
            return true
        }
    }

    inner class WebAppInterface {

        @JavascriptInterface
        fun retry() {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    if (isNetworkAvailable()) {
                        loadHome()
                    } else {
                        swipeRefreshLayout.isRefreshing = false
                        showToast(R.string.no_internet)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null

        webView.removeJavascriptInterface(JS_INTERFACE_NAME)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()

        super.onDestroy()
    }
}
