package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.commcare.AppUtils;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.commcaresupportlibrary.CommCareLauncher;
import org.commcare.connect.network.ApiConnect;
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
import org.javarosa.core.util.PropertyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
        ConnectDatabaseHelper.init(parent);

        if(manager.connectStatus == ConnectIdStatus.NotIntroduced) {
            ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
            if (user != null) {
                boolean registering = user.getRegistrationPhase() != ConnectTask.CONNECT_NO_ACTIVITY;
                manager.connectStatus = registering ? ConnectIdStatus.Registering : ConnectIdStatus.LoggedIn;
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
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    public static boolean isUnlocked() {
        return AppManagerDeveloperPreferences.isConnectIdEnabled()
                && getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    private static DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
    public static String formatDate(Date date) {
        return dateFormat.format(date);
    }

    public static String formatDateTime(Date date) {
        return SimpleDateFormat.getDateTimeInstance().format(date);
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

    public static String getConnectButtonText(Context context) {
        return switch (getInstance().connectStatus) {
            case Registering, NotIntroduced ->
                    context.getString(R.string.connect_button_logged_out);
            case LoggedIn -> context.getString(R.string.connect_button_logged_in);
        };
    }

    public static boolean shouldShowConnectButton() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    public static void handleFinishedActivity(int requestCode, int resultCode, Intent intent) {
        if(ConnectIdWorkflows.handleFinishedActivity(requestCode, resultCode, intent)) {
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

    public static void filterConnectManagedApps(Context context, ArrayList<ApplicationRecord> readyApps, String presetAppId) {
        if(ConnectManager.isConnectIdIntroduced()) {
            //We need to remove any apps that are managed by Connect
            String username = ConnectManager.getUser(context).getUserId().toLowerCase(Locale.getDefault());
            for(int i= readyApps.size()-1; i>=0; i--) {
                String appId = readyApps.get(i).getUniqueId();
                //Preset app needs to remain in the list if set
                if(!appId.equals(presetAppId)) {
                    if (isLoginManagedByConnectId(appId, username)) {
                        //Creds stored for the CID username indicates this app is managed by Connect
                        readyApps.remove(i);
                    }
                }
            }
        }
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

    public static void goToConnectJobsList() {
        ConnectTask task = ConnectTask.CONNECT_MAIN;
        Intent i = new Intent(manager.parentActivity, task.getNextActivity());
        manager.parentActivity.startActivityForResult(i, task.getRequestCode());
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
                                    Toast.makeText(activity, "Failed to acquire SSO token", Toast.LENGTH_SHORT).show();
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

    public static void updatePaymentConfirmed(Context context, final ConnectJobPaymentRecord payment, boolean confirmed, ConnectActivityCompleteListener listener) {
        ApiConnect.setPaymentConfirmed(context, payment.getPaymentId(), confirmed, new ConnectNetworkHelper.INetworkResultHandler() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                payment.setConfirmed(confirmed);
                ConnectDatabaseHelper.storePayment(context, payment);

                //No need to report to user
                listener.connectActivityComplete(true);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Toast.makeText(context, R.string.connect_payment_confirm_failed, Toast.LENGTH_SHORT).show();
                listener.connectActivityComplete(false);
            }

            @Override
            public void processNetworkFailure() {
                Toast.makeText(context, R.string.connect_payment_confirm_failed, Toast.LENGTH_SHORT).show();
                listener.connectActivityComplete(false);
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(context);
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

    private static ConnectJobRecord activeJob = null;
    public static void setActiveJob(ConnectJobRecord job) {
        activeJob = job;
    }
    public static ConnectJobRecord getActiveJob() {
        return activeJob;
    }
}
