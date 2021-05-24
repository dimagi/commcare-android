package org.commcare.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase
import org.commcare.CommCareTestApplication
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
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
        every { mockContext.contentResolver.getType(any()) } returns null
        mediaCursor.setColumnNames(MEDIA_COLUMN)

        // Unfortunately, MimeTypeMap.getSingleton() inside FileUtils returns a shadow implementation
        // And I'm not sure how to make robolectric use the actual implementation rather than a shadow.
        Shadows.shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypMapping("jpg", "image/jpeg")
    }

    @Test
    fun testContentTools() {
        Assert.assertFalse(FileUtil.isContentUri("/path/to/data"))
        Assert.assertFalse(FileUtil.isContentUri("file:///path/to/data"))
        TestCase.assertTrue(FileUtil.isContentUri("content://org.rdtoolkit.fileprovider/session_media_data/86f9bfa5-6144-4466-92e3-dc30eb3d8c85/20210420_140316_cropped.jpg"))
    }

    @Test
    fun getFileName_withNullCursorAndImageType_returnsUriSegment() {
        every { mockContext.contentResolver.query(any(), any(), any(), any(), any()) } returns null
        every { mockContext.contentResolver.getType(any()) } returns "image/jpeg"
        val fileName = FileUtil.getFileName(mockContext, Uri.parse("any"))
        Assert.assertEquals(fileName, "any.jpg")
    }

    @Test(expected = FileExtensionNotFoundException::class)
    fun getFileName_withNullCursorAndNullType_throwsException() {
        every { mockContext.contentResolver.query(any(), any(), any(), any(), any()) } returns null
        val fileName = FileUtil.getFileName(mockContext, Uri.parse("any"))
    }

    @Test
    fun getFileName_whenCursorHasFileExtension_shouldHaveExtension() {
        mediaCursor.setResults(arrayOf(MEDIA_WITH_EXT))
        val fileName = FileUtil.getFileName(mockContext, Uri.parse("any"))
        val extension = FileUtil.getExtension(fileName)
        Assert.assertEquals(extension, "jpg")
        Assert.assertEquals(fileName, "file_with_extension.jpg")
    }

    @Test
    fun getFileName_whenCursorHasMimeType_shouldHaveExtension() {
        mediaCursor.setResults(arrayOf(MEDIA_WITH_MIME_TYPE))
        val fileName = FileUtil.getFileName(mockContext, Uri.parse("any"))
        val extension = FileUtil.getExtension(fileName)
        Assert.assertEquals(extension, "jpg")
        Assert.assertEquals(fileName, "file_without_extension.jpg")
    }

    @Test(expected = FileExtensionNotFoundException::class)
    fun getFileName_whenCursorHasNoFileExtension_throwsException() {
        mediaCursor.setResults(arrayOf(MEDIA_WITHOUT_EXT_OR_TYPE))
        FileUtil.getFileName(mockContext, Uri.parse("any"))
    }

    companion object MockMediaData {
        private val MEDIA_COLUMN = listOf(
                OpenableColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE
        )
        private val MEDIA_WITH_EXT = arrayOf(
                "file_with_extension.jpg",
                null
        )
        private val MEDIA_WITH_MIME_TYPE = arrayOf(
                "file_without_extension",
                "image/jpeg"
        )
        private val MEDIA_WITHOUT_EXT_OR_TYPE = arrayOf(
                "file_without_extension",
                null
        )
    }
}
