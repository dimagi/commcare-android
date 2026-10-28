package org.commcare.connect.database

import android.content.Context
import java.io.File
import java.io.IOException

object ConnectMessagingAttachmentFileStore {
    private const val DIRECTORY_NAME = "connect_messaging_attachments"
    private const val PARTIAL_FILE_SUFFIX = ".part"

    fun directory(context: Context): File = File(context.filesDir, DIRECTORY_NAME)

    @JvmStatic
    fun fileFor(
        context: Context,
        attachmentId: String,
    ): File = File(directory(context), attachmentId)

    @Throws(IOException::class)
    fun write(
        context: Context,
        attachmentId: String,
        contents: ByteArray,
    ) {
        val directory = directory(context)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create attachment directory $directory")
        }
        val partialFile = File(directory, attachmentId + PARTIAL_FILE_SUFFIX)
        partialFile.writeBytes(contents)
        val completeFile = fileFor(context, attachmentId)
        if (!partialFile.renameTo(completeFile)) {
            partialFile.delete()
            throw IOException("Could not move $partialFile to $completeFile")
        }
    }

    @JvmStatic
    fun deleteAll(context: Context) {
        directory(context).deleteRecursively()
    }
}
