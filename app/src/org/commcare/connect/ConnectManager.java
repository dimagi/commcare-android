package org.commcare.connect;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.connect.ConnectIdActivity;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.commcaresupportlibrary.CommCareLauncher;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.ConnectSsoHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.preferences.AppManagerDeveloperPreferences;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.utils.BiometricsHelper;
import org.commcare.utils.CrashUtil;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.constraintlayout.widget.ConstraintLayout;
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
public class ConnectManager {
    private static final String CONNECT_WORKER = "connect_worker";
    private static final long PERIODICITY_FOR_HEARTBEAT_IN_HOURS = 4;
    private static final long BACKOFF_DELAY_FOR_HEARTBEAT_RETRY = 5 * 60 * 1000L; // 5 mins
    private static final String CONNECT_HEARTBEAT_REQUEST_NAME = "connect_hearbeat_periodic_request";
    private static final int APP_DOWNLOAD_TASK_ID = 4;
    public static final int MethodRegistrationPrimary = 1;
    public static final int MethodRecoveryPrimary = 2;
//    public static final int MethodRecoveryAlternate = 3;
//    public static final int MethodVerifyAlternate = 4;

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

    /**
     * Interface for handling callbacks when a ConnectID activity finishes
     */
    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }

    private static ConnectManager manager = null;
    private ConnectIdStatus connectStatus = ConnectIdStatus.NotIntroduced;
    private CommCareActivity<?> parentActivity;

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

    public static ConnectIdStatus getStatus() {
        return getInstance().connectStatus;
    }

    public static void setStatus(ConnectIdStatus connectStatus) {
        getInstance().connectStatus = connectStatus;
    }

    public static void init(CommCareActivity<?> parent) {
        ConnectManager manager = getInstance();
        manager.parentActivity = parent;

        if (manager.connectStatus == ConnectIdStatus.NotIntroduced) {
            ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
            if (user != null) {
                boolean registering = user.getRegistrationPhase() != ConnectConstants.CONNECT_NO_ACTIVITY;
                manager.connectStatus = registering ? ConnectIdStatus.Registering : ConnectIdStatus.LoggedIn;

                String remotePassphrase = ConnectDatabaseHelper.getConnectDbEncodedPassphrase(parent, false);
                if (remotePassphrase == null) {
                    getRemoteDbPassphrase(parent, user);
                }
            } else if (ConnectDatabaseHelper.isDbBroken()) {
                //Corrupt DB, inform user to recover
                ConnectDatabaseHelper.handleCorruptDb(parent);
            }
        }
    }

    public static BiometricManager getBiometricManager(CommCareActivity<?> parent){
        ConnectManager instance = getInstance();
        if (instance.biometricManager == null) {
            instance.biometricManager = BiometricManager.from(parent);
        }

        return instance.biometricManager;
    }

    public static void setParent(CommCareActivity<?> parent) {
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
            boolean allowOtherOptions = BiometricsHelper.isPinConfigured(activity, bioManager);
            BiometricsHelper.authenticateFingerprint(activity, bioManager, allowOtherOptions, callbacks);
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

    public static String formatDateTime(Date date) {
        return SimpleDateFormat.getDateTimeInstance().format(date);
    }

    public static boolean shouldShowSecondaryPhoneConfirmationTile(Context context) {
        boolean show = false;

        if(isConnectIdConfigured()) {
            ConnectUserRecord user = getUser(context);
            show = !user.getSecondaryPhoneVerified();
        }

        return show;
    }

    public static void updateSecondaryPhoneConfirmationTile(Context context, ConstraintLayout tile, boolean show, View.OnClickListener listener) {
        tile.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            ConnectUserRecord user = getUser(context);
            String dateStr = formatDate(user.getSecondaryPhoneVerifyByDate());
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

    public static void completeSignin() {
        ConnectManager instance = getInstance();
        instance.connectStatus = ConnectIdStatus.LoggedIn;

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

    public static void handleFinishedActivity(CommCareActivity<?> activity, int requestCode, int resultCode, Intent intent) {
        getInstance().parentActivity = activity;

        if (!BiometricsHelper.handlePinUnlockActivityResult(requestCode, resultCode, intent)) {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                //Nothing to do
            }
        }
    }

    public static ConnectUserRecord getUser(Context context) {
        return ConnectDatabaseHelper.getUser(context);
    }

    public static void forgetUser() {
        ConnectManager manager = getInstance();

        ConnectDatabaseHelper.forgetUser(manager.parentActivity);

        ConnectIdActivity.reset();

        manager.connectStatus = ConnectIdStatus.NotIntroduced;
    }

    private int failedPinAttempts = 0;

    private static void launchConnectId(CommCareActivity<?> parent, String task, ConnectActivityCompleteListener listener) {
        Intent intent = new Intent(parent, ConnectIdActivity.class);
        intent.putExtra("TASK", task);
        parent.startActivityForResult(intent, CONNECTID_REQUEST_CODE);
    }

    public static void registerUser(CommCareActivity<?> parent, ConnectActivityCompleteListener callback) {
        launchConnectId(parent, ConnectConstants.BEGIN_REGISTRATION, callback);
    }

    public static void beginSecondaryPhoneVerification(CommCareActivity<?> parent, ConnectActivityCompleteListener callback) {
        launchConnectId(parent, ConnectConstants.VERIFY_PHONE, callback);
    }

    public static void forgetAppCredentials(String appId, String userId) {
        ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(manager.parentActivity, appId, userId);
        if (record != null) {
            ConnectDatabaseHelper.deleteAppData(manager.parentActivity, record);
        }
    }

    public static void updateAppAccess(CommCareActivity<?> activity, String appId, String username) {
        ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(activity, appId, username);
        if(record != null) {
            record.setLastAccessed(new Date());
            ConnectDatabaseHelper.storeApp(activity, record);
        }
    }

    public static void checkConnectIdLink(CommCareActivity<?> activity, boolean autoLoggedIn, String appId, String username, String password, ConnectActivityCompleteListener callback) {
        if (isLoginManagedByConnectId(appId, username)) {
            //ConnectID is configured
            if (!autoLoggedIn) {
                //See if user wishes to permanently sever the connection
                StandardAlertDialog d = new StandardAlertDialog(activity,
                        activity.getString(R.string.login_unlink_connectid_title),
                        activity.getString(R.string.login_unlink_connectid_message));

                d.setPositiveButton(activity.getString(R.string.login_link_connectid_yes), (dialog, which) -> {
                    activity.dismissAlertDialog();

                    unlockConnect(activity, success -> {
                        if (success) {
                            ConnectLinkedAppRecord linkedApp = ConnectDatabaseHelper.getAppData(activity, appId, username);
                            if (linkedApp != null) {
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
                    linkedApp = ConnectDatabaseHelper.storeApp(activity, appId, username, false, "", false, false);
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
                            ConnectDatabaseHelper.storeApp(activity, appRecordFinal);

                            //Link the HQ user by aqcuiring the SSO token for the first time
                            ConnectSsoHelper.retrieveHqSsoTokenAsync(activity, username, true, auth -> {
                                if (auth == null) {
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
        if (isConnectIdConfigured()) {
            ConnectUserRecord user = ConnectDatabaseHelper.getUser(manager.parentActivity);
            if (user != null && (new Date()).compareTo(user.getConnectTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(user.getConnectToken());
            }
        }

        return null;
    }

    public static AuthInfo.TokenAuth getTokenCredentialsForApp(String appId, String userId) {
        if (isConnectIdConfigured()) {
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

    public static String checkAutoLoginAndOverridePassword(Context context, String appId, String username,
                                                           String passwordOrPin, boolean appLaunchedFromConnect, boolean uiInAutoLogin) {
        if (isConnectIdConfigured()) {
            if(appLaunchedFromConnect) {
                //Configure some things if we haven't already
                ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(context,
                        appId, username);
                if (record == null) {
                    record = prepareConnectManagedApp(context, appId, username);
                }

                passwordOrPin = record.getPassword();
            } else if (uiInAutoLogin) {
                String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
                ConnectLinkedAppRecord record = ConnectDatabaseHelper.getAppData(context, seatedAppId,
                        username);
                passwordOrPin = record != null ? record.getPassword() : null;

                if(record != null && record.isUsingLocalPassphrase()) {
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
        ConnectLinkedAppRecord appRecord = ConnectDatabaseHelper.storeApp(context, appId, username, true, password, true, false);

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