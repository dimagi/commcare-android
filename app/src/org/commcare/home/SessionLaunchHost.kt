package org.commcare.home

import android.content.Intent
import org.commcare.android.database.user.models.FormRecord
import org.javarosa.xpath.XPathException

/**
 * What [SessionLaunchDelegate] needs from the activity it launches form entry for, beyond the rest
 * of [HomeActivityHost].
 */
interface SessionLaunchHost {
    /** The intent the host was launched with, which says whether to exit once a form is done. */
    val launchIntent: Intent?

    /** The header form entry shows for a form started from the session. */
    val formEntryTitle: String?

    /** Advance the session navigator to its next step. */
    fun startNextSessionStep()

    /** Like [startNextSessionStep], but steps back with an error when a fixture fails to load. */
    fun startNextSessionStepSafe()

    /** Open the saved forms list, or the incomplete one, with [record] selected when given. */
    fun showFormArchive(
        incomplete: Boolean,
        record: FormRecord?,
    )

    /** Finish the host with [resultCode], handing control back to whoever launched it. */
    fun finishWithResult(resultCode: Int)

    /** Show [exception] from ending a session, then close the host. */
    fun showXPathErrorAndExit(exception: XPathException)

    /** Form entry is about to start, so the host holds off background syncs until it next resumes. */
    fun onFormEntryLaunching()
}
