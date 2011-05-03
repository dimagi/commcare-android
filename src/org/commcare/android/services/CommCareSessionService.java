/**
 * 
 */
package org.commcare.android.services;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.activities.CommCareHomeActivity;
import org.commcare.android.activities.LoginActivity;
import org.commcare.android.models.User;
import org.commcare.android.util.CryptUtil;
import org.commcare.android.util.SessionUnavailableException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.text.format.DateFormat;

/**
 * The CommCare Session Service is a persistent service which maintains
 * a CommCare login session 
 * 
 * @author ctsims
 *
 */
public class CommCareSessionService extends Service {

    private NotificationManager mNM;
    
    private static long MAINTENANCE_PERIOD = 1000;
    
    private static long SESSION_LENGTH = 1000*60*60*24;
    
    private Timer maintenanceTimer;

    private User user;
	private byte[] key = null;
	
    private Date sessionExpireDate;
    
    private String lock = "Lock";
    
    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = org.commcare.android.R.string.notificationtitle;
    
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
        
        // TODO: Create a notification which the user can click to restart the session 
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
    private void showLoggedInNotification() {
    	//mNM.cancel(org.commcare.android.R.string.expirenotification);
    	
        CharSequence text = "Session Expires: " + DateFormat.format("MMM dd h:mmaa", sessionExpireDate);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(org.commcare.android.R.drawable.notification, text, System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, CommCareHomeActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, this.getString(org.commcare.android.R.string.notificationtitle), text, contentIntent);

        // Send the notification.
        this.startForeground(NOTIFICATION, notification);
    }
    
    /*
     * Notify the user that they've been timed out and need to relog in
     */
    private void showLoggedOutNotification() {
    	
        this.stopForeground(true);
    	
    	String text = "Click here to log back into your session";
    	
        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(org.commcare.android.R.drawable.notification, text, System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        Intent i = new Intent(this, LoginActivity.class);
        
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i, notification.flags |= Notification.FLAG_AUTO_CANCEL);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, this.getString(org.commcare.android.R.string.expirenotification), text, contentIntent);

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);

    }

    //Start CommCare Specific Functionality
    
	public void logIn(byte[] symetricKey, User user) {
		this.key = symetricKey;
		this.user = user;
		
		this.sessionExpireDate = new Date(new Date().getTime() + SESSION_LENGTH);
		
        // Display a notification about us starting.  We put an icon in the status bar.
        showLoggedInNotification();
        
        maintenanceTimer = new Timer("CommCareService");
        maintenanceTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				maintenance();
			}
        	
        }, MAINTENANCE_PERIOD, MAINTENANCE_PERIOD);
	}
	
	private void maintenance() {
		boolean logout = false;;
		long time = new Date().getTime();
		// If we're either past the session expire time, or the session expires more than its period in the future, 
		// we need to log the user out
		if(time > sessionExpireDate.getTime() || (sessionExpireDate.getTime() - time  > SESSION_LENGTH )) { 
			logout = true;
		}
		
		if(logout) {
			logout();
			showLoggedOutNotification();
		}
	}
	
	public void logout() {
		synchronized(lock){
			key = null;
			maintenanceTimer.cancel();
	        this.stopForeground(true);
		}
	}
	
	public boolean isLoggedIn() {
		synchronized(lock){
			if(key == null) { return false;}
			return true;
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
	
	public Cipher getDecrypter() throws SessionUnavailableException{
		synchronized(lock){
			if(key == null) {
				throw new SessionUnavailableException();
			}
			
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
			return null;
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
	
}
