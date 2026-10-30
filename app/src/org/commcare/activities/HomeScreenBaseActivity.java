package org.commcare.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;


import org.apache.commons.lang3.StringUtils;
import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.ConnectNavHelper;
import org.commcare.core.process.CommCareInstanceInitializer;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.home.HomeActivityCoordinator;
import org.commcare.home.HomeActivityHost;
import org.commcare.home.SessionLaunchDelegate;
import org.commcare.home.SyncDelegate;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.preferences.AdvancedActionsPreferences;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.recovery.measures.RecoveryMeasuresHelper;
import org.commcare.services.CommCareSessionService;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.session.SessionNavigationResponder;
import org.commcare.session.SessionNavigator;
import org.commcare.suite.model.Endpoint;
import org.commcare.suite.model.EntityDatum;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.PostRequest;
import org.commcare.suite.model.RemoteRequestEntry;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.StackFrameStep;
import org.commcare.suite.model.Text;
import org.commcare.sync.FirebaseMessagingDataSyncer;
import org.commcare.tasks.DataPullTask;
import org.commcare.util.DatumUtil;
import org.commcare.util.LogTypes;
import org.commcare.utils.AppLogoutHelper;
import org.commcare.utils.CommCareUtil;
import org.commcare.utils.EntityDetailUtils;
import org.commcare.utils.SessionRegistrationHelper;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.dialogs.StandardAlertDialog;
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
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import static org.commcare.activities.DispatchActivity.SESSION_ENDPOINT_ARGUMENTS_BUNDLE;
import static org.commcare.activities.DispatchActivity.SESSION_ENDPOINT_ARGUMENTS_LIST;
import static org.commcare.activities.DispatchActivity.SESSION_ENDPOINT_ID;
import static org.commcare.activities.EntitySelectActivity.EXTRA_ENTITY_KEY;
import static org.commcare.connect.ConnectConstants.PERSONALID_MANAGED_LOGIN;
import static org.commcare.connect.database.ConnectTaskUtils.isLastTaskUpdateLaterThanLastSync;

/**
 * Manages all of the shared (mostly non-UI) components of a CommCare home screen: activity
 * lifecycle, implementation of available actions, session navigation, etc.
 */
public abstract class HomeScreenBaseActivity extends SessionAwareCommCareActivity<SyncDelegate>
        implements SessionNavigationResponder, HomeActivityHost {

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
    protected static final int MODEL_RESULT = SessionLaunchDelegate.FORM_ENTRY_REQUEST_CODE;
    protected static final int MAKE_REMOTE_POST = 5;
    public static final int GET_INCOMPLETE_FORM = HomeActivityCoordinator.FORM_ARCHIVE_REQUEST_CODE;
    protected static final int PREFERENCES_ACTIVITY = HomeActivityCoordinator.PREFERENCES_REQUEST_CODE;
    protected static final int ADVANCED_ACTIONS_ACTIVITY = HomeActivityCoordinator.ADVANCED_ACTIONS_REQUEST_CODE;
    protected static final int CREATE_PIN = HomeActivityCoordinator.CREATE_PIN_REQUEST_CODE;
    protected static final int AUTHENTICATION_FOR_PIN =
            HomeActivityCoordinator.AUTHENTICATION_FOR_PIN_REQUEST_CODE;

    private static final String KEY_PENDING_SESSION_DATA = "pending-session-data-id";
    private static final String KEY_PENDING_SESSION_DATUM_ID = "pending-session-datum-id";

    /**
     * Restart is a special CommCare activity result code which means that the session was
     * invalidated in the calling activity and that the current session should be resynced
     */
    public static final int RESULT_RESTART = 3;
    private static final String CC_APP_HOME_ENDPOINT = "cc_app_home";

    private SessionNavigator sessionNavigator;
    private boolean sessionNavigationProceedingAfterOnResume;

    // Indicates if 1 of the checks we performed in onCreate resulted in redirecting to a
    // different activity or starting a UI-blocking task
    private boolean redirectedInOnCreate = false;

    // This is to trigger a background sync after a form submission,
    private boolean shouldTriggerBackgroundSync = true;

    // This is to restore the selected entity when restarting EntityDetailActivity after a
    // background sync
    private String selectedEntityPostSync = null;

    private FirebaseMessagingDataSyncer dataSyncer;
    private boolean isVisible;

    private final HomeActivityCoordinator coordinator = new HomeActivityCoordinator(this);

    {
        dataSyncer = new FirebaseMessagingDataSyncer(this);
    }

    /**
     * The coordinator this home activity composes. Public so tests can assert on the launch/nav
     * state it now owns. Held here rather than in each concrete home activity for as long as this
     * class exists
     */
    public HomeActivityCoordinator getCoordinator() {
        return coordinator;
    }

    protected SyncDelegate getSyncDelegate() {
        return coordinator.getSync();
    }

    @NonNull
    @Override
    public CommCareActivity<?> getSyncConnector() {
        return this;
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return getSyncDelegate().generateProgressDialog(taskId);
    }

    @Override
    public SyncDelegate getReceiver() {
        return coordinator.getSync();
    }

    @Override
    protected SessionExpirationHandler getSessionExpirationHandler() {
        return coordinator.getSessionExpiration();
    }

    @Override
    public void onSessionLost() {
        SessionRegistrationHelper.redirectToLogin(this);
    }

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);

        updateLastSuccessfulCommCareVersion();
        sessionNavigator = new SessionNavigator(this);

        processFromExternalLaunch(savedInstanceState);
        processFromShortcutLaunch();
        processFromLoginLaunch();
    }

    private void updateLastSuccessfulCommCareVersion() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                CommCareApplication.instance());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(
                HiddenPreferences.LAST_SUCCESSFUL_CC_VERSION,
                ReportingUtils.getCommCareVersionString()
        );
        editor.apply();
    }

    /**
     * Set state that signifies activity was launch from external app
     */
    private void processFromExternalLaunch(Bundle savedInstanceState) {
        if (savedInstanceState == null && getIntent().hasExtra(DispatchActivity.WAS_EXTERNAL)) {
            coordinator.setWasExternal(true);
            if (getIntent().getBooleanExtra(DispatchActivity.CC_LAUNCH_REQUIRE_SYNC, false)) {
                getIntent().removeExtra(DispatchActivity.CC_LAUNCH_REQUIRE_SYNC);
                coordinator.setPendingEndpointNavigationAfterSync(true);
                redirectedInOnCreate = true;
                triggerSync(false);
            } else if (processSessionEndpoint()) {
                sessionNavigator.startNextSessionStep();
            }
        }
    }

    /**
     * @return Whether we should proceed with next step in session navigation after processing the session endpoint
     */
    private boolean processSessionEndpoint() {
        if (getIntent().hasExtra(SESSION_ENDPOINT_ID)) {
            String sessionEndpointId = getIntent().getStringExtra(SESSION_ENDPOINT_ID);
            if (CC_APP_HOME_ENDPOINT.equals(sessionEndpointId)) {
                return false;
            }
            Endpoint endpoint = validateIntentForSessionEndpoint(sessionEndpointId);
            if (endpoint != null) {
                Bundle intentArgumentsAsBundle = getIntent().getBundleExtra(
                        SESSION_ENDPOINT_ARGUMENTS_BUNDLE);
                ArrayList<String> intentArgumentsAsList = getIntent().getStringArrayListExtra(
                        SESSION_ENDPOINT_ARGUMENTS_LIST);

                // Reset the Session to make sure we don't carry forward any session state to the
                // endpoint launch
                CommCareApplication.instance().getCurrentSessionWrapper().reset();

                try {
                    CommCareApplication.instance().getCurrentSessionWrapper()
                            .executeEndpointStack(
                                    endpoint,
                                    intentArgumentsAsBundle,
                                    intentArgumentsAsList
                            );
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
                                            StringUtils.join(endpoint.getArguments(), ",")
                                    }
                            );
                    new UserfacingErrorHandling<>().createErrorDialog(
                            this,
                            invalidEndpointArgsError,
                            true
                    );
                }
            }
            return false;
        }
        return true;
    }

    private Endpoint validateIntentForSessionEndpoint(String sessionEndpointId) {
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
                            StringUtils.join(allEndpoints.keySet(), ",")
                    }
            );
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
                !coordinator.getLoginExtraWasConsumed()) {
            getIntent().removeExtra(DispatchActivity.START_FROM_LOGIN);
            coordinator.setLoginExtraWasConsumed(true);
            try {
                redirectedInOnCreate = coordinator.runLaunchChecks();
            } finally {
                // make sure this happens no matter what
                clearOneTimeLoginActionFlags();
            }
        }
    }

    /**
     * Regardless of what action(s) we ended up executing in the coordinator's runLaunchChecks(), we don't
     * want to end up trying the actions associated with these flags again at a later point. They
     * either need to happen the first time on login, or not at all.
     */
    private void clearOneTimeLoginActionFlags() {
        HiddenPreferences.setPostUpdateSyncNeeded(false);
        HiddenPreferences.clearInterruptedSSD();
        String username = CommCareApplication.instance().getSession().getLoggedInUser().getUsername();
        HiddenPreferences.clearPendingSyncRequest(username);
    }

    protected void goToFormArchive(boolean incomplete) {
        coordinator.showFormArchive(incomplete);
    }

    protected void userTriggeredLogout() {
        if (AppLogoutHelper.isBlockedByActiveSync(this)) {
            return;
        }
        CommCareApplication.instance().closeUserSession();
        setResult(RESULT_OK);
        finish();
    }

    protected void userPressedOpportunityStatus() {
        // Launch the seated app's job status page on top of this (still-live) Home so the app
        // session is preserved and backing out of the status page returns here.
        ConnectJobRecord job = Objects.requireNonNull(
                ConnectJobUtils.getJobForSeatedApp(this),
                "View Job Status pressed but no Connect job was found for the seated app"
        );
        ConnectNavHelper.INSTANCE.goToActiveInfoForJob(this, job);
    }

    @Override
    public void onActivityResultSessionSafe(int requestCode, int resultCode, Intent intent) {
        if (coordinator.onActivityResult(requestCode, resultCode, intent)) {
            return;
        }
        if (resultCode == RESULT_RESTART) {
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
                    if (!coordinator.getSessionLaunch().handleIncompleteFormResult(resultCode, intent)) {
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
                    continueWithSessionNav = coordinator.handleFormEntryResult(resultCode, intent);
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
                case MAKE_REMOTE_POST:
                    stepBackIfCancelled(resultCode);
                    if (resultCode == RESULT_OK) {
                        CommCareApplication.instance().getCurrentSessionWrapper().terminateSession();
                    }
                    break;
                case GET_REMOTE_DATA:
                    stepBackIfCancelled(resultCode);
                    break;
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
                    getSyncDelegate().performCustomRestoreFromFile(f);
                } else {
                    Toast.makeText(
                            this,
                            Localization.get("custom.restore.file.not.exist"),
                            Toast.LENGTH_LONG
                    ).show();
                }
            } else {
                Toast.makeText(
                        this,
                        Localization.get("custom.restore.file.not.set"),
                        Toast.LENGTH_LONG
                ).show();
            }
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    Localization.get("custom.restore.error"),
                    Toast.LENGTH_LONG
            ).show();
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

    private boolean processReturnFromGetCommand(int resultCode, Intent intent) {
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
            refreshUI();
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
            refreshUI();
        }
    }

    private static void stepBackIfCancelled(int resultCode) {
        if (resultCode == RESULT_CANCELED) {
            AndroidSessionWrapper asw = CommCareApplication.instance().getCurrentSessionWrapper();
            CommCareSession currentSession = asw.getSession();
            currentSession.stepBack(asw.getEvaluationContext());
        }
    }

    @Override
    public void startNextSessionStepSafe() {
        try {
            sessionNavigator.startNextSessionStep();
        } catch (CommCareInstanceInitializer.FixtureInitializationException e) {
            sessionNavigator.stepBack();
            if (isDemoUser()) {
                // most likely crashing due to data not being available in demo mode
                new UserfacingErrorHandling<>().createErrorDialog(
                        this,
                        Localization.get("demo.mode.feature.unavailable"),
                        false
                );
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

    private void clearSessionAndExit(AndroidSessionWrapper currentState, boolean shouldWarnUser) {
        coordinator.clearSessionAndExit(currentState, shouldWarnUser);
    }

    private void createErrorDialog(String errorMsg, AlertDialog.OnClickListener errorListener) {
        showAlertDialog(
                StandardAlertDialog.getBasicAlertDialogWithIcon(
                        Localization.get("app.handled.error.title"),
                        errorMsg,
                        android.R.drawable.ic_dialog_info,
                        errorListener
                )
        );
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
                coordinator.getSessionLaunch().startForm(asw);
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
        Toast.makeText(
                this,
                Localization.get("background.sync.form.entry.attempt.during.sync"),
                Toast.LENGTH_LONG
        ).show();
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
        createErrorDialog(
                text.evaluate(ec),
                (dialog, i) -> {
                    dialog.dismiss();
                    asw.getSession().stepBack(asw.getEvaluationContext());
                    HomeScreenBaseActivity.this.sessionNavigator.startNextSessionStep();
                }
        );
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
            refreshUI();
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
            i.putExtra(
                    PostRequestActivity.PARAMS_KEY,
                    (Serializable) postRequest.getEvaluatedParams(asw.getEvaluationContext(), false)
            );

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
                Intent detailIntent = new Intent(
                        getApplicationContext(),
                        EntityDetailActivity.class
                );
                EntityDetailUtils.populateDetailIntent(
                        detailIntent, contextRef, entityDatum, asw);
                addPendingDataExtra(detailIntent, session);
                addPendingDatumIdExtra(detailIntent, session);
                startActivityForResult(detailIntent, GET_CASE);
            }
        }
    }

    static Intent buildHomeIntent(
            Context context,
            LoginMode loginMode,
            boolean startFromLogin,
            boolean manualSwitchToPwMode,
            boolean personalIdManagedLogin
    ) {
        Intent intent = new Intent(context, StandardHomeActivity.class);
        intent.putExtra(DispatchActivity.START_FROM_LOGIN, startFromLogin);
        intent.putExtra(LoginActivity.LOGIN_MODE, loginMode);
        intent.putExtra(LoginActivity.MANUAL_SWITCH_TO_PW_MODE, manualSwitchToPwMode);
        intent.putExtra(PERSONALID_MANAGED_LOGIN, personalIdManagedLogin);
        return intent;
    }

    /**
     * The Home intent used by Connect app launches that bypass LoginActivity. Exposed so callers can
     * start it via {@code startActivityForResult} and react to the Home result (e.g. a back-out).
     */
    public static Intent buildHomeLaunchIntent(Context context) {
        return buildHomeIntent(context, LoginMode.PASSWORD, true, false, true);
    }

    protected static void addPendingDataExtra(Intent i, CommCareSession session) {
        EvaluationContext evalContext =
                CommCareApplication.instance().getCurrentSessionWrapper().getEvaluationContext();
        i.putExtra(KEY_PENDING_SESSION_DATA, session.getNeededData(evalContext));
    }

    private static void addPendingDatumIdExtra(Intent i, CommCareSession session) {
        i.putExtra(KEY_PENDING_SESSION_DATUM_ID, session.getNeededDatum().getDataId());
    }

    private void triggerSync(boolean triggeredByAutoSyncPending) {
        if (triggeredByAutoSyncPending) {
            long lastUploadSyncAttempt = HiddenPreferences.getLastUploadSyncAttempt();
            String footer = lastUploadSyncAttempt == 0 ? "never" :
                    SimpleDateFormat.getDateTimeInstance().format(lastUploadSyncAttempt);
            Logger.log(LogTypes.TYPE_USER, "autosync triggered. Last Sync|" + footer);
        }
        if (isVisible) {
            refreshUI();
        }
        getSyncDelegate().sendFormsOrSync(false);
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

        resetNavigationFlags();
    }

    private void resetNavigationFlags() {
        redirectedInOnCreate = false;
        sessionNavigationProceedingAfterOnResume = false;
        shouldTriggerBackgroundSync = true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        isVisible = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        isVisible = false;
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
            refreshUI();
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
        } else if (CommCareApplication.instance().isSyncPending()
                || isLastTaskUpdateLaterThanLastSync(this)) {
            triggerSync(true);
            kickedOff = true;
        } else if (coordinator.getAppUpdate().promptForUpdateIfNeeded(true)) {
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
    public void onPullTaskFinished(@NonNull DataPullTask.PullTaskResult result) {
        if (coordinator.getPendingEndpointNavigationAfterSync() && processSessionEndpoint()) {
            sessionNavigator.startNextSessionStep();
            coordinator.setPendingEndpointNavigationAfterSync(false);
        } else if (UpdateActivity.sBlockedUpdateWorkflowInProgress) {
            Intent i = new Intent(getApplicationContext(), UpdateActivity.class);
            i.putExtra(UpdateActivity.KEY_PROCEED_AUTOMATICALLY, true);

            if (result == DataPullTask.PullTaskResult.DOWNLOAD_SUCCESS) {
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

    @Override
    public boolean isDemoUser() {
        try {
            User u = CommCareApplication.instance().getSession().getLoggedInUser();
            return (User.TYPE_DEMO.equals(u.getUserType()));
        } catch (SessionUnavailableException e) {
            // Default to a normal user: this should only happen if session
            // expires and hasn't redirected to login.
            return false;
        }
    }

    /**
     * Per-action availability for the session-gated home activities for CC apps
     */
    @Override
    public boolean areAppActionsAvailable() {
        return !isDemoUser();
    }

    @NonNull
    @Override
    public Context getHostContext() {
        return this;
    }

    @NonNull
    @Override
    public AppCompatActivity getHostActivity() {
        return this;
    }

    @Override
    public void refreshHostUi() {
        refreshUI();
    }

    @Override
    public Intent getLaunchIntent() {
        return getIntent();
    }

    @Override
    public String getFormEntryTitle() {
        return CommCareActivity.getTitle(this, null);
    }

    @Override
    public void startNextSessionStep() {
        sessionNavigator.startNextSessionStep();
    }

    @Override
    public void showFormArchive(boolean incomplete, FormRecord record) {
        coordinator.showFormArchive(incomplete, record);
    }

    @Override
    public void finishWithResult(int resultCode) {
        setResult(resultCode);
        finish();
    }

    @Override
    public void showXPathErrorAndExit(@NonNull XPathException exception) {
        new UserfacingErrorHandling<>().logErrorAndShowDialog(this, exception, true);
    }

    @Override
    public void onFormEntryLaunching() {
        shouldTriggerBackgroundSync = false;
    }

    public static void createPreferencesMenu(AppCompatActivity activity) {
        activity.startActivityForResult(
                HomeActivityCoordinator.buildPreferencesIntent(activity),
                PREFERENCES_ACTIVITY
        );
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
            getSyncDelegate().setFormAndDataSyncer(formAndDataSyncer);
        } else {
            throw new RuntimeException("On principal of design, only meant for testing purposes");
        }
    }

    abstract void refreshUI();

    @Override
    public ReentrantLock getBackgroundSyncLock() {
        return CommCareSessionService.sessionAliveLock;
    }
}
