package org.commcare.connect;

import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectDatabaseUtils;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.ConnectSsoHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.connect.network.TokenRequestDeniedException;
import org.commcare.connect.network.TokenUnavailableException;
import org.commcare.connect.workers.ConnectHeartbeatWorker;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.preferences.AppManagerDeveloperPreferences;
import org.commcare.util.LogTypes;
import org.commcare.utils.BiometricsHelper;
import org.commcare.utils.CrashUtil;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

import static org.commcare.connect.ConnectConstants.CONNECTID_REQUEST_CODE;

/**
 * Manager class for ConnectID, handles workflow navigation and user management
 *
 * @author dviggiano
 */
public class ConnectIDManager {
    /**
     * Enum representing the current state of ConnectID
     */
    public enum ConnectIdStatus {
        NotIntroduced,//ConnectID is not intoduced to the user
        Registering,//User is in the recovery or registration phase of ConnectID
        LoggedIn//User Loggedin ConnectId
    }

    public enum ConnectAppMangement {
        Unmanaged, ConnectId, Connect
    }

    private static final String CONNECT_HEARTBEAT_WORKER = "connect_heartbeat_worker";
    private static final long PERIODICITY_FOR_HEARTBEAT_IN_HOURS = 4;
    public static final int PENDING_ACTION_OPP_STATUS = 2;
    public static final int PENDING_ACTION_NONE = 0;
    private static final long BACKOFF_DELAY_FOR_HEARTBEAT_RETRY = 5 * 60 * 1000L; // 5 mins
    private static final String CONNECT_HEARTBEAT_REQUEST_NAME = "connect_hearbeat_periodic_request";
    public static final int MethodRegistrationPrimary = 1;
    public static final int MethodRecoveryPrimary = 2;
    private BiometricManager biometricManager;

    private static volatile ConnectIDManager manager = null;
    private ConnectIdStatus connectStatus = ConnectIdStatus.NotIntroduced;
    private Context parentActivity;
    private String primedAppIdForAutoLogin = null;
    private int failedPinAttempts = 0;
    private int pendingAction = PENDING_ACTION_NONE;
    private ConnectJobRecord activeJob = null;


    //Singleton, private constructor
    private ConnectIDManager() {
        // Protect against reflection
        if (manager != null) {
            throw new IllegalStateException("Already initialized.");
        }
    }

    public static ConnectIDManager getInstance() {
        if (manager == null) {
            synchronized (ConnectIDManager.class) {
                if (manager == null) {
                    manager = new ConnectIDManager();
                }
            }
        }
        return manager;
    }

    public void init(Context parent) {
        ConnectIDManager manager = getInstance();
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

    public boolean wasAppLaunchedFromConnect(String appId) {
        String primed = primedAppIdForAutoLogin;
        primedAppIdForAutoLogin = null;
        return primed != null && primed.equals(appId);
    }

    public void setPendingAction(int action) {
        pendingAction = action;
    }

    public String generatePassword() {
        int passwordLength = 20;

        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_!.?";
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder password = new StringBuilder(passwordLength);
        for (int i = 0; i < passwordLength; i++) {
            password.append(charSet.charAt(secureRandom.nextInt(charSet.length())));
        }

        return password.toString();
    }

    private void scheduleHearbeat() {
        if (AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build();

            PeriodicWorkRequest heartbeatRequest =
                    new PeriodicWorkRequest.Builder(ConnectHeartbeatWorker.class,
                            PERIODICITY_FOR_HEARTBEAT_IN_HOURS,
                            TimeUnit.HOURS)
                            .addTag(CONNECT_HEARTBEAT_WORKER)
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


    public boolean isLoggedIN() {
        return AppManagerDeveloperPreferences.isConnectIdEnabled()
                && connectStatus == ConnectIdStatus.LoggedIn;
    }

    public void unlockConnect(CommCareActivity<?> activity, ConnectActivityCompleteListener callback) {
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
            boolean allowOtherOptions = BiometricsHelper.isPinConfigured(activity, bioManager);
            BiometricsHelper.authenticateFingerprint(activity, bioManager, allowOtherOptions, callbacks);
        } else if (BiometricsHelper.isPinConfigured(activity, bioManager)) {
            BiometricsHelper.authenticatePin(activity, bioManager, callbacks);
        } else {
            callback.connectActivityComplete(false);
            Logger.exception("No unlock method available when trying to unlock ConnectID", new Exception("No unlock option"));
        }
    }

    public void completeSignin() {
        ConnectIDManager instance = getInstance();
        instance.connectStatus = ConnectIdStatus.LoggedIn;

        scheduleHearbeat();
        CrashUtil.registerConnectUser();
    }

    public void handleFinishedActivity(CommCareActivity<?> activity, int requestCode, int resultCode, Intent intent) {
        parentActivity = activity;

        if (!BiometricsHelper.handlePinUnlockActivityResult(requestCode, resultCode)) {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                goToConnectJobsList(activity);
            }
        }
    }

    public boolean handleConnectSignIn(CommCareActivity<?> context, String username, String enteredPasswordPin, boolean loginManagedByConnectId) {
        AtomicBoolean result = new AtomicBoolean(false);
        if (isLoggedIN()) {
            completeSignin();
            String appId = CommCareApplication.instance().getCurrentApp().getUniqueId();
            ConnectJobRecord job = setConnectJobForApp(context, appId);

            if (job != null) {
                updateAppAccess(context, appId, username);
                //Update job status
                updateJobProgress(context, job, success -> {
                    result.set(job.getIsUserSuspended());
                });
            } else {
                //Possibly offer to link or de-link ConnectId-managed login
                checkConnectIdLink(context,
                        loginManagedByConnectId, appId,
                        username,
                        enteredPasswordPin, success -> {
                            ConnectIDManager.updateAppAccess(context, appId, username);
                            result.set(false);
                        });
            }

            return result.get();
        }

        return true;
    }


    public static void forgetUser(String reason) {
        ConnectIDManager manager = getInstance();

        if (ConnectDatabaseHelper.dbExists(manager.parentActivity)) {
            FirebaseAnalyticsUtil.reportCccDeconfigure(reason);
        }

        ConnectUserDatabaseUtil.forgetUser(manager.parentActivity);
        ConnectIdActivity connectIdActivity = new ConnectIdActivity();
        connectIdActivity.reset();
        manager.connectStatus = ConnectIdStatus.NotIntroduced;
    }

    public AuthInfo.TokenAuth getConnectToken() {
        if (isLoggedIN()) {
            ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(manager.parentActivity);
            Date currentDate = new Date();
            if (user != null && currentDate.compareTo(user.getConnectTokenExpiration()) < 0) {
                Logger.log(LogTypes.TYPE_MAINTENANCE,
                        "Found a valid existing Connect Token with current date set to " + currentDate +
                                " and record expiration date being " + user.getConnectTokenExpiration());
                return new AuthInfo.TokenAuth(user.getConnectToken().bearerToken);
            } else if (user != null) {
                Logger.log(LogTypes.TYPE_MAINTENANCE, "Existing Connect token is not valid");
            }
        }

        return null;
    }

    private void launchConnectId(CommCareActivity<?> parent, String task) {
        Intent intent = new Intent(parent, ConnectIdActivity.class);
        intent.putExtra(ConnectConstants.TASK, task);
        parent.startActivityForResult(intent, CONNECTID_REQUEST_CODE);
    }

    public void launchConnectId(CommCareActivity<?> parent) {
        launchConnectId(parent, ConnectConstants.BEGIN_REGISTRATION);
    }

    private static void updateAppAccess(CommCareActivity<?> activity, String appId, String username) {
        ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getAppData(activity, appId, username);
        if (record != null) {
            record.setLastAccessed(new Date());
            ConnectAppDatabaseUtil.storeApp(activity, record);
        }
    }

    private void checkConnectIdLink(CommCareActivity<?> activity, boolean autoLoggedIn, String appId, String username, String password, ConnectActivityCompleteListener callback) {
        switch (getAppManagement(activity, appId, username)) {
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

                                //Link the HQ user by acquiring the SSO token for the first time
                                ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(activity);
                                ConnectSsoHelper.retrieveHqSsoTokenAsync(activity, user, appRecordFinal, username, true, new ConnectSsoHelper.TokenCallback() {
                                    @Override
                                    public void tokenRetrieved(AuthInfo.TokenAuth token) {
                                        callback.connectActivityComplete(true);
                                    }

                                    @Override
                                    public void tokenUnavailable() {
                                        ConnectNetworkHelper.handleTokenUnavailableException(activity);
                                        callback.connectActivityComplete(false);
                                    }

                                    @Override
                                    public void tokenRequestDenied() {
                                        ConnectNetworkHelper.handleTokenRequestDeniedException(activity);
                                        callback.connectActivityComplete(false);
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

    ///TODO update the code with connect code
    private static void updateJobProgress(Context context, ConnectJobRecord job, ConnectActivityCompleteListener listener) {
        switch (job.getStatus()) {
            case ConnectJobRecord.STATUS_LEARNING -> {
//                updateLearningProgress(context, job, listener);
            }
            case ConnectJobRecord.STATUS_DELIVERING -> {
//                updateDeliveryProgress(context, job, listener);
            }
            default -> {
                listener.connectActivityComplete(true);
            }
        }
    }

    private void goToConnectJobsList(Context parent) {
        manager.parentActivity = parent;
        completeSignin();
//        Intent i = new Intent(parent, ConnectActivity.class);
//        parent.startActivity(i);
    }

    private ConnectJobRecord setConnectJobForApp(Context context, String appId) {
        ConnectJobRecord job = null;

        ConnectAppRecord appRecord = getAppRecord(context, appId);
        if (appRecord != null) {
            job = ConnectJobUtils.getCompositeJob(context, appRecord.getJobId());
        }

        setActiveJob(job);

        return job;
    }

    public boolean isLoginManagedByConnectId(String appId, String userId) {
        AuthInfo.ProvidedAuth auth = getCredentialsForApp(appId, userId);
        return auth != null;
    }

    private ConnectAppRecord getAppRecord(Context context, String appId) {
        return ConnectJobUtils.getAppRecord(context, appId);
    }

    @Nullable
    public AuthInfo.ProvidedAuth getCredentialsForApp(String appId, String userId) {
        ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getAppData(manager.parentActivity, appId,
                userId);
        if (record != null && record.getConnectIdLinked() && record.getPassword().length() > 0) {
            return new AuthInfo.ProvidedAuth(record.getUserId(), record.getPassword(), false);
        }

        return null;
    }

    public AuthInfo.TokenAuth getTokenCredentialsForApp(String appId, String userId) {
        if (isLoggedIN()) {
            ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getAppData(manager.parentActivity, appId,
                    userId);
            if (record != null && (new Date()).compareTo(record.getHqTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(record.getHqToken());
            }
        }

        return null;
    }

    private void getRemoteDbPassphrase(Context context, ConnectUserRecord user) {
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

    public ConnectIdStatus getStatus() {
        return connectStatus;
    }

    public void setStatus(ConnectIdStatus status) {
        connectStatus = status;
    }

    public void setParent(Context parent) {
        parentActivity = parent;
    }

    public ConnectUserRecord getUser(Context context) {
        return ConnectUserDatabaseUtil.getUser(context);
    }

    public String getConnectUsername(Context context) {
        return ConnectUserDatabaseUtil.getUser(context).getUserId();
    }


    public void setActiveJob(ConnectJobRecord job) {
        activeJob = job;
    }

    public boolean isSeatedAppLinkedToConnectId(String username) {
        try {
            if (getInstance().isLoggedIN()) {
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

    public ConnectAppMangement getAppManagement(Context context, String appId, String userId) {
        ConnectAppRecord record = getAppRecord(context, appId);
        if (record != null) {
            return ConnectAppMangement.Connect;
        }

        return getCredentialsForApp(appId, userId) != null ?
                ConnectAppMangement.ConnectId :
                ConnectAppMangement.Unmanaged;
    }

    public static AuthInfo.TokenAuth getHqTokenIfLinked(String username) throws TokenRequestDeniedException, TokenUnavailableException {
        if (!manager.isLoggedIN()) {
            return null;
        }

        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(manager.parentActivity);
        if (user == null) {
            return null;
        }

        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectLinkedAppRecord appRecord = ConnectAppDatabaseUtil.getAppData(manager.parentActivity, seatedAppId, username);
        if(appRecord == null) {
            return null;
        }

        return ConnectSsoHelper.retrieveHqSsoTokenSync(CommCareApplication.instance(), user, appRecord, username, false);
    }

    public BiometricManager getBiometricManager(CommCareActivity<?> parent) {
        ConnectIDManager instance = getInstance();
        if (instance.biometricManager == null) {
            instance.biometricManager = BiometricManager.from(parent);
        }

        return instance.biometricManager;
    }

    public int getFailureAttempt() {
        return failedPinAttempts;
    }

    public void setFailureAttempt(int failureAttempt) {
        failedPinAttempts = failureAttempt;
    }


    /**
     * Interface for handling callbacks when a ConnectID activity finishes
     */
    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }
}
