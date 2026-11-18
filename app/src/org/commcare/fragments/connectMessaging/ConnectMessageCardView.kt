package org.commcare.fragments.connectMessaging

import android.content.Context
import android.util.AttributeSet
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ViewConnectMessageCardBinding
import java.io.File

/**
 * Shows one `card-html` message in its sandboxed WebView (rich-messaging spec 12, section 7).
 *
 * Inline in the chat the card has a fixed width and takes the height its page reports. Past the
 * cap the WebView keeps the page's full height inside this view, which cuts it off with a fade and
 * an "Open" control, so the WebView never scrolls inside itself or fights the chat list for drags.
 * Full screen it fills this view and scrolls normally.
 */
class ConnectMessageCardView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : FrameLayout(context, attrs) {
        interface Listener {
            /** The card's renderer crashed or hung; its WebView is already gone. */
            fun onCardStopped(messageId: String)

            fun onCardOpenRequested(messageId: String)
        }

        private val binding = ViewConnectMessageCardBinding.inflate(LayoutInflater.from(context), this)
        private var webView: ConnectMessageCardWebView? = null
        private var listener: Listener? = null
        private var inline = false
        private var maxInlineHeightPx = 0

        var messageId: String? = null
            private set
        var cardWidthPx = 0
            private set
        var inlineHeightPx = 0
            private set
        private var contentHeightPx = 0

        // The page can post heights as fast as it likes; the chat lays out for at most one per frame
        private var pendingContentHeightPx: Int? = null
        private val applyPendingHeight =
            Runnable {
                pendingContentHeightPx?.let { height ->
                    pendingContentHeightPx = null
                    applyContentHeight(height)
                }
            }

        private val sandboxCallbacks =
            object : ConnectMessageCardSandbox.Callbacks {
                override fun onHeightReported(cssPixels: Int) {
                    val id = messageId
                    if (!inline || id == null) {
                        return
                    }
                    val height = ConnectMessageCardSizer.contentHeightPx(cssPixels, resources.displayMetrics.density) ?: return
                    heightCache.put(cacheKey(id, cardWidthPx), height)
                    if (pendingContentHeightPx == null) {
                        postOnAnimation(applyPendingHeight)
                    }
                    pendingContentHeightPx = height
                }

                override fun onStopped() {
                    val id = messageId ?: return
                    ConnectMessageCardSupport.markStopped(id)
                    destroy()
                    listener?.onCardStopped(id)
                }
            }

        init {
            binding.tvOpen.setOnClickListener { messageId?.let { listener?.onCardOpenRequested(it) } }
        }

        /** Loads the card for the chat. False when no WebView could be made for it. */
        fun showInline(
            messageId: String,
            files: Map<String, File>,
            widthPx: Int,
            maxInlineHeightPx: Int,
            listener: Listener,
        ): Boolean {
            inline = true
            cardWidthPx = widthPx
            this.maxInlineHeightPx = maxInlineHeightPx
            val initialHeight =
                heightCache.get(cacheKey(messageId, widthPx))
                    ?: resources.getDimensionPixelSize(R.dimen.connect_message_pending_tile_min_height)
            if (!start(messageId, files, listener, LayoutParams(LayoutParams.MATCH_PARENT, initialHeight))) {
                return false
            }
            applyContentHeight(initialHeight)
            return true
        }

        /** Loads the card to fill this view. False when no WebView could be made for it. */
        fun showFullScreen(
            messageId: String,
            files: Map<String, File>,
            listener: Listener,
        ): Boolean {
            inline = false
            return start(messageId, files, listener, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        fun isShowing(messageId: String): Boolean = webView != null && this.messageId == messageId

        /** Pauses or resumes the card's timers and media, as its screen pauses and resumes. */
        fun setActive(active: Boolean) {
            val view = webView ?: return
            if (active) view.onResume() else view.onPause()
        }

        fun destroy() {
            removeCallbacks(applyPendingHeight)
            pendingContentHeightPx = null
            val view = webView ?: return
            webView = null
            binding.flWebView.removeView(view)
            view.destroy()
        }

        private fun start(
            messageId: String,
            files: Map<String, File>,
            listener: Listener,
            params: LayoutParams,
        ): Boolean {
            destroy()
            contentHeightPx = 0
            this.messageId = messageId
            this.listener = listener
            val loader = ConnectMessageCardLoader.forMessage(messageId, files) ?: return false
            val view =
                try {
                    ConnectMessageCardWebView(context)
                } catch (e: RuntimeException) {
                    // No WebView provider, or one in the middle of an update
                    return false
                }
            webView = view
            binding.flWebView.addView(view, params)
            ConnectMessageCardSandbox.load(view, loader, sandboxCallbacks)
            return true
        }

        private fun applyContentHeight(contentHeightPx: Int) {
            if (contentHeightPx == this.contentHeightPx) {
                return
            }
            this.contentHeightPx = contentHeightPx
            webView?.let { view -> view.layoutParams = view.layoutParams.apply { height = contentHeightPx } }
            inlineHeightPx = minOf(contentHeightPx, maxInlineHeightPx)
            val cutOff = contentHeightPx > maxInlineHeightPx
            binding.vFade.visibility = if (cutOff) VISIBLE else GONE
            binding.tvOpen.visibility = if (cutOff) VISIBLE else GONE
            val params: ViewGroup.LayoutParams? = layoutParams
            if (params != null && params.height != inlineHeightPx) {
                params.height = inlineHeightPx
                layoutParams = params
            }
        }

        private companion object {
            const val HEIGHT_CACHE_ENTRIES = 100

            // Last height each card reported, by width, so a card scrolled back into view does not jump
            val heightCache = LruCache<String, Int>(HEIGHT_CACHE_ENTRIES)

            fun cacheKey(
                messageId: String,
                widthPx: Int,
            ) = "$messageId@$widthPx"
        }
    }
