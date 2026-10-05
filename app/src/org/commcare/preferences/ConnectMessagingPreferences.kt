package org.commcare.preferences

import android.content.Context
import androidx.core.content.edit

object ConnectMessagingPreferences {
    private const val PREF_NAME = "connect_messaging_prefs"
    private const val KEY_ATTACHMENT_AUTO_DOWNLOAD = "attachment_auto_download"

    enum class AttachmentAutoDownload(
        val value: String,
    ) {
        ANY_NETWORK("any_network"),
        LARGE_ON_WIFI_ONLY("large_on_wifi_only"),
        MANUAL_ONLY("manual_only"),
    }

    @JvmStatic
    fun getAttachmentAutoDownload(context: Context): AttachmentAutoDownload {
        val stored =
            context
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ATTACHMENT_AUTO_DOWNLOAD, null)
        return AttachmentAutoDownload.values().firstOrNull { it.value == stored }
            ?: AttachmentAutoDownload.ANY_NETWORK
    }

    @JvmStatic
    fun setAttachmentAutoDownload(
        context: Context,
        setting: AttachmentAutoDownload,
    ) {
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_ATTACHMENT_AUTO_DOWNLOAD, setting.value) }
    }

    @JvmStatic
    fun isAutomaticDownloadEnabled(context: Context): Boolean =
        getAttachmentAutoDownload(context) != AttachmentAutoDownload.MANUAL_ONLY

    @JvmStatic
    fun clear(context: Context) {
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { clear() }
    }
}
