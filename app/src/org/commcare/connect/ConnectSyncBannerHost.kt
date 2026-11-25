package org.commcare.connect

/** A Connect page that can show the banner a sync with nothing to send leaves. */
fun interface ConnectSyncBannerHost {
    /** @return whether the page showed [message] */
    fun showSyncUpToDate(message: String): Boolean
}
