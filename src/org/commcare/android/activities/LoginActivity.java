/**
 * 
 */
package org.commcare.android.activities;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.Date;

import org.commcare.android.R;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.User;
import org.commcare.android.tasks.DataPullListener;
import org.commcare.android.tasks.DataPullTask;
import org.commcare.android.util.CryptUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.javarosa.core.model.utils.DateUtils;

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
				
				if(false) {
					Toast.makeText(LoginActivity.this, 
							"Login or Pasword are incorrect. Please try again.", 
							Toast.LENGTH_LONG).show();
					return;
				}
				
				//We should go digest auth this user on the server and see whether to pull them
				//down.
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this);
				
				String un = username.getText().toString();
				
				if(prefs.contains("cc_user_domain")) {
					un += "@" + prefs.getString("cc_user_domain",null);
				}
				
				dataPuller = new DataPullTask(un, 
						                             password.getText().toString(),
						                             prefs.getString("ota-restore-url",LoginActivity.this.getString(R.string.ota_restore_url)),
						                             prefs.getString("key_server",LoginActivity.this.getString(R.string.key_server)),
						                             LoginActivity.this);
				
				dataPuller.setPullListener(LoginActivity.this);
				dataPuller.execute();
				LoginActivity.this.showDialog(DIALOG_CHECKING_SERVER);
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
        if(CommCareApplication._().getSession().isLoggedIn()) {
    		Intent i = new Intent();
    		i.putExtra(ALREADY_LOGGED_IN, true);
            setResult(RESULT_OK, i);
            
    		finish();
    		return;
        }
        }catch(SessionUnavailableException sue) {
        	//Nothing, we're logging in here anyway
        }
        
        refreshView();
    }
    
    private void refreshView() {
    	passLabel.setText("Password:");
    	userLabel.setText("Username:");
    	login.setText("Log In");
    	autoUpdateConfig();
    }
    
    private boolean tryLocalLogin() {
    	try{
    	String passwd = password.getText().toString();
    	for(User u : storage()) {
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
        			logIn(u, key);
        			return true;
        		}
        	} else {
        		if(u.getPassword().equals(passwd)) {
        			byte[] key = CryptUtil.unWrapKey(u.getWrappedKey(), passwd);
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
			Toast.makeText(this, 
					"Authentication failed on server, please check credentials and try again.", 
					Toast.LENGTH_LONG).show();
			this.dismissDialog(DIALOG_CHECKING_SERVER);
			break;
		case DataPullTask.BAD_DATA:
			Toast.makeText(this, 
					"Server provided improperly formatted data, please contact your supervisor.", 
					Toast.LENGTH_LONG).show();
			this.dismissDialog(DIALOG_CHECKING_SERVER);
			break;
		case DataPullTask.DOWNLOAD_SUCCESS:
			if(tryLocalLogin()) {
				//success, don't need to do anything
				break;
			} else {
				Toast.makeText(this, 
						"Your information was fetched, but a problem occured with the log in. Please try again.", 
						Toast.LENGTH_LONG).show();
				break;
			}
		case DataPullTask.UNKNOWN_FAILURE:
			Toast.makeText(this, 
					"Unknown failure, please try again.", 
					Toast.LENGTH_LONG).show();
			this.dismissDialog(DIALOG_CHECKING_SERVER);
			break;
		}
	}

	public void progressUpdate(int progressCode) {
		if(progressCode == DataPullTask.PROGRESS_AUTHED) {
			mProgressDialog.setMessage("Server contacted, downloading data.");
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
                                dataPuller.cancel(true);
                            }
                        };
                mProgressDialog.setTitle("Communicating with Server");
                mProgressDialog.setMessage("Requesting Data...");
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton("cancel", loadingButtonListener);
                return mProgressDialog;
        }
        return null;
    }
    
}
