package org.commcare.connect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.global.models.ConnectKeyRecord;
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

/**
 * Manager class for PersonalID, handles workflow navigation and user management
 *
 * @author dviggiano
 */
public class PersonalIdManager {
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
    public static final int MethodRegistrationPrimary = 1;
    public static final int MethodRecoveryPrimary = 2;
    private BiometricManager biometricManager;

    private static volatile PersonalIdManager manager = null;
    private PersonalIdStatus personalIdSatus = PersonalIdStatus.NotIntroduced;
    private Context parentActivity;
    private String primedAppIdForAutoLogin = null;
    private int failedPinAttempts = 0;
    private ConnectJobRecord activeJob = null;


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

    public String init(Context parent) {
        parentActivity = parent;
        if (personalIdSatus == PersonalIdStatus.NotIntroduced) {
            String errorMessage = checkForLogoutErrorMessage(parent);
            if(errorMessage != null) {
                return errorMessage;
            }

            ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(parentActivity);
            if (user != null) {
                boolean registering = user.getRegistrationPhase() != ConnectConstants.PERSONALID_NO_ACTIVITY;
                personalIdSatus = registering ? PersonalIdStatus.Registering : PersonalIdStatus.LoggedIn;

                String remotePassphrase = ConnectDatabaseUtils.getConnectDbEncodedPassphrase(parent, false);
                if (remotePassphrase == null) {
                    getRemoteDbPassphrase(parent, user);
                }
            } else if (ConnectDatabaseHelper.isDbBroken()) {
                //Corrupt DB, inform user to recover
                ConnectDatabaseHelper.crashDb();
            }
        }

        return null;
    }

    private String checkForLogoutErrorMessage(Context context) {
        ConnectKeyRecord keyRecord = ConnectDatabaseUtils.getKeyRecord(true);
        if(keyRecord != null) {
            int message = keyRecord.getLogoutErrorMessage();
            if(message > 0) {
                String reason = context.getString(message);
                forgetUser(reason);
                return reason;
            }
        }

        return null;
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
            BiometricsHelper.authenticateFingerprint(activity, bioManager, callbacks);
        } else if (BiometricsHelper.isPinConfigured(activity, bioManager)) {
            BiometricsHelper.authenticatePin(activity, bioManager, callbacks);
        } else {
            callback.connectActivityComplete(false);
            Logger.exception("No unlock method available when trying to unlock PersonalId", new Exception("No unlock option"));
            Toast.makeText(activity, activity.getString(R.string.connect_unlock_unavailable), Toast.LENGTH_SHORT).show();

        }
    }

    public void completeSignin() {
        personalIdSatus = PersonalIdStatus.LoggedIn;
        scheduleHearbeat();
        CrashUtil.registerUserData();
    }

    public void handleFinishedActivity(CommCareActivity<?> activity, int resultCode) {
        parentActivity = activity;
        if (resultCode == AppCompatActivity.RESULT_OK) {
            goToConnectJobsList(activity);
        }
    }


    public void forgetUser(String reason) {
        if (ConnectDatabaseHelper.dbExists(parentActivity)) {
            FirebaseAnalyticsUtil.reportCccDeconfigure(reason);
        }
        ConnectUserDatabaseUtil.forgetUser(parentActivity);
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
        launchPersonalId(parent, ConnectConstants.BEGIN_REGISTRATION, requestCode);
    }

    private void launchPersonalId(CommCareActivity<?> parent, String task, int requestCode) {
        Intent intent = new Intent(parent, PersonalIdActivity.class);
        intent.putExtra(ConnectConstants.TASK, task);
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
            callback.connectActivityComplete(false);
        });

        activity.showAlertDialog(dialog);
    }

    private void unlockAndLinkConnect(CommCareActivity<?> activity, ConnectLinkedAppRecord linkedApp, String username, String password, ConnectActivityCompleteListener callback) {
        unlockConnect(activity, success -> {
            if (!success) {
                callback.connectActivityComplete(false);
                return;
            }

            linkedApp.linkToPersonalId(password);
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

    ///TODO update the code with connect code
    public void updateJobProgress(Context context, ConnectJobRecord job, ConnectActivityCompleteListener listener) {
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

    public void goToConnectJobsList(Context parent) {
        parentActivity = parent;
        completeSignin();
//        Intent i = new Intent(parent, ConnectActivity.class);
//        parent.startActivity(i);
    }


    public void goToActiveInfoForJob(Activity activity, boolean allowProgression) {
        ///TODO uncomment with connect pahse pr
//        completeSignin();
//        Intent i = new Intent(activity, ConnectActivity.class);
//        i.putExtra("info", true);
//        i.putExtra("buttons", allowProgression);
//        activity.startActivity(i);
    }

    public ConnectJobRecord setConnectJobForApp(Context context, String appId) {
        ConnectJobRecord job = null;
        ConnectAppRecord appRecord = getAppRecord(context, appId);
        if (appRecord != null) {
            job = ConnectJobUtils.getCompositeJob(context, appRecord.getJobId());
        }
        setActiveJob(job);
        return job;
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
        if (record != null && record.getPersonalIdLinked() && !record.getPassword().isEmpty()) {
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

    public boolean shouldShowSecondaryPhoneConfirmationTile(Context context) {
        boolean show = false;
        if (isloggedIn()) {
            ConnectUserRecord user = getUser(context);
            show = !user.getSecondaryPhoneVerified();
        }
        return show;
    }

    public void beginSecondaryPhoneVerification(CommCareActivity<?> parent, int requestCode) {
        launchPersonalId(parent, ConnectConstants.VERIFY_PHONE, requestCode);
    }

    public void setActiveJob(ConnectJobRecord job) {
        activeJob = job;
    }

    public boolean isSeatedAppLinkedToPersonalId(String username) {
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

    public static AuthInfo.TokenAuth getHqTokenIfLinked(String username) throws TokenDeniedException, TokenUnavailableException {
        if (!manager.isloggedIn()) {
            return null;
        }

        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(manager.parentActivity);
        if (user == null) {
            return null;
        }

        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectLinkedAppRecord appRecord = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(manager.parentActivity, seatedAppId, username);
        if(appRecord == null) {
            return null;
        }

        return ConnectSsoHelper.retrieveHqSsoTokenSync(CommCareApplication.instance(), user, appRecord, username, false);
    }

    public BiometricManager getBiometricManager(CommCareActivity<?> parent) {
        if (biometricManager == null) {
            biometricManager = BiometricManager.from(parent);
        }

        return biometricManager;
    }

    public int getFailureAttempt() {
        return failedPinAttempts;
    }

    public void setFailureAttempt(int failureAttempt) {
        failedPinAttempts = failureAttempt;
    }

    public boolean shouldShowJobStatus(Context context, String appId) {
        ConnectAppRecord record = getAppRecord(context, appId);
        if(record == null || activeJob == null) {
            return false;
        }

        //Only time not to show is when we're in learn app but job is in delivery state
        return !record.getIsLearning() || activeJob.getStatus() != ConnectJobRecord.STATUS_DELIVERING;
    }


    /**
     * Interface for handling callbacks when a PersonalId activity finishes
     */
    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }
}
