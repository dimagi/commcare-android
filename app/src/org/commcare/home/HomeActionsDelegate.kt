package org.commcare.home

import android.content.Intent
import android.widget.AdapterView
import android.widget.Toast
import org.commcare.CommCareApplication
import org.commcare.activities.CommCarePreferenceActivity
import org.commcare.activities.FormRecordListActivity
import org.commcare.activities.ReportProblemActivity
import org.commcare.activities.SessionAwarePreferenceActivity
import org.commcare.android.database.user.models.FormRecord
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.preferences.AdvancedActionsPreferences
import org.commcare.preferences.DeveloperPreferences
import org.commcare.preferences.MainConfigurablePreferences
import org.commcare.utils.ChangeLocaleUtil
import org.commcare.utils.ConnectivityStatus
import org.commcare.views.dialogs.DialogChoiceItem
import org.commcare.views.dialogs.DialogCreationHelpers
import org.commcare.views.dialogs.PaneledChoiceDialog
import org.commcare.views.notifications.NotificationMessageFactory
import org.javarosa.core.services.locale.Localization
import java.io.File

/**
 * The home screen's actions, each paired with an availability query. The host decides where each
 * one is surfaced: overflow menu, on-screen button or drawer entry. Handles the results of the
 * Settings and Advanced Actions screens it opens.
 */
class HomeActionsDelegate(
    private val host: HomeActivityHost,
    private val sync: SyncDelegate,
    private val appUpdate: AppUpdateDelegate,
    private val pin: PinDelegate,
) {
    private var aboutDialogDismissals = 0

    /** Send unsent forms, then pull data, at the user's request. */
    fun sync() = sync.sendFormsOrSync(true)

    /**
     * [sync] when there is a network. Without one, says why not and posts a notification instead.
     * Returns whether the sync started.
     */
    fun syncIfOnline(): Boolean {
        val context = host.hostContext
        if (!ConnectivityStatus.isNetworkAvailable(context)) {
            reportNoConnection(ConnectivityStatus.isAirplaneModeOn(context))
            return false
        }
        CommCareApplication.notificationManager().clearNotifications(AIRPLANE_MODE_CATEGORY)
        sync()
        return true
    }

    private fun reportNoConnection(airplaneMode: Boolean) {
        val (messageKey, stockMessage) =
            if (airplaneMode) {
                "notification.sync.airplane.action" to NotificationMessageFactory.StockMessages.Sync_AirplaneMode
            } else {
                "notification.sync.connections.action" to NotificationMessageFactory.StockMessages.Sync_NoConnections
            }
        sync.handleSyncNotAttempted(Localization.get(messageKey))
        CommCareApplication.notificationManager().reportNotificationMessage(
            NotificationMessageFactory.message(stockMessage, AIRPLANE_MODE_CATEGORY),
        )
        FirebaseAnalyticsUtil.reportSyncResult(
            false,
            AnalyticsParamValue.SYNC_TRIGGER_USER,
            AnalyticsParamValue.SYNC_MODE_SEND_FORMS,
            AnalyticsParamValue.SYNC_FAIL_NO_CONNECTION,
        )
    }

    fun canViewSavedForms(): Boolean = host.areAppActionsAvailable()

    fun viewSavedForms() = showFormArchive(incomplete = false)

    /** Open the saved forms, or only the incomplete ones, optionally starting at [record]. */
    @JvmOverloads
    fun showFormArchive(
        incomplete: Boolean,
        record: FormRecord? = null,
    ) {
        FirebaseAnalyticsUtil.reportViewArchivedFormsList(incomplete)
        val intent = Intent(host.hostContext, FormRecordListActivity::class.java)
        if (incomplete) {
            intent.putExtra(FormRecord.META_STATUS, FormRecord.STATUS_INCOMPLETE)
        }
        if (record != null) {
            intent.putExtra(FormRecordListActivity.KEY_INITIAL_RECORD_ID, record.id)
        }
        host.startActivityForResult(intent, HomeRequestCodes.GET_INCOMPLETE_FORM)
    }

    /** Demo users keep the language even though other app actions are withheld from them. */
    fun canChangeLanguage(): Boolean = host.isDemoUser() || host.areAppActionsAvailable()

    fun changeLanguage() {
        val dialog = PaneledChoiceDialog(host.hostContext, Localization.get("home.menu.locale.select"))
        val listClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val localeCodes = ChangeLocaleUtil.getLocaleCodes()
                if (position >= localeCodes.size) {
                    Localization.setLocale("default")
                } else {
                    val selectedLocale = localeCodes[position]
                    Localization.setLocale(selectedLocale)
                    MainConfigurablePreferences.setCurrentLocale(selectedLocale)
                }
                host.onLocaleChanged()
                host.refreshActionSurface()
                dialog.dismiss()
            }
        val choices = ChangeLocaleUtil.getLocaleNames().map { DialogChoiceItem.nonListenerItem(it) }
        dialog.setChoiceItems(choices.toTypedArray(), listClickListener)
        host.showAlertDialog(dialog)
    }

    fun canOpenSettings(): Boolean = host.areAppActionsAvailable()

    fun openSettings() =
        host.startActivityForResult(
            SessionAwarePreferenceActivity.buildCommCareSettingsIntent(host.hostContext),
            HomeRequestCodes.PREFERENCES_ACTIVITY,
        )

    fun canOpenAdvanced(): Boolean = host.areAppActionsAvailable()

    fun openAdvanced() {
        val intent =
            Intent(host.hostContext, SessionAwarePreferenceActivity::class.java)
                .putExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE, CommCarePreferenceActivity.PREF_TYPE_ADVANCED_ACTIONS)
        host.startActivityForResult(intent, HomeRequestCodes.ADVANCED_ACTIONS_ACTIVITY)
    }

    fun canShowAbout(): Boolean = host.areAppActionsAvailable()

    /** Show the About dialog. Dismissing it for the fourth time turns on the developer options. */
    fun showAbout() {
        val dialog = DialogCreationHelpers.buildAboutCommCareDialog(host.hostActivity)
        dialog.makeCancelable()
        dialog.setOnDismissListener { countAboutDialogDismissal() }
        host.showAlertDialog(dialog)
    }

    fun canSetPin(): Boolean = pin.canSetPin()

    fun setPin() = pin.setPin()

    fun canUpdateApp(): Boolean = host.areAppActionsAvailable()

    fun updateApp() = appUpdate.launchUpdateActivity(false)

    fun canUpdateCommCare(): Boolean = host.areAppActionsAvailable() && appUpdate.showCommCareUpdateMenu

    fun updateCommCare() = appUpdate.startCommCareUpdate()

    /** Unless the app hides issue reports. */
    fun canReportProblem(): Boolean = host.areAppActionsAvailable() && !DeveloperPreferences.shouldHideReportIssue()

    fun reportProblem() = host.startActivity(Intent(host.hostContext, ReportProblemActivity::class.java))

    /**
     * Handle the results of the Settings and Advanced Actions screens.
     *
     * @return true when [requestCode] belonged to one of them
     */
    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        intent: Intent?,
    ): Boolean {
        when (requestCode) {
            HomeRequestCodes.PREFERENCES_ACTIVITY -> handlePreferencesResult(resultCode)
            HomeRequestCodes.ADVANCED_ACTIONS_ACTIVITY -> handleAdvancedActionsResult(resultCode, intent)
            else -> return false
        }
        return true
    }

    private fun handlePreferencesResult(resultCode: Int) {
        if (resultCode == HomeResultCodes.RESULT_DATA_RESET) {
            host.finish()
        } else if (resultCode == DeveloperPreferences.RESULT_SYNC_CUSTOM) {
            performCustomRestore()
        }
    }

    private fun performCustomRestore() {
        val failureMessageKey =
            try {
                val filePath = DeveloperPreferences.getCustomRestoreDocLocation()
                when {
                    filePath.isNullOrEmpty() -> {
                        "custom.restore.file.not.set"
                    }

                    !File(filePath).exists() -> {
                        "custom.restore.file.not.exist"
                    }

                    else -> {
                        sync.performCustomRestoreFromFile(File(filePath))
                        null
                    }
                }
            } catch (e: Exception) {
                "custom.restore.error"
            }
        failureMessageKey?.let { Toast.makeText(host.hostContext, Localization.get(it), Toast.LENGTH_LONG).show() }
    }

    private fun handleAdvancedActionsResult(
        resultCode: Int,
        intent: Intent?,
    ) {
        if (resultCode != HomeResultCodes.RESULT_FORMS_PROCESSED) {
            return
        }
        val formProcessCount = intent!!.getIntExtra(AdvancedActionsPreferences.FORM_PROCESS_COUNT_KEY, 0)
        val localizationKey = intent.getStringExtra(AdvancedActionsPreferences.FORM_PROCESS_MESSAGE_KEY)
        host.displayToast(Localization.get(localizationKey, arrayOf(formProcessCount.toString())))
        host.refreshHostUi()
    }

    private fun countAboutDialogDismissal() {
        aboutDialogDismissals++
        if (aboutDialogDismissals == DEVELOPER_MODE_DISMISSALS) {
            DeveloperPreferences.setSuperuserEnabled(true)
            host.displayShortToast(Localization.get("home.developer.options.enabled"))
        }
    }

    private companion object {
        const val DEVELOPER_MODE_DISMISSALS = 4
        const val AIRPLANE_MODE_CATEGORY = "airplane-mode"
    }
}
