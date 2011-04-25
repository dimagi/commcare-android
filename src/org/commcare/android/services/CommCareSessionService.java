/**
 * 
 */
package org.commcare.android.services;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.activities.CommCareHomeActivity;
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
    
    private Date loginDate;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = org.commcare.android.R.string.notificationtitle;
    
	private byte[] key = null;
    
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
        CharSequence text = "Session Expires: " + DateFormat.format("MMM dd h:mmaa", loginDate);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(org.commcare.android.R.drawable.notification, text, System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, CommCareHomeActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, this.getString(org.commcare.android.R.string.notificationtitle), text, contentIntent);

        // Send the notification.
        this.startForeground(NOTIFICATION, notification);
    }

    //Start CommCare Specific Functionality
    
	public void logIn(byte[] symetricKey) {
		this.key = symetricKey;
		
		this.loginDate = new Date(new Date().getTime() + 1000*60*60*24);
		
        // Display a notification about us starting.  We put an icon in the status bar.
        showLoggedInNotification();
	}
	
	public void logout() {
		key = null;
	}
	
	public boolean isLoggedIn() {
		if(key == null) { return false;}
		return true;
	}
	
	public Cipher getEncrypter() throws SessionUnavailableException {
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
	
	public Cipher getDecrypter() throws SessionUnavailableException{
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
	
	public SecretKey createNewSymetricKey() {
		return CryptUtil.generateSymetricKey(CryptUtil.uniqueSeedFromSecureStatic(key));
	}
}
