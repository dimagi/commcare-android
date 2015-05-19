package org.commcare.dalvik.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.text.format.DateFormat;
import android.widget.RemoteViews;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.crypt.CipherPool;
import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.CommCareUserOpenHelper;
import org.commcare.android.database.user.UserSandboxUtils;
import org.commcare.android.database.user.models.User;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.DataSubmissionListener;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.odk.collect.android.listeners.FormSaveCallback;
import org.commcare.dalvik.activities.LoginActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.services.Logger;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

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
    
    private static final long MAINTENANCE_PERIOD = 1000;
    // session length in MS
    private static long sessionLength = 1000 * 60 * 60 * 24;
    
    private Timer maintenanceTimer;
    private CipherPool pool;

    private byte[] key = null;
    
    private boolean multimediaIsVerified=false;
    
    private Date sessionExpireDate;
    
    private final Object lock = new Object();

    /**
     * Lock that must be obtained in order to close down the session.
     * NOTE PLM: Worth considering eventually removing this and refactoring
     * processes to be more easily interrupted/aborted.
     */
    public static final Object LOGOUT_LOCK = new Object();
    
    private User user;
    
    private SQLiteDatabase userDatabase; 
    
    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
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

    /*
     * (non-Javadoc)
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        setSessionLength();
        pool = new CipherPool() {

            /*
             * (non-Javadoc)
             * @see org.commcare.android.crypt.CipherPool#generateNewCipher()
             */
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
                }
                return null;
            }
            
        };
    }

    /*
     * (non-Javadoc)
     * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    /*
     * (non-Javadoc)
     * @see android.app.Service#onDestroy()
     */
    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        this.stopForeground(true);
        
        // TODO: Create a notification which the user can click to restart the session 
    }

    /*
     * (non-Javadoc)
     * @see android.app.Service#onBind(android.content.Intent)
     */
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
        //mNM.cancel(org.commcare.dalvik.R.string.expirenotification);
        
        CharSequence text = "Session Expires: " + DateFormat.format("MMM dd h:mmaa", sessionExpireDate);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(org.commcare.dalvik.R.drawable.notification, text, System.currentTimeMillis());

        //We always want this click to simply bring the live stack back to the top
        Intent callable = new Intent(this, CommCareHomeActivity.class);
        callable.setAction("android.intent.action.MAIN");
        callable.addCategory("android.intent.category.LAUNCHER");  

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, callable, 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, this.getString(org.commcare.dalvik.R.string.notificationtitle), text, contentIntent);

        if(user != null) {
            //Send the notification.
            this.startForeground(NOTIFICATION, notification);
        }
    }
    
    /*
     * Notify the user that they've been timed out and need to relog in
     */
    private void showLoggedOutNotification() {
        
        this.stopForeground(true);
        
        String text = "Click here to log back into your session";
        
        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(org.commcare.dalvik.R.drawable.notification, text, System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        Intent i = new Intent(this, LoginActivity.class);
        
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i, notification.flags |= Notification.FLAG_AUTO_CANCEL);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, this.getString(org.commcare.dalvik.R.string.expirenotification), text, contentIntent);

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
    
                /*
                 * (non-Javadoc)
                 * @see java.util.TimerTask#run()
                 */
                @Override
                public void run() {
                    maintenance();
                }
                
            }, MAINTENANCE_PERIOD, MAINTENANCE_PERIOD);
        }
    }
    
    private void maintenance() {
        long time = new Date().getTime();
        // If logout process started and has taken longer than the logout
        // timeout then wrap-up the process. This is especially necessary since
        // if the FormEntryActivity  isn't active then it will never launch
        // closeSession upon receiving the KEY_SESSION_ENDING broadcast
        if (logoutStartedAt != -1 &&
                time > (logoutStartedAt + LOGOUT_TIMEOUT)) {
            closeSession(true);
        }

        // If we haven't started closing the session and we're either past the
        // session expire time, or the session expires more than its period in
        // the future, we need to log the user out. The second case occurs if
        // the system's clock is altered.
        if (isActive() &&
                logoutStartedAt == -1 &&
                (time > sessionExpireDate.getTime() || 
                 (sessionExpireDate.getTime() - time  > sessionLength))) {
            logoutStartedAt = new Date().getTime();
            saveFormAndCloseSession();

            showLoggedOutNotification();
        }
    }

    /**
     * Notify any open form that it needs to save, then close the key session
     * after waiting for the form save to complete/timeout.
     */
    private void saveFormAndCloseSession() {
        // Remember when we started so that if form saving takes too long, the
        // maintenance timer will launch closeSession
        logoutStartedAt = new Date().getTime();

        // save form progress, if any
        synchronized(lock) {
            if (formSaver != null) {
                formSaver.formSaveCallback();
            } else {
                closeSession(true);
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
     * Closes the key pool and user database. Performs CommCareApplication
     * logout to unbind its connection to this object.
     *
     * @param sessionExpired should the user be redirected to the login screen
     *                       upon closing this session?
     */
    public void closeSession(boolean sessionExpired) {
        synchronized (lock) {
            synchronized (CommCareSessionService.LOGOUT_LOCK) {
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

                CommCareApplication._().logout();

                pool.expire();
                this.stopForeground(true);

                if (sessionExpired) {
                    // Re-direct to the home screen
                    Intent loginIntent = new Intent(this, CommCareHomeActivity.class);
                    // TODO: instead of launching here, which will pop-up the login
                    // screen even if CommCare isn't in the foreground, we should
                    // broadcast an intent, which CommCareActivity can receive if
                    // in focus and dispatch the login activity. Will also need to
                    // extend CommCareActivity's onResume to check if we need to
                    // re-login when we bring CommCare back into the foreground, so
                    // that the user can't just continue doing work while logged
                    // out. -- PLM
                    loginIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(loginIntent);
                }
            }
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
            
            /*
             * (non-Javadoc)
             * @see org.commcare.android.tasks.DataSubmissionListener#beginSubmissionProcess(int)
             */
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

            /*
             * (non-Javadoc)
             * @see org.commcare.android.tasks.DataSubmissionListener#startSubmission(int, long)
             */
            @Override
            public void startSubmission(int itemNumber, long length) {
                currentSize = length;
                
                submissionNotification.contentView.setTextViewText(R.id.progressText, getSubmissionText(itemNumber + 1, totalItems));
                submissionNotification.contentView.setProgressBar(R.id.submissionProgress, 100, 0, false);
                mNM.notify(notificationId, submissionNotification);
            }

            /*
             * (non-Javadoc)
             * @see org.commcare.android.tasks.DataSubmissionListener#notifyProgress(int, long)
             */
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
            /*
             * (non-Javadoc)
             * @see org.commcare.android.tasks.DataSubmissionListener#endSubmissionProcess()
             */
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

    public boolean isMultimediaVerified(){
        return multimediaIsVerified;
    }
    
    public void setMultiMediaVerified(boolean toggle){
        multimediaIsVerified = toggle;
    }
}
