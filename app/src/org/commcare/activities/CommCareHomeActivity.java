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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.core.process.CommCareInstanceInitializer;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.ConnectorWithResultCallback;
import org.commcare.interfaces.WithUIController;
import org.commcare.logging.AndroidLogger;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.provider.FormsProviderAPI;
import org.commcare.provider.InstanceProviderAPI;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.session.SessionNavigationResponder;
import org.commcare.session.SessionNavigator;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.EntityDatum;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.StackFrameStep;
import org.commcare.suite.model.RemoteRequestEntry;
import org.commcare.suite.model.PostRequest;
import org.commcare.suite.model.Text;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.FormLoaderTask;
import org.commcare.tasks.FormRecordCleanupTask;
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.tasks.PullTaskReceiver;
import org.commcare.tasks.ResultAndError;
import org.commcare.utils.ACRAUtil;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.AndroidInstanceInitializer;
import org.commcare.utils.ChangeLocaleUtil;
import org.commcare.utils.ConnectivityStatus;
import org.commcare.utils.EntityDetailUtils;
import org.commcare.utils.GlobalConstants;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.NotificationMessageFactory.StockMessages;
import org.javarosa.core.model.User;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xpath.XPathTypeMismatchException;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class CommCareHomeActivity
        extends SessionAwareCommCareActivity<CommCareHomeActivity>
        implements SessionNavigationResponder, WithUIController,
                   PullTaskReceiver, ConnectorWithResultCallback<CommCareHomeActivity> {

    private static final String TAG = CommCareHomeActivity.class.getSimpleName();

    /**
     * Request code for launching a menu list or menu grid
     */
    private static final int GET_COMMAND = 1;

    /**
     * Request code for launching EntitySelectActivity (to allow user to select a case),
     * or EntityDetailActivity (to allow user to confirm an auto-selected case)
     */
    private static final int GET_CASE = 2;
    private static final int GET_REMOTE_DATA = 3;
    private static final int MAKE_REMOTE_POST = 5;

    /**
     * Request code for launching FormEntryActivity
     */
    private static final int MODEL_RESULT = 4;

    public static final int GET_INCOMPLETE_FORM = 16;
    public static final int REPORT_PROBLEM_ACTIVITY = 64;

    private static final int PREFERENCES_ACTIVITY=512;
    private static final int ADVANCED_ACTIONS_ACTIVITY=1024;

    private static final int CREATE_PIN = 16384;
    private static final int AUTHENTICATION_FOR_PIN = 32768;

    private static final int MENU_UPDATE = Menu.FIRST;
    private static final int MENU_SAVED_FORMS = Menu.FIRST + 1;
    private static final int MENU_CHANGE_LANGUAGE = Menu.FIRST + 2;
    private static final int MENU_PREFERENCES = Menu.FIRST + 3;
    private static final int MENU_ADVANCED = Menu.FIRST + 4;
    private static final int MENU_ABOUT = Menu.FIRST + 5;
    private static final int MENU_PIN = Menu.FIRST + 6;

    /**
     * Restart is a special CommCare return code which means that the session was invalidated in the
     * calling activity and that the current session should be resynced
     */
    public static final int RESULT_RESTART = 3;

    private static final String KEY_PENDING_SESSION_DATA = "pending-session-data-id";
    private static final String KEY_PENDING_SESSION_DATUM_ID = "pending-session-datum-id";

    private static final String AIRPLANE_MODE_CATEGORY = "airplane-mode";
    public static final String MENU_STYLE_GRID = "grid";

    // The API allows for external calls. When this occurs, redispatch to their
    // activity instead of commcare.
    private boolean wasExternal = false;
    private static final String WAS_EXTERNAL_KEY = "was_external";

    private int mDeveloperModeClicks = 0;

    private HomeActivityUIController uiController;
    private SessionNavigator sessionNavigator;
    private FormAndDataSyncer formAndDataSyncer;

    private boolean loginExtraWasConsumed;
    private static final String EXTRA_CONSUMED_KEY = "login_extra_was_consumed";
    private boolean isRestoringSession = false;
    private boolean isSyncUserLaunched = false;

    private boolean sessionNavigationProceedingAfterOnResume;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);

        loadInstanceState(savedInstanceState);

        ACRAUtil.registerAppData();
        uiController.setupUI();
        sessionNavigator = new SessionNavigator(this);
        formAndDataSyncer = new FormAndDataSyncer();

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

            CommCareSession session = CommCareApplication._().getCurrentSession();
            if (session.getCommand() != null) {
                // restore the session state if there is a command.
                // For debugging and occurs when a serialized
                // session is stored upon login
                isRestoringSession = true;
                sessionNavigator.startNextSessionStep();
                return;
            }

            // Trigger off a regular unsent task processor, unless we're about to sync (which will
            // then handle this in a blocking fashion)
            if (!CommCareApplication._().isSyncPending(false)) {
                checkAndStartUnsentFormsTask(false, false);
            }

            if (isDemoUser()) {
                showDemoModeWarning();
            }

            checkForPinLaunchConditions();
        }
    }

    // See if we should launch either the pin choice dialog, or the create pin activity directly
    private void checkForPinLaunchConditions() {
        LoginMode loginMode = (LoginMode)getIntent().getSerializableExtra(LoginActivity.LOGIN_MODE);

        if (loginMode == LoginMode.PRIMED) {
            launchPinCreateScreen(loginMode);
            return;
        }

        if (loginMode == LoginMode.PASSWORD) {
            boolean pinCreationEnabledForApp = DeveloperPreferences.shouldOfferPinForLogin();
            if (!pinCreationEnabledForApp) {
                return;
            }

            boolean userManuallyEnteredPasswordMode = getIntent()
                    .getBooleanExtra(LoginActivity.MANUAL_SWITCH_TO_PW_MODE, false);
            boolean alreadyDismissedPinCreation =
                    CommCareApplication._().getCurrentApp().getAppPreferences()
                            .getBoolean(CommCarePreferences.HAS_DISMISSED_PIN_CREATION, false);
            if (!alreadyDismissedPinCreation || userManuallyEnteredPasswordMode) {
                showPinChoiceDialog(loginMode);
            }
        }
    }

    private void showPinChoiceDialog(final LoginMode loginMode) {
        String promptMessage;
        UserKeyRecord currentUserRecord = CommCareApplication._().getRecordForCurrentUser();
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
                        CommCareApplication._().getCurrentApp().getAppPreferences()
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

    private void launchPinAuthentication() {
        Intent i = new Intent(this, PinAuthenticationActivity.class);
        startActivityForResult(i, AUTHENTICATION_FOR_PIN);
    }

    private void launchPinCreateScreen(LoginMode loginMode) {
        Intent i = new Intent(this, CreatePinActivity.class);
        i.putExtra(LoginActivity.LOGIN_MODE, loginMode);
        startActivityForResult(i, CREATE_PIN);
    }

    protected void goToFormArchive(boolean incomplete) {
        goToFormArchive(incomplete, null);
    }

    private void showLocaleChangeMenu() {
        final PaneledChoiceDialog dialog =
                new PaneledChoiceDialog(this, Localization.get("home.menu.locale.select"));

        AdapterView.OnItemClickListener listClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String[] localeCodes = ChangeLocaleUtil.getLocaleCodes();
                if (position >= localeCodes.length) {
                    Localization.setLocale("default");
                } else {
                    Localization.setLocale(localeCodes[position]);
                }
                // rebuild home buttons in case language changed;
                uiController.setupUI();
                rebuildOptionMenu();
                dismissAlertDialog();
            }
        };

        dialog.setChoiceItems(buildLocaleChoices(), listClickListener);
        showAlertDialog(dialog);
    }

    private static DialogChoiceItem[] buildLocaleChoices() {
        String[] locales = ChangeLocaleUtil.getLocaleNames();
        DialogChoiceItem[] choices =new DialogChoiceItem[locales.length];
        for (int i = 0; i < choices.length; i++) {
            choices[i] = DialogChoiceItem.nonListenerItem(locales[i]);
        }
        return choices;
    }

    private void goToFormArchive(boolean incomplete, FormRecord record) {
        if (incomplete) {
            GoogleAnalyticsUtils.reportViewArchivedFormsList(GoogleAnalyticsFields.LABEL_INCOMPLETE);
        } else {
            GoogleAnalyticsUtils.reportViewArchivedFormsList(GoogleAnalyticsFields.LABEL_COMPLETE);
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

    void enterRootModule() {
        Intent i;
        if (useGridMenu(org.commcare.suite.model.Menu.ROOT_MENU_ID)) {
            i = new Intent(this, MenuGrid.class);
        } else {
            i = new Intent(this, MenuList.class);
        }
        addPendingDataExtra(i, CommCareApplication._().getCurrentSessionWrapper().getSession());
        startActivityForResult(i, GET_COMMAND);
    }

    private boolean useGridMenu(String menuId) {
        // first check if this is enabled in profile
        if(CommCarePreferences.isGridMenuEnabled()) {
            return true;
        }
        // if not, check style attribute for this particular menu block
        if(menuId == null) {
            menuId = org.commcare.suite.model.Menu.ROOT_MENU_ID;
        }
        AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
        String commonDisplayStyle = platform.getMenuDisplayStyle(menuId);
        return MENU_STYLE_GRID.equals(commonDisplayStyle);
    }

    void userTriggeredLogout() {
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
        if(resultCode == RESULT_RESTART) {
            sessionNavigator.startNextSessionStep();
        } else {
            // if handling new return code (want to return to home screen) but a return at the end of your statement
            switch(requestCode) {
                case PREFERENCES_ACTIVITY:
                    if (resultCode == AdvancedActionsActivity.RESULT_DATA_RESET) {
                        finish();
                    }
                    return;
                case ADVANCED_ACTIONS_ACTIVITY:
                    handleAdvancedActionResult(resultCode, intent);
                    return;
                case GET_INCOMPLETE_FORM:
                    //TODO: We might need to load this from serialized state?
                    if(resultCode == RESULT_CANCELED) {
                        uiController.refreshView();
                        return;
                    } else if(resultCode == RESULT_OK) {
                        int record = intent.getIntExtra("FORMRECORDS", -1);
                        if(record == -1) {
                            //Hm, what to do here?
                            break;
                        }
                        FormRecord r = CommCareApplication._().getUserStorage(FormRecord.class).read(record);

                        //Retrieve and load the appropriate ssd
                        SqlStorage<SessionStateDescriptor> ssdStorage = CommCareApplication._().getUserStorage(SessionStateDescriptor.class);
                        Vector<Integer> ssds = ssdStorage.getIDsForValue(SessionStateDescriptor.META_FORM_RECORD_ID, r.getID());
                        AndroidSessionWrapper currentState =
                                CommCareApplication._().getCurrentSessionWrapper();
                        if(ssds.size() == 1) {
                            currentState.loadFromStateDescription(ssdStorage.read(ssds.firstElement()));
                        } else {
                            currentState.setFormRecordId(r.getID());
                        }

                        AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
                        formEntry(platform.getFormContentUri(r.getFormNamespace()), r);
                        return;
                    }
                    break;
                case GET_COMMAND:
                    //TODO: We might need to load this from serialized state?
                    AndroidSessionWrapper currentState =
                            CommCareApplication._().getCurrentSessionWrapper();
                    if (resultCode == RESULT_CANCELED) {
                        if (currentState.getSession().getCommand() == null) {
                            //Needed a command, and didn't already have one. Stepping back from
                            //an empty state, Go home!
                            currentState.reset();
                            uiController.refreshView();
                            return;
                        } else {
                            currentState.getSession().stepBack(currentState.getEvaluationContext());
                        }
                    } else if (resultCode == RESULT_OK) {
                        CommCareSession session = currentState.getSession();
                        if (sessionStateUnchangedSinceCallout(session, intent)) {
                            //Get our command, set it, and continue forward
                            String command = intent.getStringExtra(SessionFrame.STATE_COMMAND_ID);
                            session.setCommand(command);
                        } else {
                            clearSessionAndExit(currentState, true);
                            return;
                        }
                    }
                    break;
                case GET_CASE:
                    //TODO: We might need to load this from serialized state?
                    AndroidSessionWrapper asw = CommCareApplication._().getCurrentSessionWrapper();
                    CommCareSession currentSession = asw.getSession();
                    if (resultCode == RESULT_CANCELED) {
                        currentSession.stepBack(asw.getEvaluationContext());
                    } else if (resultCode == RESULT_OK) {
                        if (sessionStateUnchangedSinceCallout(currentSession, intent)) {
                            String sessionDatumId = currentSession.getNeededDatum().getDataId();
                            String chosenCaseId = intent.getStringExtra(SessionFrame.STATE_DATUM_VAL);
                            currentSession.setDatum(sessionDatumId, chosenCaseId);
                        } else {
                            clearSessionAndExit(asw, true);
                            return;
                        }
                    }
                    break;
                case MODEL_RESULT:
                    boolean fetchNext = processReturnFromFormEntry(resultCode, intent);
                    if (!fetchNext) {
                        return;
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
                        CommCareApplication._().closeUserSession();
                    } else if (resultCode == RESULT_OK) {
                        Toast.makeText(this, Localization.get("pin.set.success"), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, Localization.get("pin.not.set"), Toast.LENGTH_SHORT).show();
                    }
                    return;
                case MAKE_REMOTE_POST:
                    stepBackIfCancelled(resultCode);
                    if (resultCode == RESULT_OK) {
                        CommCareApplication._().getCurrentSessionWrapper().terminateSession();
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

    private void handleAdvancedActionResult(int resultCode, Intent intent) {
        if (resultCode == AdvancedActionsActivity.RESULT_FORMS_PROCESSED) {
            int formProcessCount = intent.getIntExtra(AdvancedActionsActivity.FORM_PROCESS_COUNT_KEY, 0);
            String localizationKey = intent.getStringExtra(AdvancedActionsActivity.FORM_PROCESS_MESSAGE_KEY);
            displayMessage(Localization.get(localizationKey, new String[]{"" + formProcessCount}), false, false);

            uiController.refreshView();
        }
    }

    private static void stepBackIfCancelled(int resultCode) {
        if (resultCode == RESULT_CANCELED) {
            AndroidSessionWrapper asw = CommCareApplication._().getCurrentSessionWrapper();
            CommCareSession currentSession = asw.getSession();
            currentSession.stepBack(asw.getEvaluationContext());
        }
    }

    private void startNextSessionStepSafe() {
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
                CommCareApplication._().getCurrentSessionWrapper().getEvaluationContext();
        boolean neededDataUnchanged = session.getNeededData(evalContext).equals(
                intent.getStringExtra(KEY_PENDING_SESSION_DATA));
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
        AndroidSessionWrapper currentState = CommCareApplication._().getCurrentSessionWrapper();

        // This is the state we were in when we _Started_ form entry
        FormRecord current = currentState.getFormRecord();

        if (current == null) {
            // somehow we lost the form record for the current session
            // TODO: how should this be handled? -- PLM
            Toast.makeText(this,
                    "Error while trying to save the form!",
                    Toast.LENGTH_LONG).show();
            Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                    "Form Entry couldn't save because of corrupt state.");
            clearSessionAndExit(currentState, true);
            return false;
        }

        // TODO: This should be the default unless we're in some "Uninit" or "incomplete" state
        if ((intent != null && intent.getBooleanExtra(FormEntryActivity.IS_ARCHIVED_FORM, false)) ||
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
                CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.FormEntry_Unretrievable));
                Toast.makeText(this,
                        "Error while trying to read the form! See the notification",
                        Toast.LENGTH_LONG).show();
                Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
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
                // We're honoring in order submissions, now, so trigger a full
                // submission cycle
                checkAndStartUnsentFormsTask(false, false);

                uiController.refreshView();

                if (wasExternal) {
                    setResult(RESULT_CANCELED);
                    this.finish();
                    return false;
                }

                // Before we can terminate the session, we need to know that the form has been processed
                // in case there is state that depends on it.
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

            Logger.log(AndroidLogger.TYPE_FORM_ENTRY, "Form Entry Cancelled");

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
        uiController.refreshView();
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
    public String getActivityTitle() {
        String userName;

        try {
            userName = CommCareApplication._().getSession().getLoggedInUser().getUsername();
            if (userName != null) {
                return Localization.get("home.logged.in.message", new String[]{userName});
            }
        } catch (Exception e) {
            //TODO: Better catch, here
        }
        return "";
    }

    @Override
    protected boolean isTopNavEnabled() {
        return false;
    }

    // region - implementing methods for SessionNavigationResponder

    @Override
    public void processSessionResponse(int statusCode) {
        AndroidSessionWrapper asw = CommCareApplication._().getCurrentSessionWrapper();
        switch(statusCode) {
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
                break;
            case SessionNavigator.REPORT_CASE_AUTOSELECT:
                GoogleAnalyticsUtils.reportFeatureUsage(GoogleAnalyticsFields.ACTION_CASE_AUTOSELECT_USED);
                break;
        }
    }

    @Override
    public CommCareSession getSessionForNavigator() {
        return CommCareApplication._().getCurrentSession();
    }

    @Override
    public EvaluationContext getEvalContextForNavigator() {
        return CommCareApplication._().getCurrentSessionWrapper().getEvaluationContext();
    }

    // endregion

    private void handleAssertionFailureFromSessionNav(final AndroidSessionWrapper asw) {
        EvaluationContext ec = asw.getEvaluationContext();
        Text text = asw.getSession().getCurrentEntry().getAssertions().getAssertionFailure(ec);
        createErrorDialog(text.evaluate(ec), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                dismissAlertDialog();
                asw.getSession().stepBack(asw.getEvaluationContext());
                CommCareHomeActivity.this.sessionNavigator.startNextSessionStep();
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
            uiController.refreshView();
        }
    }

    private void handleGetCommand(AndroidSessionWrapper asw) {
        Intent i;
        String command = asw.getSession().getCommand();

        if (useGridMenu(command)) {
            i = new Intent(this, MenuGrid.class);
        } else {
            i = new Intent(this, MenuList.class);
        }
        i.putExtra(SessionFrame.STATE_COMMAND_ID, command);
        addPendingDataExtra(i, asw.getSession());
        startActivityForResult(i, GET_COMMAND);
    }

    private void launchRemoteSync(AndroidSessionWrapper asw) {
        String command = asw.getSession().getCommand();
        Entry commandEntry = CommCareApplication._().getCommCarePlatform().getEntry(command);
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

    // Launch an intent to load the confirmation screen for the current selection
    private void launchConfirmDetail(AndroidSessionWrapper asw) {
        CommCareSession session = asw.getSession();
        SessionDatum selectDatum = session.getNeededDatum();
        if (selectDatum instanceof EntityDatum) {
            EntityDatum entityDatum = (EntityDatum) selectDatum;
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

    private static void addPendingDataExtra(Intent i, CommCareSession session) {
        EvaluationContext evalContext =
                CommCareApplication._().getCurrentSessionWrapper().getEvaluationContext();
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
            Logger.log("form-entry", "Somehow ended up starting form entry with old state?");
        }

        FormRecord record = state.getFormRecord();
        AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
        formEntry(platform.getFormContentUri(record.getFormNamespace()), record,
                CommCareActivity.getTitle(this, null));
    }

    private void formEntry(Uri formUri, FormRecord r) {
        formEntry(formUri, r, null);
    }

    private void formEntry(Uri formUri, FormRecord r, String headerTitle) {
        Logger.log(AndroidLogger.TYPE_FORM_ENTRY, "Form Entry Starting|" + r.getFormNamespace());

        //TODO: This is... just terrible. Specify where external instance data should come from
        FormLoaderTask.iif = new AndroidInstanceInitializer(CommCareApplication._().getCurrentSession());

        // Create our form entry activity callout
        Intent i = new Intent(getApplicationContext(), FormEntryActivity.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(FormEntryActivity.KEY_INSTANCEDESTINATION, CommCareApplication._().getCurrentApp().fsPath((GlobalConstants.FILE_CC_FORMS)));
        
        // See if there's existing form data that we want to continue entering
        // (note, this should be stored in the form record as a URI link to
        // the instance provider in the future)
        if(r.getInstanceURI() != null) {
            i.setData(r.getInstanceURI());
        } else {
            i.setData(formUri);
        }

        i.putExtra(FormEntryActivity.KEY_RESIZING_ENABLED, CommCarePreferences.getResizeMethod());
        i.putExtra(FormEntryActivity.KEY_INCOMPLETE_ENABLED, CommCarePreferences.isIncompleteFormsEnabled());
        i.putExtra(FormEntryActivity.KEY_AES_STORAGE_KEY, Base64.encodeToString(r.getAesKey(), Base64.DEFAULT));
        i.putExtra(FormEntryActivity.KEY_FORM_CONTENT_URI, FormsProviderAPI.FormsColumns.CONTENT_URI.toString());
        i.putExtra(FormEntryActivity.KEY_INSTANCE_CONTENT_URI, InstanceProviderAPI.InstanceColumns.CONTENT_URI.toString());
        i.putExtra(FormEntryActivity.KEY_RECORD_FORM_ENTRY_SESSION, DeveloperPreferences.isSessionSavingEnabled());
        if (headerTitle != null) {
            i.putExtra(FormEntryActivity.KEY_HEADER_STRING, headerTitle);
        }
        if (isRestoringSession) {
            isRestoringSession = false;
            SharedPreferences prefs =
                    CommCareApplication._().getCurrentApp().getAppPreferences();
            String formEntrySession = prefs.getString(CommCarePreferences.CURRENT_FORM_ENTRY_SESSION, "");
            if (!"".equals(formEntrySession)) {
                i.putExtra(FormEntryActivity.KEY_FORM_ENTRY_SESSION, formEntrySession);
            }
        }

        startActivityForResult(i, MODEL_RESULT);
    }

    /**
     * Triggered by a user manually clicking the sync button
     */
    void syncButtonPressed() {
        if (!ConnectivityStatus.isNetworkAvailable(CommCareHomeActivity.this)) {
            if (ConnectivityStatus.isAirplaneModeOn(CommCareHomeActivity.this)) {
                displayMessage(Localization.get("notification.sync.airplane.action"), true, true);
                CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(NotificationMessageFactory.StockMessages.Sync_AirplaneMode, AIRPLANE_MODE_CATEGORY));
            } else {
                displayMessage(Localization.get("notification.sync.connections.action"), true, true);
                CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(NotificationMessageFactory.StockMessages.Sync_NoConnections, AIRPLANE_MODE_CATEGORY));
            }
            GoogleAnalyticsUtils.reportSyncAttempt(
                    GoogleAnalyticsFields.ACTION_USER_SYNC_ATTEMPT,
                    GoogleAnalyticsFields.LABEL_SYNC_FAILURE,
                    GoogleAnalyticsFields.VALUE_NO_CONNECTION);
            return;
        }
        CommCareApplication._().clearNotifications(AIRPLANE_MODE_CATEGORY);
        sendFormsOrSync(true);
    }

    /**
     * Triggered when an automatic sync is pending
     */
    private void handlePendingSync() {
        long lastSync = CommCareApplication._().getCurrentApp().getAppPreferences().getLong("last-ota-restore", 0);
        String footer = lastSync == 0 ? "never" : SimpleDateFormat.getDateTimeInstance().format(lastSync);
        Logger.log(AndroidLogger.TYPE_USER, "autosync triggered. Last Sync|" + footer);

        uiController.refreshView();
        sendFormsOrSync(false);
    }

    /**
     * Attempts first to send unsent forms to the server.  If any forms are sent, a sync will be
     * triggered after they are submitted. If no forms are sent, triggers a sync explicitly.
     */
    private void sendFormsOrSync(boolean userTriggeredSync) {
        boolean formsSentToServer = checkAndStartUnsentFormsTask(true, userTriggeredSync);
        if(!formsSentToServer) {
            formAndDataSyncer.syncDataForLoggedInUser(this, false, userTriggeredSync);
        }
    }

    /**
     * @return Were forms sent to the server by this method invocation?
     */
    private boolean checkAndStartUnsentFormsTask(final boolean syncAfterwards,
                                                 boolean userTriggered) {
        isSyncUserLaunched = userTriggered;
        SqlStorage<FormRecord> storage = CommCareApplication._().getUserStorage(FormRecord.class);
        FormRecord[] records = StorageUtils.getUnsentRecords(storage);

        if(records.length > 0) {
            formAndDataSyncer.processAndSendForms(this, records, syncAfterwards, userTriggered);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onResumeSessionSafe() {
        if (!sessionNavigationProceedingAfterOnResume) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                refreshActionBar();
            }
            attemptDispatchHomeScreen();
        }

        sessionNavigationProceedingAfterOnResume = false;
    }

    /**
     * Decides if we should actually be on the home screen, or else should redirect elsewhere
     */
    private void attemptDispatchHomeScreen() {
        try {
            CommCareApplication._().getSession();
        } catch (SessionUnavailableException e) {
            // User was logged out somehow, so we want to return to dispatch activity
            setResult(RESULT_OK);
            this.finish();
            return;
        }

        if (CommCareApplication._().isSyncPending(false)) {
            // There is a sync pending
            handlePendingSync();
        } else if (CommCareApplication._().isConsumerApp()) {
            // so that the user never sees the real home screen in a consumer app
            enterRootModule();
        } else {
            // Display the normal home screen!
            uiController.refreshView();
        }
    }

    private void createAskUseOldDialog(final AndroidSessionWrapper state, final SessionStateDescriptor existing) {
        final AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
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
                        FormRecordCleanupTask.wipeRecord(CommCareHomeActivity.this, existing);
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

    @Override
    public void reportSuccess(String message) {
        displayMessage(message, false, false);
    }

    @Override
    public void reportFailure(String message, boolean showPopupNotification) {
        displayMessage(message, true, !showPopupNotification);
    }

    void displayMessage(String message, boolean bad, boolean suppressToast) {
        uiController.displayMessage(message, bad, suppressToast);
    }

    protected static boolean isDemoUser() {
        try {
            User u = CommCareApplication._().getSession().getLoggedInUser();
            return (User.TYPE_DEMO.equals(u.getUserType()));
        } catch (SessionUnavailableException e) {
            // Default to a normal user: this should only happen if session
            // expires and hasn't redirected to login.
            return false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, MENU_UPDATE, 0, Localization.get("home.menu.update")).setIcon(
                android.R.drawable.ic_menu_upload);
        menu.add(0, MENU_SAVED_FORMS, 0, Localization.get("home.menu.saved.forms")).setIcon(
                android.R.drawable.ic_menu_save);
        menu.add(0, MENU_CHANGE_LANGUAGE, 0, Localization.get("home.menu.locale.change")).setIcon(
                android.R.drawable.ic_menu_set_as);
        menu.add(0, MENU_ABOUT, 0, Localization.get("home.menu.about")).setIcon(
                android.R.drawable.ic_menu_help);
        menu.add(0, MENU_ADVANCED, 0, Localization.get("home.menu.advanced")).setIcon(
                android.R.drawable.ic_menu_edit);
        menu.add(0, MENU_PREFERENCES, 0, Localization.get("home.menu.settings")).setIcon(
                android.R.drawable.ic_menu_preferences);
        menu.add(0, MENU_PIN, 0, Localization.get("home.menu.pin.set"));
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        GoogleAnalyticsUtils.reportOptionsMenuEntry(GoogleAnalyticsFields.CATEGORY_HOME_SCREEN);
        //In Holo theme this gets called on startup
        boolean enableMenus = !isDemoUser();
        menu.findItem(MENU_UPDATE).setVisible(enableMenus);
        menu.findItem(MENU_SAVED_FORMS).setVisible(enableMenus);
        menu.findItem(MENU_CHANGE_LANGUAGE).setVisible(enableMenus);
        menu.findItem(MENU_PREFERENCES).setVisible(enableMenus);
        menu.findItem(MENU_ADVANCED).setVisible(enableMenus);
        menu.findItem(MENU_ABOUT).setVisible(enableMenus);
        preparePinMenu(menu, enableMenus);
        return true;
    }

    private static void preparePinMenu(Menu menu, boolean enableMenus) {
        boolean pinEnabled = enableMenus && DeveloperPreferences.shouldOfferPinForLogin();
        menu.findItem(MENU_PIN).setVisible(pinEnabled);
        boolean hasPinSet = false;

        try {
            hasPinSet = CommCareApplication._().getRecordForCurrentUser().hasPinSet();
        } catch (SessionUnavailableException e) {
            Log.d(TAG, "Session expired and menu is being created before redirect to login screen");
        }

        if (hasPinSet) {
            menu.findItem(MENU_PIN).setTitle(Localization.get("home.menu.pin.change"));
        } else {
            menu.findItem(MENU_PIN).setTitle(Localization.get("home.menu.pin.set"));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Map<Integer, String> menuIdToAnalyticsEventLabel = createMenuItemToEventMapping();
        GoogleAnalyticsUtils.reportOptionsMenuItemEntry(
                GoogleAnalyticsFields.CATEGORY_HOME_SCREEN,
                menuIdToAnalyticsEventLabel.get(item.getItemId()));
        switch (item.getItemId()) {
            case MENU_UPDATE:
                Intent i = new Intent(getApplicationContext(), UpdateActivity.class);
                startActivity(i);
                return true;
            case MENU_SAVED_FORMS:
                goToFormArchive(false);
                return true;
            case MENU_CHANGE_LANGUAGE:
                showLocaleChangeMenu();
                return true;
            case MENU_PREFERENCES:
                createPreferencesMenu(this);
                return true;
            case MENU_ADVANCED:
                startAdvancedActionsActivity();
                return true;
            case MENU_ABOUT:
                showAboutCommCareDialog();
                return true;
            case MENU_PIN:
                launchPinAuthentication();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static Map<Integer, String> createMenuItemToEventMapping() {
        Map<Integer, String> menuIdToAnalyticsEvent = new HashMap<>();
        menuIdToAnalyticsEvent.put(MENU_UPDATE, GoogleAnalyticsFields.LABEL_UPDATE_CC);
        menuIdToAnalyticsEvent.put(MENU_SAVED_FORMS, GoogleAnalyticsFields.LABEL_SAVED_FORMS);
        menuIdToAnalyticsEvent.put(MENU_CHANGE_LANGUAGE, GoogleAnalyticsFields.LABEL_LOCALE);
        menuIdToAnalyticsEvent.put(MENU_PREFERENCES, GoogleAnalyticsFields.LABEL_SETTINGS);
        menuIdToAnalyticsEvent.put(MENU_ADVANCED, GoogleAnalyticsFields.LABEL_ADVANCED_ACTIONS);
        menuIdToAnalyticsEvent.put(MENU_ABOUT, GoogleAnalyticsFields.LABEL_ABOUT_CC);
        return menuIdToAnalyticsEvent;
    }

    public static void createPreferencesMenu(Activity activity) {
        Intent i = new Intent(activity, CommCarePreferences.class);
        activity.startActivityForResult(i, PREFERENCES_ACTIVITY);
    }

    private void startAdvancedActionsActivity() {
        startActivityForResult(new Intent(this, AdvancedActionsActivity.class), ADVANCED_ACTIONS_ACTIVITY);
    }

    private void showAboutCommCareDialog() {
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
            CommCareApplication._().getCurrentApp().getAppPreferences()
                    .edit()
                    .putString(DeveloperPreferences.SUPERUSER_ENABLED, CommCarePreferences.YES)
                    .commit();
            Toast.makeText(CommCareHomeActivity.this,
                    Localization.get("home.developer.options.enabled"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        CustomProgressDialog dialog;
        switch (taskId) {
            case ProcessAndSendTask.SEND_PHASE_ID:
                title = Localization.get("sync.progress.submitting.title");
                message = Localization.get("sync.progress.submitting");
                dialog = CustomProgressDialog.newInstance(title, message, taskId);
                break;
            case ProcessAndSendTask.PROCESSING_PHASE_ID:
                title = Localization.get("form.entry.processing.title");
                message = Localization.get("form.entry.processing");
                dialog = CustomProgressDialog.newInstance(title, message, taskId);
                dialog.addProgressBar();
                break;
            case DataPullTask.DATA_PULL_TASK_ID:
                title = Localization.get("sync.communicating.title");
                message = Localization.get("sync.progress.purge");
                dialog = CustomProgressDialog.newInstance(title, message, taskId);
                if (isSyncUserLaunched) {
                    // allow users to cancel syncs that they launched
                    dialog.addCancelButton();
                }
                isSyncUserLaunched = false;
                break;
            default:
                Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                        + "any valid possibilities in CommCareHomeActivity");
                return null;
        }
        return dialog;
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

    public FormAndDataSyncer getFormAndDataSyncer() {
        return formAndDataSyncer;
    }

    @Override
    public void initUIController() {
        uiController = new HomeActivityUIController(this);
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void handlePullTaskResult(ResultAndError<DataPullTask.PullTaskResult> resultAndErrorMessage,
                                     boolean userTriggeredSync, boolean formsToSend) {
        getUIController().refreshView();
        if (CommCareApplication._().isConsumerApp()) {
            return;
        }

        SyncUIHandling.handleSyncResult(this, resultAndErrorMessage, userTriggeredSync, formsToSend);
    }

    @Override
    public void handlePullTaskUpdate(Integer... update) {
        SyncUIHandling.handleSyncUpdate(this, update);
    }

    @Override
    public void handlePullTaskError() {
        reportFailure(Localization.get("sync.fail.unknown"), true);
    }
}
