package org.commcare.fragments.connectMessaging

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessageCardLoaderTest {
    private val folder = File(ApplicationProvider.getApplicationContext<CommCareTestApplication>().cacheDir, "card")
    private val files =
        mapOf(
            "index.html" to write("index-file", "<p>card</p>"),
            "data.json" to write("data-file", "{\"a\": 1}"),
            "img/logo.png" to write("logo-file", "png"),
            "my chart.svg" to write("svg-file", "<svg/>"),
            "notes.exe" to write("exe-file", "MZ"),
        )
    private val loader = ConnectMessageCardLoader.forMessage(MESSAGE_ID, files)!!

    @Test
    fun `each card gets its own lower-case https origin`() {
        assertEquals("https://3f2a6c1e-0000-4000-8000-00000000abcd.card.invalid", loader.origin)
        assertEquals(loader.origin + "/index.html", loader.entryUrl)
    }

    @Test
    fun `a message id that cannot be a host name gets no loader`() {
        assertNull(ConnectMessageCardLoader.forMessage("not a uuid/../x", files))
        assertNull(ConnectMessageCardLoader.forMessage("", files))
    }

    @Test
    fun `paths resolve to attachments by exact name`() {
        assertEquals("index.html", resolve("/index.html"))
        assertEquals("index.html", resolve("/"))
        assertEquals("index.html", resolve(""))
        assertEquals("data.json", resolve("/data.json?cache=1#top"))
        assertEquals("img/logo.png", resolve("/img/logo.png"))
        assertEquals("my chart.svg", resolve("/my%20chart.svg"))
    }

    @Test
    fun `anything that is not an exact match with an allowed type resolves to nothing`() {
        assertNull(resolve("/Index.html"))
        assertNull(resolve("/missing.txt"))
        assertNull(resolve("/notes.exe"))
        assertNull(resolve("/../index.html"))
        assertNull(resolve("/index.html", method = "POST"))
    }

    @Test
    fun `other origins resolve to nothing`() {
        val names = files.keys
        for (url in listOf(
            "http://${loader.host}/index.html",
            "https://${loader.host}:8443/index.html",
            "https://user@${loader.host}/index.html",
            "https://other.card.invalid/index.html",
            "https://example.com/index.html",
            "file:///index.html",
            "content://org.commcare.dalvik.external.files.provider/index.html",
            "not a url",
        )) {
            assertNull(url, ConnectMessageCardLoader.resolve(url, "GET", loader.host, names))
        }
    }

    @Test
    fun `a served file carries the sandbox headers and its type from the extension`() {
        val response = loader.respond(loader.origin + "/data.json", "GET")

        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.mimeType)
        assertEquals("{\"a\": 1}", response.data.bufferedReader().readText())
        assertSandboxHeaders(response.responseHeaders)
    }

    @Test
    fun `everything else is an empty 404 with the same headers, never null`() {
        for (url in listOf(loader.origin + "/missing.txt", "https://example.com/tracker.gif", "ws://example.com/socket")) {
            val response = loader.respond(url, "GET")

            assertNotNull(url, response)
            assertEquals(url, 404, response.statusCode)
            assertEquals(url, "", response.data.bufferedReader().readText())
            assertSandboxHeaders(response.responseHeaders)
        }
    }

    @Test
    fun `a listed attachment whose file has gone is a 404`() {
        val gone = ConnectMessageCardLoader.forMessage(MESSAGE_ID, mapOf("index.html" to File(folder, "deleted")))!!

        assertEquals(404, gone.respond(gone.entryUrl, "GET").statusCode)
    }

    @Test
    fun `only messages from the card's own origin count`() {
        assertTrue(loader.isOrigin(Uri.parse(loader.origin)))
        assertFalse(loader.isOrigin(Uri.parse("https://example.com")))
        assertFalse(loader.isOrigin(Uri.parse("http://" + loader.host)))
        assertFalse(loader.isOrigin(Uri.parse("null")))
    }

    @Test
    fun `the policy blocks every network directive the spec lists`() {
        val policy = ConnectMessageCardLoader.CONTENT_SECURITY_POLICY
        for (directive in listOf(
            "default-src 'self'", "connect-src 'self'", "frame-src 'none'", "child-src 'none'",
            "worker-src 'none'", "object-src 'none'", "form-action 'none'", "base-uri 'none'",
        )) {
            assertTrue(directive, policy.contains(directive))
        }
    }

    private fun resolve(
        path: String,
        method: String = "GET",
    ) = ConnectMessageCardLoader.resolve(loader.origin + path, method, loader.host, files.keys)

    private fun assertSandboxHeaders(headers: Map<String, String>) {
        assertEquals(ConnectMessageCardLoader.CONTENT_SECURITY_POLICY, headers["Content-Security-Policy"])
        assertEquals("nosniff", headers["X-Content-Type-Options"])
        assertEquals("no-store", headers["Cache-Control"])
    }

    private fun write(
        name: String,
        text: String,
    ): File {
        folder.mkdirs()
        return File(folder, name).apply { writeText(text) }
    }

    private companion object {
        const val MESSAGE_ID = "3F2A6C1E-0000-4000-8000-00000000ABCD"
    }
}
