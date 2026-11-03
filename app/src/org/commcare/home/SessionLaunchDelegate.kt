package org.commcare.home

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.DialogInterface
import android.content.Intent
import android.util.Base64
import android.widget.Toast
import org.commcare.CommCareApplication
import org.commcare.activities.DispatchActivity.EXIT_AFTER_FORM_SUBMISSION
import org.commcare.activities.DispatchActivity.EXIT_AFTER_FORM_SUBMISSION_DEFAULT
import org.commcare.activities.FormEntryActivity
import org.commcare.activities.components.FormEntryConstants
import org.commcare.activities.components.FormEntryInstanceState
import org.commcare.activities.components.FormEntrySessionWrapper
import org.commcare.android.database.user.models.FormRecord
import org.commcare.android.database.user.models.SessionStateDescriptor
import org.commcare.models.AndroidSessionWrapper
import org.commcare.preferences.DevSessionRestorer
import org.commcare.preferences.DeveloperPreferences
import org.commcare.preferences.HiddenPreferences
import org.commcare.tasks.FormLoaderTask
import org.commcare.tasks.FormRecordCleanupTask
import org.commcare.util.LogTypes
import org.commcare.utils.AndroidInstanceInitializer
import org.commcare.utils.GlobalConstants
import org.commcare.views.dialogs.StandardAlertDialog
import org.javarosa.core.services.Logger
import org.javarosa.core.services.locale.Localization
import org.javarosa.xpath.XPathException

/**
 * Starts form entry for a home activity and handles what form entry hands back: forms started from
 * the session, reopened incomplete forms, the forms and sessions restored at login, and the result
 * of each.
 *
 * The launch state it reads, `wasExternal`, belongs to [HomeActivityCoordinator] and is passed in.
 */
class SessionLaunchDelegate(
    private val host: HomeActivityHost,
) {
    private var restoringSession = false

    /** Launch step: open the form the app asks to show after an update. */
    fun showUpdateInfoForm(): Boolean {
        if (!HiddenPreferences.shouldShowXformUpdateInfo()) {
            return false
        }
        HiddenPreferences.setShowXformUpdateInfo(false)
        val updateInfoFormXmlns = CommCareApplication.instance().commCarePlatform.updateInfoFormXmlns
        if (updateInfoFormXmlns.isNullOrEmpty()) {
            return false
        }
        val session = CommCareApplication.instance().currentSession
        val formEntry = session.getEntryForNameSpace(updateInfoFormXmlns) ?: return false
        session.setCommand(formEntry.commandID)
        host.startNextSessionStepSafe()
        return true
    }

    /** Launch step, and the answer to a form that reports it was interrupted: reopen that form. */
    fun tryRestoringFormFromSessionExpiration(): Boolean {
        val existing = AndroidSessionWrapper.getFormStateForInterruptedUserSession() ?: return false
        val state = CommCareApplication.instance().currentSessionWrapper
        state.loadFromStateDescription(existing)

        val formRecord = state.formRecord
        Logger.log(
            LogTypes.TYPE_FORM_ENTRY,
            "Restoring form from expired Session |" +
                (if (formRecord.instanceID == null) "" else formRecord.instanceID + "|") +
                formRecord.formNamespace,
        )
        launchFormEntry(
            CommCareApplication.instance().commCarePlatform.getFormDefId(state.session.form),
            state.formRecord,
            null,
            true,
        )
        return true
    }

    /** Launch step: resume a session a previous user session saved, for debugging. */
    fun tryRestoringSession(): Boolean {
        if (CommCareApplication.instance().currentSession.command == null) {
            return false
        }
        restoringSession = true
        host.startNextSessionStep()
        return true
    }

    /**
     * Create (or re-use) a form record and start form entry on it. If an incomplete form already
     * uses the same case, ask the user whether to continue that one instead.
     */
    fun startForm(state: AndroidSessionWrapper) {
        if (state.formRecordId == -1) {
            if (HiddenPreferences.isIncompleteFormsEnabled()) {
                val existing = state.existingIncompleteCaseDescriptor
                if (existing != null) {
                    askToContinueIncompleteForm(state, existing)
                    return
                }
            }
            state.commitStub()
        } else {
            Logger.log(LogTypes.TYPE_FORM_ENTRY, "Somehow ended up starting form entry with old state?")
        }

        val record = state.formRecord
        launchFormEntry(
            CommCareApplication.instance().commCarePlatform.getFormDefId(record.formNamespace),
            record,
            host.formEntryTitle,
            false,
        )
    }

    /**
     * Handle the user's return from the incomplete forms list.
     *
     * @return whether the host should continue session navigation
     */
    fun handleIncompleteFormResult(
        resultCode: Int,
        intent: Intent?,
    ): Boolean {
        if (resultCode == RESULT_CANCELED) {
            host.refreshHostUi()
            return false
        }
        if (resultCode != RESULT_OK) {
            return true
        }
        val recordId = intent!!.getIntExtra("FORMRECORDS", -1)
        if (recordId == -1) {
            return true
        }
        val record = CommCareApplication.instance().getUserStorage(FormRecord::class.java).read(recordId)

        val ssdStorage = CommCareApplication.instance().getUserStorage(SessionStateDescriptor::class.java)
        val ssds = ssdStorage.getIDsForValue(SessionStateDescriptor.META_FORM_RECORD_ID, record.id)
        val currentState = CommCareApplication.instance().currentSessionWrapper
        if (ssds.size == 1) {
            currentState.loadFromStateDescription(ssdStorage.read(ssds.firstElement()))
        } else {
            currentState.setFormRecordId(record.id)
        }

        Logger.log(
            LogTypes.TYPE_FORM_ENTRY,
            "Loading an incomplete form |" +
                (if (record.instanceID == null) "" else record.instanceID + "|") +
                record.formNamespace,
        )
        launchFormEntry(CommCareApplication.instance().commCarePlatform.getFormDefId(record.formNamespace), record)
        return false
    }

    /**
     * Handle the user's return from form entry: clean up after a cancelled form, and end the session
     * where the form leaves it.
     */
    fun handleFormEntryResult(
        resultCode: Int,
        intent: Intent?,
        wasExternal: Boolean,
    ): FormEntryOutcome {
        if (intent != null && intent.getBooleanExtra(FormEntryConstants.WAS_INTERRUPTED, false)) {
            tryRestoringFormFromSessionExpiration()
            return FormEntryOutcome(continueSessionNav = false)
        }

        val currentState = CommCareApplication.instance().currentSessionWrapper
        val current = currentState.formRecord

        if (current == null) {
            Toast.makeText(host.hostContext, "Error while trying to save the form!", Toast.LENGTH_LONG).show()
            Logger.log(LogTypes.TYPE_ERROR_WORKFLOW, "Form Entry couldn't save because of corrupt state.")
            clearSessionAndExit(currentState, true, wasExternal)
            return FormEntryOutcome(continueSessionNav = false)
        }

        if (intent?.getBooleanExtra(FormEntryConstants.IS_ARCHIVED_FORM, false) == true ||
            FormRecord.STATUS_COMPLETE == current.status ||
            FormRecord.STATUS_SAVED == current.status
        ) {
            currentState.reset()
            if (exitFromExternalLaunch(wasExternal) ||
                intent?.getBooleanExtra(FormEntryActivity.KEY_IS_RESTART_AFTER_EXPIRATION, false) == true
            ) {
                host.finishWithResult(RESULT_CANCELED)
            } else {
                host.showFormArchive(false, current)
            }
            return FormEntryOutcome(continueSessionNav = false)
        }

        if (resultCode == RESULT_OK) {
            val complete =
                FormRecord.STATUS_COMPLETE == current.status || FormRecord.STATUS_UNSENT == current.status
            if (!complete) {
                clearSessionAndExit(currentState, false, wasExternal)
                return FormEntryOutcome(continueSessionNav = false)
            }
            host.refreshHostUi()

            if (exitFromExternalLaunch(wasExternal)) {
                currentState.reset()
                host.finishWithResult(RESULT_CANCELED)
                return FormEntryOutcome(continueSessionNav = false, formCompleted = true)
            }

            val terminateSuccessful =
                try {
                    currentState.terminateSession()
                } catch (e: XPathException) {
                    host.showXPathErrorAndExit(e)
                    return FormEntryOutcome(continueSessionNav = false, formCompleted = true)
                }
            return FormEntryOutcome(continueSessionNav = terminateSuccessful, formCompleted = true)
        } else if (resultCode == RESULT_CANCELED) {
            Logger.log(LogTypes.TYPE_FORM_ENTRY, "Form Entry Cancelled")

            if (current.status == FormRecord.STATUS_UNSTARTED) {
                FormRecordCleanupTask.wipeRecord(currentState)
            }

            if (exitFromExternalLaunch(wasExternal)) {
                currentState.reset()
                host.finishWithResult(RESULT_CANCELED)
                return FormEntryOutcome(continueSessionNav = false)
            } else if (current.status == FormRecord.STATUS_INCOMPLETE &&
                intent != null &&
                !intent.getBooleanExtra(FormEntryActivity.KEY_IS_RESTART_AFTER_EXPIRATION, false)
            ) {
                currentState.reset()
                host.showFormArchive(true, current)
                return FormEntryOutcome(continueSessionNav = false)
            } else {
                currentState.session.stepBack(currentState.evaluationContext)
                currentState.setFormRecordId(-1)
            }
        }
        return FormEntryOutcome(continueSessionNav = true)
    }

    /** Reset the session, finish the host if an external launch asked to exit, and refresh home. */
    fun clearSessionAndExit(
        currentState: AndroidSessionWrapper,
        shouldWarnUser: Boolean,
        wasExternal: Boolean,
    ) {
        currentState.reset()
        if (exitFromExternalLaunch(wasExternal)) {
            host.finishWithResult(RESULT_CANCELED)
        }
        host.refreshHostUi()
        if (shouldWarnUser) {
            host.showAlertDialog(
                StandardAlertDialog.getBasicAlertDialog(
                    Localization.get("session.refresh.error.title"),
                    Localization.get("session.refresh.error.message"),
                    null,
                ),
            )
        }
    }

    /** Whether an external launch asked to exit after its form. The ask is consumed on the first check. */
    private fun exitFromExternalLaunch(wasExternal: Boolean): Boolean {
        val intent = host.launchIntent
        val exitAfterFormSubmission =
            intent?.getBooleanExtra(EXIT_AFTER_FORM_SUBMISSION, EXIT_AFTER_FORM_SUBMISSION_DEFAULT) == true
        intent?.removeExtra(EXIT_AFTER_FORM_SUBMISSION)
        return wasExternal && exitAfterFormSubmission
    }

    private fun askToContinueIncompleteForm(
        state: AndroidSessionWrapper,
        existing: SessionStateDescriptor,
    ) {
        val platform = CommCareApplication.instance().commCarePlatform
        val dialog =
            StandardAlertDialog(
                Localization.get("app.workflow.incomplete.continue.title"),
                Localization.get("app.workflow.incomplete.continue"),
            )
        val listener =
            DialogInterface.OnClickListener { shown, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        state.loadFromStateDescription(existing)
                        launchFormEntry(platform.getFormDefId(state.session.form), state.formRecord)
                    }

                    DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_NEUTRAL -> {
                        if (which == DialogInterface.BUTTON_NEGATIVE) {
                            FormRecordCleanupTask.wipeRecord(existing)
                        }
                        state.commitStub()
                        launchFormEntry(platform.getFormDefId(state.session.form), state.formRecord)
                    }
                }
                shown.dismiss()
            }
        dialog.setPositiveButton(Localization.get("option.yes"), listener)
        dialog.setNegativeButton(Localization.get("app.workflow.incomplete.continue.option.delete"), listener)
        dialog.setNeutralButton(Localization.get("option.no"), listener)
        host.showAlertDialog(dialog)
    }

    private fun launchFormEntry(
        formDefId: Int,
        record: FormRecord,
        headerTitle: String? = null,
        isRestartAfterSessionExpiration: Boolean = false,
    ) {
        host.onFormEntryLaunching()

        Logger.log(
            LogTypes.TYPE_FORM_ENTRY,
            "Form Entry Starting|" +
                (if (record.instanceID == null) "" else record.instanceID + "|") +
                record.formNamespace,
        )

        FormLoaderTask.iif = AndroidInstanceInitializer(CommCareApplication.instance().currentSession)

        val intent =
            Intent(host.hostContext.applicationContext, FormEntryActivity::class.java).apply {
                action = Intent.ACTION_EDIT
                putExtra(
                    FormEntryInstanceState.KEY_FORM_RECORD_DESTINATION,
                    CommCareApplication.instance().currentApp.fsPath(GlobalConstants.FILE_CC_FORMS),
                )
                if (!record.filePath.isNullOrEmpty()) {
                    putExtra(FormEntryActivity.KEY_FORM_RECORD_ID, record.id)
                } else {
                    putExtra(FormEntryActivity.KEY_FORM_DEF_ID, formDefId)
                }
                putExtra(FormEntryActivity.KEY_RESIZING_ENABLED, HiddenPreferences.getResizeMethod())
                putExtra(FormEntryActivity.KEY_INCOMPLETE_ENABLED, HiddenPreferences.isIncompleteFormsEnabled())
                putExtra(FormEntryActivity.KEY_AES_STORAGE_KEY, Base64.encodeToString(record.aesKey, Base64.DEFAULT))
                putExtra(
                    FormEntrySessionWrapper.KEY_RECORD_FORM_ENTRY_SESSION,
                    DeveloperPreferences.isSessionSavingEnabled(),
                )
                putExtra(FormEntryActivity.KEY_IS_RESTART_AFTER_EXPIRATION, isRestartAfterSessionExpiration)
                if (headerTitle != null) {
                    putExtra(FormEntryActivity.KEY_HEADER_STRING, headerTitle)
                }
                if (restoringSession) {
                    restoringSession = false
                    val formEntrySession =
                        CommCareApplication
                            .instance()
                            .currentApp.appPreferences
                            .getString(DevSessionRestorer.CURRENT_FORM_ENTRY_SESSION, "")
                    if (!formEntrySession.isNullOrEmpty()) {
                        putExtra(FormEntrySessionWrapper.KEY_FORM_ENTRY_SESSION, formEntrySession)
                    }
                }
            }
        host.startActivityForResult(intent, FORM_ENTRY_REQUEST_CODE)
    }

    companion object {
        /** The request code form entry is started with; its result comes back through the host. */
        const val FORM_ENTRY_REQUEST_CODE = 4
    }
}

/**
 * What [SessionLaunchDelegate.handleFormEntryResult] leaves for the coordinator.
 *
 * @property continueSessionNav whether the host should continue session navigation; if false the
 * host stays home or has been finished
 * @property formCompleted a completed form was saved and is waiting to be sent
 */
data class FormEntryOutcome(
    val continueSessionNav: Boolean,
    val formCompleted: Boolean = false,
)
