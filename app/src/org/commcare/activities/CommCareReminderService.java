package org.commcare.activities;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Alert;

import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;

import java.util.Vector;

/**
 * The CommCare Session Service is a persistent service which maintains
 * a CommCare login session 
 * 
 * @author ctsims
 *
 */
public class CommCareReminderService extends Service  {
    private NotificationManager mNM;
    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.drawable.notification;
    
    ReminderThread mReminderJob;
    
    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        public CommCareReminderService getService() {
            return CommCareReminderService.this;
        }
    }

    /*
     * (non-Javadoc)
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    }

    /*
     * (non-Javadoc)
     * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    /*
     * (non-Javadoc)
     * @see android.app.Service#onDestroy()
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Cancel the persistent notification.
        this.stopForeground(true); 
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

    public void showNotice(Vector<Alert> alerts) {
        //mNM.cancel(org.commcare.dalvik.R.string.expirenotification);
        
        //CharSequence text = "Session Expires: " + DateFormat.format("MMM dd h:mmaa", sessionExpireDate);

        //We always want this click to simply bring the live stack back to the top
        Intent callable = new Intent(this, CommCareHomeActivity.class);
        callable.setAction("android.intent.action.MAIN");
        callable.addCategory("android.intent.category.LAUNCHER");  

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, callable, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        builder.setAutoCancel(false);
        builder.setContentTitle("CommCare Reminder");
        builder.setSmallIcon(R.drawable.notification);
        builder.setContentIntent(contentIntent);
        builder.build();

        Notification notification = builder.getNotification();

        mReminderJob = new ReminderThread(CommCareApplication._());
        
        mReminderJob.startPolling(alerts);

        //Send the notification.
        this.startForeground(NOTIFICATION, notification);
    }
    
    public void stop() {
        mReminderJob.stopService();
        mReminderJob = null;
        this.stopForeground(true);
    }

}
