package org.commcare.fragments.connectMessaging

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewRenderProcess
import androidx.webkit.WebViewRenderProcessClient
import kotlin.math.roundToInt

/**
 * Sets up a card's WebView as the sandbox in rich-messaging spec 12, section 6: files only from
 * its own message, no network, no navigation, no dialogs or permissions, and one channel back to
 * the app, for the page's height. Never add a JavaScript interface to a card.
 */
object ConnectMessageCardSandbox {
    // The object the page sees as window.cardHost; it only carries the height
    const val HOST_OBJECT_NAME = "cardHost"

    // Cards keep their cookies and storage in their own WebView profile, apart from the app's other WebViews
    const val PROFILE_NAME = "connect-message-cards"

    /**
     * Section 6.7. Removes WebRTC before the page's own scripts run (a speed bump, not a wall: D28
     * accepts the bypass), then reports the html element's laid-out height whenever it changes.
     * Not scrollHeight, which never drops below the view's current height.
     */
    val DOCUMENT_START_SCRIPT =
        """
        (() => {
          for (const name of ["RTCPeerConnection", "webkitRTCPeerConnection", "RTCDataChannel"]) {
            try { delete window[name]; } catch (e) {}
          }
          const host = window.cardHost;
          if (!host) return;
          let last = -1;
          const report = () => {
            const height = Math.ceil(document.documentElement.getBoundingClientRect().height);
            if (height !== last) { last = height; host.postMessage(String(height)); }
          };
          const start = () => { new ResizeObserver(report).observe(document.documentElement); report(); };
          if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", start, { once: true });
          } else {
            start();
          }
        })();
        """.trimIndent()

    interface Callbacks {
        /** The page's height in CSS pixels, as the page itself reported it. Untrusted: clamp it. */
        fun onHeightReported(cssPixels: Int)

        /** The renderer crashed, hung or was killed. The WebView must be removed and destroyed. */
        fun onStopped()
    }

    /**
     * Puts a new card WebView in the cards' own profile, where the WebView supports profiles. Must run
     * before anything else uses the WebView. On older WebViews cards share the default profile.
     */
    @JvmStatic
    @SuppressLint("RequiresFeature")
    fun useCardProfile(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().getOrCreateProfile(PROFILE_NAME)
            WebViewCompat.setProfile(webView, PROFILE_NAME)
        }
    }

    /** Configures the WebView as a sandbox for this card and loads it. Device support must already be checked. */
    @JvmStatic
    @SuppressLint("RequiresFeature")
    fun load(
        webView: WebView,
        loader: ConnectMessageCardLoader,
        callbacks: Callbacks,
    ) {
        configure(webView.settings, webView.resources.configuration.fontScale)
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setDownloadListener { _, _, _, _, _ -> }
        webView.webViewClient = CardClient(loader, callbacks)
        webView.webChromeClient = CardChromeClient()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)
        ) {
            WebViewCompat.setWebViewRenderProcessClient(webView, UnresponsiveRendererClient)
        }
        val origins = setOf(loader.origin)
        WebViewCompat.addWebMessageListener(webView, HOST_OBJECT_NAME, origins) { _, message, sourceOrigin, isMainFrame, _ ->
            if (isMainFrame && loader.isOrigin(sourceOrigin)) {
                message.data
                    ?.trim()
                    ?.toIntOrNull()
                    ?.let(callbacks::onHeightReported)
            }
        }
        WebViewCompat.addDocumentStartJavaScript(webView, DOCUMENT_START_SCRIPT, origins)
        webView.loadUrl(loader.entryUrl)
    }

    /**
     * Clears what cards stored on their own origins, for logout. Where WebView supports profiles, cards
     * live in their own profile, and deleting it removes everything they stored, Cache Storage and
     * cookies included, without touching the app's other WebViews. Older WebViews keep cards in the
     * default profile, where these calls reach IndexedDB, Web Storage and cookies but may leave Cache
     * Storage behind.
     */
    @JvmStatic
    fun clearStoredData() {
        Handler(Looper.getMainLooper()).post {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    deleteCardProfile()
                } else {
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies(null)
                }
            } catch (e: RuntimeException) {
                // No WebView provider on this device, so no card ever stored anything
            }
        }
    }

    @SuppressLint("RequiresFeature")
    private fun deleteCardProfile() {
        val store = ProfileStore.getInstance()
        val profile = store.getProfile(PROFILE_NAME) ?: return
        try {
            store.deleteProfile(PROFILE_NAME)
        } catch (e: IllegalStateException) {
            // A card WebView still holds the profile: clear what its own storage and cookie APIs reach
            profile.webStorage.deleteAllData()
            profile.cookieManager.removeAllCookies(null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(
        settings: WebSettings,
        fontScale: Float,
    ) {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        // A backstop: responses from shouldInterceptRequest still load
        settings.blockNetworkLoads = true
        settings.domStorageEnabled = false
        settings.setGeolocationEnabled(false)
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        // The layout width is the view's width in CSS pixels; any viewport meta tag is ignored
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        settings.textZoom = (fontScale * 100).roundToInt()
    }

    private class CardClient(
        private val loader: ConnectMessageCardLoader,
        private val callbacks: Callbacks,
    ) : WebViewClient() {
        // Never null: null would send the request to the network
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse = loader.respond(request.url.toString(), request.method)

        // Links, location changes, form posts and window.open all end here; the app's own loadUrl does not
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = true

        @Deprecated("Superseded by the WebResourceRequest version; kept so no navigation slips through")
        override fun shouldOverrideUrlLoading(
            view: WebView,
            url: String,
        ): Boolean = true

        // Returning true keeps the app alive. Every WebView in the app hears this, as they share one renderer
        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean {
            callbacks.onStopped()
            return true
        }
    }

    private class CardChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback,
        ) {
            callback.invoke(origin, false, false)
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean = false

        // Dialogs would appear as the app's own, which a card could use to imitate it
        override fun onJsAlert(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onJsConfirm(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onJsBeforeUnload(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?,
        ): Boolean = false

        override fun onShowCustomView(
            view: View?,
            callback: CustomViewCallback?,
        ) {
            callback?.onCustomViewHidden()
        }

        // A card's console may hold its content; it is not logged
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
    }

    // Android 10 and later: a card stuck in a loop has its renderer ended, which onRenderProcessGone then reports
    private object UnresponsiveRendererClient : WebViewRenderProcessClient() {
        override fun onRenderProcessUnresponsive(
            view: WebView,
            renderer: WebViewRenderProcess?,
        ) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)) {
                renderer?.terminate()
            }
        }

        override fun onRenderProcessResponsive(
            view: WebView,
            renderer: WebViewRenderProcess?,
        ) = Unit
    }
}
