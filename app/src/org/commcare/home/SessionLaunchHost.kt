package org.commcare.home

import android.content.Intent
import org.commcare.android.database.user.models.FormRecord
import org.javarosa.xpath.XPathException

/**
 * What [SessionLaunchDelegate] and [SessionNavigationDelegate] need from the activity they launch
 * screens for, beyond the rest of [HomeActivityHost].
 */
interface SessionLaunchHost {
    /** The intent the host was launched with, which says whether to exit once a form is done. */
    val launchIntent: Intent?

    /** The header form entry shows for a form started from the session. */
    val formEntryTitle: String?

    /** Open the saved forms list, or the incomplete one, with [record] selected when given. */
    fun showFormArchive(
        incomplete: Boolean,
        record: FormRecord?,
    )

    /** Finish the host with [resultCode] and [data], handing control back to whoever launched it. */
    fun finishWithResult(
        resultCode: Int,
        data: Intent? = null,
    )

    /** Log [exception] against the app and show it, closing the host on dismissal when [shouldExit]. */
    fun showXPathError(
        exception: XPathException,
        shouldExit: Boolean,
    )

    /** Show an error dialog, closing the host on dismissal when [shouldExit]. */
    fun showErrorDialog(
        message: String,
        shouldExit: Boolean,
    )
}
