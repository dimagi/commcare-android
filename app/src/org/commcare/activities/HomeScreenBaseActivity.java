package org.commcare.activities;

import static org.commcare.activities.DispatchActivity.EXIT_AFTER_FORM_SUBMISSION;
import static org.commcare.activities.DispatchActivity.EXIT_AFTER_FORM_SUBMISSION_DEFAULT;
import static org.commcare.activities.DispatchActivity.SESSION_ENDPOINT_ARGUMENTS_BUNDLE;
import static org.commcare.activities.DispatchActivity.SESSION_ENDPOINT_ARGUMENTS_LIST;
import static org.commcare.activities.DispatchActivity.SESSION_ENDPOINT_ID;
import static org.commcare.activities.DriftHelper.getCurrentDrift;
import static org.commcare.activities.DriftHelper.getDriftDialog;
import static org.commcare.activities.DriftHelper.shouldShowDriftWarning;
import static org.commcare.activities.DriftHelper.updateLastDriftWarningTime;
import static org.commcare.activities.EntitySelectActivity.EXTRA_ENTITY_KEY;
import static org.commcare.appupdate.AppUpdateController.IN_APP_UPDATE_REQUEST_CODE;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Base64;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.play.core.install.model.InstallErrorCode;

import org.apache.commons.lang3.StringUtils;
import org.commcare.CommCareApplication;
import org.commcare.activities.components.FormEntryConstants;
import org.commcare.activities.components.FormEntryInstanceState;
import org.commcare.activities.components.FormEntrySessionWrapper;
import org.commcare.connect.ConnectManager;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.appupdate.AppUpdateControllerFactory;
import org.commcare.appupdate.AppUpdateState;
import org.commcare.appupdate.FlexibleAppUpdateController;
import org.commcare.core.process.CommCareInstanceInitializer;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.heartbeat.UpdatePromptHelper;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.AdvancedActionsPreferences;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.MainConfigurablePreferences;
import org.commcare.recovery.measures.RecoveryMeasuresHelper;
import org.commcare.services.CommCareSessionService;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.session.SessionNavigationResponder;
import org.commcare.session.SessionNavigator;
import org.commcare.suite.model.Endpoint;
import org.commcare.suite.model.EntityDatum;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.FormEntry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.PostRequest;
import org.commcare.suite.model.RemoteRequestEntry;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.StackFrameStep;
import org.commcare.suite.model.Text;
import org.commcare.sync.FirebaseMessagingDataSyncer;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.FormLoaderTask;
import org.commcare.tasks.FormRecordCleanupTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.util.DatumUtil;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.AndroidInstanceInitializer;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.ChangeLocaleUtil;
import org.commcare.utils.CommCareUtil;
import org.commcare.utils.ConnectivityStatus;
import org.commcare.utils.CrashUtil;
import org.commcare.utils.EntityDetailUtils;
import org.commcare.utils.GlobalConstants;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.model.User;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.XPathTypeMismatchException;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages all of the shared (mostly non-UI) components of a CommCare home screen: activity
 * lifecycle, implementation of available actions, session navigation, etc.
 */
public abstract class HomeScreenBaseActivity<T> extends SyncCapableCommCareActivity<T>
        implements SessionNavigationResponder {

    /**
     * Request code for launching a menu list or menu grid
     */
    public static final int GET_COMMAND = 1;
    /**
     * Request code for launching EntitySelectActivity (to allow user to select a case), or
     * EntityDetailActivity (to allow user to confirm an auto-selected case)
     */
    protected static final int GET_CASE = 2;
    protected static final int GET_REMOTE_DATA = 3;
    /**
     * Request code for launching FormEntryActivity
     */
    protected static final int MODEL_RESULT = 4;
    protected static final int MAKE_REMOTE_POST = 5;
    public static final int GET_INCOMPLETE_FORM = 6;
    protected static final int PREFERENCES_ACTIVITY = 7;
    protected static final int ADVANCED_ACTIONS_ACTIVITY = 8;
    protected static final int CREATE_PIN = 9;
    protected static final int AUTHENTICATION_FOR_PIN = 10;

    private static final String KEY_PENDING_SESSION_DATA = "pending-session-data-id";
    private static final String KEY_PENDING_SESSION_DATUM_ID = "pending-session-datum-id";

    /**
     * Restart is a special CommCare activity result code which means that the session was
     * invalidated in the calling activity and that the current session should be resynced
     */
    public static final int RESULT_RESTART = 3;

    private int mDeveloperModeClicks = 0;

    private SessionNavigator sessionNavigator;
    private boolean sessionNavigationProceedingAfterOnResume;

    private boolean loginExtraWasConsumed;
    private static final String EXTRA_CONSUMED_KEY = "login_extra_was_consumed";
    private boolean isRestoringSession = false;

    // The API allows for external calls. When this occurs, redispatch to their
    // activity instead of commcare.
    private boolean wasExternal = false;
    private static final String WAS_EXTERNAL_KEY = "was_external";

    // Indicates if 1 of the checks we performed in onCreate resulted in redirecting to a
    // different activity or starting a UI-blocking task
    private boolean redirectedInOnCreate = false;

    private FlexibleAppUpdateController appUpdateController;
    private static final String APP_UPDATE_NOTIFICATION = "app_update_notification";
    protected boolean showCommCareUpdateMenu = false;
    private static final int MAX_CC_UPDATE_CANCELLATION = 3;

    // This is to trigger a background sync after a form submission,
    private boolean shouldTriggerBackgroundSync = true;

    // This is to restore the selected entity when restarting EntityDetailActivity after a
    // background sync
    private String selectedEntityPostSync = null;

    private FirebaseMessagingDataSyncer dataSyncer;

    {
        dataSyncer = new FirebaseMessagingDataSyncer(this);

    }

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        loadInstanceState(savedInstanceState);
        CrashUtil.registerAppData();

        updateLastSuccessfulCommCareVersion();
        sessionNavigator = new SessionNavigator(this);

        processFromExternalLaunch(savedInstanceState);
        processFromShortcutLaunch();
        processFromLoginLaunch();
        appUpdateController = AppUpdateControllerFactory.create(this::handleAppUpdate,
                getApplicationContext());
        appUpdateController.register();
    }

    private void updateLastSuccessfulCommCareVersion() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                CommCareApplication.instance());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(HiddenPreferences.LAST_SUCCESSFUL_CC_VERSION,
                ReportingUtils.getCommCareVersionString());
        editor.apply();
    }

    private void loadInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            loginExtraWasConsumed = savedInstanceState.getBoolean(EXTRA_CONSUMED_KEY);
            wasExternal = savedInstanceState.getBoolean(WAS_EXTERNAL_KEY);
        }
    }

    /**
     * Set state that signifies activity was launch from external app
     */
    private void processFromExternalLaunch(Bundle savedInstanceState) {
        if (savedInstanceState == null && getIntent().hasExtra(DispatchActivity.WAS_EXTERNAL)) {
            wasExternal = true;
            if (processSessionEndpoint()) {
                sessionNavigator.startNextSessionStep();
            }
        }
    }

    /**
     * @return If we are launched with a session endpoint, returns whether the endpoint was
     * successfully processed without errors. If this was not an external launch using session
     * endpoint, returns true
     */
    private boolean processSessionEndpoint() {
        if (getIntent().hasExtra(SESSION_ENDPOINT_ID)) {
            Endpoint endpoint = validateIntentForSessionEndpoint(getIntent());
            if (endpoint != null) {
                Bundle intentArgumentsAsBundle = getIntent().getBundleExtra(
                        SESSION_ENDPOINT_ARGUMENTS_BUNDLE);
                ArrayList<String> intentArgumentsAsList = getIntent().getStringArrayListExtra(
                        SESSION_ENDPOINT_ARGUMENTS_LIST);

                // Reset the Session to make sure we don't carry forward any session state to the
                // endpoint launch
                CommCareApplication.instance().getCurrentSessionWrapper().reset();

                try {
                    if (intentArgumentsAsBundle != null) {
                        CommCareApplication.instance().getCurrentSessionWrapper()
                                .executeEndpointStack(endpoint,
                                        AndroidUtil.bundleAsMap(intentArgumentsAsBundle));
                    } else if (intentArgumentsAsList != null) {
                        CommCareApplication.instance().getCurrentSessionWrapper()
                                .executeEndpointStack(endpoint, intentArgumentsAsList);
                    }
                    return true;
                } catch (Endpoint.InvalidEndpointArgumentsException e) {
                    String invalidEndpointArgsError =
                            org.commcare.utils.StringUtils.getStringRobust(
                                    this,
                                    R.string.session_endpoint_invalid_arguments,
                                    new String[]{
                                            endpoint.getId(),
                                            intentArgumentsAsBundle != null ?
                                                    StringUtils.join(intentArgumentsAsBundle, ",") :
                                                    String.valueOf(intentArgumentsAsList.size()),
                                            StringUtils.join(endpoint.getArguments(), ",")});
                    new UserfacingErrorHandling<>().createErrorDialog(this, invalidEndpointArgsError, true);
                }
            }
            return false;
        }
        return true;
    }

    private Endpoint validateIntentForSessionEndpoint(Intent intent) {
        String sessionEndpointId = intent.getStringExtra(SESSION_ENDPOINT_ID);
        Endpoint endpoint = CommCareApplication.instance().getCommCarePlatform().getEndpoint(
                sessionEndpointId);
        if (endpoint == null) {
            Hashtable<String, Endpoint> allEndpoints =
                    CommCareApplication.instance().getCommCarePlatform().getAllEndpoints();
            String invalidEndpointError = org.commcare.utils.StringUtils.getStringRobust(
                    this,
                    R.string.session_endpoint_unavailable,
                    new String[]{
                            sessionEndpointId,
                            StringUtils.join(allEndpoints.keySet(), ",")});
            new UserfacingErrorHandling<>().createErrorDialog(this, invalidEndpointError, true);
            return null;
        }
        return endpoint;
    }

    private void processFromShortcutLaunch() {
        if (getIntent().getBooleanExtra(DispatchActivity.WAS_SHORTCUT_LAUNCH, false)) {
            sessionNavigator.startNextSessionStep();
        }
    }

    private void processFromLoginLaunch() {
        if (getIntent().getBooleanExtra(DispatchActivity.START_FROM_LOGIN, false) &&
                !loginExtraWasConsumed) {
            getIntent().removeExtra(DispatchActivity.START_FROM_LOGIN);
            loginExtraWasConsumed = true;
            try {
                redirectedInOnCreate = doLoginLaunchChecksInOrder();
            } finally {
                // make sure this happens no matter what
                clearOneTimeLoginActionFlags();
            }
        }
    }

    /**
     * The order of operations in this method is very deliberate, and the logic for it is as
     * follows: - If we're in demo mode, then we don't want to do any of the other checks because
     * they're not relevant - Form and session restorations need to happen before we try to sync,
     * because once we sync it could invalidate those states - Restoring a form that was interrupted
     * by session expiration comes before restoring a saved session because it is of higher
     * importance - Check for a post-update sync before doing a standard background form-send, since
     * a sync action will include a form-send action - Check if we need to show an Update Prompt -
     * Once we're past that point, starting a background form-send process is safe, and we can
     * safely do checkForPinLaunchConditions() at the same time
     */
    private boolean doLoginLaunchChecksInOrder() {
        if (isDemoUser()) {
            showDemoModeWarning();
            return false;
        }

        if (showUpdateInfoForm()) {
            return true;
        }

        if (tryRestoringFormFromSessionExpiration()) {
            return true;
        }

        if (tryRestoringSession()) {
            return true;
        }

        if (CommCareApplication.instance().isPostUpdateSyncNeeded()
                || UpdateActivity.isUpdateBlockedOnSync()) {
            HiddenPreferences.setPostUpdateSyncNeeded(false);
            triggerSync(false);
            return true;
        }

        // In case a sync request from FCM was made while the user was logged out, this will
        // trigger a blocking sync
        String username = CommCareApplication.instance().getSession().getLoggedInUser().getUsername();
        if (HiddenPreferences.isPendingSyncRequest(username)) {
            sendFormsOrSync(false);

            return true;
        }

        if (UpdatePromptHelper.promptForUpdateIfNeeded(this)) {
            return true;
        }
        checkForPinLaunchConditions();
        checkForDrift();
        return false;
    }


    private void checkForDrift() {
        if (shouldShowDriftWarning()) {
            if (getCurrentDrift() != 0) {
                showAlertDialog(getDriftDialog(this));
                updateLastDriftWarningTime();
            }
        }
    }

    // Open the update info form if available
    private boolean showUpdateInfoForm() {
        if (HiddenPreferences.shouldShowXformUpdateInfo()) {
            HiddenPreferences.setShowXformUpdateInfo(false);
            String updateInfoFormXmlns =
                    CommCareApplication.instance().getCommCarePlatform().getUpdateInfoFormXmlns();
            if (!StringUtils.isEmpty(updateInfoFormXmlns)) {
                CommCareSession session = CommCareApplication.instance().getCurrentSession();
                FormEntry formEntry = session.getEntryForNameSpace(updateInfoFormXmlns);
                if (formEntry != null) {
                    session.setCommand(formEntry.getCommandID());
                    startNextSessionStepSafe();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Regardless of what action(s) we ended up executing in doLoginLaunchChecksInOrder(), we don't
     * want to end up trying the actions associated with these flags again at a later point. They
     * either need to happen the first time on login, or not at all.
     */
    private void clearOneTimeLoginActionFlags() {
        HiddenPreferences.setPostUpdateSyncNeeded(false);
        HiddenPreferences.clearInterruptedSSD();
        String username = CommCareApplication.instance().getSession().getLoggedInUser().getUsername();
        HiddenPreferences.clearPendingSyncRequest(username);
    }

    private boolean tryRestoringFormFromSessionExpiration() {
        SessionStateDescriptor existing =
                AndroidSessionWrapper.getFormStateForInterruptedUserSession();
        if (existing != null) {
            AndroidSessionWrapper state = CommCareApplication.instance().getCurrentSessionWrapper();
            state.loadFromStateDescription(existing);
            formEntry(CommCareApplication.instance().getCommCarePlatform()
                            .getFormDefId(state.getSession().getForm()), state.getFormRecord(),
                    null, true);
            return true;
        }
        return false;
    }

    private boolean tryRestoringSession() {
        CommCareSession session = CommCareApplication.instance().getCurrentSession();
        if (session.getCommand() != null) {
            // Restore the session state if there is a command. This is for debugging and
            // occurs when a serialized session was stored by a previous user session
            isRestoringSession = true;
            sessionNavigator.startNextSessionStep();
            return true;
        }
        return false;
    }

    /**
     * See if we should launch either the pin choice dialog, or the create pin activity directly
     */
    private void checkForPinLaunchConditions() {
        LoginMode loginMode = (LoginMode)getIntent().getSerializableExtra(LoginActivity.LOGIN_MODE);
        if (loginMode == LoginMode.PRIMED) {
            launchPinCreateScreen(loginMode);
        } else if (loginMode == LoginMode.PASSWORD
                && DeveloperPreferences.shouldOfferPinForLogin()) {
            boolean userManuallyEnteredPasswordMode = getIntent()
                    .getBooleanExtra(LoginActivity.MANUAL_SWITCH_TO_PW_MODE, false);
            boolean alreadyDismissedPinCreation =
                    CommCareApplication.instance().getCurrentApp().getAppPreferences()
                            .getBoolean(HiddenPreferences.HAS_DISMISSED_PIN_CREATION, false);
            if (!alreadyDismissedPinCreation || userManuallyEnteredPasswordMode) {
                showPinChoiceDialog(loginMode);
            }
        }
    }

    private void showPinChoiceDialog(final LoginMode loginMode) {
        String promptMessage;
        UserKeyRecord currentUserRecord = CommCareApplication.instance().getRecordForCurrentUser();
        if (currentUserRecord.hasPinSet()) {
            promptMessage = Localization.get("pin.dialog.prompt.reset");
        } else {
            promptMessage = Localization.get("pin.dialog.prompt.set");
        }

        final PaneledChoiceDialog dialog = new PaneledChoiceDialog(this, promptMessage);

        DialogChoiceItem createPinChoice = new DialogChoiceItem(
                Localization.get("pin.dialog.yes"), -1, v -> {
            dismissAlertDialog();
            launchPinCreateScreen(loginMode);
        });

        DialogChoiceItem nextTimeChoice = new DialogChoiceItem(
                Localization.get("pin.dialog.not.now"), -1, v -> dismissAlertDialog());

        DialogChoiceItem notAgainChoice = new DialogChoiceItem(
                Localization.get("pin.dialog.never"), -1, v -> {
            dismissAlertDialog();
            CommCareApplication.instance().getCurrentApp().getAppPreferences()
                    .edit()
                    .putBoolean(HiddenPreferences.HAS_DISMISSED_PIN_CREATION, true)
                    .apply();
            showPinFutureAccessDialog();
        });


        dialog.setChoiceItems(
                new DialogChoiceItem[]{createPinChoice, nextTimeChoice, notAgainChoice});
        dialog.addCollapsibleInfoPane(Localization.get("pin.dialog.extra.info"));
        showAlertDialog(dialog);
    }

    private void showPinFutureAccessDialog() {
        StandardAlertDialog.getBasicAlertDialog(this,
                Localization.get("pin.dialog.set.later.title"),
                Localization.get("pin.dialog.set.later.message"), null).showNonPersistentDialog();
    }

    protected void launchPinAuthentication() {
        Intent i = new Intent(this, PinAuthenticationActivity.class);
        startActivityForResult(i, AUTHENTICATION_FOR_PIN);
    }

    private void launchPinCreateScreen(LoginMode loginMode) {
        Intent i = new Intent(this, CreatePinActivity.class);
        i.putExtra(LoginActivity.LOGIN_MODE, loginMode);
        startActivityForResult(i, CREATE_PIN);
    }

    protected void showLocaleChangeMenu(final CommCareActivityUIController uiController) {
        final PaneledChoiceDialog dialog =
                new PaneledChoiceDialog(this, Localization.get("home.menu.locale.select"));

        AdapterView.OnItemClickListener listClickListener = (parent, view, position, id) -> {
            String[] localeCodes = ChangeLocaleUtil.getLocaleCodes();
            if (position >= localeCodes.length) {
                Localization.setLocale("default");
            } else {
                String selectedLocale = localeCodes[position];
                Localization.setLocale(selectedLocale);
                MainConfigurablePreferences.setCurrentLocale(selectedLocale);
            }
            // rebuild home buttons in case language changed;
            if (uiController != null) {
                uiController.setupUI();
            }
            rebuildOptionsMenu();
            dismissAlertDialog();
        };

        dialog.setChoiceItems(buildLocaleChoices(), listClickListener);
        showAlertDialog(dialog);
    }

    private static DialogChoiceItem[] buildLocaleChoices() {
        String[] locales = ChangeLocaleUtil.getLocaleNames();
        DialogChoiceItem[] choices = new DialogChoiceItem[locales.length];
        for (int i = 0; i < choices.length; i++) {
            choices[i] = DialogChoiceItem.nonListenerItem(locales[i]);
        }
        return choices;
    }

    protected void goToFormArchive(boolean incomplete) {
        goToFormArchive(incomplete, null);
    }

    protected void goToFormArchive(boolean incomplete, FormRecord record) {
        FirebaseAnalyticsUtil.reportViewArchivedFormsList(incomplete);
        Intent i = new Intent(getApplicationContext(), FormRecordListActivity.class);
        if (incomplete) {
            i.putExtra(FormRecord.META_STATUS, FormRecord.STATUS_INCOMPLETE);
        }
        if (record != null) {
            i.putExtra(FormRecordListActivity.KEY_INITIAL_RECORD_ID, record.getID());
        }
        startActivityForResult(i, GET_INCOMPLETE_FORM);
    }

    protected void userTriggeredLogout() {
        if (CommCareSessionService.sessionAliveLock.isLocked()) {
            Toast.makeText(this, Localization.get("background.sync.logout.attempt.during.sync"), Toast.LENGTH_LONG).show();
            return;
        }
        CommCareApplication.instance().closeUserSession();
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(WAS_EXTERNAL_KEY, wasExternal);
        outState.putBoolean(EXTRA_CONSUMED_KEY, loginExtraWasConsumed);
    }

    @Override
    public void onActivityResultSessionSafe(int requestCode, int resultCode, Intent intent) {
        if(ConnectManager.isConnectTask(requestCode)) {
            ConnectManager.handleFinishedActivity(this, requestCode, resultCode, intent);
        } else if (resultCode == RESULT_RESTART) {
            if (intent != null && intent.hasExtra(EXTRA_ENTITY_KEY))
                selectedEntityPostSync = intent.getStringExtra(EXTRA_ENTITY_KEY);

            // Reset the AndroidInstanceInitializer to force the eval context to be rebuilt
            // during restart
            CommCareApplication.instance().getCurrentSessionWrapper().cleanVolatiles();

            startNextSessionStepSafe();
        } else {
            // if handling new return code (want to return to home screen) but a return at the
            // end of your statement
            switch (requestCode) {
                case PREFERENCES_ACTIVITY:
                    if (resultCode == AdvancedActionsPreferences.RESULT_DATA_RESET) {
                        finish();
                    } else if (resultCode == DeveloperPreferences.RESULT_SYNC_CUSTOM) {
                        performCustomRestore();
                    }
                    return;
                case ADVANCED_ACTIONS_ACTIVITY:
                    handleAdvancedActionResult(resultCode, intent);
                    return;
                case GET_INCOMPLETE_FORM:
                    //TODO: We might need to load this from serialized state?
                    if (resultCode == RESULT_CANCELED) {
                        refreshUi();
                        return;
                    } else if (resultCode == RESULT_OK) {
                        int record = intent.getIntExtra("FORMRECORDS", -1);
                        if (record == -1) {
                            //Hm, what to do here?
                            break;
                        }
                        FormRecord r = CommCareApplication.instance().getUserStorage(
                                FormRecord.class).read(record);

                        //Retrieve and load the appropriate ssd
                        SqlStorage<SessionStateDescriptor> ssdStorage =
                                CommCareApplication.instance().getUserStorage(
                                        SessionStateDescriptor.class);
                        Vector<Integer> ssds = ssdStorage.getIDsForValue(
                                SessionStateDescriptor.META_FORM_RECORD_ID, r.getID());
                        AndroidSessionWrapper currentState =
                                CommCareApplication.instance().getCurrentSessionWrapper();
                        if (ssds.size() == 1) {
                            currentState.loadFromStateDescription(
                                    ssdStorage.read(ssds.firstElement()));
                        } else {
                            currentState.setFormRecordId(r.getID());
                        }

                        AndroidCommCarePlatform platform =
                                CommCareApplication.instance().getCommCarePlatform();
                        formEntry(platform.getFormDefId(r.getFormNamespace()), r);
                        return;
                    }
                    break;
                case GET_COMMAND:
                    boolean continueWithSessionNav =
                            processReturnFromGetCommand(resultCode, intent);
                    if (!continueWithSessionNav) {
                        return;
                    }
                    break;
                case GET_CASE:
                    continueWithSessionNav = processReturnFromGetCase(resultCode, intent);
                    if (!continueWithSessionNav) {
                        return;
                    }
                    break;
                case MODEL_RESULT:
                    if (intent != null && intent.getBooleanExtra(FormEntryConstants.WAS_INTERRUPTED,
                            false)) {
                        tryRestoringFormFromSessionExpiration();
                        return;
                    }
                    continueWithSessionNav = processReturnFromFormEntry(resultCode, intent);
                    if (!continueWithSessionNav) {
                        return;
                    }
                    if (!CommCareApplication.instance().getSession().appHealthChecksCompleted()) {
                        // If we haven't done these checks yet in this user session, try to
                        if (checkForPendingAppHealthActions()) {
                            // If we kick one off, abandon the session navigation that we were
                            // going to proceed with, because it may be invalid now
                            return;
                        }
                    }
                    break;
                case AUTHENTICATION_FOR_PIN:
                    if (resultCode == RESULT_OK) {
                        launchPinCreateScreen(LoginMode.PASSWORD);
                    }
                    return;
                case CREATE_PIN:
                    boolean choseRememberPassword = intent != null && intent.getBooleanExtra(
                            CreatePinActivity.CHOSE_REMEMBER_PASSWORD, false);
                    if (choseRememberPassword) {
                        CommCareApplication.instance().closeUserSession();
                    } else if (resultCode == RESULT_OK) {
                        Toast.makeText(this, Localization.get("pin.set.success"),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, Localization.get("pin.not.set"),
                                Toast.LENGTH_SHORT).show();
                    }
                    return;
                case MAKE_REMOTE_POST:
                    stepBackIfCancelled(resultCode);
                    if (resultCode == RESULT_OK) {
                        CommCareApplication.instance().getCurrentSessionWrapper().terminateSession();
                    }
                    break;
                case GET_REMOTE_DATA:
                    stepBackIfCancelled(resultCode);
                    break;
                case IN_APP_UPDATE_REQUEST_CODE:
                    if (resultCode == RESULT_CANCELED
                            && appUpdateController.availableVersionCode() != null) {
                        // An update was available for CommCare but user denied updating.
                        HiddenPreferences.incrementCommCareUpdateCancellationCounter(
                                String.valueOf(appUpdateController.availableVersionCode()));
                        // User might be busy right now, so let's not ask him again in this session.
                        CommCareApplication.instance().getSession().hideInAppUpdate();
                    }
                    return;
            }
            sessionNavigationProceedingAfterOnResume = true;
            startNextSessionStepSafe();
        }
    }

    private void performCustomRestore() {
        try {
            String filePath = DeveloperPreferences.getCustomRestoreDocLocation();
            if (filePath != null && !filePath.isEmpty()) {
                File f = new File(filePath);
                if (f.exists()) {
                    formAndDataSyncer.performCustomRestoreFromFile(this, f);
                } else {
                    Toast.makeText(this, Localization.get("custom.restore.file.not.exist"),
                            Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, Localization.get("custom.restore.file.not.set"),
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, Localization.get("custom.restore.error"),
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean processReturnFromGetCase(int resultCode, Intent intent) {
        if (resultCode == RESULT_CANCELED) {
            return processCanceledGetCommandOrCase();
        } else if (resultCode == RESULT_OK) {
            return processSuccessfulGetCase(intent);
        }
        return false;
    }

    public boolean processReturnFromGetCommand(int resultCode, Intent intent) {
        if (resultCode == RESULT_CANCELED) {
            return processCanceledGetCommandOrCase();
        } else if (resultCode == RESULT_OK) {
            return processSuccessfulGetCommand(intent);
        }
        return true;
    }

    private boolean processSuccessfulGetCommand(Intent intent) {
        AndroidSessionWrapper currentState =
                CommCareApplication.instance().getCurrentSessionWrapper();
        CommCareSession session = currentState.getSession();
        if (sessionStateUnchangedSinceCallout(session, intent)) {
            // Get our command, set it, and continue forward
            String command = intent.getStringExtra(SessionFrame.STATE_COMMAND_ID);
            session.setCommand(command);
            return true;
        } else {
            clearSessionAndExit(currentState, true);
            return false;
        }
    }

    private boolean processSuccessfulGetCase(Intent intent) {
        AndroidSessionWrapper asw = CommCareApplication.instance().getCurrentSessionWrapper();
        CommCareSession currentSession = asw.getSession();
        if (sessionStateUnchangedSinceCallout(currentSession, intent)) {
            String chosenCaseId = intent.getStringExtra(SessionFrame.STATE_DATUM_VAL);
            currentSession.setEntityDatum(currentSession.getNeededDatum(), chosenCaseId);
            return true;
        } else {
            clearSessionAndExit(asw, true);
            return false;
        }
    }

    private boolean processCanceledGetCommandOrCase() {
        AndroidSessionWrapper currentState =
                CommCareApplication.instance().getCurrentSessionWrapper();
        String currentCommand = currentState.getSession().getCommand();
        if (currentCommand == null || currentCommand.equals(Menu.TRAINING_MENU_ROOT)) {
            // We're stepping back from either the root module menu or the training root menu, so
            // go home
            currentState.reset();
            refreshUi();
            return false;
        } else {
            currentState.getSession().stepBack(currentState.getEvaluationContext());
            return true;
        }
    }

    private void handleAdvancedActionResult(int resultCode, Intent intent) {
        if (resultCode == AdvancedActionsPreferences.RESULT_FORMS_PROCESSED) {
            int formProcessCount = intent.getIntExtra(
                    AdvancedActionsPreferences.FORM_PROCESS_COUNT_KEY, 0);
            String localizationKey = intent.getStringExtra(
                    AdvancedActionsPreferences.FORM_PROCESS_MESSAGE_KEY);
            displayToast(Localization.get(localizationKey, new String[]{"" + formProcessCount}));
            refreshUi();
        }
    }

    private static void stepBackIfCancelled(int resultCode) {
        if (resultCode == RESULT_CANCELED) {
            AndroidSessionWrapper asw = CommCareApplication.instance().getCurrentSessionWrapper();
            CommCareSession currentSession = asw.getSession();
            currentSession.stepBack(asw.getEvaluationContext());
        }
    }

    public void startNextSessionStepSafe() {
        try {
            sessionNavigator.startNextSessionStep();
        } catch (CommCareInstanceInitializer.FixtureInitializationException e) {
            sessionNavigator.stepBack();
            if (isDemoUser()) {
                // most likely crashing due to data not being available in demo mode
                new UserfacingErrorHandling<>().createErrorDialog(this,
                        Localization.get("demo.mode.feature.unavailable"),
                        false);
            } else {
                new UserfacingErrorHandling<>().createErrorDialog(this, e.getMessage(), false);
            }
        }
    }

    /**
     * @return If the nature of the data that the session is waiting for has not changed since the
     * callout that we are returning from was made
     */
    private boolean sessionStateUnchangedSinceCallout(CommCareSession session, Intent intent) {
        EvaluationContext evalContext =
                CommCareApplication.instance().getCurrentSessionWrapper().getEvaluationContext();

        String pendingSessionData = intent.getStringExtra(KEY_PENDING_SESSION_DATA);
        String sessionNeededData = session.getNeededData(evalContext);
        boolean neededDataUnchanged = (pendingSessionData == null && sessionNeededData == null)
                || (pendingSessionData != null && pendingSessionData.equals(sessionNeededData));

        String intentDatum = intent.getStringExtra(KEY_PENDING_SESSION_DATUM_ID);
        boolean datumIdsUnchanged = intentDatum == null || intentDatum.equals(
                session.getNeededDatum().getDataId());
        return neededDataUnchanged && datumIdsUnchanged;
    }

    /**
     * Process user returning home from the form entry activity. Triggers form submission cycle,
     * cleans up some session state.
     *
     * @param resultCode exit code of form entry activity
     * @param intent     The intent of the returning activity, with the saved form provided as the
     *                   intent URI data. Null if the form didn't exit cleanly
     * @return Flag signifying that caller should fetch the next activity in the session to launch.
     * If false then caller should exit or spawn home activity.
     */
    private boolean processReturnFromFormEntry(int resultCode, Intent intent) {
        // TODO: We might need to load this from serialized state?
        AndroidSessionWrapper currentState =
                CommCareApplication.instance().getCurrentSessionWrapper();

        // This is the state we were in when we _Started_ form entry
        FormRecord current = currentState.getFormRecord();

        if (current == null) {
            // somehow we lost the form record for the current session
            Toast.makeText(this,
                    "Error while trying to save the form!",
                    Toast.LENGTH_LONG).show();
            Logger.log(LogTypes.TYPE_ERROR_WORKFLOW,
                    "Form Entry couldn't save because of corrupt state.");
            clearSessionAndExit(currentState, true);
            return false;
        }

        // TODO: This should be the default unless we're in some "Uninit" or "incomplete" state
        if ((intent != null && intent.getBooleanExtra(FormEntryConstants.IS_ARCHIVED_FORM, false))
                ||
                FormRecord.STATUS_COMPLETE.equals(current.getStatus()) ||
                FormRecord.STATUS_SAVED.equals(current.getStatus())) {
            // Viewing an old form, so don't change the historical record
            // regardless of the exit code
            currentState.reset();
            if (exitFromExternalLaunch() ||
                    (intent != null && intent.getBooleanExtra(
                            FormEntryActivity.KEY_IS_RESTART_AFTER_EXPIRATION, false))) {
                setResult(RESULT_CANCELED);
                this.finish();
            } else {
                // Return to where we started
                goToFormArchive(false, current);
            }
            return false;
        }

        if (resultCode == RESULT_OK) {
            String formRecordStatus = current.getStatus();
            // was the record marked complete?
            boolean complete = FormRecord.STATUS_COMPLETE.equals(formRecordStatus)
                    || FormRecord.STATUS_UNSENT.equals(formRecordStatus);

            // The form is either ready for processing, or not, depending on how it was saved
            if (complete) {
                startUnsentFormsTask(false, false);
                refreshUi();

                if (exitFromExternalLaunch()) {
                    currentState.reset();
                    setResult(RESULT_CANCELED);
                    this.finish();
                    return false;
                }

                // Before we can terminate the session, we need to know that the form has been
                // processed in case there is state that depends on it.
                boolean terminateSuccessful;
                try {
                    terminateSuccessful = currentState.terminateSession();
                } catch (XPathException e) {
                    new UserfacingErrorHandling<>().logErrorAndShowDialog(this, e, true);
                    return false;
                }
                if (!terminateSuccessful) {
                    // If we didn't find somewhere to go, we're gonna stay here
                    return false;
                }
                // Otherwise, we want to keep proceeding in order
                // to keep running the workflow
            } else {
                clearSessionAndExit(currentState, false);
                return false;
            }
        } else if (resultCode == RESULT_CANCELED) {
            // Nothing was saved during the form entry activity

            Logger.log(LogTypes.TYPE_FORM_ENTRY, "Form Entry Cancelled");

            // If the form was unstarted, we want to wipe the record.
            if (current.getStatus().equals(FormRecord.STATUS_UNSTARTED)) {
                // Entry was cancelled.
                FormRecordCleanupTask.wipeRecord(currentState);
            }

            if (exitFromExternalLaunch()) {
                currentState.reset();
                setResult(RESULT_CANCELED);
                this.finish();
                return false;
            } else if (current.getStatus().equals(FormRecord.STATUS_INCOMPLETE) &&
                    intent != null && !intent.getBooleanExtra(
                    FormEntryActivity.KEY_IS_RESTART_AFTER_EXPIRATION, false)) {
                currentState.reset();
                // We should head back to the incomplete forms screen
                goToFormArchive(true, current);
                return false;
            } else {
                // If we cancelled form entry from a normal menu entry
                // we want to go back to where were were right before we started
                // entering the form.
                currentState.getSession().stepBack(currentState.getEvaluationContext());
                currentState.setFormRecordId(-1);
            }
        }
        return true;
    }

    private boolean exitFromExternalLaunch() {
        return wasExternal && getIntent() != null &&
                getIntent().getBooleanExtra(EXIT_AFTER_FORM_SUBMISSION,
                        EXIT_AFTER_FORM_SUBMISSION_DEFAULT);
    }

    private void clearSessionAndExit(AndroidSessionWrapper currentState, boolean shouldWarnUser) {
        currentState.reset();
        if (exitFromExternalLaunch()) {
            setResult(RESULT_CANCELED);
            this.finish();
        }
        refreshUi();
        if (shouldWarnUser) {
            showSessionRefreshWarning();
        }
    }

    private void showSessionRefreshWarning() {
        showAlertDialog(StandardAlertDialog.getBasicAlertDialog(this,
                Localization.get("session.refresh.error.title"),
                Localization.get("session.refresh.error.message"), null));
    }

    private void showDemoModeWarning() {
        StandardAlertDialog d = StandardAlertDialog.getBasicAlertDialogWithIcon(this,
                Localization.get("demo.mode.warning.title"),
                Localization.get("demo.mode.warning.main"),
                android.R.drawable.ic_dialog_info, null);
        d.addEmphasizedMessage(Localization.get("demo.mode.warning.emphasized"));
        showAlertDialog(d);
    }

    private void createErrorDialog(String errorMsg, AlertDialog.OnClickListener errorListener) {
        showAlertDialog(StandardAlertDialog.getBasicAlertDialogWithIcon(this,
                Localization.get("app.handled.error.title"), errorMsg,
                android.R.drawable.ic_dialog_info, errorListener));
    }

    @Override
    public void processSessionResponse(int statusCode) {
        AndroidSessionWrapper asw = CommCareApplication.instance().getCurrentSessionWrapper();
        switch (statusCode) {
            case SessionNavigator.ASSERTION_FAILURE:
                handleAssertionFailureFromSessionNav(asw);
                break;
            case SessionNavigator.NO_CURRENT_FORM:
                handleNoFormFromSessionNav(asw);
                break;
            case SessionNavigator.START_FORM_ENTRY:
                startFormEntry(asw);
                break;
            case SessionNavigator.GET_COMMAND:
                handleGetCommand(asw);
                break;
            case SessionNavigator.START_ENTITY_SELECTION:
                launchEntitySelect(asw.getSession());
                break;
            case SessionNavigator.LAUNCH_CONFIRM_DETAIL:
                launchConfirmDetail(asw);
                break;
            case SessionNavigator.PROCESS_QUERY_REQUEST:
                launchQueryMaker();
                break;
            case SessionNavigator.START_SYNC_REQUEST:
                launchRemoteSync(asw);
                break;
            case SessionNavigator.XPATH_EXCEPTION_THROWN:
                new UserfacingErrorHandling<>()
                        .logErrorAndShowDialog(this, sessionNavigator.getCurrentException(), false);
                asw.reset();
                break;
            case SessionNavigator.REPORT_CASE_AUTOSELECT:
                FirebaseAnalyticsUtil.reportFeatureUsage(
                        AnalyticsParamValue.FEATURE_CASE_AUTOSELECT);
                break;
            case SessionNavigator.FORM_ENTRY_ATTEMPT_DURING_SYNC:
                handleFormEntryAttemptDuringSync(asw);
                break;
        }
    }

    private void handleFormEntryAttemptDuringSync(AndroidSessionWrapper asw) {
        sessionNavigator.stepBack();
        sessionNavigator.startNextSessionStep();
        Toast.makeText(this, Localization.get("background.sync.form.entry.attempt.during.sync"), Toast.LENGTH_LONG).show();
    }

    @Override
    public CommCareSession getSessionForNavigator() {
        return CommCareApplication.instance().getCurrentSession();
    }

    @Override
    public EvaluationContext getEvalContextForNavigator() {
        return CommCareApplication.instance().getCurrentSessionWrapper().getEvaluationContext();
    }

    private void handleAssertionFailureFromSessionNav(final AndroidSessionWrapper asw) {
        EvaluationContext ec = asw.getEvaluationContext();
        Text text = asw.getSession().getCurrentEntry().getAssertions().getAssertionFailure(ec);
        createErrorDialog(text.evaluate(ec), (dialog, i) -> {
            dismissAlertDialog();
            asw.getSession().stepBack(asw.getEvaluationContext());
            HomeScreenBaseActivity.this.sessionNavigator.startNextSessionStep();
        });
    }

    private void handleNoFormFromSessionNav(AndroidSessionWrapper asw) {
        boolean terminateSuccesful;
        try {
            terminateSuccesful = asw.terminateSession();
        } catch (XPathTypeMismatchException e) {
            new UserfacingErrorHandling<>().logErrorAndShowDialog(this, e, true);
            return;
        }
        if (terminateSuccesful) {
            sessionNavigator.startNextSessionStep();
        } else {
            refreshUi();
        }
    }

    private void handleGetCommand(AndroidSessionWrapper asw) {
        Intent i = new Intent(this, MenuActivity.class);
        String command = asw.getSession().getCommand();
        i.putExtra(SessionFrame.STATE_COMMAND_ID, command);
        addPendingDataExtra(i, asw.getSession());
        startActivityForResult(i, GET_COMMAND);
    }

    private void launchRemoteSync(AndroidSessionWrapper asw) {
        String command = asw.getSession().getCommand();
        Entry commandEntry = CommCareApplication.instance().getCommCarePlatform().getEntry(command);
        if (commandEntry instanceof RemoteRequestEntry) {
            PostRequest postRequest = ((RemoteRequestEntry)commandEntry).getPostRequest();
            Intent i = new Intent(getApplicationContext(), PostRequestActivity.class);
            i.putExtra(PostRequestActivity.URL_KEY, postRequest.getUrl());
            i.putExtra(PostRequestActivity.PARAMS_KEY,
                    (Serializable)postRequest.getEvaluatedParams(asw.getEvaluationContext(), false));

            startActivityForResult(i, MAKE_REMOTE_POST);
        } else {
            // expected a sync entry; clear session and show vague 'session error' message to user
            clearSessionAndExit(asw, true);
        }
    }

    private void launchQueryMaker() {
        Intent i = new Intent(getApplicationContext(), QueryRequestActivity.class);
        startActivityForResult(i, GET_REMOTE_DATA);
    }

    private void launchEntitySelect(CommCareSession session) {
        startActivityForResult(getSelectIntent(session), GET_CASE);
    }

    private Intent getSelectIntent(CommCareSession session) {
        Intent i = new Intent(getApplicationContext(), EntitySelectActivity.class);
        i.putExtra(SessionFrame.STATE_COMMAND_ID, session.getCommand());
        StackFrameStep lastPopped = session.getPoppedStep();
        if (lastPopped != null && SessionFrame.STATE_DATUM_VAL.equals(lastPopped.getType())) {
            i.putExtra(EXTRA_ENTITY_KEY, lastPopped.getValue());
        }
        if (selectedEntityPostSync != null) {
            i.putExtra(EXTRA_ENTITY_KEY, selectedEntityPostSync);
            selectedEntityPostSync = null;
        }
        addPendingDataExtra(i, session);
        addPendingDatumIdExtra(i, session);
        return i;
    }

    public void launchUpdateActivity(boolean autoProceedUpdateInstall) {
        Intent i = new Intent(getApplicationContext(), UpdateActivity.class);
        i.putExtra(UpdateActivity.KEY_PROCEED_AUTOMATICALLY, autoProceedUpdateInstall);
        startActivity(i);
    }

    void enterTrainingModule() {
        CommCareApplication.instance().getCurrentSession().setCommand(
                org.commcare.suite.model.Menu.TRAINING_MENU_ROOT);
        startNextSessionStepSafe();
    }

    // Launch an intent to load the confirmation screen for the current selection
    private void launchConfirmDetail(AndroidSessionWrapper asw) {
        CommCareSession session = asw.getSession();
        SessionDatum selectDatum = session.getNeededDatum();
        if (selectDatum instanceof EntityDatum) {
            EntityDatum entityDatum = (EntityDatum)selectDatum;
            TreeReference contextRef = sessionNavigator.getCurrentAutoSelection();
            if (this.getString(R.string.panes).equals("two")
                    && getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE) {
                // Large tablet in landscape: send to entity select activity
                // (awesome mode, with case pre-selected) instead of entity detail
                Intent i = getSelectIntent(session);
                String caseId = DatumUtil.getReturnValueFromSelection(
                        contextRef, entityDatum, asw.getEvaluationContext());
                i.putExtra(EXTRA_ENTITY_KEY, caseId);
                startActivityForResult(i, GET_CASE);
            } else {
                // Launch entity detail activity
                Intent detailIntent = new Intent(getApplicationContext(),
                        EntityDetailActivity.class);
                EntityDetailUtils.populateDetailIntent(
                        detailIntent, contextRef, entityDatum, asw);
                addPendingDataExtra(detailIntent, session);
                addPendingDatumIdExtra(detailIntent, session);
                startActivityForResult(detailIntent, GET_CASE);
            }
        }
    }

    protected static void addPendingDataExtra(Intent i, CommCareSession session) {
        EvaluationContext evalContext =
                CommCareApplication.instance().getCurrentSessionWrapper().getEvaluationContext();
        i.putExtra(KEY_PENDING_SESSION_DATA, session.getNeededData(evalContext));
    }

    private static void addPendingDatumIdExtra(Intent i, CommCareSession session) {
        i.putExtra(KEY_PENDING_SESSION_DATUM_ID, session.getNeededDatum().getDataId());
    }

    /**
     * Create (or re-use) a form record and pass it to the form entry activity launcher. If there is
     * an existing incomplete form that uses the same case, ask the user if they want to edit or
     * delete that one.
     *
     * @param state Needed for FormRecord manipulations
     */
    private void startFormEntry(AndroidSessionWrapper state) {
        if (state.getFormRecordId() == -1) {
            if (HiddenPreferences.isIncompleteFormsEnabled()) {
                // Are existing (incomplete) forms using the same case?
                SessionStateDescriptor existing =
                        state.getExistingIncompleteCaseDescriptor();

                if (existing != null) {
                    // Ask user if they want to just edit existing form that
                    // uses the same case.
                    createAskUseOldDialog(state, existing);
                    return;
                }
            }

            // Generate a stub form record and commit it
            state.commitStub();
        } else {
            Logger.log(LogTypes.TYPE_FORM_ENTRY,
                    "Somehow ended up starting form entry with old state?");
        }

        FormRecord record = state.getFormRecord();
        AndroidCommCarePlatform platform = CommCareApplication.instance().getCommCarePlatform();
        formEntry(platform.getFormDefId(record.getFormNamespace()), record,
                CommCareActivity.getTitle(this, null), false);
    }

    private void formEntry(int formDefId, FormRecord r) {
        formEntry(formDefId, r, null, false);
    }

    private void formEntry(int formDefId, FormRecord r, String headerTitle,
                           boolean isRestartAfterSessionExpiration) {

        // Block any background syncs during a form entry
        shouldTriggerBackgroundSync = false;

        Logger.log(LogTypes.TYPE_FORM_ENTRY, "Form Entry Starting|" +
                (r.getInstanceID() == null ? "" : r.getInstanceID() + "|") +
                r.getFormNamespace());

        //TODO: This is... just terrible. Specify where external instance data should come from
        FormLoaderTask.iif = new AndroidInstanceInitializer(
                CommCareApplication.instance().getCurrentSession());

        // Create our form entry activity callout
        Intent i = new Intent(getApplicationContext(), FormEntryActivity.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(FormEntryInstanceState.KEY_FORM_RECORD_DESTINATION,
                CommCareApplication.instance().getCurrentApp().fsPath(
                        (GlobalConstants.FILE_CC_FORMS)));

        // See if there's existing form data that we want to continue entering
        if (!StringUtils.isEmpty(r.getFilePath())) {
            i.putExtra(FormEntryActivity.KEY_FORM_RECORD_ID, r.getID());
        } else {
            i.putExtra(FormEntryActivity.KEY_FORM_DEF_ID, formDefId);
        }

        i.putExtra(FormEntryActivity.KEY_RESIZING_ENABLED, HiddenPreferences.getResizeMethod());
        i.putExtra(FormEntryActivity.KEY_INCOMPLETE_ENABLED,
                HiddenPreferences.isIncompleteFormsEnabled());
        i.putExtra(FormEntryActivity.KEY_AES_STORAGE_KEY,
                Base64.encodeToString(r.getAesKey(), Base64.DEFAULT));
        i.putExtra(FormEntrySessionWrapper.KEY_RECORD_FORM_ENTRY_SESSION,
                DeveloperPreferences.isSessionSavingEnabled());
        i.putExtra(FormEntryActivity.KEY_IS_RESTART_AFTER_EXPIRATION,
                isRestartAfterSessionExpiration);
        if (headerTitle != null) {
            i.putExtra(FormEntryActivity.KEY_HEADER_STRING, headerTitle);
        }
        if (isRestoringSession) {
            isRestoringSession = false;
            SharedPreferences prefs =
                    CommCareApplication.instance().getCurrentApp().getAppPreferences();
            String formEntrySession = prefs.getString(DevSessionRestorer.CURRENT_FORM_ENTRY_SESSION,
                    "");
            if (!"".equals(formEntrySession)) {
                i.putExtra(FormEntrySessionWrapper.KEY_FORM_ENTRY_SESSION, formEntrySession);
            }
        }
        startActivityForResult(i, MODEL_RESULT);
    }

    private void triggerSync(boolean triggeredByAutoSyncPending) {
        if (triggeredByAutoSyncPending) {
            long lastUploadSyncAttempt = HiddenPreferences.getLastUploadSyncAttempt();
            String footer = lastUploadSyncAttempt == 0 ? "never" :
                    SimpleDateFormat.getDateTimeInstance().format(lastUploadSyncAttempt);
            Logger.log(LogTypes.TYPE_USER, "autosync triggered. Last Sync|" + footer);
        }

        refreshUi();
        sendFormsOrSync(false);
    }

    @Override
    public void onResumeSessionSafe() {
        if (!redirectedInOnCreate && !sessionNavigationProceedingAfterOnResume) {
            refreshActionBar();
            attemptDispatchHomeScreen();
        }

        // In case a Sync was blocked because of a form entry, trigger now if it's safe
        String username = CommCareApplication.instance().getSession().getLoggedInUser().getUsername();
        if (HiddenPreferences.isPendingSyncRequest(username) && shouldTriggerBackgroundSync) {
            dataSyncer.syncData(HiddenPreferences.getPendingSyncRequest(username));
        }

        // reset these
        redirectedInOnCreate = false;
        sessionNavigationProceedingAfterOnResume = false;
        shouldTriggerBackgroundSync = true;
    }

    private void attemptDispatchHomeScreen() {
        try {
            CommCareApplication.instance().getSession();
        } catch (SessionUnavailableException e) {
            // User was logged out somehow, so we want to return to dispatch activity
            setResult(RESULT_OK);
            this.finish();
            return;
        }

        if (!checkForPendingAppHealthActions()) {
            // Display the home screen!
            refreshUi();
        }
    }

    /**
     * @return true if we kicked off any foreground processes
     */
    private boolean checkForPendingAppHealthActions() {
        boolean kickedOff = false;

        if (RecoveryMeasuresHelper.recoveryMeasuresPending()) {
            finishWithExecutionIntent();
            kickedOff = true;
        } else if (UpdateActivity.isUpdateBlockedOnSync()
                && UpdateActivity.sBlockedUpdateWorkflowInProgress) {
            triggerSync(true);
            kickedOff = true;
        } else if (CommCareApplication.instance().isSyncPending()) {
            triggerSync(true);
            kickedOff = true;
        }

        // Trigger background log submission if required
        String userId = CommCareApplication.instance().getSession().getLoggedInUser().getUniqueId();
        if (HiddenPreferences.shouldForceLogs(userId)) {
            CommCareUtil.triggerLogSubmission(CommCareApplication.instance(), true);
        }

        CommCareApplication.instance().getSession().setAppHealthChecksCompleted();
        return kickedOff;
    }

    @Override
    public void handlePullTaskResult(ResultAndError<DataPullTask.PullTaskResult> resultAndError,
                                     boolean userTriggeredSync, boolean formsToSend, boolean usingRemoteKeyManagement) {
        super.handlePullTaskResult(resultAndError, userTriggeredSync, formsToSend,
                usingRemoteKeyManagement);
        if (UpdateActivity.sBlockedUpdateWorkflowInProgress) {
            Intent i = new Intent(getApplicationContext(), UpdateActivity.class);
            i.putExtra(UpdateActivity.KEY_PROCEED_AUTOMATICALLY, true);

            if (resultAndError.data == DataPullTask.PullTaskResult.DOWNLOAD_SUCCESS) {
                i.putExtra(UpdateActivity.KEY_PRE_UPDATE_SYNC_SUCCEED, true);
            } else {
                i.putExtra(UpdateActivity.KEY_PRE_UPDATE_SYNC_SUCCEED, false);
            }
            startActivity(i);
        }
    }

    private void finishWithExecutionIntent() {
        Intent i = new Intent();
        i.putExtra(DispatchActivity.EXECUTE_RECOVERY_MEASURES, true);
        setResult(RESULT_OK, i);
        finish();
    }

    private void createAskUseOldDialog(final AndroidSessionWrapper state,
                                       final SessionStateDescriptor existing) {
        final AndroidCommCarePlatform platform =
                CommCareApplication.instance().getCommCarePlatform();
        String title = Localization.get("app.workflow.incomplete.continue.title");
        String msg = Localization.get("app.workflow.incomplete.continue");
        StandardAlertDialog d = new StandardAlertDialog(this, title, msg);
        DialogInterface.OnClickListener listener = (dialog, i) -> {
            switch (i) {
                case DialogInterface.BUTTON_POSITIVE:
                    // use the old form instance and load the it's state from the descriptor
                    state.loadFromStateDescription(existing);
                    formEntry(platform.getFormDefId(state.getSession().getForm()),
                            state.getFormRecord());
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    // delete the old incomplete form
                    FormRecordCleanupTask.wipeRecord(existing);
                    // fallthrough to new now that old record is gone
                case DialogInterface.BUTTON_NEUTRAL:
                    // create a new form record and begin form entry
                    state.commitStub();
                    formEntry(platform.getFormDefId(state.getSession().getForm()),
                            state.getFormRecord());
            }
            dismissAlertDialog();
        };
        d.setPositiveButton(Localization.get("option.yes"), listener);
        d.setNegativeButton(Localization.get("app.workflow.incomplete.continue.option.delete"),
                listener);
        d.setNeutralButton(Localization.get("option.no"), listener);
        showAlertDialog(d);
    }

    protected static boolean isDemoUser() {
        try {
            User u = CommCareApplication.instance().getSession().getLoggedInUser();
            return (User.TYPE_DEMO.equals(u.getUserType()));
        } catch (SessionUnavailableException e) {
            // Default to a normal user: this should only happen if session
            // expires and hasn't redirected to login.
            return false;
        }
    }

    public static void createPreferencesMenu(AppCompatActivity activity) {
        Intent i = new Intent(activity, SessionAwarePreferenceActivity.class);
        i.putExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE,
                CommCarePreferenceActivity.PREF_TYPE_COMMCARE);
        activity.startActivityForResult(i, PREFERENCES_ACTIVITY);
    }

    protected void showAdvancedActionsPreferences() {
        Intent intent = new Intent(this, SessionAwarePreferenceActivity.class);
        intent.putExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE,
                CommCarePreferenceActivity.PREF_TYPE_ADVANCED_ACTIONS);
        startActivityForResult(intent, ADVANCED_ACTIONS_ACTIVITY);
    }

    protected void showAboutCommCareDialog() {
        CommCareAlertDialog dialog = DialogCreationHelpers.buildAboutCommCareDialog(this);
        dialog.makeCancelable();
        dialog.setOnDismissListener(dialog1 -> handleDeveloperModeClicks());
        showAlertDialog(dialog);
    }

    private void handleDeveloperModeClicks() {
        mDeveloperModeClicks++;
        if (mDeveloperModeClicks == 4) {
            DeveloperPreferences.setSuperuserEnabled(true);
            Toast.makeText(this, Localization.get("home.developer.options.enabled"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean isBackEnabled() {
        return false;
    }

    /**
     * For Testing purposes only
     */
    public SessionNavigator getSessionNavigator() {
        if (BuildConfig.DEBUG) {
            return sessionNavigator;
        } else {
            throw new RuntimeException("On principal of design, only meant for testing purposes");
        }
    }

    /**
     * For Testing purposes only
     */
    public void setFormAndDataSyncer(FormAndDataSyncer formAndDataSyncer) {
        if (BuildConfig.DEBUG) {
            this.formAndDataSyncer = formAndDataSyncer;
        } else {
            throw new RuntimeException("On principal of design, only meant for testing purposes");
        }
    }

    abstract void refreshUi();

    abstract void refreshCcUpdateOption();

    @Override
    protected void onDestroy() {
        if (appUpdateController != null) {
            appUpdateController.unregister();
        }
        super.onDestroy();
    }

    protected void startCommCareUpdate() {
        appUpdateController.startUpdate(this);
    }

    private void handleAppUpdate() {
        AppUpdateState state = appUpdateController.getStatus();
        switch (state) {
            case UNAVAILABLE:
                if (ConnectivityStatus.isNetworkAvailable(this)) {
                    // We just queried and found that no update is available.
                    // Let's check again in next session.
                    CommCareApplication.instance().getSession().hideInAppUpdate();
                }
                break;
            case AVAILABLE:
                if (HiddenPreferences.getCommCareUpdateCancellationCounter(
                        String.valueOf(appUpdateController.availableVersionCode()))
                        > MAX_CC_UPDATE_CANCELLATION) {
                    showCommCareUpdateMenu = true;
                    refreshCcUpdateOption();
                    return;
                }
                startCommCareUpdate();
                break;
            case DOWNLOADING:
                // Native downloads app gives a notification regarding the current download in
                // progress.
                NotificationMessage message = NotificationMessageFactory.message(
                        NotificationMessageFactory.StockMessages.InApp_Update,
                        APP_UPDATE_NOTIFICATION);
                CommCareApplication.notificationManager().reportNotificationMessage(message);
                if (showCommCareUpdateMenu) {
                    // Once downloading is started, we shouldn't show the update menu anymore.
                    showCommCareUpdateMenu = false;
                    refreshCcUpdateOption();
                }
                break;
            case DOWNLOADED:
                CommCareApplication.notificationManager().clearNotifications(
                        APP_UPDATE_NOTIFICATION);
                StandardAlertDialog dialog = StandardAlertDialog.getBasicAlertDialog(this,
                        Localization.get("in.app.update.installed.title"),
                        Localization.get("in.app.update.installed.detail"),
                        null);
                dialog.setPositiveButton(Localization.get("in.app.update.dialog.restart"),
                        (dialog1, which) -> {
                            appUpdateController.completeUpdate();
                            dismissAlertDialog();
                        });
                dialog.setNegativeButton(Localization.get("in.app.update.dialog.cancel"),
                        (dialog1, which) -> {
                            dismissAlertDialog();
                        });
                showAlertDialog(dialog);
                FirebaseAnalyticsUtil.reportInAppUpdateResult(true,
                        AnalyticsParamValue.IN_APP_UPDATE_SUCCESS);
                break;
            case FAILED:
                String errorReason = "in.app.update.error.unknown";
                switch (appUpdateController.getErrorCode()) {
                    case InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED:
                        errorReason = "in.app.update.error.not.allowed";
                        break;
                    case InstallErrorCode.NO_ERROR_PARTIALLY_ALLOWED:
                        errorReason = "in.app.update.error.partially.allowed";
                        break;
                    case InstallErrorCode.ERROR_UNKNOWN:
                        errorReason = "in.app.update.error.unknown";
                        break;
                    case InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND:
                        errorReason = "in.app.update.error.playstore";
                        break;
                    case InstallErrorCode.ERROR_INVALID_REQUEST:
                        errorReason = "in.app.update.error.invalid.request";
                        break;
                    case InstallErrorCode.ERROR_INTERNAL_ERROR:
                        errorReason = "in.app.update.error.internal.error";
                        break;
                }
                Logger.log(LogTypes.TYPE_CC_UPDATE,
                        "CommCare In App Update failed because : " + errorReason);
                CommCareApplication.notificationManager().clearNotifications(
                        APP_UPDATE_NOTIFICATION);
                Toast.makeText(this, Localization.get(errorReason), Toast.LENGTH_LONG).show();
                FirebaseAnalyticsUtil.reportInAppUpdateResult(false, errorReason);
                break;
        }
    }

    @Override
    public ReentrantLock getBackgroundSyncLock() {
        return CommCareSessionService.sessionAliveLock;
    }
}
