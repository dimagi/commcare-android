package org.commcare.mediadownload

interface MissingMediaDownloadListener {
    fun onComplete(result: MissingMediaDownloadResult)
}