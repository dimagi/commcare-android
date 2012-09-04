/**
 * 
 */
package org.commcare.dalvik.activities;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.Date;

import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.User;
import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.android.tasks.DataPullListener;
import org.commcare.android.tasks.DataPullTask;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.android.util.CryptUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.locale.Localization;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author ctsims
 *
 */
public class LoginActivity extends Activity implements DataPullListener {
	
	public static String ALREADY_LOGGED_IN = "la_loggedin";
	
	ProgressDialog mProgressDialog;
	
	private static LoginActivity currentActivity;
	
	Button login;
	
	TextView userLabel;
	TextView passLabel;
	
	EditText username;
	EditText password;
	
	CheckBox checkServer;
	
	public static final int DIALOG_CHECKING_SERVER = 0;
	
	SqlIndexedStorageUtility<User> storage;
	
	DataPullTask dataPuller;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        currentActivity = this;
        
        setContentView(R.layout.login);
        
        login = (Button)findViewById(R.id.login_button);
        
        userLabel = (TextView)findViewById(R.id.text_username);
        
        passLabel = (TextView)findViewById(R.id.text_password);
        
        username = (EditText)findViewById(R.id.edit_username);
        
        password = (EditText)findViewById(R.id.edit_password);
        
        checkServer = (CheckBox)findViewById(R.id.checkserver);
        
        login.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				//If they don't manually want to check the server, try logging in locally
				if(!checkServer.isChecked() && tryLocalLogin()) {
					return;
				}
				
				//We should go digest auth this user on the server and see whether to pull them
				//down.
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this);

				//TODO: we don't actually always want to do this. We need to have an alternate route where we log in locally and sync 
				//(with unsent form submissions) more centrally.
				
				dataPuller = new DataPullTask(getUsername(), 
						                             password.getText().toString(),
						                             prefs.getString("ota-restore-url",LoginActivity.this.getString(R.string.ota_restore_url)),
						                             prefs.getString("key_server",LoginActivity.this.getString(R.string.key_server)),
						                             LoginActivity.this);
				
				dataPuller.setPullListener(LoginActivity.this);
				LoginActivity.this.showDialog(DIALOG_CHECKING_SERVER);
				dataPuller.execute();
			}
        });
        
        TextView versionDisplay = (TextView)findViewById(R.id.str_version);
        versionDisplay.setText(CommCareApplication._().getCurrentVersionString());
    }
    
    private void autoUpdateConfig() {
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this);
    	
    	if(!"true".equals(prefs.getString("cc-auto-update","false"))) { return;}
		
		long now = new Date().getTime();
		
		long lastRestore = prefs.getLong("last-ota-restore", 0);
		Calendar lastRestoreCalendar = Calendar.getInstance();
		lastRestoreCalendar.setTimeInMillis(lastRestore);
		
		if(now - lastRestore > DateUtils.DAY_IN_MS || (lastRestoreCalendar.get(Calendar.DAY_OF_WEEK) != Calendar.getInstance().get(Calendar.DAY_OF_WEEK))) {
			//If it's been more than 24 hrs since the last update or if it's the next day. 
			checkServer.setChecked(true);
			checkServer.setEnabled(false);
		}
		checkServer.setText(Localization.get("login.sync"));
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        
        try {
        	//TODO: there is a weirde circumstance where we're logging in somewhere else and this gets locked.
        if(CommCareApplication._().getSession().isLoggedIn() && CommCareApplication._().getSession().getLoggedInUser() != null) {
    		Intent i = new Intent();
    		i.putExtra(ALREADY_LOGGED_IN, true);
            setResult(RESULT_OK, i);
            
            CommCareApplication._().clearNotifications(NOTIFICATION_MESSAGE_LOGIN);
    		finish();
    		return;
        }
        }catch(SessionUnavailableException sue) {
        	//Nothing, we're logging in here anyway
        }
        
        refreshView();
    }
    
    private void refreshView() {
    	userLabel.setText(Localization.get("login.username"));
    	passLabel.setText(Localization.get("login.password"));
    	login.setText(Localization.get("login.button"));
    	autoUpdateConfig();
    }
    
    private String getUsername() {
    	return username.getText().toString().toLowerCase().trim();
    }
    
    private boolean tryLocalLogin() {
    	try{
    	String passwd = password.getText().toString();
    	for(User u : storage()) {
    		if(!u.getUsername().equals(getUsername())) {
    			continue;
    		}
    		String hash = u.getPassword();
    		if(hash.contains("$")) {
        		String alg = "sha1";
        		String salt = hash.split("\\$")[1];
        		String check = hash.split("\\$")[2];
        		MessageDigest md = MessageDigest.getInstance("SHA-1");
        		BigInteger number = new BigInteger(1, md.digest((salt+passwd).getBytes()));
        		String hashed = number.toString(16);
        		
        		while(hashed.length() < check.length()) {
        			hashed = "0" + hashed;
        		}
        		
        		if(hash.equals(alg + "$" + salt + "$" + hashed)) {
        			byte[] key = CryptUtil.unWrapKey(u.getWrappedKey(), passwd);
        			u.setCachedPwd(passwd);
        			logIn(u, key);
        			return true;
        		}
        	} else {
        		if(u.getPassword().equals(passwd)) {
        			byte[] key = CryptUtil.unWrapKey(u.getWrappedKey(), passwd);
        			u.setCachedPwd(passwd);
        			logIn(u, key);
        			return true;
    			}
        	}
    	}
    	return false;
    	}catch (Exception e) {
    		e.printStackTrace();
    		return false;
    	}
    }
    
    private void logIn(User u, byte[] key) {
    	CommCareApplication._().logIn(key, u);
    	
		Intent i = new Intent();
        setResult(RESULT_OK, i);
     
        CommCareApplication._().clearNotifications(NOTIFICATION_MESSAGE_LOGIN);
		finish();
    }
    
    private SqlIndexedStorageUtility<User> storage() throws SessionUnavailableException{
    	if(storage == null) {
    		storage=  CommCareApplication._().getStorage(User.STORAGE_KEY, User.class);
    	}
    	return storage;
    }

	public void finished(int status) {
		switch(status) {
		case DataPullTask.AUTH_FAILED:
			raiseMessage(NotificationMessageFactory.message(StockMessages.Auth_BadCredentials, NOTIFICATION_MESSAGE_LOGIN));
			currentActivity.dismissDialog(DIALOG_CHECKING_SERVER);
			break;
		case DataPullTask.BAD_DATA:
			raiseMessage(NotificationMessageFactory.message(StockMessages.Remote_BadRestore, NOTIFICATION_MESSAGE_LOGIN));

			currentActivity.dismissDialog(DIALOG_CHECKING_SERVER);
			break;
		case DataPullTask.DOWNLOAD_SUCCESS:
			if(tryLocalLogin()) {
				//success, don't need to do anything
				break;
			} else {
				raiseMessage(NotificationMessageFactory.message(StockMessages.Auth_RemoteCredentialsChanged, NOTIFICATION_MESSAGE_LOGIN));
				break;
			}
		case DataPullTask.UNREACHABLE_HOST:
			raiseMessage(NotificationMessageFactory.message(StockMessages.Remote_NoNetwork, NOTIFICATION_MESSAGE_LOGIN));
			currentActivity.dismissDialog(DIALOG_CHECKING_SERVER);
			break;
		case DataPullTask.UNKNOWN_FAILURE:
			raiseMessage(NotificationMessageFactory.message(StockMessages.Restore_Unknown, NOTIFICATION_MESSAGE_LOGIN));
			currentActivity.dismissDialog(DIALOG_CHECKING_SERVER);
			break;
		}
	}

	public void progressUpdate(Integer ... progress) {
		//TODO: Unify this into some progress dialog or something? We reuse in a couple of places
		if(progress[0] == DataPullTask.PROGRESS_STARTED) {
			mProgressDialog.setMessage(Localization.get("sync.progress.purge"));
		} else if(progress[0] == DataPullTask.PROGRESS_CLEANED) {
			mProgressDialog.setMessage(Localization.get("sync.progress.authing"));
		} else if(progress[0] == DataPullTask.PROGRESS_AUTHED) {
			mProgressDialog.setMessage(Localization.get("sync.progress.downloading"));
		} else if(progress[0] == DataPullTask.PROGRESS_RECOVERY_NEEDED) {
			mProgressDialog.setMessage(Localization.get("sync.recover.needed"));
		} else if(progress[0] == DataPullTask.PROGRESS_RECOVERY_STARTED) {
			mProgressDialog.setMessage(Localization.get("sync.recover.started"));
		}
		
	}
    
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onCreateDialog(int)
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_CHECKING_SERVER:
                mProgressDialog = new ProgressDialog(this);
                DialogInterface.OnClickListener loadingButtonListener =
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                //If it is null, this is tricky to recover from? 
                                if(dataPuller != null) {
                                	dataPuller.cancel(true);
                                }
                            }
                        };
                mProgressDialog.setTitle(Localization.get("sync.progress.title"));
                mProgressDialog.setMessage(Localization.get("sync.progress.starting"));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(Localization.get("option.cancel"), loadingButtonListener);
                return mProgressDialog;
        }
        return null;
    }
    
    private void raiseMessage(NotificationMessage message) {
    	CommCareApplication._().reportNotificationMessage(message);
		Toast.makeText(this, message.getTitle(), Toast.LENGTH_LONG).show();
    }
    
    public final static String NOTIFICATION_MESSAGE_LOGIN = "login_message";
}
