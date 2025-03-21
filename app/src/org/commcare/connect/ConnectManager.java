package org.commcare.connect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.connect.ConnectActivity;
import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.activities.connect.ConnectMessagingActivity;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.commcaresupportlibrary.CommCareLauncher;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectDatabaseUtils;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.ConnectSsoHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.connect.workers.ConnectHeartbeatWorker;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.preferences.AppManagerDeveloperPreferences;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.util.LogTypes;
import org.commcare.utils.BiometricsHelper;
import org.commcare.utils.CrashUtil;
import org.commcare.views.connect.RoundedButton;
import org.commcare.views.connect.connecttextview.ConnectRegularTextView;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Manager class for ConnectID, handles workflow navigation and user management
 *
 * @author dviggiano
 */
public class ConnectManager {
    private static final String CONNECT_WORKER = "connect_worker";
    private static final long PERIODICITY_FOR_HEARTBEAT_IN_HOURS = 4;
    private static final long BACKOFF_DELAY_FOR_HEARTBEAT_RETRY = 5 * 60 * 1000L; // 5 mins
    private static final String CONNECT_HEARTBEAT_REQUEST_NAME = "connect_hearbeat_periodic_request";
    private static final int APP_DOWNLOAD_TASK_ID = 4;
    public static final int MethodRegistrationPrimary = 1;
    public static final int MethodRecoveryPrimary = 2;

    public static final int PENDING_ACTION_NONE = 0;
    public static final int PENDING_ACTION_CONNECT_HOME = 1;
    public static final int PENDING_ACTION_OPP_STATUS = 2;

    private BiometricManager biometricManager;


    public static int getFailureAttempt() {
        return getInstance().failedPinAttempts;
    }

    public static void setFailureAttempt(int failureAttempt) {
        getInstance().failedPinAttempts = failureAttempt;
    }

    /**
     * Enum representing the current state of ConnectID
     */
    public enum ConnectIdStatus {
        NotIntroduced,
        Registering,
        LoggedIn
    }

    public enum ConnectAppMangement {
        Unmanaged, ConnectId, Connect
    }

    /**
     * Interface for handling callbacks when a ConnectID activity finishes
     */
    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }

    private static ConnectManager manager = null;
    private ConnectIdStatus connectStatus = ConnectIdStatus.NotIntroduced;
    private Context parentActivity;

    private String primedAppIdForAutoLogin = null;

    private int pendingAction = PENDING_ACTION_NONE;

    //Singleton, private constructor
    private ConnectManager() {
    }

    private static ConnectManager getInstance() {
        if (manager == null) {
            manager = new ConnectManager();
        }

        return manager;
    }

    public static ConnectIdStatus getStatus() {
        return getInstance().connectStatus;
    }

    public static void setStatus(ConnectIdStatus connectStatus) {
        getInstance().connectStatus = connectStatus;
    }

    public static void init(Context parent) {
        ConnectManager manager = getInstance();
        manager.parentActivity = parent;

        if (manager.connectStatus == ConnectIdStatus.NotIntroduced) {
            ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(manager.parentActivity);
            if (user != null) {
                boolean registering = user.getRegistrationPhase() != ConnectConstants.CONNECT_NO_ACTIVITY;
                manager.connectStatus = registering ? ConnectIdStatus.Registering : ConnectIdStatus.LoggedIn;

                String remotePassphrase = ConnectDatabaseUtils.getConnectDbEncodedPassphrase(parent, false);
                if (remotePassphrase == null) {
                    getRemoteDbPassphrase(parent, user);
                }
            } else if (ConnectDatabaseHelper.isDbBroken()) {
                //Corrupt DB, inform user to recover
                ConnectDatabaseHelper.handleCorruptDb(parent);
            }
        }
    }

    public static void setPendingAction(int action) {
        getInstance().pendingAction = action;
    }

    public static int getPendingAction() {
        int action = getInstance().pendingAction;
        getInstance().pendingAction = PENDING_ACTION_NONE;
        return action;
    }

    public static BiometricManager getBiometricManager(CommCareActivity<?> parent){
        ConnectManager instance = getInstance();
        if (instance.biometricManager == null) {
            instance.biometricManager = BiometricManager.from(parent);
        }

        return instance.biometricManager;
    }

    private static void scheduleHearbeat() {
        if (AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build();

            PeriodicWorkRequest heartbeatRequest =
                    new PeriodicWorkRequest.Builder(ConnectHeartbeatWorker.class,
                            PERIODICITY_FOR_HEARTBEAT_IN_HOURS,
                            TimeUnit.HOURS)
                            .addTag(CONNECT_WORKER)
                            .setConstraints(constraints)
                            .setBackoffCriteria(
                                    BackoffPolicy.EXPONENTIAL,
                                    BACKOFF_DELAY_FOR_HEARTBEAT_RETRY,
                                    TimeUnit.MILLISECONDS)
                            .build();

            WorkManager.getInstance(CommCareApplication.instance()).enqueueUniquePeriodicWork(
                    CONNECT_HEARTBEAT_REQUEST_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    heartbeatRequest
            );
        }
    }

    public static void setParent(Context parent) {
        getInstance().parentActivity = parent;
    }

    public static boolean isConnectIdConfigured() {
        return AppManagerDeveloperPreferences.isConnectIdEnabled()
                && getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    public static void unlockConnect(CommCareActivity<?> activity, ConnectActivityCompleteListener callback) {
        BiometricPrompt.AuthenticationCallback callbacks = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                callback.connectActivityComplete(false);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                callback.connectActivityComplete(true);
            }

            @Override
            public void onAuthenticationFailed() {
                callback.connectActivityComplete(false);
            }
        };

        BiometricManager bioManager = getBiometricManager(activity);
        if (BiometricsHelper.isFingerprintConfigured(activity, bioManager)) {
            BiometricsHelper.authenticateFingerprint(activity, bioManager, callbacks);
        } else if (BiometricsHelper.isPinConfigured(activity, bioManager)) {
            BiometricsHelper.authenticatePin(activity, bioManager, callbacks);
        } else {
            callback.connectActivityComplete(false);
            Logger.exception("No unlock method available when trying to unlock ConnectID", new Exception("No unlock option"));
        }
    }

    private static final DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());

    public static String formatDate(Date date) {
        return dateFormat.format(date);
    }

    private static final DateFormat opportunitydateFormat = new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault());

    public static String opportunityFormatDate(Date date) {
        return opportunitydateFormat.format(date);
    }

    private static final DateFormat paymentDateFormat = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault());

    public static String paymentDateFormat(Date date) {
        return paymentDateFormat.format(date);
    }

    public static String formatDateTime(Date date) {
        return SimpleDateFormat.getDateTimeInstance().format(date);
    }

    public static boolean shouldShowSecondaryPhoneConfirmationTile(Context context) {
        boolean show = false;

        if (isConnectIdConfigured()) {
            ConnectUserRecord user = getUser(context);
            show = !user.getSecondaryPhoneVerified();
        }

        return show;
    }

    public static void updateSecondaryPhoneConfirmationTile(Context context, View tile, boolean show, View.OnClickListener listener) {
        tile.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            ConnectUserRecord user = getUser(context);
            String dateStr = formatDate(user.getSecondaryPhoneVerifyByDate());
            String message = context.getString(R.string.login_connect_secondary_phone_message, dateStr);

            ConnectRegularTextView view = tile.findViewById(R.id.connect_phone_label);
            view.setText(message);

            RoundedButton yesButton = tile.findViewById(R.id.connect_phone_yes_button);
            yesButton.setOnClickListener(listener);

            RoundedButton noButton = tile.findViewById(R.id.connect_phone_no_button);
            noButton.setOnClickListener(v -> {
                tile.setVisibility(View.GONE);
            });
        }
    }

    public static void completeSignin() {
        ConnectManager instance = getInstance();
        instance.connectStatus = ConnectIdStatus.LoggedIn;

        scheduleHearbeat();
        CrashUtil.registerConnectUser();
    }

    public static boolean shouldShowSignInMenuOption() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return getInstance().connectStatus != ConnectIdStatus.LoggedIn;
    }

    public static boolean shouldShowSignOutMenuOption() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    public static boolean shouldShowConnectButton() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    public static void handleFinishedActivity(CommCareActivity<?> activity, int requestCode, int resultCode, Intent intent) {
        getInstance().parentActivity = activity;

        if (!BiometricsHelper.handlePinUnlockActivityResult(requestCode, resultCode)) {
            if (requestCode == ConnectConstants.CONNECT_JOB_INFO && resultCode == AppCompatActivity.RESULT_OK) {
                goToConnectJobsList(activity);
            }
        }
    }

    public static ConnectUserRecord getUser(Context context) {
        return ConnectUserDatabaseUtil.getUser(context);
    }

    public static void forgetUser(String reason) {
        ConnectManager manager = getInstance();

        if(ConnectDatabaseHelper.dbExists(manager.parentActivity)) {
            FirebaseAnalyticsUtil.reportCccDeconfigure(reason);
        }

        ConnectUserDatabaseUtil.forgetUser(manager.parentActivity);

        ConnectIdActivity.reset();

        manager.connectStatus = ConnectIdStatus.NotIntroduced;
    }

    public static ConnectJobRecord setConnectJobForApp(Context context, String appId) {
        ConnectJobRecord job = null;

        ConnectAppRecord appRecord = getAppRecord(context, appId);
        if (appRecord != null) {
            job = ConnectJobUtils.getCompositeJob(context, appRecord.getJobId());
        }

        setActiveJob(job);

        return job;
    }

    private ConnectJobRecord activeJob = null;
    private int failedPinAttempts = 0;

    public static void setActiveJob(ConnectJobRecord job) {
        getInstance().activeJob = job;
    }

    public static ConnectJobRecord getActiveJob() {
        return getInstance().activeJob;
    }

    private static void launchConnectId(CommCareActivity<?> parent, String task, ConnectActivityCompleteListener listener) {
        Intent intent = new Intent(parent, ConnectIdActivity.class);
        intent.putExtra("TASK", task);
        parent.startActivityForResult(intent, ConnectConstants.CONNECT_JOB_INFO);
    }

    public static void registerUser(CommCareActivity<?> parent, ConnectActivityCompleteListener callback) {
        launchConnectId(parent, ConnectConstants.BEGIN_REGISTRATION, callback);
    }

    public static void beginSecondaryPhoneVerification(CommCareActivity<?> parent, ConnectActivityCompleteListener callback) {
        launchConnectId(parent, ConnectConstants.VERIFY_PHONE, callback);
    }

    public static void goToConnectJobsList(Context parent) {
        manager.parentActivity = parent;
        completeSignin();
        Intent i = new Intent(parent, ConnectActivity.class);
        parent.startActivity(i);
    }

    public static void goToMessaging(Context parent) {
        manager.parentActivity = parent;
        Intent i = new Intent(parent, ConnectMessagingActivity.class);
        parent.startActivity(i);
    }

    public static void goToActiveInfoForJob(Activity activity, boolean allowProgression) {
        completeSignin();
        Intent i = new Intent(activity, ConnectActivity.class);
        i.putExtra("info", true);
        i.putExtra("buttons", allowProgression);
        activity.startActivity(i);
    }

    public static void forgetAppCredentials(String appId, String userId) {
        ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getAppData(manager.parentActivity, appId, userId);
        if (record != null) {
            ConnectAppDatabaseUtil.deleteAppData(manager.parentActivity, record);
        }
    }

    public static void updateAppAccess(CommCareActivity<?> activity, String appId, String username) {
        ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getAppData(activity, appId, username);
        if (record != null) {
            record.setLastAccessed(new Date());
            ConnectAppDatabaseUtil.storeApp(activity, record);
        }
    }

    public static void checkConnectIdLink(CommCareActivity<?> activity, boolean autoLoggedIn, String appId, String username, String password, ConnectActivityCompleteListener callback) {
        switch(getAppManagement(activity, appId, username)) {
            case Unmanaged -> {
                //ConnectID is NOT configured
                boolean offerToLink = true;
                boolean isSecondOffer = false;

                ConnectLinkedAppRecord linkedApp = ConnectAppDatabaseUtil.getAppData(activity, appId, username);
                //See if we've offered to link already
                Date firstOffer = linkedApp != null ? linkedApp.getLinkOfferDate1() : null;
                if (firstOffer != null) {
                    isSecondOffer = true;
                    //See if we've done the second offer
                    Date secondOffer = linkedApp.getLinkOfferDate2();
                    if (secondOffer != null) {
                        //They've declined twice, we won't bug them again
                        offerToLink = false;
                    } else {
                        //Determine whether to do second offer
                        int daysToSecondOffer = 30;
                        long millis = (new Date()).getTime() - firstOffer.getTime();
                        long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
                        offerToLink = days >= daysToSecondOffer;
                    }
                }

                if (offerToLink) {
                    if (linkedApp == null) {
                        //Create the linked app record (even if just to remember that we offered)
                        linkedApp = ConnectAppDatabaseUtil.storeApp(activity, appId, username, false, "", false, false);
                    }

                    //Update that we offered
                    if (isSecondOffer) {
                        linkedApp.setLinkOfferDate2(new Date());
                    } else {
                        linkedApp.setLinkOfferDate1(new Date());
                    }

                    final ConnectLinkedAppRecord appRecordFinal = linkedApp;
                    StandardAlertDialog d = new StandardAlertDialog(activity,
                            activity.getString(R.string.login_link_connectid_title),
                            activity.getString(R.string.login_link_connectid_message));

                    d.setPositiveButton(activity.getString(R.string.login_link_connectid_yes), (dialog, which) -> {
                        activity.dismissAlertDialog();

                        unlockConnect(activity, success -> {
                            if (success) {
                                appRecordFinal.linkToConnectId(password);
                                ConnectAppDatabaseUtil.storeApp(activity, appRecordFinal);

                                //Link the HQ user by aqcuiring the SSO token for the first time
                                ConnectSsoHelper.retrieveHqSsoTokenAsync(activity, username, true, new ConnectSsoHelper.TokenCallback() {
                                    @Override
                                    public void tokenRetrieved(AuthInfo.TokenAuth token) {
                                        if (token == null) {
                                            //Toast.makeText(activity, "Failed to acquire SSO token", Toast.LENGTH_SHORT).show();
                                            //TODO: Re-enable when token working again
                                            //ConnectManager.forgetAppCredentials(appId, username);
                                        }

                                        callback.connectActivityComplete(true);
                                    }

                                    @Override
                                    public void tokenUnavailable() {
                                        ConnectNetworkHelper.handleTokenUnavailableException(activity);
                                    }

                                    @Override
                                    public void tokenRequestDenied() {
                                        ConnectNetworkHelper.handleTokenRequestDeniedException(activity);
                                    }
                                });
                            } else {
                                callback.connectActivityComplete(false);
                            }
                        });
                    });

                    d.setNegativeButton(activity.getString(R.string.login_link_connectid_no), (dialog, which) -> {
                        activity.dismissAlertDialog();

                        //Save updated record indicating that we offered
                        ConnectAppDatabaseUtil.storeApp(activity, appRecordFinal);

                        callback.connectActivityComplete(false);
                    });

                    activity.showAlertDialog(d);
                    return;
                }
            }
            case ConnectId -> {
                if (!autoLoggedIn) {
                    //See if user wishes to permanently sever the connection
                    StandardAlertDialog d = new StandardAlertDialog(activity,
                            activity.getString(R.string.login_unlink_connectid_title),
                            activity.getString(R.string.login_unlink_connectid_message));

                    d.setPositiveButton(activity.getString(R.string.login_link_connectid_yes), (dialog, which) -> {
                        activity.dismissAlertDialog();

                        unlockConnect(activity, success -> {
                            if (success) {
                                ConnectLinkedAppRecord linkedApp = ConnectAppDatabaseUtil.getAppData(activity, appId, username);
                                if (linkedApp != null) {
                                    linkedApp.severConnectIdLink();
                                    ConnectAppDatabaseUtil.storeApp(activity, linkedApp);
                                }
                            }

                            callback.connectActivityComplete(success);
                        });
                    });

                    d.setNegativeButton(activity.getString(R.string.login_link_connectid_no), (dialog, which) -> {
                        activity.dismissAlertDialog();

                        callback.connectActivityComplete(false);
                    });

                    activity.showAlertDialog(d);
                    return;
                }
            }
            default -> {
                //Connect managed app, nothing to do
            }
        }

        callback.connectActivityComplete(false);
    }

    public static boolean checkForFailedConnectIdAuth(String username) {
        try {
            if (isConnectIdConfigured()) {
                String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
                ConnectLinkedAppRecord appRecord = ConnectAppDatabaseUtil.getAppData(
                        CommCareApplication.instance(), seatedAppId, username);
                return appRecord != null && appRecord.getWorkerLinked();
            }
        } catch (Exception e){
            Logger.exception("Error while checking ConnectId status after failed token auth", e);
        }

        return false;
    }

    public static ConnectAppMangement getAppManagement(Context context, String appId, String userId) {
        ConnectAppRecord record = getAppRecord(context, appId);
        if(record != null) {
            return ConnectAppMangement.Connect;
        }

        return getCredentialsForApp(appId, userId) != null ?
                ConnectAppMangement.ConnectId :
                ConnectAppMangement.Unmanaged;
    }

    public static ConnectAppRecord getAppRecord(Context context, String appId) {
        return ConnectJobUtils.getAppRecord(context, appId);
    }

    public static String getStoredPasswordForApp(String appId, String userId) {
        AuthInfo.ProvidedAuth auth = getCredentialsForApp(appId, userId);
        return auth != null ? auth.password : null;
    }

    @Nullable
    public static AuthInfo.ProvidedAuth getCredentialsForApp(String appId, String userId) {
        ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getAppData(manager.parentActivity, appId,
                userId);
        if (record != null && record.getConnectIdLinked() && record.getPassword().length() > 0) {
            return new AuthInfo.ProvidedAuth(record.getUserId(), record.getPassword(), false);
        }

        return null;
    }

    public static AuthInfo.TokenAuth getConnectToken() {
        if (isConnectIdConfigured()) {
            ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(manager.parentActivity);
            Date currentDate = new Date();
            if (user != null && currentDate.compareTo(user.getConnectTokenExpiration()) < 0) {
                Logger.log(LogTypes.TYPE_MAINTENANCE,
                        "Found a valid existing Connect Token with current date set to " + currentDate +
                                " and record expiration date being " + user.getConnectTokenExpiration());
                return new AuthInfo.TokenAuth(user.getConnectToken());
            } else if (user != null) {
                Logger.log(LogTypes.TYPE_MAINTENANCE, "Existing Connect token is not valid");
            }
        }

        return null;
    }

    public static AuthInfo.TokenAuth getTokenCredentialsForApp(String appId, String userId) {
        if (isConnectIdConfigured()) {
            ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getAppData(manager.parentActivity, appId,
                    userId);
            Date currentDate = new Date();
            if (record != null && currentDate.compareTo(record.getHqTokenExpiration()) < 0) {
                Logger.log(LogTypes.TYPE_MAINTENANCE, "Found a valid existing HQ Token with current date set to " + currentDate +
                        " and record expiration date being "  + record.getHqTokenExpiration());
                return new AuthInfo.TokenAuth(record.getHqToken());
            } else if (record != null) {
                Logger.log(LogTypes.TYPE_MAINTENANCE, "Existing HQ Token is not valid");
            }
        }
        return null;
    }

    public static boolean isAppInstalled(String appId) {
        boolean installed = false;
        ArrayList<ApplicationRecord> apps = AppUtils.
                getInstalledAppRecords();
        for (ApplicationRecord app : apps) {
            if (appId.equals(app.getUniqueId())) {
                installed = true;
                break;
            }
        }
        return installed;
    }

    private boolean downloading = false;
    private ResourceEngineListener downloadListener = null;

    public static void downloadAppOrResumeUpdates(String installUrl, ResourceEngineListener listener) {
        ConnectManager instance = getInstance();
        instance.downloadListener = listener;
        if (!instance.downloading) {
            instance.downloading = true;
            //Start a new download
            ResourceInstallUtils.startAppInstallAsync(false, APP_DOWNLOAD_TASK_ID, new CommCareTaskConnector<ResourceEngineListener>() {
                @Override
                public void connectTask(CommCareTask task) {

                }

                @Override
                public void startBlockingForTask(int id) {

                }

                @Override
                public void stopBlockingForTask(int id) {
                    instance.downloading = false;
                }

                @Override
                public void taskCancelled() {

                }

                @Override
                public ResourceEngineListener getReceiver() {
                    return instance.downloadListener;
                }

                @Override
                public void startTaskTransition() {

                }

                @Override
                public void stopTaskTransition(int taskId) {

                }

                @Override
                public void hideTaskCancelButton() {

                }
            }, installUrl);
        }
    }

    public static void launchApp(Activity activity, boolean isLearning, String appId) {
        CommCareApplication.instance().closeUserSession();

        String appType = isLearning ? "Learn" : "Deliver";
        FirebaseAnalyticsUtil.reportCccAppLaunch(appType, appId);

        getInstance().primedAppIdForAutoLogin = appId;

        CommCareLauncher.launchCommCareForAppId(activity, appId);

        activity.finish();
    }

    public static boolean wasAppLaunchedFromConnect(String appId) {
        String primed = getInstance().primedAppIdForAutoLogin;
        getInstance().primedAppIdForAutoLogin = null;
        return primed != null && primed.equals(appId);
    }

    public static String checkAutoLoginAndOverridePassword(Context context, String appId, String username,
                                                           String passwordOrPin, boolean appLaunchedFromConnect, boolean uiInAutoLogin) {
        if (isConnectIdConfigured()) {
            if (appLaunchedFromConnect) {
                //Configure some things if we haven't already
                ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getAppData(context,
                        appId, username);
                if (record == null) {
                    record = prepareConnectManagedApp(context, appId, username);
                }

                passwordOrPin = record.getPassword();
            } else if (uiInAutoLogin) {
                String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
                ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getAppData(context, seatedAppId,
                        username);
                passwordOrPin = record != null ? record.getPassword() : null;

                if (record != null && record.isUsingLocalPassphrase()) {
                    //Report to analytics so we know when this stops happening
                    FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase(seatedAppId);
                }
            }
        }

        return passwordOrPin;
    }

    public static ConnectLinkedAppRecord prepareConnectManagedApp(Context context, String appId, String username) {
        //Create app password
        String password = generatePassword();

        //Store ConnectLinkedAppRecord (note worker already linked)
        ConnectLinkedAppRecord appRecord = ConnectAppDatabaseUtil.storeApp(context, appId, username, true, password, true, false);

        return appRecord;
    }

    public static void getRemoteDbPassphrase(Context context, ConnectUserRecord user) {
        ApiConnectId.fetchDbPassphrase(context, user, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(
                            StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);
                        String key = ConnectConstants.CONNECT_KEY_DB_KEY;
                        if (json.has(key)) {
                            ConnectDatabaseHelper.handleReceivedDbPassphrase(context, json.getString(key));
                        }
                    }
                } catch (IOException | JSONException e) {
                    Logger.exception("Parsing return from DB key request", e);
                }
            }

            @Override
            public void processFailure(int responseCode) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Failed: %d", responseCode));
            }

            @Override
            public void processNetworkFailure() {
                Logger.log("ERROR", "Failed (network)");
            }

            @Override
            public void processTokenUnavailableError() {
                Logger.log("ERROR", "Failed (token unavailable)");
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenRequestDeniedException(context);
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(context);
            }
        });
    }

    public static void updateJobProgress(Context context, ConnectJobRecord job, ConnectActivityCompleteListener listener) {
        switch (job.getStatus()) {
            case ConnectJobRecord.STATUS_LEARNING -> {
                updateLearningProgress(context, job, listener);
            }
            case ConnectJobRecord.STATUS_DELIVERING -> {
                updateDeliveryProgress(context, job, listener);
            }
            default -> {
                listener.connectActivityComplete(true);
            }
        }
    }

    public static void updateLearningProgress(Context context, ConnectJobRecord job, ConnectActivityCompleteListener listener) {
        ApiConnect.getLearnProgress(context, job.getJobId(), new IApiCallback() {
            private static void reportApiCall(boolean success) {
                FirebaseAnalyticsUtil.reportCccApiLearnProgress(success);
            }

            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        //Parse the JSON
                        JSONObject json = new JSONObject(responseAsString);

                        String key = "completed_modules";
                        JSONArray modules = json.getJSONArray(key);
                        List<ConnectJobLearningRecord> learningRecords = new ArrayList<>(modules.length());
                        for (int i = 0; i < modules.length(); i++) {
                            JSONObject obj = (JSONObject) modules.get(i);
                            ConnectJobLearningRecord record = ConnectJobLearningRecord.fromJson(obj, job.getJobId());
                            learningRecords.add(record);
                        }
                        job.setLearnings(learningRecords);
                        job.setCompletedLearningModules(learningRecords.size());

                        key = "assessments";
                        JSONArray assessments = json.getJSONArray(key);
                        List<ConnectJobAssessmentRecord> assessmentRecords = new ArrayList<>(assessments.length());
                        for (int i = 0; i < assessments.length(); i++) {
                            JSONObject obj = (JSONObject) assessments.get(i);
                            ConnectJobAssessmentRecord record = ConnectJobAssessmentRecord.fromJson(obj, job.getJobId());
                            assessmentRecords.add(record);
                        }
                        job.setAssessments(assessmentRecords);

                        ConnectJobUtils.updateJobLearnProgress(context, job);
                    }
                } catch (IOException | JSONException | ParseException e) {
                    Logger.exception("Parsing return from learn_progress request", e);
                }

                reportApiCall(true);
                listener.connectActivityComplete(true);
            }

            @Override
            public void processFailure(int responseCode) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Failed: %d", responseCode));
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processNetworkFailure() {
                Logger.log("ERROR", "Failed (network)");
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenRequestDeniedException(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }
        });
    }

    public static void updateDeliveryProgress(Context context, ConnectJobRecord job, ConnectActivityCompleteListener listener) {
        ApiConnect.getDeliveries(context, job.getJobId(), new IApiCallback() {
            private static void reportApiCall(boolean success) {
                FirebaseAnalyticsUtil.reportCccApiDeliveryProgress(success);
            }

            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                boolean success = true;
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        //Parse the JSON
                        JSONObject json = new JSONObject(responseAsString);

                        boolean updatedJob = false;
                        String key = "max_payments";
                        if (json.has(key)) {
                            job.setMaxVisits(json.getInt(key));
                            updatedJob = true;
                        }

                        key = "end_date";
                        if (json.has(key)) {
                            job.setProjectEndDate(DateUtils.parseDate(json.getString(key)));
                            updatedJob = true;
                        }

                        key = "payment_accrued";
                        if (json.has(key)) {
                            job.setPaymentAccrued(json.getInt(key));
                            updatedJob = true;
                        }

                        key = "is_user_suspended";
                        if (json.has(key)) {
                            job.setIsUserSuspended(json.getBoolean(key));
                            updatedJob = true;
                        }

                        if (updatedJob) {
                            job.setLastDeliveryUpdate(new Date());
                            ConnectJobUtils.upsertJob(context, job);
                        }

                        List<ConnectJobDeliveryRecord> deliveries = new ArrayList<>(json.length());
                        key = "deliveries";
                        if (json.has(key)) {
                            JSONArray array = json.getJSONArray(key);
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = (JSONObject) array.get(i);
                                ConnectJobDeliveryRecord delivery = ConnectJobDeliveryRecord.fromJson(obj, job.getJobId());
                                if (delivery != null) {
                                    //Note: Ignoring faulty deliveries (non-fatal exception logged)
                                    deliveries.add(delivery);
                                }
                            }

                            //Store retrieved deliveries
                            ConnectJobUtils.storeDeliveries(context, deliveries, job.getJobId(), true);

                            job.setDeliveries(deliveries);
                        }

                        List<ConnectJobPaymentRecord> payments = new ArrayList<>();
                        key = "payments";
                        if (json.has(key)) {
                            JSONArray array = json.getJSONArray(key);
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = (JSONObject) array.get(i);
                                payments.add(ConnectJobPaymentRecord.fromJson(obj, job.getJobId()));
                            }

                            ConnectJobUtils.storePayments(context, payments, job.getJobId(), true);

                            job.setPayments(payments);
                        }
                    }
                } catch (IOException | JSONException | ParseException e) {
                    Logger.exception("Parsing return from delivery progress request", e);
                    success = false;
                }

                reportApiCall(success);
                listener.connectActivityComplete(success);
            }

            @Override
            public void processFailure(int responseCode) {
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processNetworkFailure() {
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenRequestDeniedException(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }
        });
    }

    public static void updatePaymentConfirmed(Context context, final ConnectJobPaymentRecord payment, boolean confirmed, ConnectActivityCompleteListener listener) {
        ApiConnect.setPaymentConfirmed(context, payment.getPaymentId(), confirmed, new IApiCallback() {
            private void reportApiCall(boolean success) {
                FirebaseAnalyticsUtil.reportCccApiPaymentConfirmation(success);
            }

            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                payment.setConfirmed(confirmed);
                ConnectJobUtils.storePayment(context, payment);

                //No need to report to user
                reportApiCall(true);
                listener.connectActivityComplete(true);
            }

            @Override
            public void processFailure(int responseCode) {
                Toast.makeText(context, R.string.connect_payment_confirm_failed, Toast.LENGTH_SHORT).show();
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processNetworkFailure() {
                Toast.makeText(context, R.string.connect_payment_confirm_failed, Toast.LENGTH_SHORT).show();
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenRequestDeniedException(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(context);
                reportApiCall(false);
                listener.connectActivityComplete(false);
            }
        });
    }

    public static String generatePassword() {
        int passwordLength = 20;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_!.?";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < passwordLength; i++) {
            password.append(charSet.charAt(new Random().nextInt(charSet.length())));
        }

        return password.toString();
    }

    public static boolean shouldShowJobStatus(Context context, String appId) {
        ConnectAppRecord record = getAppRecord(context, appId);
        ConnectJobRecord job = getActiveJob();
        if(record == null || job == null) {
            return false;
        }

        //Only time not to show is when we're in learn app but job is in delivery state
        return !record.getIsLearning() || job.getStatus() != ConnectJobRecord.STATUS_DELIVERING;
    }
}
