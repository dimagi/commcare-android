package org.commcare.fragments.connectMessaging

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Serves one card's files to its WebView, and nothing else (rich-messaging spec 12, section 6.2).
 *
 * Each card has its own origin, `https://<message id>.card.invalid`, so storage and cookies are
 * never shared between cards. A request is answered from the card's attachments only when its path
 * exactly matches an attachment name with an allowed extension. Everything else, including every
 * other host and scheme, gets an empty 404. Never answering null matters: null would send the
 * request to the network.
 */
class ConnectMessageCardLoader private constructor(
    val origin: String,
    private val files: Map<String, File>,
) {
    val host: String = origin.removePrefix(SCHEME_PREFIX)
    val entryUrl: String = "$origin/$ENTRY_FILE"

    fun respond(
        url: String,
        method: String,
    ): WebResourceResponse {
        val name = resolve(url, method, host, files.keys) ?: return notFound()
        val mimeType = mimeTypeFor(name) ?: return notFound()
        return try {
            response(mimeType, HTTP_OK, "OK", FileInputStream(files.getValue(name)))
        } catch (e: IOException) {
            notFound()
        }
    }

    /** Whether a message from the page came from this card's own origin. */
    fun isOrigin(uri: Uri): Boolean =
        "https".equals(uri.scheme, ignoreCase = true) && host.equals(uri.host, ignoreCase = true) && uri.port == -1

    companion object {
        const val ENTRY_FILE = "index.html"
        private const val SCHEME_PREFIX = "https://"
        private const val HOST_SUFFIX = ".card.invalid"
        private const val HTTP_OK = 200
        private const val HTTP_NOT_FOUND = 404

        // Message ids are UUIDs; anything else could not be a single host name label
        private val MESSAGE_ID = Regex("^[0-9A-Fa-f-]{1,63}$")

        /**
         * Section 6.2. 'self' is the card's own origin, which only this loader serves. The policy is
         * there for its network directives; inline scripts and eval are allowed because the author
         * already controls every script in the package.
         */
        const val CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; media-src 'self' data: blob:; " +
                "font-src 'self' data:; connect-src 'self'; frame-src 'none'; child-src 'none'; worker-src 'none'; " +
                "object-src 'none'; form-action 'none'; base-uri 'none'"

        val RESPONSE_HEADERS: Map<String, String> =
            mapOf(
                "Content-Security-Policy" to CONTENT_SECURITY_POLICY,
                "X-Content-Type-Options" to "nosniff",
                "Cache-Control" to "no-store",
            )

        // Section 3: the only file types a card can use, by extension. The sender's declared type is ignored
        private val MIME_TYPES =
            mapOf(
                "html" to "text/html",
                "css" to "text/css",
                "js" to "text/javascript",
                "mjs" to "text/javascript",
                "json" to "application/json",
                "txt" to "text/plain",
                "svg" to "image/svg+xml",
                "png" to "image/png",
                "jpg" to "image/jpeg",
                "jpeg" to "image/jpeg",
                "gif" to "image/gif",
                "webp" to "image/webp",
                "mp3" to "audio/mpeg",
                "m4a" to "audio/mp4",
                "ogg" to "audio/ogg",
                "wav" to "audio/wav",
                "mp4" to "video/mp4",
                "webm" to "video/webm",
                "woff" to "font/woff",
                "woff2" to "font/woff2",
                "ttf" to "font/ttf",
            )
        private val TEXT_MIME_TYPES = setOf("text/html", "text/css", "text/javascript", "application/json", "text/plain", "image/svg+xml")

        @JvmStatic
        fun isValidMessageId(messageId: String): Boolean = MESSAGE_ID.matches(messageId)

        /** The loader for a card, or null when the message id cannot form its host name. */
        @JvmStatic
        fun forMessage(
            messageId: String,
            files: Map<String, File>,
        ): ConnectMessageCardLoader? {
            if (!isValidMessageId(messageId)) {
                return null
            }
            return ConnectMessageCardLoader(SCHEME_PREFIX + messageId.lowercase(Locale.ROOT) + HOST_SUFFIX, files)
        }

        /** The attachment name a request asks for, or null when it must get a 404. */
        @JvmStatic
        fun resolve(
            url: String,
            method: String,
            host: String,
            names: Set<String>,
        ): String? {
            if (!method.equals("GET", ignoreCase = true)) {
                return null
            }
            val uri =
                try {
                    URI(url)
                } catch (e: URISyntaxException) {
                    return null
                }
            val sameOrigin =
                "https".equals(uri.scheme, ignoreCase = true) &&
                    host.equals(uri.host, ignoreCase = true) &&
                    uri.port == -1 &&
                    uri.rawUserInfo == null
            if (!sameOrigin) {
                return null
            }
            // getPath() is already percent-decoded; the query and fragment play no part
            val name = (uri.path ?: "").removePrefix("/").ifEmpty { ENTRY_FILE }
            return name.takeIf { it in names && mimeTypeFor(it) != null }
        }

        @JvmStatic
        fun mimeTypeFor(name: String): String? = MIME_TYPES[name.substringAfterLast('.', "").lowercase(Locale.ROOT)]

        private fun notFound(): WebResourceResponse = response("text/plain", HTTP_NOT_FOUND, "Not Found", ByteArrayInputStream(ByteArray(0)))

        private fun response(
            mimeType: String,
            status: Int,
            reason: String,
            data: InputStream,
        ) = WebResourceResponse(
            mimeType,
            if (mimeType in TEXT_MIME_TYPES) "utf-8" else null,
            status,
            reason,
            RESPONSE_HEADERS,
            data,
        )
    }
}
