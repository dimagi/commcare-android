package org.commcare.dalvik.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import org.commcare.android.analytics.GoogleAnalyticsFields;
import org.commcare.android.analytics.GoogleAnalyticsUtils;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.framework.BreadcrumbBarFragment;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.CommCareActivityUIController;
import org.commcare.android.framework.SessionAwareCommCareActivity;
import org.commcare.android.framework.WithUIController;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.android.tasks.DataPullTask;
import org.commcare.android.tasks.DumpTask;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.tasks.SendTask;
import org.commcare.android.tasks.WipeTask;
import org.commcare.android.util.ACRAUtil;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.AndroidInstanceInitializer;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.util.StorageUtils;
import org.commcare.android.view.HorizontalMediaView;
import org.commcare.core.process.CommCareInstanceInitializer;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.utils.EntityDetailUtils;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.AlertDialogFactory;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.dalvik.dialogs.DialogChoiceItem;
import org.commcare.dalvik.dialogs.DialogCreationHelpers;
import org.commcare.dalvik.dialogs.PaneledChoiceDialog;
import org.commcare.dalvik.odk.provider.FormsProviderAPI;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.dalvik.utils.ConnectivityStatus;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.session.SessionNavigationResponder;
import org.commcare.session.SessionNavigator;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.StackFrameStep;
import org.commcare.suite.model.Text;
import org.javarosa.core.model.User;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xpath.XPathTypeMismatchException;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.tasks.FormLoaderTask;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class CommCareHomeActivity
        extends SessionAwareCommCareActivity<CommCareHomeActivity>
        implements SessionNavigationResponder, WithUIController {

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

    /**
     * Request code for launching FormEntryActivity
     */
    private static final int MODEL_RESULT = 4;

    private static final int GET_INCOMPLETE_FORM = 16;
    public static final int UPGRADE_APP = 32;
    public static final int REPORT_PROBLEM_ACTIVITY = 64;

    private static final int DUMP_FORMS_ACTIVITY=512;
    private static final int WIFI_DIRECT_ACTIVITY=1024;
    public static final int CONNECTION_DIAGNOSTIC_ACTIVITY=2048;
    private static final int PREFERENCES_ACTIVITY=4096;

    /**
     * Request code for launching media validator manually (Settings ->
     * Validate Media). Should signal a return from
     * CommCareVerificationActivity.
     */
    private static final int MEDIA_VALIDATOR_ACTIVITY=8192;

    private static final int CREATE_PIN = 16384;
    private static final int AUTHENTICATION_FOR_PIN = 32768;

    private static final int MENU_PREFERENCES = Menu.FIRST;
    private static final int MENU_UPDATE = Menu.FIRST + 1;
    private static final int MENU_REPORT_PROBLEM = Menu.FIRST + 2;
    private static final int MENU_VALIDATE_MEDIA = Menu.FIRST + 3;
    private static final int MENU_DUMP_FORMS = Menu.FIRST + 4;
    private static final int MENU_WIFI_DIRECT = Menu.FIRST + 5;
    private static final int MENU_CONNECTION_DIAGNOSTIC = Menu.FIRST + 6;
    private static final int MENU_SAVED_FORMS = Menu.FIRST + 7;
    private static final int MENU_ABOUT = Menu.FIRST + 8;
    private static final int MENU_PIN = Menu.FIRST + 9;
    private static final int MENU_DISABLE_ANALYTICS = Menu.FIRST + 10;

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

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) throws SessionUnavailableException {
        super.onCreateSessionSafe(savedInstanceState);

        if (savedInstanceState != null) {
            loginExtraWasConsumed = savedInstanceState.getBoolean(EXTRA_CONSUMED_KEY);
        }

        ACRAUtil.registerAppData();
        uiController.setupUI();
        sessionNavigator = new SessionNavigator(this);
        formAndDataSyncer = new FormAndDataSyncer(this);

        processFromExternalLaunch(savedInstanceState);
        processFromShortcutLaunch();
        processFromLoginLaunch();
    }

    /**
     * Set state that signifies activity was launch from external app.
     */
    private void processFromExternalLaunch(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            wasExternal = savedInstanceState.getBoolean(WAS_EXTERNAL_KEY);
        } else {
            if (getIntent().hasExtra(DispatchActivity.WAS_EXTERNAL)) {
                wasExternal = true;
                sessionNavigator.startNextSessionStep();
            }
        }
    }

    private void processFromShortcutLaunch() {
        if (getIntent().getBooleanExtra(DispatchActivity.WAS_SHORTCUT_LAUNCH, false)) {
            sessionNavigator.startNextSessionStep();
        }
    }

    private void processFromLoginLaunch() throws SessionUnavailableException {
        if (getIntent().getBooleanExtra(DispatchActivity.START_FROM_LOGIN, false) &&
                !loginExtraWasConsumed) {

            getIntent().removeExtra(DispatchActivity.START_FROM_LOGIN);
            loginExtraWasConsumed = true;

            CommCareSession session = CommCareApplication._().getCurrentSession();
            if (session.getCommand() != null) {
                // restore the session state if there is a command.
                // For debugging and occurs when a serialized
                // session is stored upon login
                sessionNavigator.startNextSessionStep();
                return;
            }

            // Trigger off a regular unsent task processor, unless we're about to sync (which will
            // then handle this in a blocking fashion)
            if (!CommCareApplication._().isSyncPending(false)) {
                checkAndStartUnsentFormsTask(false, false);
            }

            if (CommCareHomeActivity.isDemoUser()) {
                showDemoModeWarning();
            }

            checkForPinLaunchConditions();
        }
    }

    // See if we should launch either the pin choice dialog, or the create pin activity directly
    private void checkForPinLaunchConditions() throws SessionUnavailableException {

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

    private void showPinChoiceDialog(final LoginMode loginMode) throws SessionUnavailableException {
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
                        dialog.dismiss();
                        launchPinCreateScreen(loginMode);
                    }
                });

        DialogChoiceItem nextTimeChoice = new DialogChoiceItem(
                Localization.get("pin.dialog.not.now"), -1, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                });

        DialogChoiceItem notAgainChoice = new DialogChoiceItem(
                Localization.get("pin.dialog.never"), -1, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        CommCareApplication._().getCurrentApp().getAppPreferences()
                                .edit()
                                .putBoolean(CommCarePreferences.HAS_DISMISSED_PIN_CREATION, true)
                                .commit();
                        showPinFutureAccessDialog();
                    }
                });


        dialog.setChoiceItems(new DialogChoiceItem[]{createPinChoice, nextTimeChoice, notAgainChoice});
        dialog.addCollapsibleInfoPane(Localization.get("pin.dialog.extra.info"));
        dialog.show();
    }

    private void showPinFutureAccessDialog() {
        AlertDialogFactory f = AlertDialogFactory.getBasicAlertFactory(this,
                Localization.get("pin.dialog.set.later.title"),
                Localization.get("pin.dialog.set.later.message"), null);
        f.showDialog();
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

    protected void enterRootModule() {
        Intent i;
        if (useGridMenu(org.commcare.suite.model.Menu.ROOT_MENU_ID)) {
            i = new Intent(getApplicationContext(), MenuGrid.class);
        } else {
            i = new Intent(getApplicationContext(), MenuList.class);
        }
        addPendingDataExtra(i, CommCareApplication._().getCurrentSessionWrapper().getSession());
        startActivityForResult(i, GET_COMMAND);
    }

    private boolean useGridMenu(String menuId) {
        if(menuId == null) {
            menuId = org.commcare.suite.model.Menu.ROOT_MENU_ID;
        }
        if(DeveloperPreferences.isGridMenuEnabled()) {
            return true;
        }
        AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
        String commonDisplayStyle = platform.getMenuDisplayStyle(menuId);
        return MENU_STYLE_GRID.equals(commonDisplayStyle);
    }

    protected void userTriggeredLogout() {
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
                    if (resultCode != CommCarePreferences.RESULT_DATA_RESET) {
                        // rebuild home buttons in case language changed;
                        // but only if we didn't just clear user data
                        uiController.setupUI();
                    }
                    rebuildOptionMenu();
                    return;
                case MEDIA_VALIDATOR_ACTIVITY:
                    if(resultCode == RESULT_CANCELED){
                        return;
                    } else if (resultCode == RESULT_OK){
                        Toast.makeText(this, "Media Validated!", Toast.LENGTH_LONG).show();
                        return;
                    }
                case DUMP_FORMS_ACTIVITY:
                    if(resultCode == RESULT_CANCELED){
                        return;
                    }
                    else if(resultCode == DumpTask.BULK_DUMP_ID){
                        int dumpedCount = intent.getIntExtra(CommCareFormDumpActivity.KEY_NUMBER_DUMPED, -1);

                        displayMessage(Localization.get("bulk.form.dump.success",new String[] {""+dumpedCount}), false, false);

                        uiController.refreshView();
                        return;
                    }
                    else if(resultCode == SendTask.BULK_SEND_ID){
                        int dumpedCount = intent.getIntExtra(CommCareFormDumpActivity.KEY_NUMBER_DUMPED, -1);

                        displayMessage(Localization.get("bulk.form.send.success",new String[] {""+dumpedCount}),false, true);

                        Toast.makeText(this, Localization.get("bulk.form.send.success",new String[] {""+dumpedCount}), Toast.LENGTH_LONG).show();
                        uiController.refreshView();
                        return;
                    }
                case CONNECTION_DIAGNOSTIC_ACTIVITY:
                    return;
                case WIFI_DIRECT_ACTIVITY:
                    if(resultCode == RESULT_CANCELED){
                        return;
                    }
                    else if(resultCode == SendTask.BULK_SEND_ID){
                        int dumpedCount = intent.getIntExtra(CommCareWiFiDirectActivity.KEY_NUMBER_DUMPED, -1);

                        displayMessage(Localization.get("bulk.form.send.success",new String[] {""+dumpedCount}),false, true);

                        Toast.makeText(this, "Forms successfully submitted.", Toast.LENGTH_LONG).show();
                        uiController.refreshView();
                        return;
                    } else if(resultCode == WipeTask.WIPE_TASK_ID){
                        int dumpedCount = intent.getIntExtra(CommCareWiFiDirectActivity.KEY_NUMBER_DUMPED, -1);

                        displayMessage(Localization.get("bulk.form.send.success",new String[] {""+dumpedCount}),false, true);

                        Toast.makeText(this, "Forms successfully submitted.", Toast.LENGTH_LONG).show();
                        uiController.refreshView();
                        return;
                    }
                case REPORT_PROBLEM_ACTIVITY:
                    if(resultCode == RESULT_CANCELED) {
                        return;
                    }
                    else if(resultCode == RESULT_OK){
                        CommCareApplication._().notifyLogsPending();
                        uiController.refreshView();
                        return;
                    }
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
                            currentState.getSession().stepBack();
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
                        currentSession.stepBack();
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
                    boolean choseRememberPassword = (intent == null) ? false :
                            intent.getBooleanExtra(CreatePinActivity.CHOSE_REMEMBER_PASSWORD, false);
                    if (choseRememberPassword) {
                        CommCareApplication._().closeUserSession();
                    } else if (resultCode == RESULT_OK) {
                        Toast.makeText(this, Localization.get("pin.set.success"), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, Localization.get("pin.not.set"), Toast.LENGTH_SHORT).show();
                    }
                    return;
            }
            startNextSessionStepSafe();
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    private void startNextSessionStepSafe() {
        try {
            sessionNavigator.startNextSessionStep();
        } catch (CommCareInstanceInitializer.FixtureInitializationException e) {
            sessionNavigator.stepBack();
            if (isDemoUser()) {
                // most likely crashing due to data not being available in demo mode
                CommCareActivity.createErrorDialog(this,
                        Localization.get("demo.mode.feature.unavailable"),
                        false);
            } else {
                CommCareActivity.createErrorDialog(this, e.getMessage(), false);
            }
        }
    }

    /**
     * @return If the nature of the data that the session is waiting for has not changed since the
     * callout that we are returning from was made
     */
    private boolean sessionStateUnchangedSinceCallout(CommCareSession session, Intent intent) {
        boolean neededDataUnchanged = session.getNeededData().equals(
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
                    this.finish();
                    return false;
                }

                // Before we can terminate the session, we need to know that the form has been processed
                // in case there is state that depends on it.
                boolean terminateSuccessful;
                try {
                    terminateSuccessful = currentState.terminateSession();
                } catch (XPathTypeMismatchException e) {
                    Logger.exception(e);
                    CommCareActivity.createErrorDialog(this, e.getMessage(), true);
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
                currentState.getSession().stepBack();
                currentState.setFormRecordId(-1);
            }
        }
        return true;
    }

    private void clearSessionAndExit(AndroidSessionWrapper currentState, boolean shouldWarnUser) {
        currentState.reset();
        if (wasExternal) {
            this.finish();
        }
        uiController.refreshView();
        if (shouldWarnUser) {
            showSessionRefreshWarning();
        }
    }

    private void showSessionRefreshWarning() {
        AlertDialogFactory.getBasicAlertFactory(this,
                Localization.get("session.refresh.error.title"),
                Localization.get("session.refresh.error.message"), null).showDialog();
    }

    private void showDemoModeWarning() {
        AlertDialog demoModeWarning = new AlertDialog.Builder(new ContextThemeWrapper(this, android.R.style.Theme_Light)).setInverseBackgroundForced(true).create();
        demoModeWarning.setTitle(Localization.get("demo.mode.warning.title"));
        demoModeWarning.setCancelable(false);

        DialogInterface.OnClickListener demoModeWarningListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                // user has acknowledged demo warning
            }
        };
        demoModeWarning.setButton(android.content.DialogInterface.BUTTON_POSITIVE,
                Localization.get("demo.mode.warning.dismiss"),
                demoModeWarningListener);

        HorizontalMediaView tiav = new HorizontalMediaView(this);
        tiav.setAVT(Localization.get("demo.mode.warning"), null, null);

        demoModeWarning.setView(tiav);
        demoModeWarning.show();
    }

    private void createErrorDialog(String errorMsg, AlertDialog.OnClickListener errorListener) {
        AlertDialogFactory f = AlertDialogFactory.getBasicAlertFactoryWithIcon(this,
                Localization.get("app.handled.error.title"), errorMsg,
                android.R.drawable.ic_dialog_info, errorListener);
        showAlertDialog(f);
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
            case SessionNavigator.EXCEPTION_THROWN:
                displayException(sessionNavigator.getCurrentException());
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
                dialog.dismiss();
                asw.getSession().stepBack();
                CommCareHomeActivity.this.sessionNavigator.startNextSessionStep();
            }
        });
    }

    private void handleNoFormFromSessionNav(AndroidSessionWrapper asw) {
        boolean terminateSuccesful;
        try {
            terminateSuccesful = asw.terminateSession();
        } catch (XPathTypeMismatchException e) {
            Logger.exception(e);
            CommCareActivity.createErrorDialog(this, e.getMessage(), true);
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
            i = new Intent(getApplicationContext(), MenuGrid.class);
        } else {
            i = new Intent(getApplicationContext(), MenuList.class);
        }
        i.putExtra(SessionFrame.STATE_COMMAND_ID, command);
        addPendingDataExtra(i, asw.getSession());
        startActivityForResult(i, GET_COMMAND);
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
        TreeReference contextRef = sessionNavigator.getCurrentAutoSelection();
        if (this.getString(R.string.panes).equals("two")
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Large tablet in landscape: send to entity select activity
            // (awesome mode, with case pre-selected) instead of entity detail
            Intent i = getSelectIntent(session);
            String caseId = SessionDatum.getCaseIdFromReference(
                    contextRef, selectDatum, asw.getEvaluationContext());
            i.putExtra(EntitySelectActivity.EXTRA_ENTITY_KEY, caseId);
            startActivityForResult(i, GET_CASE);
        } else {
            // Launch entity detail activity
            Intent detailIntent = new Intent(getApplicationContext(), EntityDetailActivity.class);
            EntityDetailUtils.populateDetailIntent(
                    detailIntent, contextRef, selectDatum, asw);
            addPendingDataExtra(detailIntent, session);
            addPendingDatumIdExtra(detailIntent, session);
            startActivityForResult(detailIntent, GET_CASE);
        }
    }

    private static void addPendingDataExtra(Intent i, CommCareSession session) {
        i.putExtra(KEY_PENDING_SESSION_DATA, session.getNeededData());
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
        formEntry(platform.getFormContentUri(record.getFormNamespace()), record, CommCareActivity.getTitle(this, null));
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
        i.putExtra(FormEntryActivity.TITLE_FRAGMENT_TAG, BreadcrumbBarFragment.class.getName());
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
        if (headerTitle != null) {
            i.putExtra(FormEntryActivity.KEY_HEADER_STRING, headerTitle);
        }

        startActivityForResult(i, MODEL_RESULT);
    }

    /**
     * Triggered by a user manually clicking the sync button
     */
    protected void syncButtonPressed() {
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
            formAndDataSyncer.syncData(false, userTriggeredSync);
        }
    }

    /**
     * @return Were forms sent to the server by this method invocation?
     */
    private boolean checkAndStartUnsentFormsTask(final boolean syncAfterwards,
                                                 boolean userTriggered) {
        SqlStorage<FormRecord> storage = CommCareApplication._().getUserStorage(FormRecord.class);
        FormRecord[] records = StorageUtils.getUnsentRecords(storage);

        if(records.length > 0) {
            formAndDataSyncer.processAndSendForms(records, syncAfterwards, userTriggered);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            refreshActionBar();
        }
        attemptDispatchHomeScreen();
    }

    /**
     * Decides if we should actually be on the home screen, or else should redirect elsewhere
     */
    private void attemptDispatchHomeScreen() {
        try {
            if (CommCareApplication._().isSyncPending(false)) {
                // There is a sync pending
                handlePendingSync();
            } else if (!CommCareApplication._().getSession().isActive()) {
                // User was logged out somehow, so we want to return to dispatch activity
                setResult(RESULT_OK);
                this.finish();
            } else {
                // Display the normal home screen!
                uiController.refreshView();
            }
        } catch (SessionUnavailableException e) {
            // User was logged out somehow, so we want to return to dispatch activity
            setResult(RESULT_OK);
            this.finish();
        }
    }

    private void createAskUseOldDialog(final AndroidSessionWrapper state, final SessionStateDescriptor existing) {
        final AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
        String title = Localization.get("app.workflow.incomplete.continue.title");
        String msg = Localization.get("app.workflow.incomplete.continue");
        AlertDialogFactory factory = new AlertDialogFactory(this, title, msg);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
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
                dialog.dismiss();
            }
        };
        factory.setPositiveButton(Localization.get("option.yes"), listener);
        factory.setNegativeButton(Localization.get("app.workflow.incomplete.continue.option.delete"), listener);
        factory.setNeutralButton(Localization.get("option.no"), listener);
        showAlertDialog(factory);
    }

    protected void displayMessage(String message) {
        displayMessage(message, false);
    }

    protected void displayMessage(String message, boolean bad) {
        displayMessage(message, bad, false);
    }

    protected void displayMessage(String message, boolean bad, boolean suppressToast) {
        uiController.displayMessage(message, bad, suppressToast);
    }

    public static boolean isDemoUser() {
        try {
            User u = CommCareApplication._().getSession().getLoggedInUser();
            return (User.TYPE_DEMO.equals(u.getUserType()));
        } catch (SessionUnavailableException e) {
            return false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, MENU_PREFERENCES, 0, Localization.get("home.menu.settings")).setIcon(
                android.R.drawable.ic_menu_preferences);
        menu.add(0, MENU_UPDATE, 0, Localization.get("home.menu.update")).setIcon(
                android.R.drawable.ic_menu_upload);
        menu.add(0, MENU_REPORT_PROBLEM, 0, Localization.get("problem.report.menuitem")).setIcon(
                android.R.drawable.ic_menu_report_image);
        menu.add(0, MENU_VALIDATE_MEDIA, 0, Localization.get("home.menu.validate")).setIcon(
                android.R.drawable.ic_menu_gallery);
        menu.add(0, MENU_DUMP_FORMS, 0, Localization.get("home.menu.formdump")).setIcon(
                android.R.drawable.ic_menu_set_as);
        menu.add(0, MENU_WIFI_DIRECT, 0, Localization.get("home.menu.wifi.direct")).setIcon(
                android.R.drawable.ic_menu_share);
        menu.add(0, MENU_CONNECTION_DIAGNOSTIC, 0, Localization.get("home.menu.connection.diagnostic")).setIcon(
                android.R.drawable.ic_menu_manage);
        menu.add(0, MENU_SAVED_FORMS, 0, Localization.get("home.menu.saved.forms")).setIcon(
                android.R.drawable.ic_menu_save);
        menu.add(0, MENU_ABOUT, 0, Localization.get("home.menu.about")).setIcon(
                android.R.drawable.ic_menu_help);
        menu.add(0, MENU_PIN, 0, Localization.get("home.menu.pin.set"));
        menu.add(0, MENU_DISABLE_ANALYTICS, 0, Localization.get("home.menu.disable.analytics"));
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        GoogleAnalyticsUtils.reportOptionsMenuEntry(GoogleAnalyticsFields.CATEGORY_HOME_SCREEN);
        //In Holo theme this gets called on startup
        try {
            User u = CommCareApplication._().getSession().getLoggedInUser();
            boolean enableMenus = !User.TYPE_DEMO.equals(u.getUserType());
            menu.findItem(MENU_PREFERENCES).setVisible(enableMenus);
            menu.findItem(MENU_UPDATE).setVisible(enableMenus);
            menu.findItem(MENU_VALIDATE_MEDIA).setVisible(enableMenus);
            menu.findItem(MENU_DUMP_FORMS).setVisible(enableMenus);
            menu.findItem(MENU_WIFI_DIRECT).setVisible(enableMenus && hasP2p());
            menu.findItem(MENU_CONNECTION_DIAGNOSTIC).setVisible(enableMenus);
            menu.findItem(MENU_SAVED_FORMS).setVisible(enableMenus);
            menu.findItem(MENU_ABOUT).setVisible(enableMenus);
            menu.findItem(MENU_DISABLE_ANALYTICS).setVisible(CommCarePreferences.isAnalyticsEnabled());
            if (CommCareApplication._().getRecordForCurrentUser().hasPinSet()) {
                menu.findItem(MENU_PIN).setTitle(Localization.get("home.menu.pin.change"));
            } else {
                menu.findItem(MENU_PIN).setTitle(Localization.get("home.menu.pin.set"));
            }
            menu.findItem(MENU_PIN).setVisible(enableMenus
                    && DeveloperPreferences.shouldOfferPinForLogin());
        } catch (SessionUnavailableException sue) {
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Map<Integer, String> menuIdToAnalyticsEventLabel = createMenuItemToEventMapping();
        GoogleAnalyticsUtils.reportOptionsMenuItemEntry(
                GoogleAnalyticsFields.CATEGORY_HOME_SCREEN,
                menuIdToAnalyticsEventLabel.get(item.getItemId()));
        switch (item.getItemId()) {
            case MENU_PREFERENCES:
                createPreferencesMenu(this);
                return true;
            case MENU_UPDATE:
                Intent i = new Intent(getApplicationContext(), UpdateActivity.class);
                startActivity(i);
                return true;
            case MENU_REPORT_PROBLEM:
                startReportActivity();
                return true;
            case MENU_VALIDATE_MEDIA:
                startValidationActivity();
                return true;
            case MENU_DUMP_FORMS:
                startFormDumpActivity();
                return true;
            case MENU_WIFI_DIRECT:
                startWifiDirectActivity();
                return true;
            case MENU_CONNECTION_DIAGNOSTIC:
                startMenuConnectionActivity();
                return true;
            case MENU_SAVED_FORMS:
                goToFormArchive(false);
                return true;
            case MENU_ABOUT:
                showAboutCommCareDialog();
                return true;
            case MENU_PIN:
                launchPinAuthentication();
            case MENU_DISABLE_ANALYTICS:
                showAnalyticsOptOutDialog();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static Map<Integer, String> createMenuItemToEventMapping() {
        Map<Integer, String> menuIdToAnalyticsEvent = new HashMap<>();
        menuIdToAnalyticsEvent.put(MENU_PREFERENCES, GoogleAnalyticsFields.LABEL_SETTINGS);
        menuIdToAnalyticsEvent.put(MENU_UPDATE, GoogleAnalyticsFields.LABEL_UPDATE_CC);
        menuIdToAnalyticsEvent.put(MENU_REPORT_PROBLEM, GoogleAnalyticsFields.LABEL_REPORT_PROBLEM);
        menuIdToAnalyticsEvent.put(MENU_VALIDATE_MEDIA, GoogleAnalyticsFields.LABEL_VALIDATE_MM);
        menuIdToAnalyticsEvent.put(MENU_DUMP_FORMS, GoogleAnalyticsFields.LABEL_MANAGE_SD);
        menuIdToAnalyticsEvent.put(MENU_WIFI_DIRECT, GoogleAnalyticsFields.LABEL_WIFI_DIRECT);
        menuIdToAnalyticsEvent.put(MENU_CONNECTION_DIAGNOSTIC, GoogleAnalyticsFields.LABEL_CONNECTION_TEST);
        menuIdToAnalyticsEvent.put(MENU_SAVED_FORMS, GoogleAnalyticsFields.LABEL_SAVED_FORMS);
        menuIdToAnalyticsEvent.put(MENU_ABOUT, GoogleAnalyticsFields.LABEL_ABOUT_CC);
        return menuIdToAnalyticsEvent;
    }

    private void showAnalyticsOptOutDialog() {
        AlertDialogFactory f = new AlertDialogFactory(this,
                Localization.get("analytics.opt.out.title"),
                Localization.get("analytics.opt.out.message"));

        f.setPositiveButton(Localization.get("analytics.disable.button"),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        CommCarePreferences.disableAnalytics();
                    }
                });

        f.setNegativeButton(Localization.get("option.cancel"),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        f.showDialog();
    }

    public static void createPreferencesMenu(Activity activity) {
        Intent i = new Intent(activity, CommCarePreferences.class);
        activity.startActivityForResult(i, PREFERENCES_ACTIVITY);
    }

    private void startReportActivity() {
        Intent i = new Intent(this, ReportProblemActivity.class);
        CommCareHomeActivity.this.startActivityForResult(i, REPORT_PROBLEM_ACTIVITY);
    }

    private void startValidationActivity() {
        Intent i = new Intent(this, CommCareVerificationActivity.class);
        i.putExtra(CommCareVerificationActivity.KEY_LAUNCH_FROM_SETTINGS, true);
        CommCareHomeActivity.this.startActivityForResult(i, MEDIA_VALIDATOR_ACTIVITY);
    }

    private void startFormDumpActivity() {
        Intent i = new Intent(this, CommCareFormDumpActivity.class);
        i.putExtra(CommCareFormDumpActivity.EXTRA_FILE_DESTINATION, CommCareApplication._().getCurrentApp().storageRoot());
        CommCareHomeActivity.this.startActivityForResult(i, DUMP_FORMS_ACTIVITY);
    }

    private void startWifiDirectActivity() {
        Intent i = new Intent(this, CommCareWiFiDirectActivity.class);
        CommCareHomeActivity.this.startActivityForResult(i, WIFI_DIRECT_ACTIVITY);
    }

    private void startMenuConnectionActivity() {
        Intent i = new Intent(this, ConnectionDiagnosticActivity.class);
        CommCareHomeActivity.this.startActivityForResult(i, CONNECTION_DIAGNOSTIC_ACTIVITY);
    }

    private void showAboutCommCareDialog() {
        AlertDialog dialog = DialogCreationHelpers.buildAboutCommCareDialog(this);

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                mDeveloperModeClicks++;
                if (mDeveloperModeClicks == 4) {
                    CommCareApplication._().getCurrentApp().getAppPreferences().
                            edit().putString(DeveloperPreferences.SUPERUSER_ENABLED, "yes").commit();
                    Toast.makeText(CommCareHomeActivity.this,
                            Localization.get("home.developer.options.enabled"),
                            Toast.LENGTH_SHORT).show();
                }
            }

        });
        dialog.show();
    }

    private boolean hasP2p() {
        return (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH && getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT));
    }


    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        switch (taskId) {
            case ProcessAndSendTask.SEND_PHASE_ID:
                title = Localization.get("sync.progress.submitting.title");
                message = Localization.get("sync.progress.submitting");
                break;
            case ProcessAndSendTask.PROCESSING_PHASE_ID:
                title = Localization.get("form.entry.processing.title");
                message = Localization.get("form.entry.processing");
                break;
            case DataPullTask.DATA_PULL_TASK_ID:
                title = Localization.get("sync.progress.title");
                message = Localization.get("sync.progress.purge");
                break;
            default:
                Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                        + "any valid possibilities in CommCareHomeActivity");
                return null;
        }
        CustomProgressDialog dialog = CustomProgressDialog.newInstance(title, message, taskId);
        if (taskId == ProcessAndSendTask.PROCESSING_PHASE_ID) {
            dialog.addProgressBar();
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

    @Override
    public void initUIController() {
        uiController = new HomeActivityUIController(this);
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

}
