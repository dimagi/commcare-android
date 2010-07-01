package org.commcare.android.activities;

import org.commcare.android.R;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.preferences.ServerPreferences;
import org.commcare.android.providers.PreloadContentProvider;
import org.commcare.android.tasks.ProcessAndSendListener;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.CommCarePlatformProvider;
import org.commcare.suite.model.Entry;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class CommCareHomeActivity extends Activity implements ProcessAndSendListener {
	public static final int LOGIN_USER = 0;
	public static final int GET_COMMAND = 1;
	public static final int GET_CASE = 2;
	public static final int MODEL_RESULT = 4;
	
	public static final int DIALOG_PROCESS = 0;
	
	View homeScreen;
	
	private AndroidCommCarePlatform platform;
	
	ProgressDialog mProgressDialog;
	
	ProcessAndSendTask mProcess;
	
	Button startButton;
	Button logoutButton;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        platform = CommCarePlatformProvider.unpack(savedInstanceState, this);
        
        // enter data button. expects a result.
        startButton = (Button) findViewById(R.id.start);
        startButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	
                Intent i = new Intent(getApplicationContext(), MenuList.class);
                Bundle b = new Bundle();
                
                CommCarePlatformProvider.pack(b, platform);
                i.putExtra(GlobalConstants.COMMCARE_PLATFORM, b);
                startActivityForResult(i, GET_COMMAND);

            }
        });
        
        logoutButton = (Button) findViewById(R.id.logout);
        logoutButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), LoginActivity.class);
                
                CommCareHomeActivity.this.platform.clearState();
                CommCareHomeActivity.this.platform.logout();
                
                startActivityForResult(i, LOGIN_USER);
            }
        });
    }

    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        platform.pack(outState);
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        platform.unpack(inState);
    }
   
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	switch(requestCode) {
    	case LOGIN_USER:
    		if(resultCode == RESULT_CANCELED) {
    			//quit somehow.
    			this.finish();
    			return;
    		} else if(resultCode == RESULT_OK) {
    			platform.logInUser(intent.getStringExtra(GlobalConstants.STATE_USER_KEY));
    			refreshView();
    			return;
    		}
    		break;
    		
    	case GET_COMMAND:
    		if(resultCode == RESULT_CANCELED) {
    			//We were already deep into getting other state
    			if(platform.getCommand() != null) {
    				//In order of depth: Ref, Case.
    				if(platform.getCaseId() != null) {
    					platform.setCaseId(null);
    					break;
    				}
    			} else {
    				//We've got nothing useful, come home bill bailey.
        			refreshView();
        			return;
    			}
    			break;
    		} else if(resultCode == RESULT_OK) {
    			//Get our command, set it, and continue forward
    			String command = intent.getStringExtra(GlobalConstants.STATE_COMMAND_ID);
    			platform.setCommand(command);
    			break;
    		}
        case GET_CASE:
        	if(resultCode == RESULT_CANCELED) {
        		platform.setCaseId(null);
        		platform.setCommand(null);
        		break;
    		} else if(resultCode == RESULT_OK) {
    			platform.setCaseId(intent.getStringExtra(GlobalConstants.STATE_CASE_ID));
    			break;
    		}
        case MODEL_RESULT:
        	if(resultCode == RESULT_OK) {
        		String instance = intent.getStringExtra("instancepath");
        		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        		mProcess = new ProcessAndSendTask(this, settings.getString(ServerPreferences.KEY_SUBMIT, this.getString(R.string.default_submit_server)));
        		mProcess.setListener(this);
        		showDialog(DIALOG_PROCESS);
        		mProcess.execute(instance);
        		refreshView();
        		return;
    		} else {
    			platform.clearState();
    			refreshView();
        		break;
    		}
    	} 
    		
    	
    	String needed = platform.getNeededData();
    	if(needed == null) {
	    	String command = platform.getCommand();
		
			Entry e = platform.getMenuMap().get(command);
			String path = platform.getFormPath(e.getXFormNamespace());
		
			PreloadContentProvider.initializeSession(platform, this);
			Intent i = new Intent("org.odk.collect.android.action.FormEntry");
			i.putExtra("formpath", path);
			i.putExtra("instancedestination", GlobalConstants.FILE_CC_SAVED);
			String[] preloaders = new String[] {"case", PreloadContentProvider.CONTENT_URI_CASE + "/" + platform.getCaseId() + "/"};
			i.putExtra("preloadproviders",preloaders);
			
			startActivityForResult(i, MODEL_RESULT);
    	}
    	if(needed == GlobalConstants.STATE_CASE_ID) {
            Intent i = new Intent(getApplicationContext(), EntitySelectActivity.class);
            Bundle b = new Bundle();
            
            CommCarePlatformProvider.pack(b, platform);
            i.putExtra(GlobalConstants.COMMCARE_PLATFORM, b);
            startActivityForResult(i, GET_CASE);
    	} else if(needed == GlobalConstants.STATE_COMMAND_ID) {
            Intent i = new Intent(getApplicationContext(), MenuList.class);
            Bundle b = new Bundle();
            
            CommCarePlatformProvider.pack(b, platform);
            i.putExtra(GlobalConstants.COMMCARE_PLATFORM, b);
            i.putExtra(GlobalConstants.STATE_COMMAND_ID, platform.getCommand());
            startActivityForResult(i, GET_COMMAND);
    	}
    	
    	super.onActivityResult(requestCode, resultCode, intent);
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        if(platform.getLoggedInUser() == null) {
        	Intent i = new Intent(getApplicationContext(), DotsEntryActivity.class);
        	i.putExtra("regimen", "4");
        	startActivityForResult(i,LOGIN_USER);
        	//Intent i = new Intent(getApplicationContext(), LoginActivity.class);
        	//startActivityForResult(i,LOGIN_USER);
        } else {
        	refreshView();
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
        case DIALOG_PROCESS:
                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setTitle("Processing Form");
                mProgressDialog.setMessage("Processing your Form");
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                return mProgressDialog;
        }
        return null;
    }
    
    private void refreshView() {
    }

	public void processAndSendFinished(int result) {
		mProgressDialog.dismiss();
		if(result == ProcessAndSendTask.FULL_SUCCESS) {
			Toast.makeText(this, "Form Sent to Server!", Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(this, "Error in Sending! Will try again later.", Toast.LENGTH_LONG).show();
		}
		refreshView();
	}
}