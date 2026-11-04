package org.commcare.home

import android.content.Intent
import android.widget.AdapterView
import org.commcare.activities.CommCarePreferenceActivity
import org.commcare.activities.FormRecordListActivity
import org.commcare.activities.SessionAwarePreferenceActivity
import org.commcare.android.database.user.models.FormRecord
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.preferences.DeveloperPreferences
import org.commcare.preferences.MainConfigurablePreferences
import org.commcare.utils.ChangeLocaleUtil
import org.commcare.views.dialogs.DialogChoiceItem
import org.commcare.views.dialogs.DialogCreationHelpers
import org.commcare.views.dialogs.PaneledChoiceDialog
import org.javarosa.core.services.locale.Localization

/**
 * The home screen's actions, each paired with an availability query. The host decides where each
 * one is surfaced: overflow menu, on-screen button or drawer entry.
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

    /** Always true: the language stays on offer to every user, demo users included. */
    fun canChangeLanguage(): Boolean = true

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
            HomeActivityCoordinator.buildPreferencesIntent(host.hostContext),
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

    private fun countAboutDialogDismissal() {
        aboutDialogDismissals++
        if (aboutDialogDismissals == DEVELOPER_MODE_DISMISSALS) {
            DeveloperPreferences.setSuperuserEnabled(true)
            host.displayShortToast(Localization.get("home.developer.options.enabled"))
        }
    }

    private companion object {
        const val DEVELOPER_MODE_DISMISSALS = 4
    }
}
