package org.commcare.fragments.connectMessaging

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

object ConnectMessageTestImages {
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

    fun writePng(
        file: File,
        storedWidth: Int,
        storedHeight: Int,
        exifOrientation: Int = ExifInterface.ORIENTATION_NORMAL,
    ): File {
        DataOutputStream(file.outputStream()).use { out ->
            out.write(PNG_SIGNATURE)
            writeChunk(
                out,
                "IHDR",
                ByteArrayOutputStream()
                    .also {
                        DataOutputStream(it).apply {
                            writeInt(storedWidth)
                            writeInt(storedHeight)
                            write(byteArrayOf(8, 0, 0, 0, 0))
                        }
                    }.toByteArray(),
            )
            val pixels = ByteArrayOutputStream()
            DeflaterOutputStream(pixels).use { deflater ->
                repeat(storedHeight) { deflater.write(ByteArray(storedWidth + 1)) }
            }
            writeChunk(out, "IDAT", pixels.toByteArray())
            writeChunk(out, "IEND", ByteArray(0))
        }
        if (exifOrientation != ExifInterface.ORIENTATION_NORMAL) {
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
                saveAttributes()
            }
        }
        return file
    }

    private fun writeChunk(
        out: DataOutputStream,
        type: String,
        data: ByteArray,
    ) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.writeInt(data.size)
        out.write(typeBytes)
        out.write(data)
        out.writeInt(CRC32().apply { update(typeBytes + data) }.value.toInt())
    }
}
