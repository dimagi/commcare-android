package org.commcare.fragments.connectMessaging

import android.os.Build
import androidx.annotation.StringRes
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.commcare.dalvik.R
import java.util.Collections

/**
 * Whether a `card-html` message can be shown as a card, and why not when it cannot (spec sections
 * 4 and 6.1).
 */
object ConnectMessageCardSupport {
    const val FORMAT = "card-html"

    private val REQUIRED_FEATURES =
        listOf(
            WebViewFeature.MULTI_PROCESS,
            WebViewFeature.DOCUMENT_START_SCRIPT,
            WebViewFeature.WEB_MESSAGE_LISTENER,
        )

    @Volatile
    private var deviceSupported: Boolean? = null

    // Cards whose renderer crashed or hung in this process; not loaded again until the app restarts
    private val stoppedCards: MutableSet<String> = Collections.synchronizedSet(HashSet())

    @JvmStatic
    fun isCard(format: String?): Boolean = FORMAT == format

    /**
     * Cards need the WebView renderer outside the app's process (D30): on Android 6 and 7 it runs
     * inside, where a renderer exploit would run as CommCare. Checked once per process.
     */
    @JvmStatic
    fun isDeviceSupported(): Boolean = deviceSupported ?: checkDevice().also { deviceSupported = it }

    private fun checkDevice(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        return try {
            REQUIRED_FEATURES.all { WebViewFeature.isFeatureSupported(it) } && WebViewCompat.isMultiProcessEnabled()
        } catch (e: RuntimeException) {
            // No WebView provider, or one in the middle of an update
            false
        }
    }

    @JvmStatic
    fun markStopped(messageId: String) {
        stoppedCards.add(messageId)
    }

    @JvmStatic
    fun hasStopped(messageId: String): Boolean = messageId in stoppedCards

    /** The notice to show instead of a card, or null when the card can be shown. */
    @JvmStatic
    @StringRes
    fun unavailableNotice(
        messageId: String,
        attachmentNames: Collection<String>,
        deviceSupported: Boolean,
    ): Int? {
        val hasEntry = ConnectMessageCardLoader.ENTRY_FILE in attachmentNames && ConnectMessageCardLoader.isValidMessageId(messageId)
        return when {
            !deviceSupported -> R.string.connect_messaging_card_unsupported_device
            !hasEntry -> R.string.connect_messaging_card_missing_entry
            hasStopped(messageId) -> R.string.connect_messaging_card_stopped
            else -> null
        }
    }
}
