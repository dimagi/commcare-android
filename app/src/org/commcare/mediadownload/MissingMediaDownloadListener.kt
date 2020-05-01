package org.commcare.mediadownload

interface MissingMediaDownloadListener {
    fun onMediaDownloaded()

    fun onError(cause: Throwable?)
}