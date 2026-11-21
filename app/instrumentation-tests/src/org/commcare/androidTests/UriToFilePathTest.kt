package org.commcare.androidTests

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.commcare.annotations.BrowserstackTests
import org.commcare.utils.UriToFilePath
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.NullPointerException

/**
 * @author $|-|!˅@M
 */
@BrowserstackTests
@RunWith(AndroidJUnit4::class)
class UriToFilePathTest {

    @Test(expected = NullPointerException::class)
    fun testDocumentMediaProviderUriCrashes() {
        val uri = Uri.parse("content://com.android.providers.media.documents/document/document:59845")
        UriToFilePath.getPathFromUri(InstrumentationRegistry.getInstrumentation().context, uri)
    }
}
