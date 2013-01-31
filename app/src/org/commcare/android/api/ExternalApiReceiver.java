/**
 * 
 */
package org.commcare.android.api;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.NoSuchElementException;
import java.util.Vector;

import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.AndroidSharedKeyRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.User;
import org.commcare.android.db.legacy.LegacyInstallUtils;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.android.tasks.DataPullListener;
import org.commcare.android.tasks.DataPullTask;
import org.commcare.android.tasks.ManageKeyRecordTask;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.tasks.ProcessTaskListener;
import org.commcare.android.tasks.templates.HttpCalloutTask.HttpCalloutOutcomes;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Broadcast receiver to clear pending notifications.
 * 
 * @author ctsims
 *
 */
public class ExternalApiReceiver extends BroadcastReceiver {

	/* (non-Javadoc)
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		if(!intent.hasExtra(AndroidSharedKeyRecord.EXTRA_KEY_ID)) {
			return;
		}
		
		String keyId = intent.getStringExtra(AndroidSharedKeyRecord.EXTRA_KEY_ID);
		SqlIndexedStorageUtility<AndroidSharedKeyRecord> storage = CommCareApplication._().getGlobalStorage(AndroidSharedKeyRecord.class);
		AndroidSharedKeyRecord sharingKey;
		try {
			sharingKey = storage.getRecordForValue(AndroidSharedKeyRecord.META_KEY_ID, keyId);
		} catch(NoSuchElementException nsee) {
			//No valid key record;
			return;
		}
		
		Bundle b = sharingKey.getIncomingCallout(intent);
		
		performAction(context, b);
	}

	private void performAction(final Context context, Bundle b) {
		if(b.getString("commcareaction").equals("login")) {
			String username = b.getString("username");
			String password = b.getString("password");
			tryLocalLogin(context, username, password);
		} else if(b.getString("commcareaction").equals("sync")) {
        	
            boolean formsToSend = checkAndStartUnsentTask(context, new ProcessTaskListener() {

				public void processTaskAllProcessed() {
					//Don't cancel the dialog, we need it to stay in the foreground to ensure things are set
				}
            	
            	public void processAndSendFinished(int result, int successfulSends) {
            		if(result == ProcessAndSendTask.FULL_SUCCESS) {
            			//OK, all forms sent, sync time 
            			syncData(context);
            			
            		} else if(result == ProcessAndSendTask.FAILURE) {
            			Toast.makeText(context, Localization.get("sync.fail.unsent"), Toast.LENGTH_LONG).show();
            		} else  {
            			Toast.makeText(context, Localization.get("sync.fail.unsent"), Toast.LENGTH_LONG).show();
            		}
            	}

            });
            
            if(!formsToSend) {
            	//No unsent forms, just sync
            	syncData(context);
            }
            

		}
	}
	
    
    protected boolean checkAndStartUnsentTask(Context c, ProcessTaskListener listener) throws SessionUnavailableException {
    	SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
    	
    	//Get all forms which are either unsent or unprocessed
    	Vector<Integer> ids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_UNSENT});
    	ids.addAll(storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_COMPLETE}));
    	if(ids.size() > 0) {
    		FormRecord[] records = new FormRecord[ids.size()];
    		for(int i = 0 ; i < ids.size() ; ++i) {
    			records[i] = storage.read(ids.elementAt(i).intValue());
    		}
    		SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
    		ProcessAndSendTask mProcess = new ProcessAndSendTask(c, CommCareApplication._().getCurrentApp().getCommCarePlatform(), settings.getString("PostURL", c.getString(R.string.PostURL)));
    		mProcess.setListeners(listener, CommCareApplication._().getSession().startDataSubmissionListener());
    		mProcess.execute(records);
    		return true;
    	} else {
    		//Nothing.
    		return false;
    	}
    }
    
    private void syncData(final Context c) {
    	User u = CommCareApplication._().getSession().getLoggedInUser();
    	
		SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();

		DataPullTask mDataPullTask = new DataPullTask(u.getUsername(), u.getCachedPwd(), prefs.getString("ota-restore-url",c.getString(R.string.ota_restore_url)), "", c);
		mDataPullTask.setPullListener(new DataPullListener() {

			@Override
			public void finished(int status) {
				if(status != DataPullTask.DOWNLOAD_SUCCESS) {
					Toast.makeText(c, "CommCare couldn't sync. Please try to sync from CommCare directly for more information", Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(c, "CommCare synced!", Toast.LENGTH_LONG).show();
				}
			}

			@Override
			public void progressUpdate(Integer... progress) {
				// TODO Auto-generated method stub
				
			}
			
		});
    	
    	mDataPullTask.execute();
    	CommCareApplication._().getSession().registerCurrentTask(mDataPullTask, "Sync");
    }

	
    private boolean tryLocalLogin(Context context, String uname, String password) {
    	try{
	    	UserKeyRecord matchingRecord = null;
	    	for(UserKeyRecord record : CommCareApplication._().getCurrentApp().getStorage(UserKeyRecord.class)) {
	    		if(!record.getUsername().equals(uname)) {
	    			continue;
	    		}
	    		String hash = record.getPasswordHash();
	    		if(hash.contains("$")) {
	        		String alg = "sha1";
	        		String salt = hash.split("\\$")[1];
	        		String check = hash.split("\\$")[2];
	        		MessageDigest md = MessageDigest.getInstance("SHA-1");
	        		BigInteger number = new BigInteger(1, md.digest((salt+password).getBytes()));
	        		String hashed = number.toString(16);
	        		
	        		while(hashed.length() < check.length()) {
	        			hashed = "0" + hashed;
	        		}
	        		
	        		if(hash.equals(alg + "$" + salt + "$" + hashed)) {
	        			matchingRecord = record;
	        		}
	        	}
	    	}
	    	
	    	if(matchingRecord == null) {
	    		return false;
	    	}
	    	//TODO: Extract this
			byte[] key = CryptUtil.unWrapKey(matchingRecord.getEncryptedKey(), password);
			if(matchingRecord.getType() == UserKeyRecord.TYPE_LEGACY_TRANSITION) {
				LegacyInstallUtils.transitionLegacyUserStorage(context, CommCareApplication._().getCurrentApp(), key, matchingRecord);
			}
			//TODO: See if it worked first?
			
			CommCareApplication._().logIn(key, matchingRecord);
			new ManageKeyRecordTask(context, matchingRecord.getUsername(), password) {

				/* (non-Javadoc)
				 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
				 */
				@Override
				protected void onPostExecute(HttpCalloutOutcomes result) {
					super.onPostExecute(result);
					if(this.proceed) {
						//something
					} else {
						//something else
					}
				}
				
			}.execute();
			
			return true;
    	}catch (Exception e) {
    		e.printStackTrace();
    		return false;
    	}
    }


}
