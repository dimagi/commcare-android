package org.commcare.home

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.commcare.CommCareApplication
import org.commcare.activities.CommCarePreferenceActivity
import org.commcare.activities.CreatePinActivity
import org.commcare.activities.DriftHelper
import org.commcare.activities.FormRecordListActivity
import org.commcare.activities.LoginActivity
import org.commcare.activities.LoginMode
import org.commcare.activities.PinAuthenticationActivity
import org.commcare.activities.SessionAwarePreferenceActivity
import org.commcare.android.database.user.models.FormRecord
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.models.AndroidSessionWrapper
import org.commcare.preferences.DeveloperPreferences
import org.commcare.preferences.HiddenPreferences
import org.commcare.preferences.MainConfigurablePreferences
import org.commcare.utils.ChangeLocaleUtil
import org.commcare.views.dialogs.DialogChoiceItem
import org.commcare.views.dialogs.DialogCreationHelpers
import org.commcare.views.dialogs.PaneledChoiceDialog
import org.commcare.views.dialogs.StandardAlertDialog
import org.javarosa.core.services.locale.Localization

/**
 * Composition root for the behavior shared by every CommCare home screen.
 *
 * - Registers itself on the host lifecycle during init — before `ON_CREATE` is dispatched — so it
 * observes the host directly instead of being driven by forwarded calls.
 * - Owns the launch/nav instance state that describes *how the activity was launched*, persisting
 * it through the host's `SavedStateRegistry`.
 * - Runs the post-login launch checks in order, and exposes the home actions, each paired with an
 * availability query, for the host to surface wherever it likes.
 *
 * @property appUpdate owns the binary and content app updates. Session-independent, registered on the
 * host lifecycle.
 * @property sync owns form sending and data pulls; the host's task receiver.
 * @property sessionLaunch starts form entry and handles its result, and owns the form and session
 * restoration launch steps.
 * @property sessionExpiration reports session loss to the host. Driven by the session-aware activity
 * chain, not the lifecycle.
 */
class HomeActivityCoordinator(
    private val host: HomeActivityHost,
    session: SeatedAppSession,
    val appUpdate: AppUpdateDelegate = AppUpdateDelegate(host = host, session = session),
    val sync: SyncDelegate = SyncDelegate(host = host),
    val sessionLaunch: SessionLaunchDelegate = SessionLaunchDelegate(host = host),
    val sessionExpiration: SessionExpirationDelegate = SessionExpirationDelegate(host = host),
) : DefaultLifecycleObserver {
    constructor(host: HomeActivityHost) : this(host, ServiceBackedSession())

    private var restored = false

    private var externalLaunch = false
    private var loginExtraConsumed = false
    private var endpointNavPendingAfterSync = false

    private var aboutDialogDismissals = 0

    /** Activity was launched by an external app, so form submission may redispatch back to it. */
    var wasExternal: Boolean
        get() {
            ensureRestored()
            return externalLaunch
        }
        set(value) {
            ensureRestored()
            externalLaunch = value
        }

    /** The one-shot `START_FROM_LOGIN` extra has already driven the post-login launch checks. */
    var loginExtraWasConsumed: Boolean
        get() {
            ensureRestored()
            return loginExtraConsumed
        }
        set(value) {
            ensureRestored()
            loginExtraConsumed = value
        }

    /** A session-endpoint launch needs a blocking sync before navigating to the endpoint. */
    var pendingEndpointNavigationAfterSync: Boolean
        get() {
            ensureRestored()
            return endpointNavPendingAfterSync
        }
        set(value) {
            ensureRestored()
            endpointNavPendingAfterSync = value
        }

    init {
        host.lifecycle.addObserver(this)
        host.lifecycle.addObserver(appUpdate)
    }

    override fun onCreate(owner: LifecycleOwner) {
        ensureRestored()
    }

    /**
     * Fan an activity result out to the delegates that need it, and handle the PIN screens' results.
     *
     * @return true when the result was consumed and the host should not handle it further
     */
    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        intent: Intent?,
    ): Boolean {
        if (appUpdate.onActivityResult(requestCode, resultCode)) {
            return true
        }
        when (requestCode) {
            AUTHENTICATION_FOR_PIN_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    launchPinCreateScreen(LoginMode.PASSWORD)
                }
            }

            CREATE_PIN_REQUEST_CODE -> {
                handleCreatePinResult(resultCode, intent)
            }

            else -> {
                return false
            }
        }
        return true
    }

    /**
     * Run the post-login launch checks. The order is deliberate: demo mode skips everything else,
     * form and session restoration come before any sync that could invalidate them, a post-update
     * sync includes the plain form send, and the PIN and drift checks run only once nothing else
     * has taken over.
     *
     * @return true when a step claimed the launch by starting another screen or a blocking task
     */
    fun runLaunchChecks(): Boolean {
        if (host.isDemoUser()) {
            showDemoModeWarning()
            return false
        }
        if (sessionLaunch.showUpdateInfoForm()) {
            return true
        }
        if (sessionLaunch.tryRestoringFormFromSessionExpiration()) {
            return true
        }
        if (sessionLaunch.tryRestoringSession()) {
            return true
        }
        if (sync.runPostUpdateSyncIfNeeded()) {
            return true
        }
        if (sync.runPendingSyncRequestIfNeeded()) {
            return true
        }
        if (appUpdate.promptForUpdateIfNeeded(false)) {
            return true
        }
        checkForPinLaunchConditions()
        checkForDrift()
        return false
    }

    /**
     * Handle the user's return from form entry, and send the form it completed.
     *
     * @return whether the host should continue session navigation
     */
    fun handleFormEntryResult(
        resultCode: Int,
        intent: Intent?,
    ): Boolean {
        val outcome = sessionLaunch.handleFormEntryResult(resultCode, intent, wasExternal)
        if (outcome.formCompleted) {
            sync.startUnsentFormsTask(syncAfterwards = false, userTriggered = false)
        }
        return outcome.continueSessionNav
    }

    /** Reset the session, finishing the host if an external launch asked to exit. */
    fun clearSessionAndExit(
        currentState: AndroidSessionWrapper,
        shouldWarnUser: Boolean,
    ) = sessionLaunch.clearSessionAndExit(currentState, shouldWarnUser, wasExternal)

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
        host.startActivityForResult(intent, FORM_ARCHIVE_REQUEST_CODE)
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

    fun openSettings() = host.startActivityForResult(buildPreferencesIntent(host.hostContext), PREFERENCES_REQUEST_CODE)

    fun canOpenAdvanced(): Boolean = host.areAppActionsAvailable()

    fun openAdvanced() {
        val intent =
            Intent(host.hostContext, SessionAwarePreferenceActivity::class.java)
                .putExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE, CommCarePreferenceActivity.PREF_TYPE_ADVANCED_ACTIONS)
        host.startActivityForResult(intent, ADVANCED_ACTIONS_REQUEST_CODE)
    }

    fun canShowAbout(): Boolean = host.areAppActionsAvailable()

    /** Show the About dialog. Dismissing it for the fourth time turns on the developer options. */
    fun showAbout() {
        val dialog = DialogCreationHelpers.buildAboutCommCareDialog(host.hostActivity)
        dialog.makeCancelable()
        dialog.setOnDismissListener { countAboutDialogDismissal() }
        host.showAlertDialog(dialog)
    }

    fun canSetPin(): Boolean = host.areAppActionsAvailable() && DeveloperPreferences.shouldOfferPinForLogin()

    /** Set or change the PIN, once the user has re-entered their password. */
    fun setPin() =
        host.startActivityForResult(
            Intent(host.hostContext, PinAuthenticationActivity::class.java),
            AUTHENTICATION_FOR_PIN_REQUEST_CODE,
        )

    fun canUpdateApp(): Boolean = host.areAppActionsAvailable()

    fun updateApp() = appUpdate.launchUpdateActivity(false)

    fun canUpdateCommCare(): Boolean = host.areAppActionsAvailable() && appUpdate.showCommCareUpdateMenu

    fun updateCommCare() = appUpdate.startCommCareUpdate()

    private fun showDemoModeWarning() {
        val dialog =
            StandardAlertDialog.getBasicAlertDialogWithIcon(
                Localization.get("demo.mode.warning.title"),
                Localization.get("demo.mode.warning.main"),
                android.R.drawable.ic_dialog_info,
                null,
            )
        dialog.setEmphasizedMessage(Localization.get("demo.mode.warning.emphasized"))
        host.showAlertDialog(dialog)
    }

    /** Launch step: offer PIN creation after a primed or password login. */
    private fun checkForPinLaunchConditions() {
        val intent = host.launchIntent ?: return
        val loginMode = IntentCompat.getSerializableExtra(intent, LoginActivity.LOGIN_MODE, LoginMode::class.java)
        if (loginMode == LoginMode.PRIMED) {
            launchPinCreateScreen(loginMode)
        } else if (loginMode == LoginMode.PASSWORD && DeveloperPreferences.shouldOfferPinForLogin()) {
            val userManuallyEnteredPasswordMode = intent.getBooleanExtra(LoginActivity.MANUAL_SWITCH_TO_PW_MODE, false)
            val alreadyDismissedPinCreation =
                CommCareApplication
                    .instance()
                    .currentApp.appPreferences
                    .getBoolean(HiddenPreferences.HAS_DISMISSED_PIN_CREATION, false)
            if (!alreadyDismissedPinCreation || userManuallyEnteredPasswordMode) {
                showPinChoiceDialog(loginMode)
            }
        }
    }

    private fun showPinChoiceDialog(loginMode: LoginMode) {
        val hasPinSet = CommCareApplication.instance().recordForCurrentUser.hasPinSet()
        val promptMessage = Localization.get(if (hasPinSet) "pin.dialog.prompt.reset" else "pin.dialog.prompt.set")
        val dialog = PaneledChoiceDialog(host.hostContext, promptMessage)
        val createPinChoice =
            DialogChoiceItem(Localization.get("pin.dialog.yes"), -1) {
                dialog.dismiss()
                launchPinCreateScreen(loginMode)
            }
        val nextTimeChoice = DialogChoiceItem(Localization.get("pin.dialog.not.now"), -1) { dialog.dismiss() }
        val notAgainChoice =
            DialogChoiceItem(Localization.get("pin.dialog.never"), -1) {
                dialog.dismiss()
                CommCareApplication
                    .instance()
                    .currentApp.appPreferences
                    .edit()
                    .putBoolean(HiddenPreferences.HAS_DISMISSED_PIN_CREATION, true)
                    .apply()
                showPinFutureAccessDialog()
            }
        dialog.setChoiceItems(arrayOf(createPinChoice, nextTimeChoice, notAgainChoice))
        dialog.addCollapsibleInfoPane(Localization.get("pin.dialog.extra.info"))
        host.showAlertDialog(dialog)
    }

    private fun showPinFutureAccessDialog() {
        StandardAlertDialog
            .getBasicAlertDialog(
                Localization.get("pin.dialog.set.later.title"),
                Localization.get("pin.dialog.set.later.message"),
                null,
            ).showNonPersistentDialog(host.hostContext)
    }

    private fun launchPinCreateScreen(loginMode: LoginMode) {
        val intent =
            Intent(host.hostContext, CreatePinActivity::class.java)
                .putExtra(LoginActivity.LOGIN_MODE, loginMode)
        host.startActivityForResult(intent, CREATE_PIN_REQUEST_CODE)
    }

    private fun handleCreatePinResult(
        resultCode: Int,
        intent: Intent?,
    ) {
        val choseRememberPassword = intent?.getBooleanExtra(CreatePinActivity.CHOSE_REMEMBER_PASSWORD, false) == true
        if (choseRememberPassword) {
            CommCareApplication.instance().closeUserSession()
        } else {
            val messageKey = if (resultCode == RESULT_OK) "pin.set.success" else "pin.not.set"
            Toast.makeText(host.hostContext, Localization.get(messageKey), Toast.LENGTH_SHORT).show()
        }
    }

    /** Launch step: warn while the device clock drifts from the server's, at most once a day. */
    private fun checkForDrift() {
        if (DriftHelper.shouldShowDriftWarning() && DriftHelper.getCurrentDrift() != 0L) {
            host.showAlertDialog(DriftHelper.getDriftDialog(host.hostContext))
            DriftHelper.updateLastDriftWarningTime()
        }
    }

    private fun countAboutDialogDismissal() {
        aboutDialogDismissals++
        if (aboutDialogDismissals == DEVELOPER_MODE_DISMISSALS) {
            DeveloperPreferences.setSuperuserEnabled(true)
            Toast
                .makeText(host.hostContext, Localization.get("home.developer.options.enabled"), Toast.LENGTH_SHORT)
                .show()
        }
    }

    /**
     * Consume any saved launch/nav state and register the provider that writes it back. Idempotent.
     *
     * Called lazily from the property accessors rather than only from [onCreate] because the
     * session-gated hosts read this state inside `onCreateSessionSafe`, which runs during
     * `Activity.onCreate` — before `ON_CREATE` is dispatched.
     */
    private fun ensureRestored() {
        if (restored) {
            return
        }
        restored = true
        val registry = host.savedStateRegistry
        registry.consumeRestoredStateForKey(PROVIDER_KEY)?.let { state ->
            externalLaunch = state.getBoolean(KEY_WAS_EXTERNAL)
            loginExtraConsumed = state.getBoolean(KEY_LOGIN_EXTRA_CONSUMED)
            endpointNavPendingAfterSync = state.getBoolean(KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC)
        }
        registry.registerSavedStateProvider(PROVIDER_KEY) {
            Bundle().apply {
                putBoolean(KEY_WAS_EXTERNAL, externalLaunch)
                putBoolean(KEY_LOGIN_EXTRA_CONSUMED, loginExtraConsumed)
                putBoolean(KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC, endpointNavPendingAfterSync)
            }
        }
    }

    companion object {
        const val FORM_ARCHIVE_REQUEST_CODE = 6
        const val PREFERENCES_REQUEST_CODE = 7
        const val ADVANCED_ACTIONS_REQUEST_CODE = 8
        const val CREATE_PIN_REQUEST_CODE = 9
        const val AUTHENTICATION_FOR_PIN_REQUEST_CODE = 10

        private const val DEVELOPER_MODE_DISMISSALS = 4

        private const val PROVIDER_KEY = "org.commcare.home.HomeActivityCoordinator"
        private const val KEY_WAS_EXTERNAL = "was_external"
        private const val KEY_LOGIN_EXTRA_CONSUMED = "login_extra_was_consumed"
        private const val KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC = "pending_endpoint_nav_after_sync"

        /** The CommCare settings screen, which reports back under [PREFERENCES_REQUEST_CODE]. */
        @JvmStatic
        fun buildPreferencesIntent(context: Context): Intent =
            Intent(context, SessionAwarePreferenceActivity::class.java)
                .putExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE, CommCarePreferenceActivity.PREF_TYPE_COMMCARE)
    }
}
