package org.commcare.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
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

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.connect.PersonalIdActivity;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.android.security.AndroidKeyStore;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectDatabaseUtils;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiPersonalId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.ConnectSsoHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.connect.network.TokenDeniedException;
import org.commcare.connect.network.TokenUnavailableException;
import org.commcare.connect.workers.ConnectHeartbeatWorker;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.util.LogTypes;
import org.commcare.utils.BiometricsHelper;
import org.commcare.utils.CrashUtil;
import org.commcare.utils.EncryptionKeyProvider;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Manager class for PersonalID, handles workflow navigation and user management
 *
 * @author dviggiano
 */
public class PersonalIdManager {
    public static final String BIOMETRIC_INVALIDATION_KEY = "biometric-invalidation-key";
    private static final long DAYS_TO_SECOND_OFFER = 30;

    /**
     * Enum representing the current state of PersonalID
     */
    public enum PersonalIdStatus {
        NotIntroduced,//PersonalID is not intoduced to the user
        Registering,//User is in the recovery or registration phase of PersonalID
        LoggedIn//User Loggedin PersonalID
    }

    public enum ConnectAppMangement {
        Unmanaged, PersonalId, Connect
    }

    private static final String CONNECT_HEARTBEAT_WORKER = "connect_heartbeat_worker";
    private static final long PERIODICITY_FOR_HEARTBEAT_IN_HOURS = 4;
    private static final long BACKOFF_DELAY_FOR_HEARTBEAT_RETRY = 5 * 60 * 1000L; // 5 mins
    private static final String CONNECT_HEARTBEAT_REQUEST_NAME = "connect_hearbeat_periodic_request";
    private BiometricManager biometricManager;

    private static volatile PersonalIdManager manager = null;
    private PersonalIdStatus personalIdSatus = PersonalIdStatus.NotIntroduced;
    private Context parentActivity;
    private int failedPinAttempts = 0;

    //Singleton, private constructor
    private PersonalIdManager() {
        // Protect against reflection
        if (manager != null) {
            throw new IllegalStateException("Already initialized.");
        }
    }

    public static PersonalIdManager getInstance() {
        if (manager == null) {
            synchronized (PersonalIdManager.class) {
                if (manager == null) {
                    manager = new PersonalIdManager();
                }
            }
        }
        return manager;
    }

    public void init(Context parent) {
        parentActivity = parent;
        if (personalIdSatus == PersonalIdStatus.NotIntroduced) {
            ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(parentActivity);
            if (user != null) {
                boolean registering = user.getRegistrationPhase() != ConnectConstants.PERSONALID_NO_ACTIVITY;
                personalIdSatus = registering ? PersonalIdStatus.Registering : PersonalIdStatus.LoggedIn;

                CrashUtil.registerUserData();

                String remotePassphrase = ConnectDatabaseUtils.getConnectDbEncodedPassphrase(parent, false);
                if (remotePassphrase == null) {
                    getRemoteDbPassphrase(parent, user);
                }
            } else if (ConnectDatabaseHelper.isDbBroken()) {
                //Corrupt DB, inform user to recover
                ConnectDatabaseHelper.crashDb();
            }
        }
    }

    private void scheduleHeartbeat() {
        if (isloggedIn()) {
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


    public boolean isloggedIn() {
        return personalIdSatus == PersonalIdStatus.LoggedIn;
    }

    public void unlockConnect(CommCareActivity<?> activity, ConnectActivityCompleteListener callback) {
        logBiometricInvalidations();

        BiometricPrompt.AuthenticationCallback callbacks = new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                callback.connectActivityComplete(false);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                completeSignin();
                callback.connectActivityComplete(true);
            }

            @Override
            public void onAuthenticationFailed() {
                callback.connectActivityComplete(false);
            }
        };


        BiometricManager bioManager = getBiometricManager(activity);
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(activity);
        if (BiometricsHelper.isFingerprintConfigured(activity, bioManager)) {
            boolean allowOtherOptions = BiometricsHelper.isPinConfigured(activity, bioManager) && PersonalIdSessionData.PIN.equals(user.getRequiredLock());
            BiometricsHelper.authenticateFingerprint(activity, bioManager, callbacks,allowOtherOptions);
        } else if (BiometricsHelper.isPinConfigured(activity, bioManager) && PersonalIdSessionData.PIN.equals(user.getRequiredLock())) {
            BiometricsHelper.authenticatePin(activity, bioManager, callbacks);
        } else {
            callback.connectActivityComplete(false);
            Logger.exception("No unlock method available when trying to unlock PersonalId", new Exception("No unlock option"));
            Toast.makeText(activity, activity.getString(R.string.connect_unlock_unavailable), Toast.LENGTH_SHORT).show();
        }
    }

    private void logBiometricInvalidations() {
        if(AndroidKeyStore.INSTANCE.doesKeyExist(BIOMETRIC_INVALIDATION_KEY)) {
            EncryptionKeyProvider encryptionKeyProvider = new EncryptionKeyProvider(parentActivity,
                    true, BIOMETRIC_INVALIDATION_KEY);
            if (!encryptionKeyProvider.isKeyValid()) {
                FirebaseAnalyticsUtil.reportBiometricInvalidated();

                // reset key
                encryptionKeyProvider.deleteKey();
                encryptionKeyProvider.getKeyForEncryption();
            }
        }
    }


    public void completeSignin() {
        personalIdSatus = PersonalIdStatus.LoggedIn;
        scheduleHeartbeat();
        CrashUtil.registerUserData();
    }

    public void handleFinishedActivity(CommCareActivity<?> activity, int resultCode) {
        parentActivity = activity;
        if (resultCode == AppCompatActivity.RESULT_OK) {
            completeSignin();
        }
    }


    public void forgetUser(String reason) {
        if (ConnectDatabaseHelper.dbExists()) {
            FirebaseAnalyticsUtil.reportPersonalIdAccountForgotten(reason);
        }
        ConnectUserDatabaseUtil.forgetUser();
        personalIdSatus = PersonalIdStatus.NotIntroduced;
    }

    public AuthInfo.TokenAuth getConnectToken() {
        if (isloggedIn()) {
            ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(parentActivity);
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

    public void launchPersonalId(CommCareActivity<?> parent, int requestCode) {
        Intent intent = new Intent(parent, PersonalIdActivity.class);
        parent.startActivityForResult(intent, requestCode);
    }

    public void updateAppAccess(CommCareActivity<?> activity, String appId, String username) {
        ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(activity, appId, username);
        if (record != null) {
            record.setLastAccessed(new Date());
            ConnectAppDatabaseUtil.storeApp(activity, record);
        }
    }

    public void checkPersonalIdLink(CommCareActivity<?> activity, boolean personalIdManagedLogin, String appId,
                                    String username, String password, ConnectActivityCompleteListener callback) {
        switch (evaluateAppState(activity, appId, username)) {
            case Unmanaged -> promptTolinkUnmanagedApp(activity, appId, username, password, callback);
            case PersonalId -> promptToDelinkPersonalIdApp(activity, appId, username, personalIdManagedLogin, callback);
            case Connect -> callback.connectActivityComplete(true);
        }
    }

    private void promptTolinkUnmanagedApp(CommCareActivity<?> activity, String appId, String username, String password, ConnectActivityCompleteListener callback) {
        ConnectLinkedAppRecord linkedApp = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(activity, appId, username);
        OfferCheckResult offerCheck = evaluateLinkOffer(linkedApp);

        if (!offerCheck.shouldOffer) {
            callback.connectActivityComplete(false);
            return;
        }

        if (linkedApp == null) {
            linkedApp = ConnectAppDatabaseUtil.storeApp(activity, appId, username, false, "", false, false);
        }

        updateLinkOfferDate(linkedApp, offerCheck.isSecondOffer);
        showLinkDialog(activity, linkedApp, username, password, callback);
    }

    private static class OfferCheckResult {
        boolean shouldOffer;
        boolean isSecondOffer;

        OfferCheckResult(boolean shouldOffer, boolean isSecondOffer) {
            this.shouldOffer = shouldOffer;
            this.isSecondOffer = isSecondOffer;
        }
    }

    private OfferCheckResult evaluateLinkOffer(ConnectLinkedAppRecord linkedApp) {
        if (linkedApp == null) {
            return new OfferCheckResult(true, false);
        }

        Date firstOffer = linkedApp.getLinkOfferDate1();
        if (firstOffer == null) {
            return new OfferCheckResult(true, false);
        }

        Date secondOffer = linkedApp.getLinkOfferDate2();
        if (secondOffer != null) {
            return new OfferCheckResult(false, true);
        }

        long millis = new Date().getTime() - firstOffer.getTime();
        long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
        return new OfferCheckResult(days >= DAYS_TO_SECOND_OFFER, false);
    }

    private void updateLinkOfferDate(ConnectLinkedAppRecord linkedApp, boolean isSecondOffer) {
        if (isSecondOffer) {
            linkedApp.setLinkOfferDate2(new Date());
        } else {
            linkedApp.setLinkOfferDate1(new Date());
        }
    }


    private void showLinkDialog(CommCareActivity<?> activity, ConnectLinkedAppRecord linkedApp, String username, String password, ConnectActivityCompleteListener callback) {
        StandardAlertDialog dialog = new StandardAlertDialog(
                activity.getString(R.string.login_link_connectid_title),
                activity.getString(R.string.login_link_connectid_message));

        dialog.setPositiveButton(activity.getString(R.string.login_link_connectid_yes), (d, w) -> {
            activity.dismissAlertDialog();
            unlockAndLinkConnect(activity, linkedApp, username, password, callback);
        });

        dialog.setNegativeButton(activity.getString(R.string.login_link_connectid_no), (d, w) -> {
            activity.dismissAlertDialog();
            ConnectAppDatabaseUtil.storeApp(activity, linkedApp);
            FirebaseAnalyticsUtil.reportPersonalIdLinkingFailed(linkedApp.getAppId());
            callback.connectActivityComplete(false);
        });

        activity.showAlertDialog(dialog);
    }

    private void unlockAndLinkConnect(CommCareActivity<?> activity, ConnectLinkedAppRecord linkedApp, String username, String password, ConnectActivityCompleteListener callback) {
        unlockConnect(activity, success -> {
            if (!success) {
                callback.connectActivityComplete(false);
                FirebaseAnalyticsUtil.reportPersonalIdLinkingFailed(linkedApp.getAppId());
                return;
            }

            linkedApp.linkToPersonalId(password);
            FirebaseAnalyticsUtil.reportPersonalIdLinked(linkedApp.getAppId());
            ConnectAppDatabaseUtil.storeApp(activity, linkedApp);

            ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(activity);
            ConnectSsoHelper.retrieveHqSsoTokenAsync(activity, user, linkedApp, username, true, new ConnectSsoHelper.TokenCallback() {
                public void tokenRetrieved(AuthInfo.TokenAuth token) {
                    callback.connectActivityComplete(false);
                }

                public void tokenUnavailable() {
                    ConnectNetworkHelper.handleTokenUnavailableException(activity);
                    callback.connectActivityComplete(false);
                }

                public void tokenRequestDenied() {
                    ConnectNetworkHelper.handleTokenDeniedException();
                    callback.connectActivityComplete(false);
                }
            });
        });
    }

    private void promptToDelinkPersonalIdApp(CommCareActivity<?> activity, String appId, String username,
                                             boolean personalIdManagedLogin, ConnectActivityCompleteListener callback) {
        // we only want to prompt when user chose non connect Id managed login
        if (personalIdManagedLogin) {
            callback.connectActivityComplete(false);
            return;
        }

        StandardAlertDialog dialog = new StandardAlertDialog(
                activity.getString(R.string.login_unlink_connectid_title),
                activity.getString(R.string.login_unlink_connectid_message));

        dialog.setPositiveButton(activity.getString(R.string.login_link_connectid_yes), (d, w) -> {
            activity.dismissAlertDialog();
            unlockConnect(activity, success -> {
                if (success) {
                    ConnectLinkedAppRecord linkedApp = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(
                            activity, appId, username);
                    if (linkedApp != null) {
                        linkedApp.severPersonalIdLink();
                        ConnectAppDatabaseUtil.storeApp(activity, linkedApp);
                    }
                }
                callback.connectActivityComplete(false);
            });
        });

        dialog.setNegativeButton(activity.getString(R.string.login_link_connectid_no), (d, w) -> {
            activity.dismissAlertDialog();
            callback.connectActivityComplete(false);
        });

        activity.showAlertDialog(dialog);

    }

    public boolean isLoginManagedByPersonalId(String appId, String userId) {
        AuthInfo.ProvidedAuth auth = getCredentialsForApp(appId, userId);
        return auth != null;
    }

    private ConnectAppRecord getAppRecord(Context context, String appId) {
        return ConnectJobUtils.getAppRecord(context, appId);
    }

    public String getStoredPasswordForApp(String appId, String userId) {
        AuthInfo.ProvidedAuth auth = getCredentialsForApp(appId, userId);
        return auth != null ? auth.password : null;
    }

    @Nullable
    public AuthInfo.ProvidedAuth getCredentialsForApp(String appId, String userId) {
        ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(parentActivity, appId,
                userId);
        if (isPersonalIdLinkedApp(appId, userId) && !record.getPassword().isEmpty()) {
            return new AuthInfo.ProvidedAuth(record.getUserId(), record.getPassword(), false);
        }
        return null;
    }

    public AuthInfo.TokenAuth getTokenCredentialsForApp(String appId, String userId) {
        if (isloggedIn()) {
            ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(parentActivity, appId,
                    userId);
            if (record != null && (new Date()).compareTo(record.getHqTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(record.getHqToken());
            }
        }

        return null;
    }

    private void getRemoteDbPassphrase(Context context, ConnectUserRecord user) {
        ApiPersonalId.fetchDbPassphrase(context, user, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try (InputStream in = responseData) {
                    String responseAsString = new String(
                            StreamsUtil.inputStreamToByteArray(in));
                    if (responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);
                        String key = ConnectConstants.CONNECT_KEY_DB_KEY;
                        if (json.has(key)) {
                            ConnectDatabaseHelper.handleReceivedDbPassphrase(context, json.getString(key));
                        }
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    Logger.exception("Parsing return from DB key request", e);
                }
            }

            @Override
            public void processFailure(int responseCode, @Nullable InputStream errorResponse,String endPoint) {
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
                ConnectNetworkHelper.handleTokenDeniedException();
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(context);
            }
        });
    }

    public PersonalIdStatus getStatus() {
        return personalIdSatus;
    }

    public void setStatus(PersonalIdStatus status) {
        personalIdSatus = status;
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

    public boolean isSeatedAppCongigureWithPersonalId(String username) {
        try {
            if (isloggedIn()) {
                String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
                ConnectLinkedAppRecord appRecord = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(
                        CommCareApplication.instance(), seatedAppId, username);
                return appRecord != null && appRecord.getWorkerLinked();
            }
        } catch (Exception e) {
            Logger.exception("Error while checking PersonalId status after failed token auth", e);
        }

        return false;
    }

    public ConnectAppMangement evaluateAppState(Context context, String appId, String userId) {
        ConnectAppRecord record = getAppRecord(context, appId);
        if (record != null) {
            return ConnectAppMangement.Connect;
        }

        return getCredentialsForApp(appId, userId) != null ?
                ConnectAppMangement.PersonalId :
                ConnectAppMangement.Unmanaged;
    }

    private boolean isConnectApp(Context context, String appId) {
        return evaluateAppState(context, appId, "") == PersonalIdManager.ConnectAppMangement.Connect;
    }

    public boolean isLoggedInWithConnectApp(Context context, String appId) {
        return isloggedIn() && isConnectApp(context, appId);
    }

    private boolean isPersonalIdLinkedApp(String appId, String username) {
        if (isloggedIn()) {
            ConnectLinkedAppRecord record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(
                    manager.parentActivity, appId, username);
            return record != null && record.getPersonalIdLinked();
        }
        return false;
    }

    public boolean isSeatedAppLinkedToPersonalId(String username) {
        if (isloggedIn()) {
            String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
            return isPersonalIdLinkedApp(seatedAppId, username);
        }
        return false;
    }

    public static AuthInfo.TokenAuth getHqTokenIfLinked(String username)
            throws TokenDeniedException, TokenUnavailableException {
        if (!manager.isloggedIn()) {
            return null;
        }

        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(manager.parentActivity);
        if (user == null) {
            return null;
        }

        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();

        if(!getInstance().isSeatedAppLinkedToPersonalId(username)){
            return null;
        }

        ConnectLinkedAppRecord appRecord = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(manager.parentActivity,
                seatedAppId, username);

        return ConnectSsoHelper.retrieveHqSsoTokenSync(CommCareApplication.instance(), user, appRecord, username,
                false);
    }

    public BiometricManager getBiometricManager(CommCareActivity<?> parent) {
        if (biometricManager == null) {
            biometricManager = BiometricManager.from(parent);
        }

        return biometricManager;
    }

    public boolean checkDeviceCompability() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    public int getFailureAttempt() {
        return failedPinAttempts;
    }

    public void setFailureAttempt(int failureAttempt) {
        failedPinAttempts = failureAttempt;
    }

    /**
     * Interface for handling callbacks when a PersonalId activity finishes
     */
    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }
}
