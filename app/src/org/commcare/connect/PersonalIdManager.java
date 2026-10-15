package org.commcare.connect;


import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectReleaseToggleRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.repository.ConnectSyncPreferences;
import org.commcare.connect.workers.ConnectHeartbeatWorker;
import org.commcare.connect.workers.ConnectReleaseTogglesWorker;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.personalId.PersonalIdBackupCodeReminderHelper;
import org.commcare.personalId.PersonalIdUnlocker;
import org.commcare.personalId.PersonalIdUserPreferences;
import org.commcare.pn.workermanager.NotificationsSyncWorkerManager;
import org.commcare.preferences.NotificationPrefs;
import org.commcare.utils.CrashUtil;
import org.commcare.utils.GlobalErrorUtil;
import org.commcare.utils.GlobalErrors;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Manager class for PersonalID, handles workflow navigation and user management
 *
 * @author dviggiano
 */
public class PersonalIdManager {

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
    private static volatile PersonalIdManager manager = null;
    private volatile PersonalIdStatus personalIdStatus = PersonalIdStatus.NotIntroduced;

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

    /**
     * Resolves the session status and, unlike {@link #isloggedIn()}, reports a corrupt database.
     * Callers that can surface the resulting error to the user should call this on startup; the
     * status itself resolves on demand.
     */
    public void init() {
        resolveStatus();
        if (personalIdStatus == PersonalIdStatus.NotIntroduced && ConnectDatabaseHelper.isDbBroken()) {
            //Corrupt DB, inform user to recover
            GlobalErrorUtil.triggerGlobalError(GlobalErrors.PERSONALID_DB_STARTUP_ERROR);
        }
    }

    private void resolveStatus() {
        if (personalIdStatus != PersonalIdStatus.NotIntroduced) {
            return;
        }
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser();
        if (user != null) {
            boolean registering = user.getRegistrationPhase() != ConnectConstants.PERSONAL_ID_USER_STATUS_REGISTERED;
            personalIdStatus = registering ? PersonalIdStatus.Registering : PersonalIdStatus.LoggedIn;
            CrashUtil.registerUserData();
        }
    }

    private void scheduleHeartbeat() {
        if (isloggedIn()) {
            Constraints constraints = new Constraints.Builder().setRequiredNetworkType(
                    NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build();

            PeriodicWorkRequest heartbeatRequest = new PeriodicWorkRequest.Builder(
                    ConnectHeartbeatWorker.class,
                    PERIODICITY_FOR_HEARTBEAT_IN_HOURS,
                    TimeUnit.HOURS
            ).addTag(CONNECT_HEARTBEAT_WORKER).setConstraints(constraints).setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_DELAY_FOR_HEARTBEAT_RETRY,
                    TimeUnit.MILLISECONDS
            ).build();

            // UPDATE rather than REPLACE: REPLACE cancels and re-enqueues, which makes the worker
            // eligible to run immediately on every unlock. An in-flight run started that way can
            // land after a subsequent sign-out and write to a deleted Connect DB.
            WorkManager.getInstance(CommCareApplication.instance()).enqueueUniquePeriodicWork(
                    CONNECT_HEARTBEAT_REQUEST_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    heartbeatRequest
            );
        }
    }

    public boolean isloggedIn() {
        resolveStatus();
        return personalIdStatus == PersonalIdStatus.LoggedIn;
    }

    public void userUnlockedPersonalId() {
        scheduleHeartbeat();
        NotificationsSyncWorkerManager.schedulePeriodicPushNotificationRetrieval(CommCareApplication.instance());
        CrashUtil.registerUserData();
        ConnectReleaseTogglesWorker.Companion.schedulePeriodicFetch(CommCareApplication.instance());
    }

    public void forgetUser(String reason) {
        if (ConnectDatabaseHelper.dbExists()) {
            FirebaseAnalyticsUtil.reportPersonalIdAccountForgotten(reason);
        }

        personalIdStatus = PersonalIdStatus.NotIntroduced;

        // Cancel periodic push notification retrieval when user logs out
        NotificationsSyncWorkerManager.cancelPeriodicPushNotificationRetrieval(CommCareApplication.instance());

        ConnectUserDatabaseUtil.forgetUser();

        // remove notification read / unread preferences
        NotificationPrefs.INSTANCE.removeNotificationReadPref(CommCareApplication.instance());

        ConnectReleaseTogglesWorker.Companion.cancelPeriodicFetch(CommCareApplication.instance());
        PersonalIdUnlocker.INSTANCE.resetSession();
        PersonalIdUserPreferences.clear();
        CommCareApplication.instance().closeUserSession();
    }

    public PersonalIdStatus getStatus() {
        return personalIdStatus;
    }

    public void setStatus(PersonalIdStatus status) {
        personalIdStatus = status;
    }

    public void onAccountConfigurationSuccess(PersonalIdSessionData sessionData) {
        createAndSaveConnectUser(sessionData);
        PersonalIdBackupCodeReminderHelper.initialize();
        setStatus(PersonalIdStatus.LoggedIn);
        List<ConnectReleaseToggleRecord> toggles = sessionData.getFeatureReleaseToggles();
        if (toggles != null) {
            ConnectAppDatabaseUtil.storeReleaseToggles(toggles);
        }
        ConnectSyncPreferences.Companion.getInstance().markSessionStart();
        userUnlockedPersonalId();
    }

    private void createAndSaveConnectUser(PersonalIdSessionData sessionData) {
        ConnectDatabaseHelper.handleReceivedDbPassphrase(sessionData.getDbKey());
        ConnectUserRecord user = new ConnectUserRecord(sessionData.getPhoneNumber(),
                sessionData.getPersonalId(),
                sessionData.getOauthPassword(), sessionData.getUserName(),
                String.valueOf(sessionData.getBackupCode()), new Date(), sessionData.getPhotoBase64(),
                sessionData.getDemoUser(),sessionData.getRequiredLock(),
                sessionData.getInvitedUser());
        user.setEmail(sessionData.getEmail());
        user.setRegistrationPhase(ConnectConstants.PERSONAL_ID_USER_STATUS_REGISTERED);
        ConnectUserDatabaseUtil.storeUser(user);
    }

    public static void clearInstance() {
        manager = null;
    }

}
