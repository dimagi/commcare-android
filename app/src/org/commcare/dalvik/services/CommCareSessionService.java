package org.commcare.dalvik.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.text.format.DateFormat;
import android.widget.RemoteViews;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.crypt.CipherPool;
import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.CommCareUserOpenHelper;
import org.commcare.android.database.user.UserSandboxUtils;
import org.javarosa.core.model.User;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.DataSubmissionListener;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;
import org.odk.collect.android.listeners.FormSaveCallback;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * The CommCare Session Service is a persistent service which maintains
 * a CommCare login session 
 * 
 * @author ctsims
 *
 */
public class CommCareSessionService extends Service  {

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

    private Timer maintenanceTimer;
    private CipherPool pool;

    private byte[] key = null;

    private Date sessionExpireDate;

    private final Object lock = new Object();

    private User user;

    private SQLiteDatabase userDatabase;

    // unique id for logged in notification
    private final int NOTIFICATION = org.commcare.dalvik.R.string.notificationtitle;

    private final int SUBMISSION_NOTIFICATION = org.commcare.dalvik.R.string.submission_notification_title;

    // How long to wait until we force the session to finish logging out. Set
    // at 90 seconds to make sure huge forms on slow phones actually get saved
    private static final long LOGOUT_TIMEOUT = 1000 * 90;

    // The logout process start time, used to wrap up logging out if
    // the saving of incomplete forms takes too long
    private long logoutStartedAt = -1;

    // Once key expiration process starts, we want to call this function to
    // save the current form if it exists.
    private FormSaveCallback formSaver;
    
    
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
        createCipherPool();
    }

    public void createCipherPool() {
        pool = new CipherPool() {
            @Override
            public Cipher generateNewCipher() {
                synchronized(lock) {
                    try {
                        synchronized(key) {
                            SecretKeySpec spec = new SecretKeySpec(key, "AES");
                            Cipher decrypter = Cipher.getInstance("AES");
                            decrypter.init(Cipher.DECRYPT_MODE, spec);

                            return decrypter;
                        }
                    } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }
        };
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
    private void showLoggedInNotification(User user) {
        //We always want this click to simply bring the live stack back to the top
        Intent callable = new Intent(this, CommCareHomeActivity.class);
        callable.setAction("android.intent.action.MAIN");
        callable.addCategory("android.intent.category.LAUNCHER");

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, callable, 0);

        String notificationText;
        if (CommCareApplication._().getInstalledAppRecords().size() > 1) {
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
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(notificationText)
                .setContentText("Session Expires: " + DateFormat.format("MMM dd h:mmaa", sessionExpireDate))
                .setSmallIcon(org.commcare.dalvik.R.drawable.notification)
                .setContentIntent(contentIntent)
                .build();

        if(user != null) {
            //Send the notification.
            this.startForeground(NOTIFICATION, notification);
        }
    }

    /**
     * Notify the user that they've been timed out and need to relog in
     */
    private void showLoggedOutNotification() {
        this.stopForeground(true);

        Intent i = new Intent(this, CommCareHomeActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(this.getString(R.string.expirenotification))
                .setContentText("Click here to log back into your session")
                .setSmallIcon(org.commcare.dalvik.R.drawable.notification)
                .setContentIntent(contentIntent)
                .build();

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
    }
    
    //Start CommCare Specific Functionality

    public SQLiteDatabase getUserDbHandle() {
        synchronized(lock){
            return userDatabase;
        }
    }

    /**
     * (Re-)open user database
     */
    public void prepareStorage(byte[] symetricKey, UserKeyRecord record) {
        synchronized(lock){
            this.key = symetricKey;
            pool.init();
            if(userDatabase != null && userDatabase.isOpen()) {
                userDatabase.close();
            }
            userDatabase = new CommCareUserOpenHelper(CommCareApplication._(), record.getUuid()).getWritableDatabase(UserSandboxUtils.getSqlCipherEncodedKey(key));
        }
    }

    /**
     * Register a user with a session and start the session expiration timer.
     * Assumes user database and key pool have already been setup .
     *
     * @param user attach this user to the session
     */
    public void startSession(User user) {
        synchronized(lock){
            if(user != null) {
                Logger.log(AndroidLogger.TYPE_USER, "login|" + user.getUsername() + "|" + user.getUniqueId());
                
                //Let anyone who is listening know!
                Intent i = new Intent("org.commcare.dalvik.api.action.session.login");
                this.sendBroadcast(i);
            }
            
            this.user = user;
            
            this.sessionExpireDate = new Date(new Date().getTime() + sessionLength);
            
            // Display a notification about us starting.  We put an icon in the status bar.
            showLoggedInNotification(user);
            
            maintenanceTimer = new Timer("CommCareService");
            maintenanceTimer.schedule(new TimerTask() {
    
                @Override
                public void run() {
                    timeToExpireSession();
                }
                
            }, MAINTENANCE_PERIOD, MAINTENANCE_PERIOD);
        }
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
            // Try and grab the logout lock, aborting if synchronization is in
            // progress.
            if (!CommCareSessionService.sessionAliveLock.tryLock()) {
                return;
            }
            try {
                CommCareApplication._().expireUserSession();
            } finally {
                CommCareSessionService.sessionAliveLock.unlock();
            }
        } else if (isActive() && logoutStartedAt == -1 &&
                (currentTime > sessionExpireDate.getTime() ||
                 (sessionExpireDate.getTime() - currentTime  > sessionLength))) {
            // If we haven't started closing the session and we're either past
            // the session expire time, or the session expires more than its
            // period in the future, we need to log the user out. The second
            // case occurs if the system's clock is altered.

            // Try and grab the logout lock, aborting if synchronization is in
            // progress.
            if (!CommCareSessionService.sessionAliveLock.tryLock()) {
                return;
            }

            try {
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
        // maintenance timer will launch CommCareApplication._().expireUserSession
        logoutStartedAt = new Date().getTime();

        // save form progress, if any
        synchronized(lock) {
            if (formSaver != null) {
                formSaver.formSaveCallback();
            } else {
                CommCareApplication._().expireUserSession();
            }
        }
    }

    /**
     * Allow for the form entry engine to register a method that can be used to
     * save any forms being editted when key expiration begins.
     *
     * @param callbackObj object with a method for saving the current form
     * being edited
     */
    public void registerFormSaveCallback(FormSaveCallback callbackObj) {
        this.formSaver = callbackObj;
    }

    /**
     * Unregister the form save callback; should occur when there is no longer
     * a form open that might need to be saved if the session expires.
     */
    public void unregisterFormSaveCallback() {
        synchronized(lock) {
            this.formSaver = null;
        }
    }

    /**
     * Closes the key pool and user database.
     */
    public void closeServiceResources() {
        synchronized(lock){
            if (!isActive()) {
                // Since both the FormSaveCallback callback and the maintenance
                // timer might call this, only run if it hasn't been called
                // before.
                return;
            }

            key = null;
            String msg = "Logging out service login";

            // Let anyone who is listening know!
            Intent i = new Intent("org.commcare.dalvik.api.action.session.logout");
            this.sendBroadcast(i);

            Logger.log(AndroidLogger.TYPE_MAINTENANCE, msg);

            if (user != null) {
                if (user.getUsername() != null) {
                    msg = "Logging out user " + user.getUsername();
                }
                user = null;
            }

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

            pool.expire();
        }
    }

    /**
     * Is the session active? Active sessions have an open key pool and user
     * database.
     */
    public boolean isActive() {
        synchronized(lock){
            return (key != null);
        }
    }

    public Cipher getEncrypter() throws SessionUnavailableException {
        synchronized(lock){
            if(key == null) {
                throw new SessionUnavailableException();
            }
            
            synchronized(key) {
    
                SecretKeySpec spec = new SecretKeySpec(key, "AES");
                
                try{
                    Cipher encrypter = Cipher.getInstance("AES");
                    encrypter.init(Cipher.ENCRYPT_MODE, spec);
                    return encrypter;
                } catch (InvalidKeyException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (NoSuchPaddingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                return null;
            }
        }
    }
    
    public CipherPool getDecrypterPool() throws SessionUnavailableException{
        synchronized(lock){
            if(key == null) {
                throw new SessionUnavailableException();
            }
            return pool;
        }
    }
    
    public SecretKey createNewSymetricKey() {
        return CryptUtil.generateSymetricKey(CryptUtil.uniqueSeedFromSecureStatic(key));
    }
    
    public User getLoggedInUser() throws SessionUnavailableException {
        if(user == null) {
            throw new SessionUnavailableException();
        }
        return user;
    }    
    
    public DataSubmissionListener startDataSubmissionListener() {
        return this.startDataSubmissionListener(SUBMISSION_NOTIFICATION);
    }
    
    public DataSubmissionListener startDataSubmissionListener(final int notificationId) {
        return new DataSubmissionListener() {
            // START - Submission Listening Hooks
            int totalItems = -1;
            long currentSize = -1;
            long totalSent = -1;
            Notification submissionNotification;
            
            int lastUpdate = 0;
            
            @Override
            public void beginSubmissionProcess(int totalItems) {
                this.totalItems = totalItems;
                
                String text = getSubmissionText(1, totalItems);
                
                // Set the icon, scrolling text and timestamp
                submissionNotification = new Notification(org.commcare.dalvik.R.drawable.notification, getTickerText(1, totalItems), System.currentTimeMillis());
                submissionNotification.flags |= (Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT);

                //We always want this click to simply bring the live stack back to the top
                Intent callable = new Intent(CommCareSessionService.this, CommCareHomeActivity.class);
                callable.setAction("android.intent.action.MAIN");
                callable.addCategory("android.intent.category.LAUNCHER");
                
                // The PendingIntent to launch our activity if the user selects this notification
                //TODO: Put something here that will, I dunno, cancel submission or something? Maybe show it live? 
                PendingIntent contentIntent = PendingIntent.getActivity(CommCareSessionService.this, 0, callable, 0);

                RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.submit_notification);
                contentView.setImageViewResource(R.id.image, R.drawable.notification);
                contentView.setTextViewText(R.id.submitTitle, getString(notificationId));
                contentView.setTextViewText(R.id.progressText, text);
                contentView.setTextViewText(R.id.submissionDetails,"0b transmitted");

                
                // Set the info for the views that show in the notification panel.
                submissionNotification.setLatestEventInfo(CommCareSessionService.this, getString(notificationId), text, contentIntent);
                
                submissionNotification.contentView = contentView;

                if(user != null) {
                    //Send the notification.
                    mNM.notify(notificationId, submissionNotification);
                }

            }

            @Override
            public void startSubmission(int itemNumber, long length) {
                currentSize = length;
                
                submissionNotification.contentView.setTextViewText(R.id.progressText, getSubmissionText(itemNumber + 1, totalItems));
                submissionNotification.contentView.setProgressBar(R.id.submissionProgress, 100, 0, false);
                mNM.notify(notificationId, submissionNotification);
            }

            @Override
            public void notifyProgress(int itemNumber, long progress) {
                int progressPercent = (int)Math.floor((progress * 1.0 / currentSize) * 100);
                
                if(progressPercent - lastUpdate > 5) {
                    
                    String progressDetails = "";
                    if(progress < 1024) {
                        progressDetails = progress + "b transmitted";
                    } else if (progress < 1024 * 1024) {
                        progressDetails =  String.format("%1$,.1f", (progress / 1024.0))+ "kb transmitted";
                    } else {
                        progressDetails = String.format("%1$,.1f", (progress / (1024.0 * 1024.0)))+ "mb transmitted";    
                    }
                    
                    int pending = ProcessAndSendTask.pending();
                    if(pending > 1) {
                        submissionNotification.contentView.setTextViewText(R.id.submissionsPending, pending -1 + " Pending");
                    }
                    
                    submissionNotification.contentView.setTextViewText(R.id.submissionDetails,progressDetails);
                    submissionNotification.contentView.setProgressBar(R.id.submissionProgress, 100, progressPercent, false);
                    mNM.notify(notificationId, submissionNotification);
                    lastUpdate = progressPercent;
                }
            }
            @Override
            public void endSubmissionProcess() {
                mNM.cancel(notificationId);
                submissionNotification = null;
                totalItems = -1;
                currentSize = -1;
                totalSent = -1;
                lastUpdate = 0;
            }
            
            private String getSubmissionText(int current, int total) {
                return current + "/" + total;
            }
            
            private String getTickerText(int current, int total) {
                return "CommCare submitting " + total +" forms";
            }
            
            // END - Submission Listening Hooks

        };
    }

    /**
     * Read the login session duration from app preferences and set the session
     * length accordingly.
     */
    private void setSessionLength(){
        sessionLength = CommCarePreferences.getLoginDuration() * 1000;
    }

    public void setCurrentUser(User user){
        this.user = user;
        this.key = user.getWrappedKey();
    }
}
