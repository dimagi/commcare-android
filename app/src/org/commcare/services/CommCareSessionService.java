package org.commcare.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.CommCareNoficationManager;
import org.commcare.activities.DispatchActivity;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.core.encryption.CryptUtil;
import org.commcare.dalvik.R;
import org.commcare.heartbeat.HeartbeatLifecycleManager;
import org.commcare.interfaces.FormSaveCallback;
import org.commcare.models.database.user.DatabaseUserOpenHelper;
import org.commcare.models.database.user.UserSandboxUtils;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.sync.FormSubmissionHelper;
import org.commcare.tasks.DataSubmissionListener;
import org.commcare.util.LogTypes;
import org.commcare.utils.SessionStateUninitException;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import static org.commcare.sync.ExternalDataUpdateHelper.sendBroadcastFailSafe;

/**
 * The CommCare Session Service is a persistent service which maintains
 * a CommCare login session
 *
 * @author ctsims
 */
public class CommCareSessionService extends Service {

    private NotificationManager mNM;

    /**
     * Milliseconds to wait before rechecking if the session is still fresh.
     */
    private static final long MAINTENANCE_PERIOD = 1000;

    /**
     * Session length in MS
     */
    private static long sessionLength = 1000 * 60 * 60 * 24;

    /**
     * Lock that must be held to expire the session. Thus if a task holds it,
     * the session remains alive. Allows server syncing tasks to prevent the
     * session from expiring and closing the user DB while they are running.
     */
    public static final ReentrantLock sessionAliveLock = new ReentrantLock();

    /**
     * 2h time in Milliseconds to extend the session if needed
     */
    private static final long SESSION_EXTENSION_TIME = 2 * 60 * 60 * 1000;
    private Timer maintenanceTimer;

    private byte[] key = null;

    private Date sessionExpireDate;

    private final Object lock = new Object();

    private User user;
    private String userKeyRecordUUID;
    private int userKeyRecordID;

    private SQLiteDatabase userDatabase;

    // unique id for logged in notification
    private final static int NOTIFICATION = org.commcare.dalvik.R.string.notificationtitle;

    private final static int SUBMISSION_NOTIFICATION = org.commcare.dalvik.R.string.submission_notification_title;

    // How long to wait until we force the session to finish logging out. Set
    // at 90 seconds to make sure huge forms on slow phones actually get saved
    private static final long LOGOUT_TIMEOUT = 1000 * 90;

    // The logout process start time, used to wrap up logging out if
    // the saving of incomplete forms takes too long
    private long logoutStartedAt = -1;

    // Once key expiration process starts, we want to call this function to
    // save the current form if it exists.
    private FormSaveCallback formSaver;

    private HeartbeatLifecycleManager heartbeatManager;
    private boolean heartbeatSucceededForSession;

    private boolean cczUpdatePromptWasShown;
    private boolean apkUpdatePromptWasShown;
    private boolean showInAppUpdate = true;

    // Have the app health checks in HomeScreenBaseActivity#checkForPendingAppHealthActions() been
    // done at least once during this session?
    private boolean appHealthChecksCompleted;
    public static final String LOG_SUBMISSION_RESULT_PREF = "log_submission_result";

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        public CommCareSessionService getService() {
            return CommCareSessionService.this;
        }
    }

    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        setSessionLength();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            CommCareApplication.instance().getCurrentSessionWrapper().reset();
        } catch (SessionStateUninitException e) {
            Log.e(LogTypes.SOFT_ASSERT,
                    "Trying to wipe uninitialized session in session service tear-down");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        this.stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    /**
     * Show a notification while this service is running.
     */
    public void showLoggedInNotification(@Nullable User user) {
        // Send the notification. This will cause error messages if CommCare doesn't have
        // permission to post notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            this.startForeground(NOTIFICATION, createSessionNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            this.startForeground(NOTIFICATION, createSessionNotification());
        }
    }

    /**
     * Notify the user that they've been timed out and need to relog in
     */
    @SuppressLint("UnspecifiedImmutableFlag")
    private void showLoggedOutNotification() {
        this.stopForeground(true);

        if (CommCareApplication.notificationManager().areNotificationsEnabled()) {
            Intent i = new Intent(this, DispatchActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            PendingIntent contentIntent = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                contentIntent = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            else
                contentIntent = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

            Notification notification = new NotificationCompat.Builder(this, CommCareNoficationManager.NOTIFICATION_CHANNEL_USER_SESSION_ID)
                    .setContentTitle(this.getString(R.string.expirenotification))
                    .setContentText("Click here to log back into your session")
                    .setSmallIcon(R.drawable.commcare_actionbar_logo)
                    .setContentIntent(contentIntent)
                    .build();

            // Send the notification.
            mNM.notify(NOTIFICATION, notification);
        }
    }

    //Start CommCare Specific Functionality

    public SQLiteDatabase getUserDbHandle() {
        synchronized (lock) {
            return userDatabase;
        }
    }

    /**
     * (Re-)open user database
     */
    public void prepareStorage(byte[] symetricKey, UserKeyRecord record) {
        synchronized (lock) {
            this.userKeyRecordUUID = record.getUuid();
            this.key = symetricKey;
            if (userDatabase != null && userDatabase.isOpen()) {
                userDatabase.close();
            }

            userDatabase = new DatabaseUserOpenHelper(CommCareApplication.instance(), userKeyRecordUUID)
                    .getWritableDatabase(UserSandboxUtils.getSqlCipherEncodedKey(key));
        }
    }

    /**
     * Register a user with a session and start the session expiration timer.
     * Assumes user database and key pool have already been setup .
     *
     * @param user attach this user to the session
     */
    public void startSession(User user, UserKeyRecord record) {
        synchronized (lock) {
            if (user != null) {
                Logger.log(LogTypes.TYPE_USER, "login|" + user.getUsername() + "|" + user.getUniqueId());

                //Let anyone who is listening know!
                Intent i = new Intent("org.commcare.dalvik.api.action.session.login");
                sendBroadcastFailSafe(this, i, null);
            }

            this.user = user;
            this.userKeyRecordID = record.getID();

            this.sessionExpireDate = new Date(new Date().getTime() + sessionLength);

            if (!CommCareApplication.instance().isConsumerApp()) {
                // Put an icon in the status bar for the session, and set up session expiration
                // (unless this is a consumer app)
                showLoggedInNotification(user);
                setUpSessionExpirationTimer();
            }
        }
    }

    private void setUpSessionExpirationTimer() {
        maintenanceTimer = new Timer("CommCareService");
        maintenanceTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                timeToExpireSession();
            }

        }, MAINTENANCE_PERIOD, MAINTENANCE_PERIOD);
    }

    /**
     * If the session has been alive for longer than its specified duration
     * then save any open forms and close it down. If data syncing is in
     * progess then don't do anything.
     */
    private void timeToExpireSession() {
        long currentTime = new Date().getTime();

        // If logout process started and has taken longer than the logout
        // timeout then wrap-up the process.
        if (logoutStartedAt != -1 &&
                currentTime > (logoutStartedAt + LOGOUT_TIMEOUT)) {
            Logger.log(LogTypes.TYPE_USER,
                    "Crossed threshold to save form session state during scheduled session expiration. Passed " + (
                            currentTime - logoutStartedAt) + "ms");
            // Try and grab the logout lock, aborting if synchronization is in
            // progress.
            if (!CommCareSessionService.sessionAliveLock.tryLock()) {
                Logger.log(LogTypes.TYPE_USER,
                        "Unabled to get session lock during scheduled session expiration");
                return;
            }
            try {
                Logger.log(LogTypes.TYPE_USER, "Expiring user session forcefully due to session timeout");
                CommCareApplication.instance().expireUserSession();
            } finally {
                CommCareSessionService.sessionAliveLock.unlock();
            }
        } else if (isActive() && logoutStartedAt == -1 &&
                (currentTime > sessionExpireDate.getTime() ||
                        (sessionExpireDate.getTime() - currentTime > sessionLength))) {
            // If we haven't started closing the session and we're either past
            // the session expire time, or the session expires more than its
            // period in the future, we need to log the user out. The second
            // case occurs if the system's clock is altered.

            // Try and grab the logout lock, aborting if synchronization is in
            // progress.
            if (!CommCareSessionService.sessionAliveLock.tryLock()) {
                Logger.log(LogTypes.TYPE_USER,
                        "Unabled to get session lock during scheduled session expiration");
                return;
            }

            try {
                Logger.log(LogTypes.TYPE_USER, "Expiring user session due to session timeout");
                saveFormAndCloseSession();
            } finally {
                CommCareSessionService.sessionAliveLock.unlock();
            }

            showLoggedOutNotification();
        }
    }

    /**
     * Notify any open form that it needs to save, then close the key session
     * after waiting for the form save to complete/timeout.
     */
    private void saveFormAndCloseSession() {
        // Remember when we started so that if form saving takes too long, the
        // maintenance timer will launch CommCareApplication.instance().expireUserSession
        logoutStartedAt = new Date().getTime();

        // Note: Any other types of callbacks should be added to both this method
        // and the non-close version

        // save form progress, if any
        synchronized (lock) {
            if (formSaver != null) {
                formSaver.formSaveCallback(true, false, () -> {
                    CommCareApplication.instance().expireUserSession();
                });
            } else {
                CommCareApplication.instance().expireUserSession();
            }
        }
    }

    /**
     * Calls the provided runnable ensuring that if there is currently an active
     * form session being executed that it is interrupted and stored
     */
    public void proceedWithSavedSessionIfNeeded(Runnable callback) {
        // save form progress, if any
        synchronized (lock) {
            if (formSaver != null) {
                Toast.makeText(CommCareApplication.instance(),
                        "Suspending existing form entry session...", Toast.LENGTH_LONG).show();
                formSaver.formSaveCallback(true, false, callback);
                formSaver = null;
                return;
            }
        }
        //No forms to be saved, just run the callback immediately
        callback.run();
    }

    /**
     * Allow for the form entry engine to register a method that can be used to
     * save any forms being editted when key expiration begins.
     *
     * @param callbackObj object with a method for saving the current form
     *                    being edited
     */
    public void registerFormSaveCallback(FormSaveCallback callbackObj) {
        this.formSaver = callbackObj;
    }

    /**
     * Unregister the form save callback; should occur when there is no longer
     * a form open that might need to be saved if the session expires.
     */
    public void unregisterFormSaveCallback() {
        synchronized (lock) {
            this.formSaver = null;
        }
    }

    /**
     * Closes the key pool and user database.
     */
    public void closeServiceResources() {
        synchronized (lock) {
            if (!isActive()) {
                // Since both the FormSaveCallback callback and the maintenance
                // timer might call this, only run if it hasn't been called
                // before.
                return;
            }

            // Let anyone who is listening know!
            Intent i = new Intent("org.commcare.dalvik.api.action.session.logout");
            sendBroadcastFailSafe(this, i, null);

            Logger.log(LogTypes.TYPE_MAINTENANCE, "Logging out service login");

            key = null;
            user = null;

            if (userDatabase != null) {
                if (userDatabase.isOpen()) {
                    userDatabase.close();
                }
                userDatabase = null;
            }

            // timer is null if we aren't actually in the foreground
            if (maintenanceTimer != null) {
                maintenanceTimer.cancel();
            }
            logoutStartedAt = -1;
            endHeartbeatLifecycle();
        }
    }

    /**
     * Is the session active? Active sessions have an open key pool and user
     * database.
     */
    public boolean isActive() {
        synchronized (lock) {
            return (key != null);
        }
    }

    public SecretKey createNewSymmetricKey() {
        synchronized (lock) {
            // Ensure we have a key to work with
            if (!isActive()) {
                throw new SessionUnavailableException("Can't generate new key when the user session key is empty.");
            }
            return CryptUtil.generateSymmetricKey(CryptUtil.uniqueSeedFromSecureStatic(key));
        }
    }

    public String getUserKeyRecordUUID() {
        if (key == null) {
            // key record hasn't been set, so error out
            throw new SessionUnavailableException();
        }

        return userKeyRecordUUID;
    }

    public User getLoggedInUser() {
        if (user == null) {
            throw new SessionUnavailableException();
        }
        return user;
    }

    public UserKeyRecord getUserKeyRecord() {
        return CommCareApplication.instance().getCurrentApp().getStorage(UserKeyRecord.class)
                .read(this.userKeyRecordID);
    }

    public DataSubmissionListener getListenerForSubmissionNotification() {
        return this.getListenerForSubmissionNotification(SUBMISSION_NOTIFICATION);
    }

    public DataSubmissionListener getListenerForSubmissionNotification(final int notificationId) {
        return new DataSubmissionListener() {
            int totalItems = -1;
            long currentSize = -1;
            NotificationCompat.Builder submissionNotification;

            int lastUpdate = 0;

            @Override
            @SuppressLint("UnspecifiedImmutableFlag")
            public void beginSubmissionProcess(int totalItems) {
                this.totalItems = totalItems;

                setLogSubmissionResultPref(false);
                //We always want this click to simply bring the live stack back to the top
                Intent callable = new Intent(CommCareSessionService.this, DispatchActivity.class);
                callable.setAction("android.intent.action.MAIN");
                callable.addCategory("android.intent.category.LAUNCHER");

                // The PendingIntent to launch our activity if the user selects this notification
                //TODO: Put something here that will, I dunno, cancel submission or something? Maybe show it live?
                PendingIntent contentIntent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    contentIntent = PendingIntent.getActivity(CommCareSessionService.this, 0, callable, PendingIntent.FLAG_IMMUTABLE);
                else
                    contentIntent = PendingIntent.getActivity(CommCareSessionService.this, 0, callable, 0);

                submissionNotification = new NotificationCompat.Builder(CommCareSessionService.this,
                        CommCareNoficationManager.NOTIFICATION_CHANNEL_SERVER_COMMUNICATIONS_ID)
                        .setContentTitle(getString(notificationId))
                        .setContentInfo(getSubmittedFormCount(1, totalItems))
                        .setContentText("0b transmitted")
                        .setSmallIcon(org.commcare.dalvik.R.drawable.commcare_actionbar_logo)
                        .setContentIntent(contentIntent)
                        .setOngoing(true)
                        .setWhen(System.currentTimeMillis())
                        .setTicker(getTickerText(totalItems));

                if (user != null) {
                    mNM.notify(notificationId, submissionNotification.build());
                }
            }

            @Override
            public void startSubmission(int itemNumber, long sizeOfItem) {
                currentSize = sizeOfItem;

                submissionNotification.setContentInfo(getSubmittedFormCount(itemNumber + 1, totalItems));
                submissionNotification.setProgress(100, 0, false);
                mNM.notify(notificationId, submissionNotification.build());
            }

            @Override
            public void notifyProgress(int itemNumber, long progress) {
                int progressPercent = (int)Math.floor((progress * 1.0 / currentSize) * 100);

                if (progressPercent - lastUpdate > 5) {

                    String progressDetails;
                    if (progress < 1024) {
                        progressDetails = progress + "b transmitted";
                    } else if (progress < 1024 * 1024) {
                        progressDetails = String.format("%1$,.1f", (progress / 1024.0)) + "kb transmitted";
                    } else {
                        progressDetails = String.format("%1$,.1f", (progress / (1024.0 * 1024.0))) + "mb transmitted";
                    }

                    int pending = FormSubmissionHelper.pending();
                    if (pending > 1) {
                        submissionNotification.setContentInfo(pending - 1 + " Pending");
                    }

                    submissionNotification.setContentText(progressDetails);
                    submissionNotification.setProgress(100, progressPercent, false);
                    mNM.notify(notificationId, submissionNotification.build());
                    lastUpdate = progressPercent;
                }
            }

            @Override
            public void endSubmissionProcess(boolean success) {
                mNM.cancel(notificationId);
                submissionNotification = null;
                totalItems = -1;
                currentSize = -1;
                lastUpdate = 0;
                setLogSubmissionResultPref(success);
            }

            private void setLogSubmissionResultPref(boolean success) {
                SharedPreferences globalPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                globalPreferences.edit().putBoolean(LOG_SUBMISSION_RESULT_PREF, success).commit();
            }

            private String getSubmittedFormCount(int current, int total) {
                return current + "/" + total;
            }

            private String getTickerText(int total) {
                return "CommCare submitting " + total + " forms";
            }
        };
    }

    /**
     * Read the login session duration from app preferences and set the session
     * length accordingly.
     */
    private void setSessionLength() {
        sessionLength = HiddenPreferences.getLoginDuration() * 1000;
    }

    public void setCurrentUser(User user, String password) {
        this.user = user;
        this.user.setCachedPwd(password);
    }

    public void initHeartbeatLifecycle() {
        if (heartbeatManager == null) {
            heartbeatManager = new HeartbeatLifecycleManager(this);
        }
        heartbeatManager.startHeartbeatCommunications(this);
    }

    private void endHeartbeatLifecycle() {
        if (heartbeatManager != null) {
            heartbeatManager.endHeartbeatCommunications(this);
            heartbeatManager = null;
        }
    }

    public void setHeartbeatSuccess() {
        this.heartbeatSucceededForSession = true;
    }

    public boolean heartbeatSucceededForSession() {
        return heartbeatSucceededForSession;
    }

    public void setCczUpdatePromptWasShown() {
        this.cczUpdatePromptWasShown = true;
    }

    public void setApkUpdatePromptWasShown() {
        this.apkUpdatePromptWasShown = true;
    }

    public boolean cczUpdatePromptWasShown() {
        return this.cczUpdatePromptWasShown;
    }

    public boolean apkUpdatePromptWasShown() {
        return this.apkUpdatePromptWasShown;
    }

    public void setAppHealthChecksCompleted() {
        this.appHealthChecksCompleted = true;
    }

    public boolean appHealthChecksCompleted() {
        return this.appHealthChecksCompleted;
    }

    public void hideInAppUpdate() {
        this.showInAppUpdate = false;
    }

    public boolean shouldShowInAppUpdate() {
        return this.showInAppUpdate;
    }

    public void extendUserSessionIfNeeded(){
        long currentTime = new Date().getTime();

        if (sessionExpireDate.getTime() < currentTime + SESSION_EXTENSION_TIME) {
            sessionExpireDate.setTime(sessionExpireDate.getTime() + SESSION_EXTENSION_TIME);
            sessionLength += SESSION_EXTENSION_TIME;

            mNM.notify(NOTIFICATION, createSessionNotification());
        }
    }

    private Notification createSessionNotification(){
        //We always want this click to simply bring the live stack back to the top
        Intent callable = new Intent(this, DispatchActivity.class);
        callable.setAction("android.intent.action.MAIN");
        callable.addCategory("android.intent.category.LAUNCHER");

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags = pendingIntentFlags | PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, callable, pendingIntentFlags);

        String notificationText;
        if (AppUtils.getInstalledAppRecords().size() > 1) {
            try {
                notificationText = Localization.get("notification.logged.in",
                        new String[]{Localization.get("app.display.name")});
            } catch (NoLocalizedTextException e) {
                notificationText = getString(NOTIFICATION);
            }
        } else {
            notificationText = getString(NOTIFICATION);
        }

        // Set the icon, scrolling text and timestamp
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CommCareNoficationManager.NOTIFICATION_CHANNEL_ERRORS_ID)
                .setContentTitle(notificationText)
                .setSmallIcon(R.drawable.commcare_actionbar_logo)
                .setContentIntent(contentIntent);

        if (user != null) {
            String contentText = "Session Expires: " + DateFormat.format("MMM dd h:mmaa", sessionExpireDate);
            notificationBuilder.setContentText(contentText);
        }
        return notificationBuilder.build();
    }
}
