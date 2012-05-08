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

import org.commcare.android.R;
import org.commcare.android.activities.CommCareHomeActivity;
import org.commcare.android.activities.LoginActivity;
import org.commcare.android.models.User;
import org.commcare.android.tasks.FormSubmissionListener;
import org.commcare.android.tasks.ProcessAndSendTask;
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
import android.widget.RemoteViews;

/**
 * The CommCare Session Service is a persistent service which maintains
 * a CommCare login session 
 * 
 * @author ctsims
 *
 */
public class CommCareSessionService extends Service implements FormSubmissionListener {

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
    private int SUBMISSION_NOTIFICATION = org.commcare.android.R.string.submission_notification_title;
    
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
    private void showLoggedInNotification(User user) {
    	//mNM.cancel(org.commcare.android.R.string.expirenotification);
    	
        CharSequence text = "Session Expires: " + DateFormat.format("MMM dd h:mmaa", sessionExpireDate);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(org.commcare.android.R.drawable.notification, text, System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, CommCareHomeActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, this.getString(org.commcare.android.R.string.notificationtitle), text, contentIntent);

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
        showLoggedInNotification(user);
        
        maintenanceTimer = new Timer("CommCareService");
        maintenanceTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				maintenance();
			}
        	
        }, MAINTENANCE_PERIOD, MAINTENANCE_PERIOD);
	}
	
	private void maintenance() {
		boolean logout = false;
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
			user = null;
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
	

	// START - Submission Listening Hooks
	int totalItems = -1;
	long currentSize = -1;
	long totalSent = -1;
	Notification submissionNotification;
	
	int lastUpdate = 0;
	
	public void beginSubmissionProcess(int totalItems) {
		this.totalItems = totalItems;
		
		String text = getSubmissionText(1, totalItems);
		
        // Set the icon, scrolling text and timestamp
        submissionNotification = new Notification(org.commcare.android.R.drawable.notification, getTickerText(1, totalItems), System.currentTimeMillis());
        submissionNotification.flags |= (Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT);

        // The PendingIntent to launch our activity if the user selects this notification
        //TODO: Put something here that will, I dunno, cancel submission or something? Maybe show it live? 
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, CommCareHomeActivity.class), 0);

        RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.submit_notification);
        contentView.setImageViewResource(R.id.image, R.drawable.notification);
        contentView.setTextViewText(R.id.submitTitle, getString(org.commcare.android.R.string.submission_notification_title));
        contentView.setTextViewText(R.id.progressText, text);
		contentView.setTextViewText(R.id.submissionDetails,"0b transmitted");

        
        // Set the info for the views that show in the notification panel.
        submissionNotification.setLatestEventInfo(this, getString(org.commcare.android.R.string.submission_notification_title), text, contentIntent);
        
        submissionNotification.contentView = contentView;

        if(user != null) {
        	//Send the notification.
        	mNM.notify(SUBMISSION_NOTIFICATION, submissionNotification);
        }

	}

	public void startSubmission(int itemNumber, long length) {
		currentSize = length;
		
		submissionNotification.contentView.setTextViewText(R.id.progressText, getSubmissionText(itemNumber + 1, totalItems));
		submissionNotification.contentView.setProgressBar(R.id.submissionProgress, 100, 0, false);
		mNM.notify(SUBMISSION_NOTIFICATION, submissionNotification);
	}

	public void notifyProgress(int itemNumber, long progress) {
		int progressPercent = (int)Math.floor((progress * 1.0 / currentSize) * 100);
		
		if(progressPercent - lastUpdate > 5) {
			System.out.println("Updating progress with " + progress);
			
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
			mNM.notify(SUBMISSION_NOTIFICATION, submissionNotification);
			lastUpdate = progressPercent;
		}
	}

	public void endSubmissionProcess() {
		mNM.cancel(SUBMISSION_NOTIFICATION);
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
}
