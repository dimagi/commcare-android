/**
 * 
 */
package org.commcare.dalvik.activities;

import java.math.BigInteger;
import java.security.MessageDigest;

import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.models.User;
import org.commcare.android.db.legacy.LegacyInstallUtils;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.android.tasks.DataPullListener;
import org.commcare.android.tasks.DataPullTask;
import org.commcare.android.tasks.ManageKeyRecordTask;
import org.commcare.android.tasks.templates.HttpCalloutTask.HttpCalloutOutcomes;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Button;
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
	TextView errorBox;
	
	EditText username;
	EditText password;
	View banner;
	
	TextView versionDisplay;
	
	public static final int DIALOG_CHECKING_SERVER = 0;
	
	SqlIndexedStorageUtility<UserKeyRecord> storage;
	
	DataPullTask dataPuller;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        currentActivity = this;
        
        setContentView(R.layout.screen_login);
        
        login = (Button)findViewById(R.id.login_button);
        
        userLabel = (TextView)findViewById(R.id.text_username);
        
        passLabel = (TextView)findViewById(R.id.text_password);
        
        username = (EditText)findViewById(R.id.edit_username);
        username.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        
        password = (EditText)findViewById(R.id.edit_password);
        
        banner = findViewById(R.id.screen_login_banner_pane);
        errorBox = (TextView)this.findViewById(R.id.screen_login_bad_password);

        try{
        	setTitle(getString(R.string.application_name) + " > " + Localization.get("app.display.name"));
        } catch(NoLocalizedTextException nlte) {
        	//nothing, app display name is optional for now.
        }
        
        //Only on the initial creation
        if(savedInstanceState ==null) {
        	String lastUser = CommCareApplication._().getCurrentApp().getAppPreferences().getString(CommCarePreferences.LAST_LOGGED_IN_USER, null);
        	if(lastUser != null) {
        		username.setText(lastUser);
        		password.requestFocus();
        	}
        }
        LoginActivity oldThis = (LoginActivity)this.getLastNonConfigurationInstance();
        if(oldThis != null) {
        	this.errorBox.setVisibility(oldThis.errorBox.getVisibility());
        	this.errorBox.setText(oldThis.errorBox.getText());
        }
        
        login.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				errorBox.setVisibility(View.GONE);
				//Try logging in locally
				if(tryLocalLogin()) {
					return;
				}

				startOta();
			}
        });
        
        versionDisplay = (TextView)findViewById(R.id.str_version);
        versionDisplay.setText(CommCareApplication._().getCurrentVersionString());
        
        
        final View activityRootView = findViewById(R.id.screen_login_main);
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
            	int hideAll = LoginActivity.this.getResources().getInteger(R.integer.login_screen_hide_all_cuttoff);
            	int hideBanner = LoginActivity.this.getResources().getInteger(R.integer.login_screen_hide_banner_cuttoff);
                int height = activityRootView.getHeight();
                
                if(height < hideAll) {
                	versionDisplay.setVisibility(View.GONE);
                	banner.setVisibility(View.GONE);
                } else if(height < hideBanner) {
                	versionDisplay.setVisibility(View.VISIBLE);
                	banner.setVisibility(View.GONE);
                }  else {
                	versionDisplay.setVisibility(View.VISIBLE);
                	banner.setVisibility(View.VISIBLE);
                }
             }
        });
    }

	private void startOta() {
		
		//We should go digest auth this user on the server and see whether to pull them
		//down.
		SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
		
		// TODO Auto-generated method stub
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

    /* (non-Javadoc)
	 * @see android.app.Activity#onRetainNonConfigurationInstance()
	 */
	@Override
	public Object onRetainNonConfigurationInstance() {
		return this;
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
        	//TODO: there is a weird circumstance where we're logging in somewhere else and this gets locked.
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
    }
    
    private String getUsername() {
    	return username.getText().toString().toLowerCase().trim();
    }
    
    private boolean tryLocalLogin() {
    	return tryLocalLogin(false);
    }
    	
    private boolean tryLocalLogin(final boolean warnMultipleAccounts) {
    	try{
	    	String passwd = password.getText().toString();
	    	UserKeyRecord matchingRecord = null;
	    	int count = 0;
	    	for(UserKeyRecord record : storage()) {
	    		if(!record.getUsername().equals(getUsername())) {
	    			continue;
	    		}
	    		count++;
	    		String hash = record.getPasswordHash();
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
	        			matchingRecord = record;
	        		}
	        	}
	    	}
	    	
	    	final boolean triggerTooManyUsers = count > 1 && warnMultipleAccounts;
	    	
	    	if(matchingRecord == null) {
	    		return false;
	    	}
	    	//TODO: Extract this
			byte[] key = CryptUtil.unWrapKey(matchingRecord.getEncryptedKey(), passwd);
			if(matchingRecord.getType() == UserKeyRecord.TYPE_LEGACY_TRANSITION) {
				LegacyInstallUtils.transitionLegacyUserStorage(this, CommCareApplication._().getCurrentApp(), key, matchingRecord);
			}
			//TODO: See if it worked first?
			
			CommCareApplication._().logIn(key, matchingRecord);
			final String username = matchingRecord.getUsername();
			new ManageKeyRecordTask(this, matchingRecord.getUsername(), passwd) {

				/* (non-Javadoc)
				 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
				 */
				@Override
				protected void onPostExecute(HttpCalloutOutcomes result) {
					super.onPostExecute(result);
					if(this.proceed) {
						
						if(triggerTooManyUsers) {
							//We've successfully pulled down new user data. 
							//Should see if the user already has a sandbox and let them know that their old data doesn't transition
							raiseMessage(NotificationMessageFactory.message(StockMessages.Auth_RemoteCredentialsChanged, new String[3]), true);
							Logger.log(AndroidLogger.TYPE_USER, "User " + username + " has logged in for the first time with a new password. They may have unsent data in their other sandbox");
						}
						
						done();
					} else {
						//Need to fetch!
						startOta();
					}
				}
				
			}.execute();
			
			return true;
    	}catch (Exception e) {
    		e.printStackTrace();
    		return false;
    	}
    }
    
    private void done() {
		Intent i = new Intent();
        setResult(RESULT_OK, i);
     
        CommCareApplication._().clearNotifications(NOTIFICATION_MESSAGE_LOGIN);
		finish();
    }
    
    private SqlIndexedStorageUtility<UserKeyRecord> storage() throws SessionUnavailableException{
    	if(storage == null) {
    		storage=  CommCareApplication._().getAppStorage(UserKeyRecord.class);
    	}
    	return storage;
    }

	public void finished(int status) {
		switch(status) {
		case DataPullTask.AUTH_FAILED:
			raiseMessage(NotificationMessageFactory.message(StockMessages.Auth_BadCredentials, new String[3], NOTIFICATION_MESSAGE_LOGIN), false);
			currentActivity.dismissDialog(DIALOG_CHECKING_SERVER);
			break;
		case DataPullTask.BAD_DATA:
			raiseMessage(NotificationMessageFactory.message(StockMessages.Remote_BadRestore, new String[3], NOTIFICATION_MESSAGE_LOGIN));

			currentActivity.dismissDialog(DIALOG_CHECKING_SERVER);
			break;
		case DataPullTask.DOWNLOAD_SUCCESS:
			if(!tryLocalLogin(true)) {
				raiseMessage(NotificationMessageFactory.message(StockMessages.Auth_CredentialMismatch, new String[3], NOTIFICATION_MESSAGE_LOGIN));
			} else {
				break;
			}
		case DataPullTask.UNREACHABLE_HOST:
			raiseMessage(NotificationMessageFactory.message(StockMessages.Remote_NoNetwork, new String[3], NOTIFICATION_MESSAGE_LOGIN));
			currentActivity.dismissDialog(DIALOG_CHECKING_SERVER);
			break;
		case DataPullTask.UNKNOWN_FAILURE:
			raiseMessage(NotificationMessageFactory.message(StockMessages.Restore_Unknown, new String[3], NOTIFICATION_MESSAGE_LOGIN));
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
    	raiseMessage(message, true);
    }
    
    private void raiseMessage(NotificationMessage message, boolean showTop) {
    	String toastText = message.getTitle();
    	if(showTop) {
    		CommCareApplication._().reportNotificationMessage(message);
    		toastText = Localization.get("notification.for.details.wrapper", new String[] {toastText});
    	} else {
    		errorBox.setVisibility(View.VISIBLE);
    		errorBox.setText(message.getTitle());
    	}
    	
		Toast.makeText(this,toastText, Toast.LENGTH_LONG).show();
    }
    
    public final static String NOTIFICATION_MESSAGE_LOGIN = "login_message";
}
