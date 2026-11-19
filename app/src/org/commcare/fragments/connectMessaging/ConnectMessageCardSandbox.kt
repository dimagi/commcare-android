package org.commcare.fragments.connectMessaging

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.core.content.edit
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewRenderProcess
import androidx.webkit.WebViewRenderProcessClient
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Sets up a card's WebView as the sandbox in rich-messaging spec 12, section 6: files only from
 * its own message, no network, no navigation, no dialogs or permissions, and one channel back to
 * the app, for the page's height. Never add a JavaScript interface to a card.
 */
object ConnectMessageCardSandbox {
    // The object the page sees as window.cardHost; it only carries the height
    const val HOST_OBJECT_NAME = "cardHost"

    // Cards keep their cookies and storage in a WebView profile of their own, apart from the app's other
    // WebViews. Each login gets a fresh one, named "connect-message-cards-<random id>" (see clearStoredData)
    private const val PROFILE_PREFIX = "connect-message-cards"
    private const val PROFILE_PREFERENCES = "connect_message_cards"
    private const val CURRENT_PROFILE_KEY = "current_profile"

    // Sentinel left by a logout that could not delete the old card profile (see retireCardProfile)
    private const val CLEANUP_PENDING_KEY = "cleanup_pending"

    // Profiles left by earlier logins are deleted once per run, before the current one is first loaded
    @Volatile
    private var staleProfilesDeleted = false

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
     * Puts a new card WebView in the cards' current profile, where the WebView supports profiles. Must
     * run before anything else uses the WebView. The first time in each run it also deletes the
     * profiles earlier logins left behind: a profile can only be deleted before it is loaded in the
     * running app. On older WebViews cards share the default profile.
     */
    @JvmStatic
    @SuppressLint("RequiresFeature")
    fun useCardProfile(webView: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            return
        }
        val current = currentProfileName(webView.context)
        // Usually already done at startup (deleteStaleProfilesIfPending); this covers a run that skipped it
        deleteStaleProfiles(webView.context, current)
        ProfileStore.getInstance().getOrCreateProfile(current)
        WebViewCompat.setProfile(webView, current)
    }

    /**
     * The startup half of the logout cleanup. Never throws, so it is safe to call from
     * Application.onCreate.
     *
     * WebView refuses to delete a profile once it has been loaded in the running process, and a
     * loaded profile stays loaded until the process ends. So the card profile in use at logout cannot
     * be deleted then: its Cache Storage in particular stays on disk until a new process deletes the
     * whole profile, before any card loads one. Logout leaves a sentinel for this (retireCardProfile);
     * this deletes the old profiles at the next app start, without waiting for the user to open
     * messaging again.
     *
     * With no sentinel this is one preferences read. WebView is only touched, which loads its engine
     * and is a real cost at startup, when there is something to delete, and that work is posted to
     * run after Application.onCreate rather than inside it.
     */
    @JvmStatic
    fun deleteStaleProfilesIfPending(context: Context) {
        try {
            val appContext = context.applicationContext
            if (!isCleanupPending(appContext)) {
                return
            }
            Handler(Looper.getMainLooper()).post {
                try {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                        deleteStaleProfiles(appContext, currentProfileName(appContext))
                    } else {
                        // The WebView no longer supports profiles, so none can be deleted; stop asking
                        setCleanupPending(appContext, false)
                    }
                } catch (e: RuntimeException) {
                    // No WebView provider right now: the sentinel stays for the next start
                }
            }
        } catch (e: RuntimeException) {
            // Card cleanup must never affect app startup
        }
    }

    /** The card profiles to delete: every one but the current, including the unnumbered name early builds used. */
    @JvmStatic
    fun staleCardProfiles(
        profileNames: List<String>,
        current: String,
    ): List<String> = profileNames.filter { it.startsWith(PROFILE_PREFIX) && it != current }

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
     * Clears what cards stored on their own origins, for logout.
     *
     * Where WebView supports profiles, cards from now on use a fresh, empty profile, even before the
     * app restarts, and the old one is deleted, which removes everything cards stored, Cache Storage
     * included, without touching the app's other WebViews. If a card was shown in this run, the old
     * profile is loaded and cannot be deleted until the process restarts: its IndexedDB and cookies
     * are cleared now, and a sentinel has the next app start delete the rest
     * (deleteStaleProfilesIfPending).
     *
     * Older WebViews keep cards in the default profile, where these calls reach IndexedDB, Web Storage
     * and cookies but may leave Cache Storage behind.
     */
    @JvmStatic
    fun clearStoredData(context: Context) {
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    retireCardProfile(appContext)
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
    private fun retireCardProfile(context: Context) {
        val previous = currentProfileName(context)
        startNewProfile(context)
        if (!deleteUnloadedProfile(previous)) {
            // A card loaded this profile in the running process, so WebView will not delete it until the
            // process restarts. Leave the sentinel for the next app start, and meanwhile clear what the
            // profile's own APIs reach (IndexedDB and cookies, not Cache Storage). It is already loaded,
            // so getProfile changes nothing.
            setCleanupPending(context, true)
            ProfileStore.getInstance().getProfile(previous)?.let { profile ->
                profile.webStorage.deleteAllData()
                profile.cookieManager.removeAllCookies(null)
            }
        }
    }

    // Once per run, before the current profile is loaded: no other card profile can be loaded yet, so
    // each deletion succeeds. The sentinel is cleared only when every old profile is gone.
    @SuppressLint("RequiresFeature")
    private fun deleteStaleProfiles(
        context: Context,
        current: String,
    ) {
        if (staleProfilesDeleted) {
            return
        }
        staleProfilesDeleted = true
        val stale = staleCardProfiles(ProfileStore.getInstance().allProfileNames, current)
        val deleted = stale.map { deleteUnloadedProfile(it) }
        if (deleted.all { it }) {
            setCleanupPending(context, false)
        }
    }

    // False when the profile is loaded in this run, which deleteProfile refuses; a later run deletes it
    @SuppressLint("RequiresFeature")
    private fun deleteUnloadedProfile(name: String): Boolean =
        try {
            ProfileStore.getInstance().deleteProfile(name)
            true
        } catch (e: IllegalStateException) {
            false
        }

    private fun currentProfileName(context: Context): String =
        profilePreferences(context).getString(CURRENT_PROFILE_KEY, null) ?: startNewProfile(context)

    private fun startNewProfile(context: Context): String {
        val name = "$PROFILE_PREFIX-${UUID.randomUUID()}"
        profilePreferences(context).edit { putString(CURRENT_PROFILE_KEY, name) }
        return name
    }

    private fun isCleanupPending(context: Context): Boolean = profilePreferences(context).getBoolean(CLEANUP_PENDING_KEY, false)

    private fun setCleanupPending(
        context: Context,
        pending: Boolean,
    ) {
        profilePreferences(context).edit { putBoolean(CLEANUP_PENDING_KEY, pending) }
    }

    // Not the messaging preferences, which logout clears: the current profile's name must outlive logout
    private fun profilePreferences(context: Context) =
        context.applicationContext.getSharedPreferences(PROFILE_PREFERENCES, Context.MODE_PRIVATE)

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
