package org.commcare.connectId;

import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectDatabaseUtils;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.connect.workers.ConnectHeartbeatWorker;
import org.commcare.core.network.AuthInfo;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.preferences.AppManagerDeveloperPreferences;
import org.commcare.utils.BiometricsHelper;
import org.commcare.utils.CrashUtil;
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

    private static final String CONNECT_HEARTBEAT_WORKER = "connect_heartbeat_worker";
    private static final long PERIODICITY_FOR_HEARTBEAT_IN_HOURS = 4;
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
        String primed = getInstance().primedAppIdForAutoLogin;
        getInstance().primedAppIdForAutoLogin = null;
        return primed != null && primed.equals(appId);
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


    public static boolean isLoggedIN() {
        return AppManagerDeveloperPreferences.isConnectIdEnabled()
                && getInstance().connectStatus == ConnectIdStatus.LoggedIn;
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
        getInstance().parentActivity = activity;

        if (!BiometricsHelper.handlePinUnlockActivityResult(requestCode, resultCode)) {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                goToConnectJobsList(activity);
            }
        }
    }

    public static void forgetUser(String reason) {
        ConnectIDManager manager = getInstance();

        if (ConnectDatabaseHelper.dbExists(manager.parentActivity)) {
            FirebaseAnalyticsUtil.reportCccDeconfigure(reason);
        }

        ConnectUserDatabaseUtil.forgetUser(manager.parentActivity);

        ConnectIdActivity.reset();

        manager.connectStatus = ConnectIdStatus.NotIntroduced;
    }


    private void launchConnectId(CommCareActivity<?> parent, String task, ConnectActivityCompleteListener listener) {
        Intent intent = new Intent(parent, ConnectIdActivity.class);
        intent.putExtra("TASK", task);
        parent.startActivityForResult(intent, CONNECTID_REQUEST_CODE);
    }

    public void registerUser(CommCareActivity<?> parent, ConnectActivityCompleteListener callback) {
        launchConnectId(parent, ConnectConstants.BEGIN_REGISTRATION, callback);
    }

    public void goToConnectJobsList(Context parent) {
        manager.parentActivity = parent;
        completeSignin();
//        Intent i = new Intent(parent, ConnectActivity.class);
//        parent.startActivity(i);
    }

    public boolean isLoginManagedByConnectId(String appId, String userId) {
        AuthInfo.ProvidedAuth auth = getCredentialsForApp(appId, userId);
        return auth != null;
    }

    public ConnectAppRecord getAppRecord(Context context, String appId) {
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

    public void getRemoteDbPassphrase(Context context, ConnectUserRecord user) {
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

    public ConnectIdStatus getStatus() {
        return getInstance().connectStatus;
    }

    public void setStatus(ConnectIdStatus connectStatus) {
        getInstance().connectStatus = connectStatus;
    }

    public void setParent(Context parent) {
        getInstance().parentActivity = parent;
    }

    public ConnectUserRecord getUser(Context context) {
        return ConnectUserDatabaseUtil.getUser(context);
    }

    public BiometricManager getBiometricManager(CommCareActivity<?> parent) {
        ConnectIDManager instance = getInstance();
        if (instance.biometricManager == null) {
            instance.biometricManager = BiometricManager.from(parent);
        }

        return instance.biometricManager;
    }

    public int getFailureAttempt() {
        return getInstance().failedPinAttempts;
    }

    public void setFailureAttempt(int failureAttempt) {
        getInstance().failedPinAttempts = failureAttempt;
    }


    /**
     * Interface for handling callbacks when a ConnectID activity finishes
     */
    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }
}
