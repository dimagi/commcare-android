package org.commcare.fragments.connectMessaging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectMessageAttachmentOpenPolicyTest {
    @Test
    fun `ordinary documents and media can be opened in another app`() {
        assertTrue(ConnectMessageAttachmentOpenPolicy.canOpenInAnotherApp("application/pdf", "report.pdf"))
        assertTrue(ConnectMessageAttachmentOpenPolicy.canOpenInAnotherApp("image/png", "photo.png"))
        assertTrue(ConnectMessageAttachmentOpenPolicy.canOpenInAnotherApp("text/plain", "notes.txt"))
        assertTrue(ConnectMessageAttachmentOpenPolicy.canOpenInAnotherApp("application/octet-stream", "no-extension"))
    }

    @Test
    fun `a script-capable declared type is refused whatever the name`() {
        for (type in listOf("text/html", "application/xhtml+xml", "image/svg+xml", "text/xml", "application/javascript")) {
            assertFalse(type, ConnectMessageAttachmentOpenPolicy.canOpenInAnotherApp(type, "harmless.pdf"))
        }
    }

    @Test
    fun `a script-capable extension is refused whatever the declared type`() {
        for (name in listOf("index.html", "page.HTM", "chart.svg", "data.xml", "app.js", "module.mjs", "archive.mhtml")) {
            assertFalse(name, ConnectMessageAttachmentOpenPolicy.canOpenInAnotherApp("application/pdf", name))
        }
    }

    @Test
    fun `type parameters and case do not get around the rule`() {
        assertFalse(ConnectMessageAttachmentOpenPolicy.canOpenInAnotherApp("Text/HTML; charset=utf-8", "page"))
        assertFalse(ConnectMessageAttachmentOpenPolicy.canOpenInAnotherApp(" image/svg+xml ", "drawing"))
    }
}
