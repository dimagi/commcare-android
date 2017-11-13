package org.commcare.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.activities.components.FormEntryConstants;
import org.commcare.activities.components.FormEntryInstanceState;
import org.commcare.activities.components.FormEntrySessionWrapper;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.core.process.CommCareInstanceInitializer;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.google.services.ads.AdMobManager;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.heartbeat.UpdatePromptHelper;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.AdvancedActionsPreferences;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.provider.FormsProviderAPI;
import org.commcare.provider.InstanceProviderAPI;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.session.SessionNavigationResponder;
import org.commcare.session.SessionNavigator;
import org.commcare.suite.model.EntityDatum;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.PostRequest;
import org.commcare.suite.model.RemoteRequestEntry;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.StackFrameStep;
import org.commcare.suite.model.Text;
import org.commcare.tasks.FormLoaderTask;
import org.commcare.tasks.FormRecordCleanupTask;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.AndroidInstanceInitializer;
import org.commcare.utils.ChangeLocaleUtil;
import org.commcare.utils.CrashUtil;
import org.commcare.utils.EntityDetailUtils;
import org.commcare.utils.GlobalConstants;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.model.User;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xpath.XPathTypeMismatchException;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Vector;

/**
 * Manages all of the shared (mostly non-UI) components of a CommCare home screen:
 * activity lifecycle, implementation of available actions, session navigation, etc.
 */
public abstract class HomeScreenBaseActivity<T> extends SyncCapableCommCareActivity<T>
        implements SessionNavigationResponder {

    /**
     * Request code for launching a menu list or menu grid
     */
    public static final int GET_COMMAND = 1;
    /**
     * Request code for launching EntitySelectActivity (to allow user to select a case),
     * or EntityDetailActivity (to allow user to confirm an auto-selected case)
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
    // different activity
    private boolean redirectedInOnCreate = true;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        loadInstanceState(savedInstanceState);
        CrashUtil.registerAppData();
        AdMobManager.initAdsForCurrentConsumerApp(getApplicationContext());
        sessionNavigator = new SessionNavigator(this);

        processFromExternalLaunch(savedInstanceState);
        processFromShortcutLaunch();
        processFromLoginLaunch();
    }

    private void loadInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            loginExtraWasConsumed = savedInstanceState.getBoolean(EXTRA_CONSUMED_KEY);
            wasExternal = savedInstanceState.getBoolean(WAS_EXTERNAL_KEY);
        }
    }

    /**
     * Set state that signifies activity was launch from external app.
     */
    private void processFromExternalLaunch(Bundle savedInstanceState) {
        if (savedInstanceState == null && getIntent().hasExtra(DispatchActivity.WAS_EXTERNAL)) {
            wasExternal = true;
            sessionNavigator.startNextSessionStep();
        }
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
            redirectedInOnCreate = doLoginLaunchChecksInOrder();
        }
    }

    /**
     * The order of operations in this method is very deliberate, and the logic for it is as
     * follows:
     * - If we're in demo mode, then we don't want to do any of the other checks because they're
     * not relevant
     * - Form and session restorations need to happen before we try to sync, because once we sync
     * it could invalidate those states.
     * - tryRestoringFormFromSessionExpiration() comes before tryRestoringSession() because it is
     * of higher importance
     * - Once we're past the first 3, starting a background form-send process is safe
     */
    private boolean doLoginLaunchChecksInOrder() {
        if (isDemoUser()) {
            showDemoModeWarning();
            return false;
        }

        if (tryRestoringFormFromSessionExpiration()) {
            return true;
        }

        if (tryRestoringSession()) {
            return true;
        }

        if (!CommCareApplication.instance().isSyncPending(false)) {
            // Trigger off a regular unsent task processor, unless we're about to sync (which will
            // then handle this in a blocking fashion)
            checkAndStartUnsentFormsTask(false, false);
        }

        checkForPinLaunchConditions();

        return false;
    }

    private boolean tryRestoringFormFromSessionExpiration() {
        SessionStateDescriptor existing = AndroidSessionWrapper.getFormStateForInterruptedUserSession();
        if (existing != null) {
            AndroidSessionWrapper state = CommCareApplication.instance().getCurrentSessionWrapper();
            state.loadFromStateDescription(existing);
            formEntry(CommCareApplication.instance().getCommCarePlatform()
                    .getFormContentUri(state.getSession().getForm()), state.getFormRecord());
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
        } else if (loginMode == LoginMode.PASSWORD && DeveloperPreferences.shouldOfferPinForLogin()) {
            boolean userManuallyEnteredPasswordMode = getIntent()
                    .getBooleanExtra(LoginActivity.MANUAL_SWITCH_TO_PW_MODE, false);
            boolean alreadyDismissedPinCreation =
                    CommCareApplication.instance().getCurrentApp().getAppPreferences()
                            .getBoolean(CommCarePreferences.HAS_DISMISSED_PIN_CREATION, false);
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
                Localization.get("pin.dialog.yes"), -1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissAlertDialog();
                launchPinCreateScreen(loginMode);
            }
        });

        DialogChoiceItem nextTimeChoice = new DialogChoiceItem(
                Localization.get("pin.dialog.not.now"), -1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissAlertDialog();
            }
        });

        DialogChoiceItem notAgainChoice = new DialogChoiceItem(
                Localization.get("pin.dialog.never"), -1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissAlertDialog();
                CommCareApplication.instance().getCurrentApp().getAppPreferences()
                        .edit()
                        .putBoolean(CommCarePreferences.HAS_DISMISSED_PIN_CREATION, true)
                        .commit();
                showPinFutureAccessDialog();
            }
        });


        dialog.setChoiceItems(new DialogChoiceItem[]{createPinChoice, nextTimeChoice, notAgainChoice});
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

        AdapterView.OnItemClickListener listClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String[] localeCodes = ChangeLocaleUtil.getLocaleCodes();
                if (position >= localeCodes.length) {
                    Localization.setLocale("default");
                } else {
                    String selectedLocale = localeCodes[position];
                    CommCarePreferences.setCurrentLocale(selectedLocale);
                    Localization.setLocale(selectedLocale);
                }
                // rebuild home buttons in case language changed;
                if (uiController != null) {
                    uiController.setupUI();
                }
                rebuildOptionsMenu();
                dismissAlertDialog();
            }
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
        if (incomplete) {
            FirebaseAnalyticsUtil.reportViewArchivedFormsList(AnalyticsParamValue.INCOMPLETE);
        } else {
            FirebaseAnalyticsUtil.reportViewArchivedFormsList(AnalyticsParamValue.SAVED);
        }
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
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_RESTART) {
            sessionNavigator.startNextSessionStep();
        } else {
            // if handling new return code (want to return to home screen) but a return at the end of your statement
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
                        refreshUI();
                        return;
                    } else if (resultCode == RESULT_OK) {
                        int record = intent.getIntExtra("FORMRECORDS", -1);
                        if (record == -1) {
                            //Hm, what to do here?
                            break;
                        }
                        FormRecord r = CommCareApplication.instance().getUserStorage(FormRecord.class).read(record);

                        //Retrieve and load the appropriate ssd
                        SqlStorage<SessionStateDescriptor> ssdStorage = CommCareApplication.instance().getUserStorage(SessionStateDescriptor.class);
                        Vector<Integer> ssds = ssdStorage.getIDsForValue(SessionStateDescriptor.META_FORM_RECORD_ID, r.getID());
                        AndroidSessionWrapper currentState =
                                CommCareApplication.instance().getCurrentSessionWrapper();
                        if (ssds.size() == 1) {
                            currentState.loadFromStateDescription(ssdStorage.read(ssds.firstElement()));
                        } else {
                            currentState.setFormRecordId(r.getID());
                        }

                        AndroidCommCarePlatform platform = CommCareApplication.instance().getCommCarePlatform();
                        formEntry(platform.getFormContentUri(r.getFormNamespace()), r);
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
                    boolean choseRememberPassword = intent != null && intent.getBooleanExtra(CreatePinActivity.CHOSE_REMEMBER_PASSWORD, false);
                    if (choseRememberPassword) {
                        CommCareApplication.instance().closeUserSession();
                    } else if (resultCode == RESULT_OK) {
                        Toast.makeText(this, Localization.get("pin.set.success"), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, Localization.get("pin.not.set"), Toast.LENGTH_SHORT).show();
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
            }
            sessionNavigationProceedingAfterOnResume = true;
            startNextSessionStepSafe();
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    private void performCustomRestore() {
        try {
            String filePath = DeveloperPreferences.getCustomRestoreDocLocation();
            if (filePath != null && !filePath.isEmpty()) {
                File f = new File(filePath);
                if (f.exists()) {
                    formAndDataSyncer.performCustomRestoreFromFile(this, f);
                } else {
                    Toast.makeText(this, Localization.get("custom.restore.file.not.exist"), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, Localization.get("custom.restore.file.not.set"), Toast.LENGTH_LONG).show();
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
            String sessionDatumId = currentSession.getNeededDatum().getDataId();
            String chosenCaseId = intent.getStringExtra(SessionFrame.STATE_DATUM_VAL);
            currentSession.setDatum(sessionDatumId, chosenCaseId);
            return true;
        } else {
            clearSessionAndExit(asw, true);
            return false;
        }
    }

    private boolean processCanceledGetCommandOrCase() {
        AndroidSessionWrapper currentState =
                CommCareApplication.instance().getCurrentSessionWrapper();
        if (currentState.getSession().getCommand() == null) {
            // Needed a command, and didn't already have one. Stepping back from
            // an empty state, Go home!
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
            int formProcessCount = intent.getIntExtra(AdvancedActionsPreferences.FORM_PROCESS_COUNT_KEY, 0);
            String localizationKey = intent.getStringExtra(AdvancedActionsPreferences.FORM_PROCESS_MESSAGE_KEY);
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

    public void startNextSessionStepSafe() {
        try {
            sessionNavigator.startNextSessionStep();
        } catch (CommCareInstanceInitializer.FixtureInitializationException e) {
            sessionNavigator.stepBack();
            if (isDemoUser()) {
                // most likely crashing due to data not being available in demo mode
                UserfacingErrorHandling.createErrorDialog(this,
                        Localization.get("demo.mode.feature.unavailable"),
                        false);
            } else {
                UserfacingErrorHandling.createErrorDialog(this, e.getMessage(), false);
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
        boolean datumIdsUnchanged = intentDatum == null || intentDatum.equals(session.getNeededDatum().getDataId());
        return neededDataUnchanged && datumIdsUnchanged;
    }

    /**
     * Process user returning home from the form entry activity.
     * Triggers form submission cycle, cleans up some session state.
     *
     * @param resultCode exit code of form entry activity
     * @param intent     The intent of the returning activity, with the
     *                   saved form provided as the intent URI data. Null if
     *                   the form didn't exit cleanly
     * @return Flag signifying that caller should fetch the next activity in
     * the session to launch. If false then caller should exit or spawn home
     * activity.
     */
    private boolean processReturnFromFormEntry(int resultCode, Intent intent) {
        // TODO: We might need to load this from serialized state?
        AndroidSessionWrapper currentState = CommCareApplication.instance().getCurrentSessionWrapper();

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
        if ((intent != null && intent.getBooleanExtra(FormEntryConstants.IS_ARCHIVED_FORM, false)) ||
                FormRecord.STATUS_COMPLETE.equals(current.getStatus()) ||
                FormRecord.STATUS_SAVED.equals(current.getStatus())) {
            // Viewing an old form, so don't change the historical record
            // regardless of the exit code
            currentState.reset();
            if (wasExternal) {
                setResult(RESULT_CANCELED);
                this.finish();
            } else {
                // Return to where we started
                goToFormArchive(false, current);
            }
            return false;
        }

        if (resultCode == RESULT_OK) {
            // Determine if the form instance is complete
            Uri resultInstanceURI = null;
            if (intent != null) {
                resultInstanceURI = intent.getData();
            }
            if (resultInstanceURI == null) {
                CommCareApplication.notificationManager().reportNotificationMessage(
                        NotificationMessageFactory.message(
                                NotificationMessageFactory.StockMessages.FormEntry_Unretrievable));
                Toast.makeText(this,
                        "Error while trying to read the form! See the notification",
                        Toast.LENGTH_LONG).show();
                Logger.log(LogTypes.TYPE_ERROR_WORKFLOW,
                        "Form Entry did not return a form");
                clearSessionAndExit(currentState, true);
                return false;
            }

            Cursor c = null;
            String instanceStatus;
            try {
                c = getContentResolver().query(resultInstanceURI, null, null, null, null);
                if (!c.moveToFirst()) {
                    throw new IllegalArgumentException("Empty query for instance record!");
                }
                instanceStatus = c.getString(c.getColumnIndexOrThrow(InstanceProviderAPI.InstanceColumns.STATUS));
            } finally {
                if (c != null) {
                    c.close();
                }
            }
            // was the record marked complete?
            boolean complete = InstanceProviderAPI.STATUS_COMPLETE.equals(instanceStatus);

            // The form is either ready for processing, or not, depending on how it was saved
            if (complete) {
                // Now that we know this form is completed, we can give it the next available
                // submission ordering number
                current.setFormNumberForSubmissionOrdering(StorageUtils.getNextFormSubmissionNumber());
                CommCareApplication.instance().getUserStorage(FormRecord.class).write(current);
                checkAndStartUnsentFormsTask(false, false);
                refreshUI();

                if (wasExternal) {
                    setResult(RESULT_CANCELED);
                    this.finish();
                    return false;
                }

                // Before we can terminate the session, we need to know that the form has been
                // processed in case there is state that depends on it.
                boolean terminateSuccessful;
                try {
                    terminateSuccessful = currentState.terminateSession();
                } catch (XPathTypeMismatchException e) {
                    UserfacingErrorHandling.logErrorAndShowDialog(this, e, true);
                    return false;
                }
                if (!terminateSuccessful) {
                    // If we didn't find somewhere to go, we're gonna stay here
                    return false;
                }
                // Otherwise, we want to keep proceeding in order
                // to keep running the workflow
            } else {
                // Form record is now stored.
                // TODO: session state clearing might be something we want to do in InstanceProvider.bindToFormRecord.
                clearSessionAndExit(currentState, false);
                return false;
            }
        } else if (resultCode == RESULT_CANCELED) {
            // Nothing was saved during the form entry activity

            Logger.log(LogTypes.TYPE_FORM_ENTRY, "Form Entry Cancelled");

            // If the form was unstarted, we want to wipe the record.
            if (current.getStatus().equals(FormRecord.STATUS_UNSTARTED)) {
                // Entry was cancelled.
                FormRecordCleanupTask.wipeRecord(this, currentState);
            }

            if (wasExternal) {
                currentState.reset();
                setResult(RESULT_CANCELED);
                this.finish();
                return false;
            } else if (current.getStatus().equals(FormRecord.STATUS_INCOMPLETE)) {
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

    private void clearSessionAndExit(AndroidSessionWrapper currentState, boolean shouldWarnUser) {
        currentState.reset();
        if (wasExternal) {
            setResult(RESULT_CANCELED);
            this.finish();
        }
        refreshUI();
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
        showAlertDialog(StandardAlertDialog.getBasicAlertDialogWithIcon(this,
                Localization.get("demo.mode.warning.title"), Localization.get("demo.mode.warning"),
                android.R.drawable.ic_dialog_info, null));
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
                UserfacingErrorHandling
                        .logErrorAndShowDialog(this, sessionNavigator.getCurrentException(), false);
                asw.reset();
                break;
            case SessionNavigator.REPORT_CASE_AUTOSELECT:
                FirebaseAnalyticsUtil.reportFeatureUsage(AnalyticsParamValue.FEATURE_CASE_AUTOSELECT);
                break;
        }
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
        createErrorDialog(text.evaluate(ec), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                dismissAlertDialog();
                asw.getSession().stepBack(asw.getEvaluationContext());
                HomeScreenBaseActivity.this.sessionNavigator.startNextSessionStep();
            }
        });
    }

    private void handleNoFormFromSessionNav(AndroidSessionWrapper asw) {
        boolean terminateSuccesful;
        try {
            terminateSuccesful = asw.terminateSession();
        } catch (XPathTypeMismatchException e) {
            UserfacingErrorHandling.logErrorAndShowDialog(this, e, true);
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
            i.putExtra(PostRequestActivity.PARAMS_KEY,
                    new HashMap<>(postRequest.getEvaluatedParams(asw.getEvaluationContext())));

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
            i.putExtra(EntitySelectActivity.EXTRA_ENTITY_KEY, lastPopped.getValue());
        }
        addPendingDataExtra(i, session);
        addPendingDatumIdExtra(i, session);
        return i;
    }

    public void launchUpdateActivity() {
        Intent i = new Intent(getApplicationContext(), UpdateActivity.class);
        startActivity(i);
    }

    // Launch an intent to load the confirmation screen for the current selection
    private void launchConfirmDetail(AndroidSessionWrapper asw) {
        CommCareSession session = asw.getSession();
        SessionDatum selectDatum = session.getNeededDatum();
        if (selectDatum instanceof EntityDatum) {
            EntityDatum entityDatum = (EntityDatum)selectDatum;
            TreeReference contextRef = sessionNavigator.getCurrentAutoSelection();
            if (this.getString(R.string.panes).equals("two")
                    && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Large tablet in landscape: send to entity select activity
                // (awesome mode, with case pre-selected) instead of entity detail
                Intent i = getSelectIntent(session);
                String caseId = EntityDatum.getCaseIdFromReference(
                        contextRef, entityDatum, asw.getEvaluationContext());
                i.putExtra(EntitySelectActivity.EXTRA_ENTITY_KEY, caseId);
                startActivityForResult(i, GET_CASE);
            } else {
                // Launch entity detail activity
                Intent detailIntent = new Intent(getApplicationContext(), EntityDetailActivity.class);
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
     * Create (or re-use) a form record and pass it to the form entry activity
     * launcher. If there is an existing incomplete form that uses the same
     * case, ask the user if they want to edit or delete that one.
     *
     * @param state Needed for FormRecord manipulations
     */
    private void startFormEntry(AndroidSessionWrapper state) {
        if (state.getFormRecordId() == -1) {
            if (CommCarePreferences.isIncompleteFormsEnabled()) {
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
            Logger.log(LogTypes.TYPE_FORM_ENTRY, "Somehow ended up starting form entry with old state?");
        }

        FormRecord record = state.getFormRecord();
        AndroidCommCarePlatform platform = CommCareApplication.instance().getCommCarePlatform();
        formEntry(platform.getFormContentUri(record.getFormNamespace()), record,
                CommCareActivity.getTitle(this, null));
    }

    private void formEntry(Uri formUri, FormRecord r) {
        formEntry(formUri, r, null);
    }

    private void formEntry(Uri formUri, FormRecord r, String headerTitle) {
        Logger.log(LogTypes.TYPE_FORM_ENTRY, "Form Entry Starting|" + r.getFormNamespace());

        //TODO: This is... just terrible. Specify where external instance data should come from
        FormLoaderTask.iif = new AndroidInstanceInitializer(CommCareApplication.instance().getCurrentSession());

        // Create our form entry activity callout
        Intent i = new Intent(getApplicationContext(), FormEntryActivity.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(FormEntryInstanceState.KEY_INSTANCEDESTINATION, CommCareApplication.instance().getCurrentApp().fsPath((GlobalConstants.FILE_CC_FORMS)));

        // See if there's existing form data that we want to continue entering
        // (note, this should be stored in the form record as a URI link to
        // the instance provider in the future)
        if (r.getInstanceURI() != null) {
            i.setData(r.getInstanceURI());
        } else {
            i.setData(formUri);
        }

        i.putExtra(FormEntryActivity.KEY_RESIZING_ENABLED, CommCarePreferences.getResizeMethod());
        i.putExtra(FormEntryActivity.KEY_INCOMPLETE_ENABLED, CommCarePreferences.isIncompleteFormsEnabled());
        i.putExtra(FormEntryActivity.KEY_AES_STORAGE_KEY, Base64.encodeToString(r.getAesKey(), Base64.DEFAULT));
        i.putExtra(FormEntryActivity.KEY_FORM_CONTENT_URI, FormsProviderAPI.FormsColumns.CONTENT_URI.toString());
        i.putExtra(FormEntryActivity.KEY_INSTANCE_CONTENT_URI, InstanceProviderAPI.InstanceColumns.CONTENT_URI.toString());
        i.putExtra(FormEntrySessionWrapper.KEY_RECORD_FORM_ENTRY_SESSION, DeveloperPreferences.isSessionSavingEnabled());
        if (headerTitle != null) {
            i.putExtra(FormEntryActivity.KEY_HEADER_STRING, headerTitle);
        }
        if (isRestoringSession) {
            isRestoringSession = false;
            SharedPreferences prefs =
                    CommCareApplication.instance().getCurrentApp().getAppPreferences();
            String formEntrySession = prefs.getString(CommCarePreferences.CURRENT_FORM_ENTRY_SESSION, "");
            if (!"".equals(formEntrySession)) {
                i.putExtra(FormEntrySessionWrapper.KEY_FORM_ENTRY_SESSION, formEntrySession);
            }
        }

        startActivityForResult(i, MODEL_RESULT);
    }

    private void triggerSync(boolean triggeredByAutoSyncPending) {
        if (triggeredByAutoSyncPending) {
            long lastSync = CommCareApplication.instance().getCurrentApp().getAppPreferences()
                    .getLong(CommCarePreferences.LAST_SYNC_ATTEMPT, 0);
            String footer = lastSync == 0 ? "never" :
                    SimpleDateFormat.getDateTimeInstance().format(lastSync);
            Logger.log(LogTypes.TYPE_USER, "autosync triggered. Last Sync|" + footer);
        }

        refreshUI();
        sendFormsOrSync(false);
    }

    @Override
    protected void onResumeSessionSafe() {
        if (!redirectedInOnCreate && !sessionNavigationProceedingAfterOnResume) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                refreshActionBar();
            }
            attemptDispatchHomeScreen();
        }

        // reset these
        redirectedInOnCreate = false;
        sessionNavigationProceedingAfterOnResume = false;
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
     *
     * @return true if we kicked off any processes
     */
    private boolean checkForPendingAppHealthActions() {
        boolean result = false;
        if (CommCareApplication.instance().isPostUpdateSyncNeeded() && !isDemoUser()) {
            CommCarePreferences.setPostUpdateSyncNeeded(false);
            triggerSync(false);
            result = true;
        } else if (CommCareApplication.instance().isSyncPending(false)) {
            triggerSync(true);
            result = true;
        } else if (UpdatePromptHelper.promptForUpdateIfNeeded(this)) {
            result = true;
        }

        CommCareApplication.instance().getSession().setAppHealthChecksCompleted();
        return result;
    }

    private void createAskUseOldDialog(final AndroidSessionWrapper state, final SessionStateDescriptor existing) {
        final AndroidCommCarePlatform platform = CommCareApplication.instance().getCommCarePlatform();
        String title = Localization.get("app.workflow.incomplete.continue.title");
        String msg = Localization.get("app.workflow.incomplete.continue");
        StandardAlertDialog d = new StandardAlertDialog(this, title, msg);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE:
                        // use the old form instance and load the it's state from the descriptor
                        state.loadFromStateDescription(existing);
                        formEntry(platform.getFormContentUri(state.getSession().getForm()), state.getFormRecord());
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        // delete the old incomplete form
                        FormRecordCleanupTask.wipeRecord(HomeScreenBaseActivity.this, existing);
                        // fallthrough to new now that old record is gone
                    case DialogInterface.BUTTON_NEUTRAL:
                        // create a new form record and begin form entry
                        state.commitStub();
                        formEntry(platform.getFormContentUri(state.getSession().getForm()), state.getFormRecord());
                }
                dismissAlertDialog();
            }
        };
        d.setPositiveButton(Localization.get("option.yes"), listener);
        d.setNegativeButton(Localization.get("app.workflow.incomplete.continue.option.delete"), listener);
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

    public static void createPreferencesMenu(Activity activity) {
        Intent i = new Intent(activity, SessionAwarePreferenceActivity.class);
        i.putExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE, CommCarePreferenceActivity.PREF_TYPE_COMMCARE);
        activity.startActivityForResult(i, PREFERENCES_ACTIVITY);
    }

    protected void showAdvancedActionsPreferences() {
        Intent intent = new Intent(this, SessionAwarePreferenceActivity.class);
        intent.putExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE, CommCarePreferenceActivity.PREF_TYPE_ADVANCED_ACTIONS);
        startActivityForResult(intent, ADVANCED_ACTIONS_ACTIVITY);
    }

    protected void showAboutCommCareDialog() {
        CommCareAlertDialog dialog = DialogCreationHelpers.buildAboutCommCareDialog(this);
        dialog.makeCancelable();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                handleDeveloperModeClicks();
            }
        });
        showAlertDialog(dialog);
    }

    private void handleDeveloperModeClicks() {
        mDeveloperModeClicks++;
        if (mDeveloperModeClicks == 4) {
            CommCareApplication.instance().getCurrentApp().getAppPreferences()
                    .edit()
                    .putString(DeveloperPreferences.SUPERUSER_ENABLED, CommCarePreferences.YES)
                    .commit();
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

    abstract void refreshUI();

}
