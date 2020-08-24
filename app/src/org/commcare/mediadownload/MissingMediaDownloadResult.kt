package org.commcare.mediadownload

// represents result of a missing media download request
sealed class MissingMediaDownloadResult(val message : String?) {
    object Success : MissingMediaDownloadResult("")
    object InProgress : MissingMediaDownloadResult("")
    class Error(val error: String) : MissingMediaDownloadResult(error)
}