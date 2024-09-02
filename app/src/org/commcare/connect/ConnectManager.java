package org.commcare.connect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.AppUtils;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.commcaresupportlibrary.CommCareLauncher;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.ConnectSsoHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.connect.workers.ConnectHeartbeatWorker;
import org.commcare.core.encryption.CryptUtil;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.models.encryption.ByteEncrypter;
import org.commcare.preferences.AppManagerDeveloperPreferences;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.utils.CrashUtil;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.PropertyUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import androidx.annotation.Nullable;

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

    public static int getFailureAttempt() {
        return ConnectManager.getInstance().failedPinAttempts;
    }

    public static void setFailureAttempt(int failureAttempt) {
        ConnectManager.getInstance().failedPinAttempts = failureAttempt;
    }

    /**
     * Enum representing the current state of ConnectID
     */
    public enum ConnectIdStatus {
        NotIntroduced,
        Registering,
        LoggedIn
    }

    /**
     * Interface for handling callbacks when a ConnectID activity finishes
     */
    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }

    private static ConnectManager manager = null;
    private ConnectIdStatus connectStatus = ConnectIdStatus.NotIntroduced;
    private CommCareActivity<?> parentActivity;
    private ConnectActivityCompleteListener loginListener;

    private String primedAppIdForAutoLogin = null;

    //Singleton, private constructor
    private ConnectManager() {
    }

    private static ConnectManager getInstance() {
        if (manager == null) {
            manager = new ConnectManager();
        }

        return manager;
    }

    public static void init(CommCareActivity<?> parent) {
        ConnectManager manager = getInstance();
        manager.parentActivity = parent;

        if(manager.connectStatus == ConnectIdStatus.NotIntroduced) {
            ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
            if (user != null) {
                boolean registering = user.getRegistrationPhase() != ConnectTask.CONNECT_NO_ACTIVITY;
                manager.connectStatus = registering ? ConnectIdStatus.Registering : ConnectIdStatus.LoggedIn;

                String remotePassphrase = ConnectDatabaseHelper.getConnectDbEncodedPassphrase(parent, false);
                if(remotePassphrase == null) {
                    getRemoteDbPassphrase(parent, user);
                }
            } else if(ConnectDatabaseHelper.isDbBroken()) {
                //Corrupt DB, inform user to recover
                ConnectDatabaseHelper.handleCorruptDb(parent);
            }
        }
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

    public static void setParent(CommCareActivity<?> parent) {
        getInstance().parentActivity = parent;
    }

    public static boolean isConnectIdIntroduced() {
        return AppManagerDeveloperPreferences.isConnectIdEnabled()
                && getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    public static boolean isUnlocked() {
        return AppManagerDeveloperPreferences.isConnectIdEnabled()
                && getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    private static final DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
    public static String formatDate(Date date) {
        return dateFormat.format(date);
    }

    public static String formatDateTime(Date date) {
        return SimpleDateFormat.getDateTimeInstance().format(date);
    }

    public static boolean shouldShowSecondaryPhoneConfirmationTile(Context context) {
        boolean show = false;

        if(isConnectIdIntroduced()) {
            ConnectUserRecord user = getUser(context);
            show = !user.getSecondaryPhoneVerified();
        }

        return show;
    }

    public static void updateSecondaryPhoneConfirmationTile(Context context, ConstraintLayout tile, boolean show, View.OnClickListener listener) {
        tile.setVisibility(show ? View.VISIBLE : View.GONE);

        if(show) {
            ConnectUserRecord user = ConnectManager.getUser(context);
            String dateStr = ConnectManager.formatDate(user.getSecondaryPhoneVerifyByDate());
            String message = context.getString(R.string.login_connect_secondary_phone_message, dateStr);

            TextView view = tile.findViewById(R.id.connect_phone_label);
            view.setText(message);

            TextView yesButton = tile.findViewById(R.id.connect_phone_yes_button);
            yesButton.setOnClickListener(listener);

            TextView noButton = tile.findViewById(R.id.connect_phone_no_button);
            noButton.setOnClickListener(v -> {
                tile.setVisibility(View.GONE);
            });
        }
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

    public static boolean isConnectTask(int code) {
        return ConnectTask.isConnectTaskCode(code);
    }

    public static void handleFinishedActivity(CommCareActivity<?> activity, int requestCode, int resultCode, Intent intent) {
        getInstance().parentActivity = activity;
        if(ConnectIdWorkflows.handleFinishedActivity(activity, requestCode, resultCode, intent)) {
            getInstance().connectStatus = ConnectIdStatus.Registering;
        }
    }
    private static void completeSignin() {
        ConnectManager instance = getInstance();
        instance.connectStatus = ConnectIdStatus.LoggedIn;

        scheduleHearbeat();
        CrashUtil.registerConnectUser();

        if(instance.loginListener != null) {
            instance.loginListener.connectActivityComplete(true);
        }
    }

    public static ConnectUserRecord getUser(Context context) {
        return ConnectDatabaseHelper.getUser(context);
    }

    public static void forgetUser() {
        ConnectManager manager = getInstance();

        ConnectDatabaseHelper.forgetUser(manager.parentActivity);

        ConnectIdWorkflows.reset();

        manager.connectStatus = ConnectIdStatus.NotIntroduced;
        manager.loginListener = null;
    }

    public static ConnectJobRecord setConnectJobForApp(Context context, String appId) {
        ConnectJobRecord job = null;

        ConnectAppRecord appRecord = getAppRecord(context, appId);
        if(appRecord != null) {
            job = ConnectDatabaseHelper.getJob(context, appRecord.getJobId());
        }

        setActiveJob(job);

        return job;
    }

    private ConnectJobRecord activeJob = null;
    private int failedPinAttempts = 0;

    public static void setActiveJob(ConnectJobRecord job) {
        ConnectManager.getInstance().activeJob = job;
    }
    public static ConnectJobRecord getActiveJob() {
        return  ConnectManager.getInstance().activeJob;
    }

    public static void unlockConnect(CommCareActivity<?> parent, ConnectActivityCompleteListener listener) {
        if(manager.connectStatus == ConnectIdStatus.LoggedIn) {
            ConnectIdWorkflows.unlockConnect(parent, success -> {
                if(success) {
                    completeSignin();
                }
                listener.connectActivityComplete(success);
            });
        }
    }

    public static void registerUser(CommCareActivity<?> parent, ConnectActivityCompleteListener listener) {
        ConnectManager manager = getInstance();
        ConnectIdWorkflows.beginRegistration(parent, manager.connectStatus, success -> {
            if(success) {
                completeSignin();
            }
            listener.connectActivityComplete(success);
        });
    }

    public static void handleConnectButtonPress(CommCareActivity<?> parent, ConnectActivityCompleteListener listener) {
        ConnectManager manager = getInstance();
        manager.parentActivity = parent;
        manager.loginListener = listener;

        switch (manager.connectStatus) {
            case NotIntroduced, Registering -> {
                ConnectIdWorkflows.beginRegistration(parent, manager.connectStatus, success -> {
                    if(success) {
                        completeSignin();
                    }
                    listener.connectActivityComplete(success);
                });
            }
            case LoggedIn -> {
                goToConnectJobsList();
            }
        }
    }

    public static void verifySecondaryPhone(CommCareActivity<?> parent, ConnectActivityCompleteListener listener) {
        ConnectIdWorkflows.beginSecondaryPhoneVerification(parent, listener);
    }

    public static void goToConnectJobsList() {
        ConnectTask task = ConnectTask.CONNECT_MAIN;
        Intent i = new Intent(manager.parentActivity, task.getNextActivity());
        manager.parentActivity.startActivity(i);
    }

    public static void goToActiveInfoForJob(Activity activity, boolean allowProgression) {
        ConnectTask task = ConnectTask.CONNECT_JOB_INFO;
        Intent i = new Intent(activity, task.getNextActivity());
        i.putExtra("info", true);
        i.putExtra("buttons", allowProgression);
        activity.startActivity(i);
    }

    public static void forgetAppCredentials(String appId, String userId) {
        ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(manager.parentActivity, appId, userId);
        if (record != null) {
            ConnectDatabaseHelper.deleteAppData(manager.parentActivity, record);
        }
    }

    public static void checkConnectIdLink(CommCareActivity<?> activity, boolean autoLoggedIn, String appId, String username, String password, ConnectActivityCompleteListener callback) {
        if(isLoginManagedByConnectId(appId, username)) {
            //ConnectID is configured
            if(!autoLoggedIn) {
                //See if user wishes to permanently sever the connection
                StandardAlertDialog d = new StandardAlertDialog(activity,
                        activity.getString(R.string.login_unlink_connectid_title),
                        activity.getString(R.string.login_unlink_connectid_message));

                d.setPositiveButton(activity.getString(R.string.login_link_connectid_yes), (dialog, which) -> {
                    activity.dismissAlertDialog();

                    unlockConnect(activity, success -> {
                        if(success) {
                            ConnectLinkedAppRecord linkedApp = ConnectDatabaseHelper.getAppData(activity, appId, username);
                            if(linkedApp != null) {
                                linkedApp.severConnectIdLink();
                                ConnectDatabaseHelper.storeApp(activity, linkedApp);
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
        } else {
            //ConnectID is NOT configured
            boolean offerToLink = true;
            boolean isSecondOffer = false;

            ConnectLinkedAppRecord linkedApp = ConnectDatabaseHelper.getAppData(activity, appId, username);
            //See if we've offered to link already
            Date firstOffer = linkedApp != null ? linkedApp.getLinkOfferDate1() : null;
            if(firstOffer != null) {
                isSecondOffer = true;
                //See if we've done the second offer
                Date secondOffer = linkedApp.getLinkOfferDate2();
                if(secondOffer != null) {
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

            if(offerToLink) {
                if(linkedApp == null) {
                    //Create the linked app record (even if just to remember that we offered
                    linkedApp = ConnectDatabaseHelper.storeApp(activity, appId, username, false, "", false);
                }

                //Update that we offered
                if(isSecondOffer) {
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
                        if(success) {
                            appRecordFinal.linkToConnectId(password);
                            ConnectDatabaseHelper.storeApp(activity, appRecordFinal);

                            //Link the HQ user by aqcuiring the SSO token for the first time
                            ConnectSsoHelper.retrieveHqSsoTokenAsync(activity, username, true, auth -> {
                                if(auth == null) {
                                    //Toast.makeText(activity, "Failed to acquire SSO token", Toast.LENGTH_SHORT).show();
                                    //TODO: Re-enable when token working again
                                    //ConnectManager.forgetAppCredentials(appId, username);
                                }

                                callback.connectActivityComplete(true);
                            });
                        } else {
                            callback.connectActivityComplete(false);
                        }
                    });
                });

                d.setNegativeButton(activity.getString(R.string.login_link_connectid_no), (dialog, which) -> {
                    activity.dismissAlertDialog();

                    //Save updated record indicating that we offered
                    ConnectDatabaseHelper.storeApp(activity, appRecordFinal);

                    callback.connectActivityComplete(false);
                });

                activity.showAlertDialog(d);
                return;
            }
        }

        callback.connectActivityComplete(false);
    }

    public static boolean isLoginManagedByConnectId(String appId, String userId) {
        AuthInfo.ProvidedAuth auth = getCredentialsForApp(appId, userId);
        return auth != null;
    }

    public static ConnectAppRecord getAppRecord(Context context, String appId) {
        return ConnectDatabaseHelper.getAppRecord(context, appId);
    }

    public static String getStoredPasswordForApp(String appId, String userId) {
        AuthInfo.ProvidedAuth auth = getCredentialsForApp(appId, userId);
        return auth != null ? auth.password : null;
    }

    @Nullable
    public static AuthInfo.ProvidedAuth getCredentialsForApp(String appId, String userId) {
        ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(manager.parentActivity, appId,
                userId);
        if (record != null && record.getConnectIdLinked() && record.getPassword().length() > 0) {
            return new AuthInfo.ProvidedAuth(record.getUserId(), record.getPassword(), false);
        }

        return null;
    }

    public static AuthInfo.TokenAuth getConnectToken() {
        if (isUnlocked()) {
            ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
            if (user != null && (new Date()).compareTo(user.getConnectTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(user.getConnectToken());
            }
        }

        return null;
    }

    public static AuthInfo.TokenAuth getTokenCredentialsForApp(String appId, String userId) {
        if (isUnlocked()) {
            ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(manager.parentActivity, appId,
                    userId);
            if (record != null && (new Date()).compareTo(record.getHqTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(record.getHqToken());
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
        if(!instance.downloading) {
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

    public static void launchApp(Context context, boolean isLearning, String appId) {
        CommCareApplication.instance().closeUserSession();

        String appType = isLearning ? "Learn" : "Deliver";
        FirebaseAnalyticsUtil.reportCccAppLaunch(appType, appId);

        getInstance().primedAppIdForAutoLogin = appId;

        CommCareLauncher.launchCommCareForAppId(context, appId);
    }

    public static boolean wasAppLaunchedFromConnect(String appId) {
        String primed = getInstance().primedAppIdForAutoLogin;
        getInstance().primedAppIdForAutoLogin = null;
        return primed != null && primed.equals(appId);
    }

    public static String checkAutoLoginAndOverridePassword(Context context, String appId, String username,
                                                    String passwordOrPin, boolean appLaunchedFromConnect, boolean uiInAutoLogin) {
        if (isUnlocked()) {
            if(appLaunchedFromConnect) {
                //Configure some things if we haven't already
                ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(context,
                        appId, username);
                if (record == null) {
                    record = prepareConnectManagedApp(context, appId, username);
                }

                passwordOrPin = record.getPassword();
            } else if(uiInAutoLogin) {
                String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
                passwordOrPin = ConnectManager.getStoredPasswordForApp(seatedAppId, username);
            }
        }

        return passwordOrPin;
    }

    public static ConnectLinkedAppRecord prepareConnectManagedApp(Context context, String appId, String username) {
        //Create app password
        String password = generatePassword();

        //Store ConnectLinkedAppRecord (note worker already linked)
        ConnectLinkedAppRecord appRecord = ConnectDatabaseHelper.storeApp(context, appId, username, true, password, true);

        //Store UKR
        SecretKey newKey = CryptUtil.generateSemiRandomKey();
        String sandboxId = PropertyUtils.genUUID().replace("-", "");
        Date now = new Date();

        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.YEAR, -10); //Begin ten years ago
        Date fromDate = cal.getTime();

        cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.YEAR, 10); //Expire in ten years
        Date toDate = cal.getTime();

        UserKeyRecord ukr = new UserKeyRecord(username, UserKeyRecord.generatePwdHash(password),
                ByteEncrypter.wrapByteArrayWithString(newKey.getEncoded(), password),
                fromDate, toDate, sandboxId);

        CommCareApplication.instance().getCurrentApp().getStorage(UserKeyRecord.class).write(ukr);

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
            public void processFailure(int responseCode, IOException e) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Failed: %d", responseCode));
            }

            @Override
            public void processNetworkFailure() {
                Logger.log("ERROR", "Failed (network)");
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
                        for(int i=0; i<modules.length(); i++) {
                            JSONObject obj = (JSONObject)modules.get(i);
                            ConnectJobLearningRecord record = ConnectJobLearningRecord.fromJson(obj, job.getJobId());
                            learningRecords.add(record);
                        }
                        job.setLearnings(learningRecords);
                        job.setComletedLearningModules(learningRecords.size());

                        key = "assessments";
                        JSONArray assessments = json.getJSONArray(key);
                        List<ConnectJobAssessmentRecord> assessmentRecords = new ArrayList<>(assessments.length());
                        for(int i=0; i<assessments.length(); i++) {
                            JSONObject obj = (JSONObject)assessments.get(i);
                            ConnectJobAssessmentRecord record = ConnectJobAssessmentRecord.fromJson(obj, job.getJobId());
                            assessmentRecords.add(record);
                        }
                        job.setAssessments(assessmentRecords);

                        ConnectDatabaseHelper.updateJobLearnProgress(context, job);
                    }
                } catch (IOException | JSONException | ParseException e) {
                    Logger.exception("Parsing return from learn_progress request", e);
                }

                reportApiCall(true);
                listener.connectActivityComplete(true);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
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
                        if(json.has(key)) {
                            job.setMaxVisits(json.getInt(key));
                            updatedJob = true;
                        }

                        key = "end_date";
                        if(json.has(key)) {
                            job.setProjectEndDate(ConnectNetworkHelper.parseDate(json.getString(key)));
                            updatedJob = true;
                        }

                        key = "payment_accrued";
                        if(json.has(key)) {
                            job.setPaymentAccrued(json.getInt(key));
                            updatedJob = true;
                        }

                        key = "is_user_suspended";
                        if(json.has(key)) {
                            job.setIsUserSuspended(json.getBoolean(key));
                            updatedJob = true;
                        }

                        if(updatedJob) {
                            job.setLastDeliveryUpdate(new Date());
                            ConnectDatabaseHelper.upsertJob(context, job);
                        }

                        List<ConnectJobDeliveryRecord> deliveries = new ArrayList<>(json.length());
                        key = "deliveries";
                        if(json.has(key)) {
                            JSONArray array = json.getJSONArray(key);
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = (JSONObject)array.get(i);
                                deliveries.add(ConnectJobDeliveryRecord.fromJson(obj, job.getJobId()));
                            }

                            //Store retrieved deliveries
                            ConnectDatabaseHelper.storeDeliveries(context, deliveries, job.getJobId(), true);

                            job.setDeliveries(deliveries);
                        }

                        List<ConnectJobPaymentRecord> payments = new ArrayList<>();
                        key = "payments";
                        if(json.has(key)) {
                            JSONArray array = json.getJSONArray(key);
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = (JSONObject)array.get(i);
                                payments.add(ConnectJobPaymentRecord.fromJson(obj, job.getJobId()));
                            }

                            ConnectDatabaseHelper.storePayments(context, payments, job.getJobId(), true);

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
            public void processFailure(int responseCode, IOException e) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Delivery progress call failed: %d", responseCode));
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
                ConnectDatabaseHelper.storePayment(context, payment);

                //No need to report to user
                reportApiCall(true);
                listener.connectActivityComplete(true);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
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
}
