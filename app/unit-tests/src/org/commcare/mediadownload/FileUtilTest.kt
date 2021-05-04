package org.commcare.mediadownload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.commcare.CommCareTestApplication
import org.commcare.utils.FileUtil
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboCursor

/**
 * @author $|-|!˅@M
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class FileUtilTest {

    private lateinit var mediaCursor: RoboCursor
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mediaCursor = RoboCursor()
        mockContext = mockk()
        every { mockContext.contentResolver.query(any(), any(), any(), any(), any()) } returns mediaCursor
        mediaCursor.setColumnNames(MEDIA_COLUMN)
    }

    @Test
    fun getFileName_withNullCursor_returnsUriSegment() {
        every { mockContext.contentResolver.query(any(), any(), any(), any(), any()) } returns null
        val fileName = FileUtil.getContentFileName(Uri.parse("any"), mockContext)
        Assert.assertEquals(fileName, "any")
    }

    @Test
    fun getFileName_withValidCursor_shouldHaveExtension() {
        mediaCursor.setResults(arrayOf(MEDIA_WITH_EXT))
        var fileName = FileUtil.getContentFileName(Uri.parse("any"), mockContext)
        var extension = FileUtil.getExtension(fileName)
        Assert.assertEquals(extension, "mp3")

        mediaCursor.setResults(arrayOf(MEDIA_WITHOUT_EXT))
        fileName = FileUtil.getContentFileName(Uri.parse("any"), mockContext)
        extension = FileUtil.getExtension(fileName)
        Assert.assertEquals(extension, "")
    }

    companion object MockMediaData {
        private val MEDIA_COLUMN = listOf (
                OpenableColumns.DISPLAY_NAME
        )
        private val MEDIA_WITH_EXT = arrayOf(
                "file_with_extension.mp3"
        )
        private val MEDIA_WITHOUT_EXT = arrayOf(
                "file_without_extension"
        )
    }
}
