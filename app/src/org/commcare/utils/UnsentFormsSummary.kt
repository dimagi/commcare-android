package org.commcare.utils

import org.commcare.android.database.user.models.FormRecord
import org.commcare.models.database.SqlStorage

/**
 * How many forms of an app are waiting to be sent, and when the oldest of them last changed.
 */
data class UnsentFormsSummary(
    val unsentFormCount: Int,
    val oldestModifiedMillis: Long?,
) {
    companion object {
        /**
         * Reads only the metadata columns, so no form record is loaded or decrypted.
         */
        @JvmStatic
        fun readForApp(
            storage: SqlStorage<FormRecord>,
            appId: String,
        ): UnsentFormsSummary {
            storage.accessLock
                .query(
                    FormRecord.STORAGE_KEY,
                    arrayOf("COUNT(*)", "MIN(${FormRecord.META_LAST_MODIFIED})"),
                    "${FormRecord.META_STATUS} IN (?, ?) AND ${FormRecord.META_APP_ID} = ?",
                    arrayOf(FormRecord.STATUS_UNSENT, FormRecord.STATUS_COMPLETE, appId),
                    null,
                    null,
                    null,
                ).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        return UnsentFormsSummary(0, null)
                    }
                    return UnsentFormsSummary(
                        unsentFormCount = cursor.getInt(0),
                        oldestModifiedMillis = if (cursor.isNull(1)) null else cursor.getLong(1),
                    )
                }
        }
    }
}
