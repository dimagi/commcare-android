/**
 * 
 */
package org.commcare.android.activities;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import org.commcare.android.R;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.User;
import org.commcare.android.tasks.DataPullListener;
import org.commcare.android.tasks.DataPullTask;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
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
				User u = tryLocalLogin();
				if(u != null) {
					Intent i = new Intent();
			        i.putExtra(GlobalConstants.STATE_USER_KEY, u.getUniqueId());
			        i.putExtra(GlobalConstants.STATE_USER_LOGIN, new Date());
			        setResult(RESULT_OK, i);
			        
					finish();
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
				
				dataPuller = new DataPullTask(username.getText().toString(), 
						                             password.getText().toString(),
						                             GlobalConstants.OTA_SERVER,
						                             LoginActivity.this);
				
				dataPuller.setPullListener(LoginActivity.this);
				dataPuller.execute();
				LoginActivity.this.showDialog(DIALOG_CHECKING_SERVER);
			}
        });
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        refreshView();
    }
    
    private void refreshView() {
    	passLabel.setText("Password:");
    	userLabel.setText("Username:");
    	login.setText("Log In");
    	checkServer.setText("Check Server");
    }
    
    private User tryLocalLogin() {
    	try{
    	String passwd = password.getText().toString();
    	for(User u : storage()) {
    		String hash = u.getPassword();
    		if(hash.contains("$")) {
        		String alg = "sha1";
        		String salt = hash.split("\\$")[1];
        		MessageDigest md = MessageDigest.getInstance("SHA-1");
        		BigInteger number = new BigInteger(1, md.digest((salt+passwd).getBytes()));
        		String hashed = number.toString(16);
        		
        		if(hash.equals(alg + "$" + salt + "$" + hashed)) {
        			return u;
        		}
        	} else {
        		if(u.getPassword().equals(passwd)) {
        			return u;
    			}
        	}
    	}
    	return null;
    	}catch (Exception e) {
    		e.printStackTrace();
    		return null;
    	}
    }
    
    private SqlIndexedStorageUtility<User> storage() {
    	if(storage == null) {
    		storage= new SqlIndexedStorageUtility<User>(User.STORAGE_KEY, User.class, this);
    	}
    	return storage;
    }

	public void finished(int status) {
		switch(status) {
		case DataPullTask.AUTH_FAILED:
			Toast.makeText(this, 
					"Authentication failed on server, please check credentials and try again.", 
					Toast.LENGTH_LONG).show();
			break;
		case DataPullTask.BAD_DATA:
			Toast.makeText(this, 
					"Server provided improperly formatted data, pleaes contact your supervisor.", 
					Toast.LENGTH_LONG).show();
			break;
		case DataPullTask.DOWNLOAD_SUCCESS:
			User u = tryLocalLogin();
			if(u != null) {
				Intent i = new Intent();
		        i.putExtra(GlobalConstants.STATE_USER_KEY, u.getUniqueId());
		        i.putExtra(GlobalConstants.STATE_USER_LOGIN, new Date());
		        setResult(RESULT_OK, i);
		        
				finish();
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
