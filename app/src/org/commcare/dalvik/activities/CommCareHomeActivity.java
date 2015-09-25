package org.commcare.dalvik.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Spannable;
import android.text.format.DateUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;

import com.etsy.android.grid.StaggeredGridView;

import org.commcare.android.adapters.HomeScreenAdapter;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.UserStorageClosedException;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.database.user.models.User;
import org.commcare.android.framework.BreadcrumbBarFragment;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.SessionAwareCommCareActivity;
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
import org.commcare.android.util.CommCareInstanceInitializer;
import org.commcare.android.util.DialogCreationHelpers;
import org.commcare.android.util.FormUploadUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.util.StorageUtils;
import org.commcare.android.view.HorizontalMediaView;
import org.commcare.android.view.SquareButtonWithNotification;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.AndroidShortcuts;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.dalvik.odk.provider.FormsProviderAPI;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.SessionDatum;
import org.commcare.suite.model.StackFrameStep;
import org.commcare.suite.model.Text;
import org.commcare.util.CommCareSession;
import org.commcare.util.SessionFrame;
import org.commcare.util.SessionNavigationResponder;
import org.commcare.util.SessionNavigator;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.tasks.FormLoaderTask;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

import in.srain.cube.views.GridViewWithHeaderAndFooter;

public class CommCareHomeActivity
        extends SessionAwareCommCareActivity<CommCareHomeActivity>
        implements SessionNavigationResponder {

    private static final String TAG = CommCareHomeActivity.class.getSimpleName();

    private static final int LOGIN_USER = 0;

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

    public static final int INIT_APP = 8;
    private static final int GET_INCOMPLETE_FORM = 16;
    public static final int UPGRADE_APP = 32;
    private static final int REPORT_PROBLEM_ACTIVITY = 64;

    /**
     * Request code for automatically validating media from home dispatch.
     * Should signal a return from CommCareVerificationActivity.
     */
    public static final int MISSING_MEDIA_ACTIVITY=256;
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


    private static final int DIALOG_CORRUPTED = 1;

    private static final int MENU_PREFERENCES = Menu.FIRST;
    private static final int MENU_UPDATE = Menu.FIRST + 1;
    private static final int MENU_CALL_LOG = Menu.FIRST + 2;
    private static final int MENU_REPORT_PROBLEM = Menu.FIRST + 3;
    private static final int MENU_VALIDATE_MEDIA = Menu.FIRST + 4;
    private static final int MENU_DUMP_FORMS = Menu.FIRST + 5;
    private static final int MENU_WIFI_DIRECT = Menu.FIRST + 6;
    private static final int MENU_CONNECTION_DIAGNOSTIC = Menu.FIRST + 7;
    private static final int MENU_SAVED_FORMS = Menu.FIRST + 8;
    private static final int MENU_ABOUT = Menu.FIRST + 9;

    /**
     * Restart is a special CommCare return code which means that the session was invalidated in the
     * calling activity and that the current session should be resynced
     */
    public static final int RESULT_RESTART = 3;

    private final static String UNSENT_FORM_NUMBER_KEY = "unsent-number-limit";
    private final static String UNSENT_FORM_TIME_KEY = "unsent-time-limit";

    private static final String SESSION_REQUEST = "ccodk_session_request";

    private static final String AIRPLANE_MODE_CATEGORY = "airplane-mode";

    // The API allows for external calls. When this occurs, redispatch to their
    // activity instead of commcare.
    private boolean wasExternal = false;

    private int mDeveloperModeClicks = 0;

    private AndroidCommCarePlatform platform;

    private SquareButtonWithNotification startButton;
    private SquareButtonWithNotification logoutButton;
    private SquareButtonWithNotification viewIncomplete;
    private SquareButtonWithNotification syncButton;

    private HomeScreenAdapter adapter;
    private GridViewWithHeaderAndFooter gridView;
    private StaggeredGridView newGridView;
    private View mTopBanner;

    private SessionNavigator sessionNavigator;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //This is a workaround required by Android Bug #2373, which is that Apps are launched from the
        //Google Play store and from the App launcher with different intent flags than everywhere else
        //in Android, which ruins the back stack and prevents the app from launching a high affinity
        //task.
        if (!isTaskRoot()) {
            Intent intent = getIntent();
            String action = intent.getAction();
            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && action != null && action.equals(Intent.ACTION_MAIN)) {
                finish();
                return;
            }
        }

        if (savedInstanceState != null) {
            wasExternal = savedInstanceState.getBoolean("was_external");
        }

        ACRAUtil.registerAppData();
        sessionNavigator = new SessionNavigator(this);

        // TODO: discover why Android is not loading the correct layout from layout[-land]-v10 and remove this
        setContentView((Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) ? R.layout.mainnew_modern_v10 : R.layout.mainnew_modern);
        adapter = new HomeScreenAdapter(this);
        mTopBanner = View.inflate(this, R.layout.grid_header_top_banner, null);

        final View grid = findViewById(R.id.home_gridview_buttons);
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Grid is: " + grid + ", build version: " + Build.VERSION.SDK_INT + ", tag is: " + grid.getTag());
        }
        if (grid instanceof StaggeredGridView) {
            newGridView = (StaggeredGridView)grid;
            newGridView.addHeaderView(mTopBanner);
            newGridView.setAdapter(adapter);
        } else {
            gridView = (GridViewWithHeaderAndFooter)grid;
            gridView.addHeaderView(mTopBanner);
            gridView.setAdapter(adapter);
        }
        //region Asserting that we're using the correct grid view for each version
        if (!(((Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) && newGridView != null) || ((Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD_MR1) && gridView != null))) {
            Log.v(TAG, "Mismatch when loading grid view! Current grid is " + grid + ", but should be the other version");
            if (BuildConfig.DEBUG) {
                throw new AssertionError("Mismatch when loading grid view! Current grid is " + grid + ", but should be the other version");
            }
        }
        //endregion
        grid.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @SuppressLint("NewApi")
            @Override
            public void onGlobalLayout() {
                if (adapter.getItem(0) == null) {
                    Log.e("configUi", "Items still not instantiated by gridView, configUi is going to crash!");
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    grid.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    grid.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                grid.requestLayout();
                adapter.notifyDataSetChanged(); // is going to populate the grid with buttons from the adapter (hardcoded there)
                configUi();
            }
        });
    }

    private void configUi() {
        if (CommCareApplication._().getCurrentApp() == null) {
            // This method depends on there being a seated app, so don't proceed with it if we
            // don't have one
            return;
        }

        TextView version = (TextView)findViewById(R.id.str_version);
        if (version != null) {
            version.setText(CommCareApplication._().getCurrentVersionString());
        }

        startButton = adapter.getButton(R.layout.home_start_button);
        if (startButton != null) {
            startButton.setText(Localization.get("home.start"));
        } else {
            Log.d("buttons", "startButton is null! Crashing!");
        }
        View.OnClickListener startListener = new OnClickListener() {
            public void onClick(View v) {
                Intent i;
                if (DeveloperPreferences.isGridMenuEnabled()) {
                    i = new Intent(getApplicationContext(), MenuGrid.class);
                } else {
                    i = new Intent(getApplicationContext(), MenuList.class);
                }
                startActivityForResult(i, GET_COMMAND);
            }
        };
        adapter.setOnClickListenerForButton(R.layout.home_start_button, startListener);

        viewIncomplete = adapter.getButton(R.layout.home_incompleteforms_button);

        if (viewIncomplete != null) {
            setIncompleteFormsText(CommCareApplication._().getSyncDisplayParameters());
        }
        View.OnClickListener viewIncompleteListener = new OnClickListener() {
            public void onClick(View v) {
                goToFormArchive(true);
            }
        };
        adapter.setOnClickListenerForButton(R.layout.home_incompleteforms_button, viewIncompleteListener);


        logoutButton = adapter.getButton(R.layout.home_disconnect_button);
        if (logoutButton != null) {
            logoutButton.setText(Localization.get("home.logout"));
        } else {
            Log.d("buttons", "logoutButton is null! Crashing!");
        }
        View.OnClickListener logoutButtonListener = new OnClickListener() {
            public void onClick(View v) {
                CommCareApplication._().closeUserSession();
                returnToLogin();
            }
        };
        adapter.setOnClickListenerForButton(R.layout.home_disconnect_button, logoutButtonListener);
        if (logoutButton != null) {
            logoutButton.setNotificationText(getActivityTitle());
            adapter.notifyDataSetChanged();
        }

        SquareButtonWithNotification viewOldForms = adapter.getButton(R.layout.home_savedforms_button);
        if (viewOldForms != null) {
            viewOldForms.setText(Localization.get("home.forms.saved"));
        } else {
            Log.d("buttons", "viewOldForms is null! Crashing!");
        }
        View.OnClickListener viewOldFormsListener = new OnClickListener() {
            public void onClick(View v) {
                goToFormArchive(false);
            }
        };
        adapter.setOnClickListenerForButton(R.layout.home_savedforms_button, viewOldFormsListener);

        syncButton = adapter.getButton(R.layout.home_sync_button);
        if (syncButton != null) {
            setSyncButtonText(CommCareApplication._().getSyncDisplayParameters(), null);
        } else {
            Log.d("buttons", "syncButton is null! Crashing!");
        }
        View.OnClickListener syncButtonListener = new OnClickListener() {
            public void onClick(View v) {
                if (isNetworkNotConnected()) {
                    if (isAirplaneModeOn()) {
                        displayMessage(Localization.get("notification.sync.airplane.action"), true, true);
                        CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Sync_AirplaneMode, AIRPLANE_MODE_CATEGORY));
                    } else {
                        displayMessage(Localization.get("notification.sync.connections.action"), true, true);
                        CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Sync_NoConnections, AIRPLANE_MODE_CATEGORY));
                    }
                    return;
                }

                CommCareApplication._().clearNotifications(AIRPLANE_MODE_CATEGORY);

                boolean formsSentToServer = checkAndStartUnsentTask(true);

                if(!formsSentToServer) {
                    // No forms needed to be sent to the server, so let's just
                    // trigger a data sync.
                    syncData(false);
                }
            }
        };
        adapter.setOnClickListenerForButton(R.layout.home_sync_button, syncButtonListener);

        rebuildMenus();
    }

    private void goToFormArchive(boolean incomplete) {
        goToFormArchive(incomplete, null);
    }

    private void goToFormArchive(boolean incomplete, FormRecord record) {
        Intent i = new Intent(getApplicationContext(), FormRecordListActivity.class);
        if (incomplete) {
            i.putExtra(FormRecord.META_STATUS, FormRecord.STATUS_INCOMPLETE);
        }
        if (record != null) {
            i.putExtra(FormRecordListActivity.KEY_INITIAL_RECORD_ID, record.getID());
        }
        startActivityForResult(i, GET_INCOMPLETE_FORM);
    }

    private void syncData(boolean formsToSend) {
        User u;
        try {
            u = CommCareApplication._().getSession().getLoggedInUser();
        } catch (SessionUnavailableException sue) {
            // abort since it looks like the session expired
            return;
        }
        
        if(User.TYPE_DEMO.equals(u.getUserType())) {
            //Remind the user that there's no syncing in demo mode.
            if (formsToSend) {
                displayMessage(Localization.get("main.sync.demo.has.forms"), true, true);
            } else {
                displayMessage(Localization.get("main.sync.demo.no.forms"), true, true);
            }
            return;
        }
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();

        DataPullTask<CommCareHomeActivity> mDataPullTask = new DataPullTask<CommCareHomeActivity>(u.getUsername(), u.getCachedPwd(), prefs.getString("ota-restore-url", this.getString(R.string.ota_restore_url)), "", this) {

            @Override
            protected void deliverResult(CommCareHomeActivity receiver, Integer result) {
                receiver.refreshView();

                //TODO: SHARES _A LOT_ with login activity. Unify into service
                switch (result) {
                    case DataPullTask.AUTH_FAILED:
                        receiver.displayMessage(Localization.get("sync.fail.auth.loggedin"), true);
                        break;
                    case DataPullTask.BAD_DATA:
                        receiver.displayMessage(Localization.get("sync.fail.bad.data"), true);
                        break;
                    case DataPullTask.DOWNLOAD_SUCCESS:
                        receiver.displayMessage(Localization.get("sync.success.synced"));
                        break;
                    case DataPullTask.SERVER_ERROR:
                        receiver.displayMessage(Localization.get("sync.fail.server.error"));
                        break;
                    case DataPullTask.UNREACHABLE_HOST:
                        receiver.displayMessage(Localization.get("sync.fail.bad.network"), true);
                        break;
                    case DataPullTask.CONNECTION_TIMEOUT:
                        receiver.displayMessage(Localization.get("sync.fail.timeout"), true);
                        break;
                    case DataPullTask.UNKNOWN_FAILURE:
                        receiver.displayMessage(Localization.get("sync.fail.unknown"), true);
                        break;
                }
                //TODO: What if the user info was updated?

            }

            @Override
            protected void deliverUpdate(CommCareHomeActivity receiver, Integer... update) {
                if (update[0] == DataPullTask.PROGRESS_STARTED) {
                    receiver.updateProgress(Localization.get("sync.progress.purge"), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_CLEANED) {
                    receiver.updateProgress(Localization.get("sync.progress.authing"), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_AUTHED) {
                    receiver.updateProgress(Localization.get("sync.progress.downloading"), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_DOWNLOADING) {
                    receiver.updateProgress(Localization.get("sync.process.downloading.progress", new String[]{String.valueOf(update[1])}), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_PROCESSING) {
                    receiver.updateProgress(Localization.get("sync.process.processing", new String[]{String.valueOf(update[1]), String.valueOf(update[2])}), DataPullTask.DATA_PULL_TASK_ID);
                    receiver.updateProgressBar(update[1], update[2], DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_RECOVERY_NEEDED) {
                    receiver.updateProgress(Localization.get("sync.recover.needed"), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_RECOVERY_STARTED) {
                    receiver.updateProgress(Localization.get("sync.recover.started"), DataPullTask.DATA_PULL_TASK_ID);
                }
            }

            @Override
            protected void deliverError(CommCareHomeActivity receiver,
                                        Exception e) {
                receiver.displayMessage(Localization.get("sync.fail.unknown"), true);
            }

        };

        mDataPullTask.connect(this);

        mDataPullTask.execute();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("was_external", wasExternal);
    }

    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        if (inState.containsKey("was_external")) {
            wasExternal = inState.getBoolean("was_external");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if(resultCode == RESULT_RESTART) {
            sessionNavigator.startNextSessionStep();
        } else {
            AndroidSessionWrapper currentState = CommCareApplication._().getCurrentSessionWrapper();

            // if handling new return code (want to return to home screen) but a return at the end of your statement
            switch(requestCode) {
            case INIT_APP:
                if (resultCode == RESULT_CANCELED) {
                    // User pressed back button from install screen, so take them out of CommCare
                    this.finish();
                    return;
                } else if (resultCode == RESULT_OK) {
                    //CTS - Removed a call to initializing resources here. The engine takes care of that.
                    //We do, however, need to re-init this screen to include new translations
                    configUi();
                    return;
                }
                break;
            case UPGRADE_APP:
                if(resultCode == RESULT_CANCELED) {
                    //This might actually be bad, but try to go about your business
                    //The onResume() will take us to the screen
                    return;
                } else if(resultCode == RESULT_OK) {
                    if(intent.getBooleanExtra(CommCareSetupActivity.KEY_REQUIRE_REFRESH, true)) {
                        Toast.makeText(this, Localization.get("update.success.refresh"), Toast.LENGTH_LONG).show();
                        CommCareApplication._().closeUserSession();
                    }
                    return;
                }
                break;
            case PREFERENCES_ACTIVITY:
                configUi();
                return;
            case MEDIA_VALIDATOR_ACTIVITY:
                if(resultCode == RESULT_CANCELED){
                    return;
                } else if (resultCode == RESULT_OK){
                    Toast.makeText(this, "Media Validated!", Toast.LENGTH_LONG).show();
                    return;
                }
            case MISSING_MEDIA_ACTIVITY:
                if(resultCode == RESULT_CANCELED){
                    // exit the app if media wasn't validated on automatic
                    // validation check.
                    this.finish();
                    return;
                } else if(resultCode == RESULT_OK){
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
                    
                    refreshView();
                    return;
                }
                else if(resultCode == SendTask.BULK_SEND_ID){
                    int dumpedCount = intent.getIntExtra(CommCareFormDumpActivity.KEY_NUMBER_DUMPED, -1);
                    
                    displayMessage(Localization.get("bulk.form.send.success",new String[] {""+dumpedCount}),false, true);
                    
                    Toast.makeText(this, Localization.get("bulk.form.send.success",new String[] {""+dumpedCount}), Toast.LENGTH_LONG).show();
                    refreshView();
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
                    refreshView();
                    return;
                } else if(resultCode == WipeTask.WIPE_TASK_ID){
                    int dumpedCount = intent.getIntExtra(CommCareWiFiDirectActivity.KEY_NUMBER_DUMPED, -1);
                    
                    displayMessage(Localization.get("bulk.form.send.success",new String[] {""+dumpedCount}),false, true);
                    
                    Toast.makeText(this, "Forms successfully submitted.", Toast.LENGTH_LONG).show();
                    refreshView();
                    return;
                }
            case REPORT_PROBLEM_ACTIVITY:
                if(resultCode == RESULT_CANCELED) {
                    return;
                }
                else if(resultCode == RESULT_OK){
                    CommCareApplication._().notifyLogsPending();
                    refreshView();
                    return;    
                }
            case LOGIN_USER:
                if(resultCode == RESULT_CANCELED) {
                    this.finish();
                    return;
                } else if(resultCode == RESULT_OK) {
                    if (!intent.getBooleanExtra(LoginActivity.ALREADY_LOGGED_IN, false)) {
                        refreshView();
                        
                        //Unless we're about to sync (which will handle this
                        //in a blocking fashion), trigger off a regular unsent
                        //task processor
                        if(!CommCareApplication._().isSyncPending(false)) {
                            checkAndStartUnsentTask(false);
                        }
                        
                        if(isDemoUser()) {
                            showDemoModeWarning();
                        }
                    }
                    return;
                }
                break;
                
            case GET_INCOMPLETE_FORM:
                //TODO: We might need to load this from serialized state?
                if(resultCode == RESULT_CANCELED) {
                    refreshView();
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
                    if(ssds.size() == 1) {
                        currentState.loadFromStateDescription(ssdStorage.read(ssds.firstElement()));
                    } else {
                        currentState.setFormRecordId(r.getID());
                    }

                    if (CommCareApplication._().getCurrentApp() != null) {
                        platform = CommCareApplication._().getCommCarePlatform();
                    }
                    formEntry(platform.getFormContentUri(r.getFormNamespace()), r);
                    return;
                }
                break;
            case GET_COMMAND:
                //TODO: We might need to load this from serialized state?
                if(resultCode == RESULT_CANCELED) {
                    if(currentState.getSession().getCommand() == null) {
                        //Needed a command, and didn't already have one. Stepping back from
                        //an empty state, Go home!
                        currentState.reset();
                        refreshView();
                        return;
                    } else {
                        currentState.getSession().stepBack();
                        break;
                    }
                } else if(resultCode == RESULT_OK) {
                    //Get our command, set it, and continue forward
                    String command = intent.getStringExtra(SessionFrame.STATE_COMMAND_ID);
                    currentState.getSession().setCommand(command);
                    break;
                }
                break;
            case GET_CASE:
                //TODO: We might need to load this from serialized state?
                CommCareSession currentSession = currentState.getSession();
                if (resultCode == RESULT_CANCELED) {
                    currentSession.stepBack();
                } else if (resultCode == RESULT_OK) {
                    String sessionDatumId = currentSession.getNeededDatum().getDataId();
                    String chosenCaseId = intent.getStringExtra(SessionFrame.STATE_DATUM_VAL);
                    currentSession.setDatum(sessionDatumId, chosenCaseId);
                }
                break;
            case MODEL_RESULT:
                boolean fetchNext = processReturnFromFormEntry(resultCode, intent);
                if (!fetchNext) {
                    return;
                }
                break;
            }
            sessionNavigator.startNextSessionStep();
        }
        super.onActivityResult(requestCode, resultCode, intent);
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
            clearSessionAndExit(currentState);
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
                clearSessionAndExit(currentState);
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
                checkAndStartUnsentTask(false);

                refreshView();

                if (wasExternal) {
                    this.finish();
                    return false;
                }

                // XXX: probably refactor part of this logic into InstanceProvider -- PLM
                // Before we can terminate the session, we need to know that the form has been processed
                // in case there is state that depends on it.
                if (!currentState.terminateSession()) {
                    // If we didn't find somewhere to go, we're gonna stay here
                    return false;
                }
                // Otherwise, we want to keep proceeding in order
                // to keep running the workflow
            } else {
                // Form record is now stored.
                // TODO: session state clearing might be something we want to
                // do in InstanceProvider.bindToFormRecord.
                clearSessionAndExit(currentState);
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

    /**
     * clear local state in session session, and finish if was external is set,
     * otherwise refesh the view.
     */
    private void clearSessionAndExit(AndroidSessionWrapper currentState) {
        currentState.reset();
        if (wasExternal) {
            this.finish();
        }
        refreshView();
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
                asw.getSession().stepBack();
                CommCareHomeActivity.this.sessionNavigator.startNextSessionStep();
            }
        });
    }

    private void handleNoFormFromSessionNav(AndroidSessionWrapper asw) {
        if (asw.terminateSession()) {
            sessionNavigator.startNextSessionStep();
        } else {
            this.refreshView();
        }
    }

    // CommCare needs a menu or form selection to proceed
    private void handleGetCommand(AndroidSessionWrapper asw) {
        Intent i;
        if (DeveloperPreferences.isGridMenuEnabled()) {
            i = new Intent(getApplicationContext(), MenuGrid.class);
        } else {
            i = new Intent(getApplicationContext(), MenuList.class);
        }
        i.putExtra(SessionFrame.STATE_COMMAND_ID, asw.getSession().getCommand());
        startActivityForResult(i, GET_COMMAND);
    }

    private void launchEntitySelect(CommCareSession session) {
        Intent i = new Intent(getApplicationContext(), EntitySelectActivity.class);
        i.putExtra(SessionFrame.STATE_COMMAND_ID, session.getCommand());
        StackFrameStep lastPopped = session.getPoppedStep();
        if (lastPopped != null && SessionFrame.STATE_DATUM_VAL.equals(lastPopped.getType())) {
            i.putExtra(EntitySelectActivity.EXTRA_ENTITY_KEY, lastPopped.getValue());
        }
        startActivityForResult(i, GET_CASE);
    }

    // Launch an intent to load the confirmation screen for the given selection
    private void launchConfirmDetail(AndroidSessionWrapper asw) {
        CommCareSession session = asw.getSession();
        SessionDatum selectDatum = session.getNeededDatum();
        Intent detailIntent = new Intent(getApplicationContext(), EntityDetailActivity.class);
        EntitySelectActivity.populateDetailIntent(
                detailIntent, sessionNavigator.getCurrentAutoSelection(), selectDatum, asw);
        startActivityForResult(detailIntent, GET_CASE);
    }

    private void createErrorDialog(String errorMsg, AlertDialog.OnClickListener errorListener) {
        AlertDialog mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAlertDialog.setTitle(Localization.get("app.handled.error.title"));
        mAlertDialog.setMessage(errorMsg);
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(DialogInterface.BUTTON_POSITIVE, Localization.get("dialog.ok"), errorListener);
        mAlertDialog.show();
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

        if (CommCareApplication._().getCurrentApp() != null) {
            platform = CommCareApplication._().getCommCarePlatform();
        }

        formEntry(platform.getFormContentUri(record.getFormNamespace()), record, CommCareActivity.getTitle(this, null));
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

    private void formEntry(Uri formUri, FormRecord r) {
        formEntry(formUri, r, null);
    }

    private void formEntry(Uri formUri, FormRecord r, String headerTitle) {
        Logger.log(AndroidLogger.TYPE_FORM_ENTRY, "Form Entry Starting|" + r.getFormNamespace());

        //TODO: This is... just terrible. Specify where external instance data should come from
        FormLoaderTask.iif = new CommCareInstanceInitializer(CommCareApplication._().getCurrentSession());

        //Create our form entry activity callout
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
     * @return Were forms sent to the server by this method invocation?
     */
    private boolean checkAndStartUnsentTask(final boolean syncAfterwards) {
        SqlStorage<FormRecord> storage = CommCareApplication._().getUserStorage(FormRecord.class);
        FormRecord[] records = StorageUtils.getUnsentRecords(storage);

        if(records.length > 0) {
            processAndSend(records, syncAfterwards);
            return true;
        } else {
            return false;
        }
    }

    private String getFormPostURL() {
        SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
        return settings.getString("PostURL", this.getString(R.string.PostURL));
    }

    @SuppressLint("NewApi")
    private void processAndSend(FormRecord[] records, final boolean syncAfterwards) {

        int sendTaskId = syncAfterwards ? ProcessAndSendTask.SEND_PHASE_ID : -1;

        ProcessAndSendTask<CommCareHomeActivity> mProcess = new ProcessAndSendTask<CommCareHomeActivity>(this, getFormPostURL(),
                sendTaskId, syncAfterwards) {

            @Override
            protected void deliverResult(CommCareHomeActivity receiver, Integer result) {
                if (result == ProcessAndSendTask.PROGRESS_LOGGED_OUT) {
                    returnToLogin();
                    return;
                }
                receiver.refreshView();

                int successfulSends = this.getSuccesfulSends();

                if (result == FormUploadUtil.FULL_SUCCESS) {
                    String label = Localization.get("sync.success.sent.singular", new String[]{String.valueOf(successfulSends)});
                    if (successfulSends > 1) {
                        label = Localization.get("sync.success.sent", new String[]{String.valueOf(successfulSends)});
                    }
                    receiver.displayMessage(label);

                    if (syncAfterwards) {
                        syncData(true);
                    }
                } else if (result != FormUploadUtil.FAILURE) {
                    // Tasks with failure result codes will have already created a notification
                    receiver.displayMessage(Localization.get("sync.fail.unsent"), true);
                }
            }

            @Override
            protected void deliverUpdate(CommCareHomeActivity receiver, Long... update) {
                //we don't need to deliver updates here, it happens on the notification bar
            }

            @Override
            protected void deliverError(CommCareHomeActivity receiver, Exception e) {
                //TODO: Display somewhere useful
                receiver.displayMessage(Localization.get("sync.fail.unsent"), true);
            }

        };

        try {
            mProcess.setListeners(CommCareApplication._().getSession().startDataSubmissionListener());
        } catch (SessionUnavailableException sue) {
            // abort since it looks like the session expired
            return;
        }
        mProcess.connect(this);

        //Execute on a true multithreaded chain. We should probably replace all of our calls with this
        //but this is the big one for now.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mProcess.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, records);
        } else {
            mProcess.execute(records);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (CommCareApplication._().getCurrentApp() != null) {
            platform = CommCareApplication._().getCommCarePlatform();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            refreshActionBar();
        }
        dispatchHomeScreen();
    }

    private void dispatchHomeScreen() {

        // Before anything else, see if we're in a failing db state
        int dbState = CommCareApplication._().getDatabaseState();
        if (dbState == CommCareApplication.STATE_MIGRATION_FAILED) {
            CommCareApplication._().triggerHandledAppExit(this,
                    getString(R.string.migration_definite_failure),
                    getString(R.string.migration_failure_title), false);
            return;
        } else if (dbState == CommCareApplication.STATE_MIGRATION_QUESTIONABLE) {
            CommCareApplication._().triggerHandledAppExit(this,
                        getString(R.string.migration_possible_failure),
                        getString(R.string.migration_failure_title), false);
            return;
        } else if (dbState == CommCareApplication.STATE_CORRUPTED) {
            handleDamagedApp();
        }

        CommCareApp currentApp = CommCareApplication._().getCurrentApp();

        // Path 1: There is a seated app
        if (currentApp != null) {
            ApplicationRecord currentRecord = currentApp.getAppRecord();

            // Note that the order in which these conditions are checked matters!!
            try {
                if (currentApp.getAppResourceState() == CommCareApplication.STATE_CORRUPTED) {
                    // Path 1a: The seated app is damaged or corrupted
                    handleDamagedApp();
                } else if (!currentRecord.isUsable()) {
                    // Path 1b: The seated app is unusable (means either it is archived or is
                    // missing its MM or both)
                    boolean unseated = handleUnusableApp(currentRecord);
                    if (unseated) {
                        // Recurse in order to make the correct decision based on the new state
                        dispatchHomeScreen();
                    }
                } else if (!CommCareApplication._().getSession().isActive()) {
                    // Path 1c: The user is not logged in
                    returnToLogin();
                } else if (this.getIntent().hasExtra(SESSION_REQUEST)) {
                    // Path 1d: CommCare was launched from an external app, with a session descriptor
                    handleExternalLaunch();
                } else if (this.getIntent().hasExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT)) {
                    // Path 1e: CommCare was launched from a shortcut
                    handleShortcutLaunch();
                } else if (CommCareApplication._().isUpdatePending()) {
                    // Path 1f: There is an update pending
                    handlePendingUpdate();
                } else if (CommCareApplication._().isSyncPending(false)) {
                    // Path 1g: There is a sync pending
                    handlePendingSync();
                } else {
                    // Path 1h: Display the normal home screen!
                    refreshView();
                }
            } catch (SessionUnavailableException sue) {
                returnToLogin();
            }
        }

        // Path 2: There is no seated app, so launch CommCareSetupActivity
        else {
            if (CommCareApplication._().usableAppsPresent()) {
                // This is BAD -- means we ended up at home screen with no seated app, but there
                // are other usable apps available. Should not be able to happen.
                Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "In CommCareHomeActivity with no" +
                        "seated app, but there are other usable apps available on the device.");
            }
            Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
            this.startActivityForResult(i, INIT_APP);
        }
    }

    // region: private helper methods used by dispatchHomeScreen(), to prevent it from being one
    // extremely long method

    private void handleDamagedApp() {
        if (!CommCareApplication._().isStorageAvailable()) {
            createNoStorageDialog();
        } else {
            // See if we're logged in. If so, prompt for recovery.
            try {
                CommCareApplication._().getSession();
                showDialog(DIALOG_CORRUPTED);
            } catch(SessionUnavailableException e) {
                // Otherwise, log in first
                returnToLogin();
            }
        }
    }

    /**
     *
     * @param record the ApplicationRecord corresponding to the seated, unusable app
     * @return if the unusable app was unseated by this method
     */
    private boolean handleUnusableApp(ApplicationRecord record) {
        if (record.isArchived()) {
            // If the app is archived, unseat it and try to seat another one
            CommCareApplication._().unseat(record);
            CommCareApplication._().initFirstUsableAppRecord();
            return true;
        }
        else {
            // This app has unvalidated MM
            if (CommCareApplication._().usableAppsPresent()) {
                // If there are other usable apps, unseat it and seat another one
                CommCareApplication._().unseat(record);
                CommCareApplication._().initFirstUsableAppRecord();
                return true;
            } else {
                handleUnvalidatedApp();
                return false;
            }
        }
    }

    /**
     * Handles the case where the seated app is unvalidated and there are no other usable apps
     * to seat instead -- Either calls out to verification activity or quits out of the app
     */
    private void handleUnvalidatedApp() {
        if (CommCareApplication._().shouldSeeMMVerification()) {
            Intent i = new Intent(this, CommCareVerificationActivity.class);
            this.startActivityForResult(i, MISSING_MEDIA_ACTIVITY);
        } else {
            // Means that there are no usable apps, but there are multiple apps who all don't have
            // MM verified -- show an error message and shut down
            CommCareApplication._().triggerHandledAppExit(this,
                    Localization.get("multiple.apps.unverified.message"),
                    Localization.get("multiple.apps.unverified.title"));
        }

    }

    private void handleExternalLaunch() {
        wasExternal = true;
        String sessionRequest = this.getIntent().getStringExtra(SESSION_REQUEST);
        SessionStateDescriptor ssd = new SessionStateDescriptor();
        ssd.fromBundle(sessionRequest);
        CommCareApplication._().getCurrentSessionWrapper().loadFromStateDescription(ssd);
        sessionNavigator.startNextSessionStep();
    }

    private void handleShortcutLaunch() {
        //We were launched in shortcut mode. Get the command and load us up.
        CommCareApplication._().getCurrentSession().setCommand(
                this.getIntent().getStringExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT));
        sessionNavigator.startNextSessionStep();
        //Only launch shortcuts once per intent
        this.getIntent().removeExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT);
    }

    private void handlePendingUpdate() {
        Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Auto-Update Triggered");

        //Create the update intent
        Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        String ref = prefs.getString("default_app_server", null);

        i.putExtra(CommCareSetupActivity.KEY_PROFILE_REF, ref);
        i.putExtra(CommCareSetupActivity.KEY_UPGRADE_MODE, true);
        i.putExtra(CommCareSetupActivity.KEY_AUTO, true);
        startActivityForResult(i, UPGRADE_APP);
    }

    private void handlePendingSync() {
        long lastSync = CommCareApplication._().getCurrentApp().getAppPreferences().getLong("last-ota-restore", 0);
        String footer = lastSync == 0 ? "never" : SimpleDateFormat.getDateTimeInstance().format(lastSync);
        Logger.log(AndroidLogger.TYPE_USER, "autosync triggered. Last Sync|" + footer);
        refreshView();

        //Send unsent forms first. If the process detects unsent forms
        //it will sync after the are submitted
        if(!this.checkAndStartUnsentTask(true)) {
            //If there were no unsent forms to be sent, we should immediately
            //trigger a sync
            this.syncData(false);
        }
    }

    private void createNoStorageDialog() {
        CommCareApplication._().triggerHandledAppExit(this, Localization.get("app.storage.missing.message"), Localization.get("app.storage.missing.title"));
    }

    // endregion


    private void createAskUseOldDialog(final AndroidSessionWrapper state, final SessionStateDescriptor existing) {
        AlertDialog mAskOldDialog = new AlertDialog.Builder(this).create();
        mAskOldDialog.setTitle(Localization.get("app.workflow.incomplete.continue.title"));
        mAskOldDialog.setMessage(Localization.get("app.workflow.incomplete.continue"));
        DialogInterface.OnClickListener useOldListener = new DialogInterface.OnClickListener() {
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
                        break;
                    default:
                        break;
                }
            }
        };
        mAskOldDialog.setCancelable(false);
        mAskOldDialog.setButton(DialogInterface.BUTTON_POSITIVE, Localization.get("option.yes"), useOldListener);
        mAskOldDialog.setButton(DialogInterface.BUTTON_NEGATIVE, Localization.get("app.workflow.incomplete.continue.option.delete"), useOldListener);
        mAskOldDialog.setButton(DialogInterface.BUTTON_NEUTRAL, Localization.get("option.no"), useOldListener);

        mAskOldDialog.show();
    }

    private void displayMessage(String message) {
        displayMessage(message, false);
    }

    private void displayMessage(String message, boolean bad) {
        displayMessage(message, bad, false);
    }

    private void displayMessage(String message, boolean bad, boolean suppressToast) {
        if (!suppressToast) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }

        adapter.setNotificationTextForButton(R.layout.home_sync_button, message);
    }

    private void refreshView() {
        TextView version = (TextView)findViewById(R.id.str_version);
        if (version == null) {
            return;
        }
        version.setText(CommCareApplication._().getCurrentVersionString());
        boolean syncOK = true;

        Pair<Long, int[]> syncDetails;
        try {
            syncDetails = CommCareApplication._().getSyncDisplayParameters();
        } catch (UserStorageClosedException e) {
            returnToLogin();
            return;
        }

        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();

        int unsentFormNumberLimit = Integer.parseInt(prefs.getString(UNSENT_FORM_NUMBER_KEY, "5"));
        int unsentFormTimeLimit = Integer.parseInt(prefs.getString(UNSENT_FORM_TIME_KEY, "5"));

        String syncKey = "home.sync";
        String lastMessageKey = "home.sync.message.last";
        String homeMessageKey = "home.start";
        String logoutMessageKey = "home.logout";

        if (isDemoUser()) {
            syncKey = "home.sync.demo";
            lastMessageKey = "home.sync.message.last";
            homeMessageKey = "home.start.demo";
            logoutMessageKey = "home.logout.demo";
        }

        if (startButton != null) {
            startButton.setText(Localization.get(homeMessageKey));
        }
        if (logoutButton != null) {
            logoutButton.setText(Localization.get(logoutMessageKey));
        }
        if (syncButton != null) {
            setSyncButtonText(syncDetails, syncKey);
        }

        CharSequence syncTime;
        if (syncDetails.first == 0) {
            syncTime = Localization.get("home.sync.message.last.never");
        } else {
            syncTime = DateUtils.formatSameDayTime(syncDetails.first, new Date().getTime(), DateFormat.DEFAULT, DateFormat.DEFAULT);
        }

        //TODO: Localize this all
        String message = "";
        if (syncDetails.second[0] == 1) {
            message += Localization.get("home.sync.message.unsent.singular") + "\n";
        } else if (syncDetails.second[0] > 1) {
            message += Localization.get("home.sync.message.unsent.plural", new String[]{String.valueOf(syncDetails.second[0])}) + "\n";
        }

        setIncompleteFormsText(syncDetails);

        if (syncDetails.second[0] > unsentFormNumberLimit) {
            syncOK = false;
        }

        long then = syncDetails.first;
        long now = new Date().getTime();

        int secs_ago = (int)((then - now) / 1000);
        int days_ago = secs_ago / 86400;

        if ((-days_ago) > unsentFormTimeLimit && (prefs.getString("server-tether", "push-only").equals("sync"))) {
            syncOK = false;
        }

        message += Localization.get(lastMessageKey, new String[]{syncTime.toString()});

        displayMessage(message, !syncOK, true);


        //Make sure that the review button is properly enabled.
        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
        if (p != null && p.isFeatureActive(Profile.FEATURE_REVIEW)) {
            adapter.setButtonVisibility(R.layout.home_savedforms_button, false);
        }

        // set adapter to hide the buttons...
        boolean showSavedForms = CommCarePreferences.isSavedFormsEnabled();
        boolean showIncompleteForms = CommCarePreferences.isIncompleteFormsEnabled();

        Log.i("ShowForms", "ShowSavedForms: " + showSavedForms + " | ShowIncompleteForms: " + showIncompleteForms);

        adapter.setButtonVisibility(R.layout.home_savedforms_button, !showSavedForms);
        adapter.setButtonVisibility(R.layout.home_incompleteforms_button, !showIncompleteForms);

        updateCommCareBanner();

        adapter.notifyDataSetChanged();
    }

    @Override
    protected View getBannerHost() {
        return mTopBanner;
    }

    private void setSyncButtonText(Pair<Long, int[]> syncDetails, String syncTextKey) {
        if (syncTextKey == null) {
            syncTextKey = isDemoUser() ? "home.sync.demo" : "home.sync";
        }
        if (syncDetails.second[0] > 0) {
            Spannable syncIndicator = (this.localize("home.sync.indicator", new String[]{String.valueOf(syncDetails.second[0]), Localization.get(syncTextKey)}));
            syncButton.setNotificationText(syncIndicator);
            adapter.notifyDataSetChanged();
        } else {
            syncButton.setText(this.localize(syncTextKey));
        }
    }

    private void setIncompleteFormsText(Pair<Long, int[]> syncDetails) {
        if (syncDetails.second[1] > 0) {
            Log.i("syncDetails", "SyncDetails has count " + syncDetails.second[1]);
            Spannable incompleteIndicator = (this.localize("home.forms.incomplete.indicator", new String[]{String.valueOf(syncDetails.second[1]), Localization.get("home.forms.incomplete")}));
            if (viewIncomplete != null) {
                viewIncomplete.setText(incompleteIndicator);
            }

        } else {
            Log.i("syncDetails", "SyncDetails has no count");
            if (viewIncomplete != null) {
                viewIncomplete.setText(this.localize("home.forms.incomplete"));
            }
        }
    }

    private boolean isDemoUser() {
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
        menu.add(0, MENU_CALL_LOG, 0, Localization.get("home.menu.call.log")).setIcon(
                android.R.drawable.ic_menu_recent_history);
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
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
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
        } catch (SessionUnavailableException sue) {
            //Nothing
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case MENU_PREFERENCES:
                createPreferencesMenu(this);
                return true;
            case MENU_UPDATE:
                if (isNetworkNotConnected() && isAirplaneModeOn()) {
                    CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Sync_AirplaneMode));
                    return true;
                }
                Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
                SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
                String ref = prefs.getString("default_app_server", null);
                i.putExtra(CommCareSetupActivity.KEY_PROFILE_REF, ref);
                i.putExtra(CommCareSetupActivity.KEY_UPGRADE_MODE, true);

                startActivityForResult(i, UPGRADE_APP);
                return true;
            case MENU_CALL_LOG:
                createCallLogActivity();
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
        }
        return super.onOptionsItemSelected(item);
    }
    
    public static void createPreferencesMenu(Activity activity) {
        Intent i = new Intent(activity, CommCarePreferences.class);
        activity.startActivityForResult(i, PREFERENCES_ACTIVITY);
    }

    private void createCallLogActivity() {
        Intent i = new Intent(this, PhoneLogActivity.class);
        startActivity(i);

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

    private boolean isAirplaneModeOn() {
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return Settings.Global.getInt(getApplicationContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        } else {
            return Settings.System.getInt(getApplicationContext().getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        }
    }

    private boolean hasP2p() {
        return (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH && getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT));
    }

    protected Dialog onCreateDialog(int id) {
        if (id == DIALOG_CORRUPTED) {
            return createAskFixDialog();
        } else return null;
    }

    private Dialog createAskFixDialog() {
        //TODO: Localize this in theory, but really shift it to the upgrade/management state
        AlertDialog mAttemptFixDialog = new AlertDialog.Builder(this).create();

        mAttemptFixDialog.setTitle("Storage is Corrupt :/");
        mAttemptFixDialog.setMessage("Sorry, something really bad has happened, and the app can't start up. With your permission CommCare can try to repair itself if you have network access.");
        DialogInterface.OnClickListener attemptFixDialog = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE: // attempt repair
                        Intent intent = new Intent(CommCareHomeActivity.this, RecoveryActivity.class);
                        startActivity(intent);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE: // Shut down
                        CommCareHomeActivity.this.finish();
                        break;
                }
            }
        };
        mAttemptFixDialog.setCancelable(false);
        mAttemptFixDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Enter Recovery Mode", attemptFixDialog);
        mAttemptFixDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Shut Down", attemptFixDialog);

        return mAttemptFixDialog;
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

    private void returnToLogin() {
        Intent i = new Intent(this.getApplicationContext(), LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        this.startActivityForResult(i, LOGIN_USER);
    }
}
