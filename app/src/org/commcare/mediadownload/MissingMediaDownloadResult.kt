package org.commcare.mediadownload

sealed class MissingMediaDownloadResult(val message : String?) {
    object Success : MissingMediaDownloadResult("")
    class Error(val error: String) : MissingMediaDownloadResult(error)
    object InProgress : MissingMediaDownloadResult("")
    class Exception(val e : java.lang.Exception) : MissingMediaDownloadResult(e.message)
}