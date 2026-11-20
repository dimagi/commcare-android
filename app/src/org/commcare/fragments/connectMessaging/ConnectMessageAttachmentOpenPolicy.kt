package org.commcare.fragments.connectMessaging

import java.util.Locale

/**
 * Decides whether a downloaded attachment may be handed to another app to open.
 *
 * Files that can run script (HTML, SVG, XML, JavaScript) never are: another app, typically a
 * browser, would run them with full network access and none of the card sandbox. Both the
 * sender's declared type and the extension of the sender's file name are checked, since either
 * can be what the receiving app goes by.
 */
object ConnectMessageAttachmentOpenPolicy {
    private val SCRIPT_CAPABLE_TYPES =
        setOf(
            "text/html",
            "application/xhtml+xml",
            "image/svg+xml",
            "text/xml",
            "application/xml",
            "text/javascript",
            "application/javascript",
            "multipart/related",
        )

    private val SCRIPT_CAPABLE_EXTENSIONS =
        setOf("html", "htm", "xhtml", "xht", "svg", "svgz", "xml", "xsl", "js", "mjs", "mht", "mhtml")

    @JvmStatic
    fun canOpenInAnotherApp(
        mimeType: String,
        name: String,
    ): Boolean {
        val type = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
        val extension = name.substringAfterLast('.', "").trim().lowercase(Locale.ROOT)
        return type !in SCRIPT_CAPABLE_TYPES && extension !in SCRIPT_CAPABLE_EXTENSIONS
    }
}
