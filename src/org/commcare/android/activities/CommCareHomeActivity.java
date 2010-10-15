package org.commcare.android.activities;

import java.io.File;
import java.util.Vector;

import javax.crypto.SecretKey;

import org.commcare.android.R;
import org.commcare.android.application.AndroidShortcuts;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.FormRecord;
import org.commcare.android.preferences.CommCarePreferences;
import org.commcare.android.providers.PreloadContentProvider;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.tasks.ProcessAndSendListener;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.FileUtil;
import org.commcare.suite.model.Entry;
import org.commcare.util.CommCareSession;
import org.javarosa.core.services.storage.StorageFullException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class CommCareHomeActivity extends Activity implements ProcessAndSendListener {
	public static final int LOGIN_USER = 0;
	public static final int GET_COMMAND = 1;
	public static final int GET_CASE = 2;
	public static final int MODEL_RESULT = 4;
	public static final int INIT_APP = 8;
	public static final int GET_INCOMPLETE_FORM = 16;
	
	public static final int DIALOG_PROCESS = 0;
	public static final int USE_OLD_DIALOG = 1;
	public static final int DIALOG_SEND_UNSENT =2;
	public static final int DIALOG_CORRUPTED = 4;
	
	private static final int MENU_PREFERENCES = Menu.FIRST;
	private static final int MENU_UPDATE = Menu.FIRST  +1;;
	
	View homeScreen;
	
	private AndroidCommCarePlatform platform;
	
	ProgressDialog mProgressDialog;
	AlertDialog mAskOldDialog;
	AlertDialog mAttemptFixDialog;
	private int mCurrentDialog;
	
	ProcessAndSendTask mProcess;
	
	Button startButton;
	Button logoutButton;
	Button viewIncomplete;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        platform = CommCareApplication._().getCommCarePlatform();
        
        // enter data button. expects a result.
        startButton = (Button) findViewById(R.id.start);
        startButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), MenuList.class);
                
                startActivityForResult(i, GET_COMMAND);
            }
        });
        
     // enter data button. expects a result.
        viewIncomplete = (Button) findViewById(R.id.incomplete);
        viewIncomplete.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), IncompleteFormActivity.class);
                
                startActivityForResult(i, GET_INCOMPLETE_FORM);
            }
        });
        
        logoutButton = (Button) findViewById(R.id.logout);
        logoutButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), LoginActivity.class);
                
                CommCareHomeActivity.this.platform.getSession().clearState();
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
    	CommCareSession session = platform.getSession();
    	
    	switch(requestCode) {
    	case INIT_APP:
    		if(resultCode == RESULT_CANCELED) {
    			//quit somehow.
    			this.finish();
    			return;
    		} else if(resultCode == RESULT_OK) {
    			CommCareApplication._().initializeGlobalResources();
    			return;
    		}
    		break;
    	case LOGIN_USER:
    		if(resultCode == RESULT_CANCELED) {
    			//quit somehow.
    			this.finish();
    			return;
    		} else if(resultCode == RESULT_OK) {
    			platform.logInUser(intent.getStringExtra(GlobalConstants.STATE_USER_KEY));
    			refreshView();
    			checkAndStartUnsentTask();
    			return;
    		}
    		break;
    		
    	case GET_INCOMPLETE_FORM:
    		if(resultCode == RESULT_CANCELED) {
    			refreshView();
    			return;
    		}
    		else if(resultCode == RESULT_OK) {
    			int record = intent.getIntExtra(FormRecord.STORAGE_KEY, -1);
    			if(record == -1) {
    				//Hm, what to do here?
    				break;
    			}
    			FormRecord r = CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class).read(record);
    			session.setXmlns(r.getFormNamespace());
    			if(r.getEntityId() != null) {
    				session.setCaseId(r.getEntityId());
    			}
    			formEntry(platform.getFormPath(r.getFormNamespace()), r);
    			return;
    		}
    	case GET_COMMAND:
    		if(resultCode == RESULT_CANCELED) {
    			//We were already deep into getting other state
    			if(session.getCommand() != null) {
    				//In order of depth: Ref, Case.
    				if(session.getCaseId() != null) {
    					session.setCaseId(null);
    					break;
    				} else {
    					session.setCommand(null);
    	        		break;
    				}
    			} else {
    				//We've got nothing useful, come home bill bailey.
        			refreshView();
        			return;
    			}
    		} else if(resultCode == RESULT_OK) {
    			//Get our command, set it, and continue forward
    			String command = intent.getStringExtra(CommCareSession.STATE_COMMAND_ID);
    			session.setCommand(command);
    			break;
    		}
        case GET_CASE:
        	if(resultCode == RESULT_CANCELED) {
        		session.setCaseId(null);
        		session.setCommand(null);
        		break;
    		} else if(resultCode == RESULT_OK) {
    			session.setCaseId(intent.getStringExtra(CommCareSession.STATE_CASE_ID));
    			if(intent.hasExtra(CallOutActivity.CALL_DURATION)) {
    				platform.setCallDuration(intent.getLongExtra(CallOutActivity.CALL_DURATION, 0));
    			}
    			break;
    		}
        case MODEL_RESULT:
        	if(resultCode == 1) {
        		//Exception in form entry!
        		
        		if(intent.hasExtra("odk_exception")) {
        			Throwable ex = (Throwable)intent.getSerializableExtra("odk_exception");
            		ExceptionReportTask task = new ExceptionReportTask();
            		task.execute(ex);
        		} else {
        			RuntimeException ex = new RuntimeException("Unspecified exception from form entry engine");
        			ExceptionReportTask task = new ExceptionReportTask();
            		task.execute(ex);
        		}
        		
        		session.clearState();
    			refreshView();
        		break;
        	}
        	else if(resultCode == RESULT_OK) {
        		String instance = intent.getStringExtra("instancepath");
        		boolean completed = intent.getBooleanExtra("instancecomplete", true);
        		//intent.get
        		SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
        		
        		//First, check to see if there's already data we're updating
        		Vector<Integer> records = storage.getIDsForValue(FormRecord.META_PATH, instance);
        		FormRecord current;
    			String caseID = session.getCaseId();
    			if(caseID == null) {
    				caseID = AndroidCommCarePlatform.ENTITY_NONE; 
    			}
        		if(records.size() > 0) {
        			current = storage.read(records.elementAt(0).intValue());
        		} else {
        			//Otherwise, we need to get the current record from the unstarted stub
        			Vector<Integer> unstarteds = storage.getIDsForValues(new String[] {FormRecord.META_XMLNS, FormRecord.META_ENTITY_ID, FormRecord.META_STATUS}, new Object[] {platform.getSession().getForm(), caseID, FormRecord.STATUS_UNSTARTED});
        			if(unstarteds.size() != 1) {
        				throw new RuntimeException("Invalid DB state upon returning from form entry"); 
        			}
        			current = storage.read(unstarteds.elementAt(0).intValue());
        		}
        		
        		if(completed) {
    				FormRecord r = new FormRecord(session.getForm(), instance, caseID, FormRecord.STATUS_COMPLETE, current.getAesKey());
    				r.setID(current.getID());
    				try {
						storage.write(r);
					} catch (StorageFullException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	        		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(CommCareApplication._());
	        		mProcess = new ProcessAndSendTask(this, settings.getString("PostURL", this.getString(R.string.PostURL)));
	        		mProcess.setListener(this);
	        		showDialog(DIALOG_PROCESS);
	        		mProcess.execute(r);
	        		refreshView();
        		} else {
        			
        			FormRecord r = new FormRecord(session.getForm(), instance, caseID, FormRecord.STATUS_INCOMPLETE, current.getAesKey());
        			r.setID(current.getID());
        			
        			try {
						storage.write(r);
					} catch (StorageFullException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
        			refreshView();
        		}
        		session.clearState();
        		return;
    		} else {
    			session.clearState();
    			refreshView();
        		break;
    		}
    	}     		
    	
    	startNextFetch();
    	
    	super.onActivityResult(requestCode, resultCode, intent);
    }
    
    private void startNextFetch() {
    	CommCareSession session = platform.getSession();
    	String needed = session.getNeededData();
    	if(needed == null) {
    		startFormEntry();
    	}
    	if(needed == CommCareSession.STATE_CASE_ID) {
            Intent i = new Intent(getApplicationContext(), EntitySelectActivity.class);
            
            i.putExtra(CommCareSession.STATE_COMMAND_ID, session.getCommand());
            
            startActivityForResult(i, GET_CASE);
    	} else if(needed == CommCareSession.STATE_COMMAND_ID) {
            Intent i = new Intent(getApplicationContext(), MenuList.class);
            
            i.putExtra(CommCareSession.STATE_COMMAND_ID, session.getCommand());
            startActivityForResult(i, GET_COMMAND);
    	}
    }
    
    private void startFormEntry() {
    	String command = platform.getSession().getCommand();
		
		Entry e = platform.getMenuMap().get(command);
		String xmlns = e.getXFormNamespace();
		String path = platform.getFormPath(xmlns);
		
		SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		String caseID = platform.getSession().getCaseId();
		if(caseID == null) {
			caseID = AndroidCommCarePlatform.ENTITY_NONE; 
		}
		Vector<Integer> records = storage.getIDsForValues(new String[] {FormRecord.META_XMLNS, FormRecord.META_ENTITY_ID, FormRecord.META_STATUS}, new Object[] {xmlns, caseID, FormRecord.STATUS_INCOMPLETE});
		if(records.size() > 0 ) {
			FormRecord r = storage.read(records.elementAt(0));
			createAskUseOldDialog(path,r);
		} else {
			formEntry(path, null);
		}
		
	
    }
    
    private void formEntry(String formpath, FormRecord r) {
		PreloadContentProvider.initializeSession(platform, this);
		Intent i = new Intent("org.odk.collect.android.action.FormEntry");
		i.putExtra("formpath", formpath);
		i.putExtra("instancedestination", CommCareApplication._().fsPath((GlobalConstants.FILE_CC_SAVED)));
		
		CommCareSession session = platform.getSession();
		
		if(r == null) {
			
			String caseID = session.getCaseId();
			if(caseID == null) {
				caseID = AndroidCommCarePlatform.ENTITY_NONE; 
			}
			
			SecretKey key = CommCareApplication._().createNewSymetricKey();
			r = new FormRecord(session.getForm(), "",caseID, FormRecord.STATUS_UNSTARTED, key.getEncoded());
			SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
			
			//Make sure that there are no other unstarted definitions for this form/case, otherwise we won't be able to tell them apart unpon completion
			Vector<Integer> ids = storage.getIDsForValues(new String[] {FormRecord.META_XMLNS, FormRecord.META_ENTITY_ID, FormRecord.META_STATUS}, new Object[] {session.getForm(),caseID, FormRecord.STATUS_UNSTARTED} );
			for(Integer recordId : ids) {
				storage.remove(recordId.intValue());
			}
			
			try {
				storage.write(r);
			} catch (StorageFullException e) {
				throw new RuntimeException(e);
			}
		}
		
		if(r.getPath() != "") {
			i.putExtra("instancepath", r.getPath());
		}
		i.putExtra("encryptionkey", r.getAesKey());
		i.putExtra("encryptionkeyalgo", "AES");
		
		String[] preloaders = new String[] {"case", PreloadContentProvider.CONTENT_URI_CASE + "/" + r.getEntityId() + "/", "meta", PreloadContentProvider.CONTENT_URI_META + "/"};
		i.putExtra("preloadproviders",preloaders);

		
		startActivityForResult(i, MODEL_RESULT);
    }
    
    
    protected void checkAndStartUnsentTask() {
    	SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
    	
    	//Get all forms which are either unsent or unprocessed
    	Vector<Integer> ids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_UNSENT});
    	ids.addAll(storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_COMPLETE}));
    	if(ids.size() > 0) {
    		FormRecord[] records = new FormRecord[ids.size()];
    		for(int i = 0 ; i < ids.size() ; ++i) {
    			records[i] = storage.read(ids.elementAt(i).intValue());
    		}
    		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(CommCareApplication._());
    		mProcess = new ProcessAndSendTask(this, settings.getString("PostURL", this.getString(R.string.PostURL)));
    		mProcess.setListener(this);
    		showDialog(DIALOG_SEND_UNSENT);
    		mProcess.execute(records);
    	} else {
    		//Nothing.
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
        dispatchHomeScreen();
    }
    
    private void dispatchHomeScreen() {
        
        //First make sure nothing catastrophic has happened
        if(CommCareApplication._().getAppResourceState() == CommCareApplication.STATE_CORRUPTED || 
           CommCareApplication._().getDatabaseState() == CommCareApplication.STATE_CORRUPTED) {
     	        
        	//If so, ask the user if they want to wipe and recover (Possibly try to send everything first?)
        	showDialog(DIALOG_CORRUPTED);
        }
        
        //Now we need to catch any resource or database upgrade flags and make sure that the application
        //is ready to go.
        else if(CommCareApplication._().getAppResourceState() != CommCareApplication.STATE_READY ||
                CommCareApplication._().getDatabaseState() != CommCareApplication.STATE_READY) {
     	        Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
     	        
     	        this.startActivityForResult(i, INIT_APP);
        } else if(platform.getLoggedInUser() == null) {
        	
//        	Intent i = new Intent(getApplicationContext(), DotsEntryActivity.class);
//        	i.putExtra("regimen", "[0,4]");
//       	
//        	i.putExtra("currentdose", "['full', 'pillbox']");
//        	i.putExtra("currentbox", "0");
//
//        	i.putExtra("currentdosetwo", "['empty', 'direct']");
//        	i.putExtra("currentboxtwo", "0");
//        	
//        	startActivityForResult(i,LOGIN_USER);
        	
        	Intent i = new Intent(getApplicationContext(), LoginActivity.class);
        	startActivityForResult(i,LOGIN_USER);
        } else if(this.getIntent().hasExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT)) {
        	platform.getSession().setCommand(this.getIntent().getStringExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT));
        	//We were launched in shortcut mode. Get the command and load us up.
        	startNextFetch();
        	//Only launch shortcuts once per intent
        	this.getIntent().removeExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT);
        }
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onCreateDialog(int)
     */
    @Override
    protected Dialog onCreateDialog(int id) {
    	mCurrentDialog = id;
        switch (id) {
        case DIALOG_PROCESS:
                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setTitle("Processing Form");
                mProgressDialog.setMessage("Processing your Form");
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                return mProgressDialog;
        case DIALOG_SEND_UNSENT:
	        	mProgressDialog = new ProgressDialog(this);
	            mProgressDialog.setTitle("Sending...");
	            mProgressDialog.setMessage("Sending Unsent Data to Server");
	            mProgressDialog.setIndeterminate(true);
	            mProgressDialog.setCancelable(false);
	            return mProgressDialog;
        case DIALOG_CORRUPTED:
        		return createAskFixDialog();
        }
        return null;
    }
    
    public Dialog createAskFixDialog() {
    	mAttemptFixDialog = new AlertDialog.Builder(this).create();
        mAttemptFixDialog.setTitle("Storage is Corrupt :/");
        mAttemptFixDialog.setMessage("Sorry, something really bad has happened, and the app can't start up. With your permission CommCare can try to repair itself if you have network access.");
        DialogInterface.OnClickListener attemptFixDialog = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1: // attempt repair
                    	CommCareApplication._().resetApplicationResources();
                    	dispatchHomeScreen();
                        break;
                    case DialogInterface.BUTTON2: // Shut down
                    	CommCareHomeActivity.this.finish();
                        break;
                }
            }
        };
        mAttemptFixDialog.setCancelable(false);
        mAttemptFixDialog.setButton("Attempt Fix", attemptFixDialog);
        mAttemptFixDialog.setButton2("Shut Down", attemptFixDialog);
        
        return mAttemptFixDialog;
    }
    
    private void createAskUseOldDialog(final String formpath, final FormRecord r) {
        mAskOldDialog = new AlertDialog.Builder(this).create();
        mAskOldDialog.setTitle("Continue Form");
        mAskOldDialog.setMessage("You've got a saved copy of an incomplete form for this client. Do you want to continue filling out that form?");
        DialogInterface.OnClickListener useOldListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1: // yes, use old
                    	formEntry(formpath, r);
                        break;
                    case DialogInterface.BUTTON3: // no, create new
                    	formEntry(formpath, null);
                        break;
                    case DialogInterface.BUTTON2: // no, and delete the old one
                    	SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
                		storage.remove(r);
                		//How do we delete the saved record here?
                		//Find the parent folder and delete it, I guess?
                		try {
                			File f = new File(r.getPath());
                			if(!f.isDirectory() && f.getParentFile() != null) {
                				f = f.getParentFile();
                			}
                			FileUtil.deleteFile(f);
                		} catch(Exception e) {
                			e.printStackTrace();
                		}
                    	
                    	formEntry(formpath, null);
                        break;
                }
            }
        };
        mAskOldDialog.setCancelable(false);
        mAskOldDialog.setButton("Yes", useOldListener);
        mAskOldDialog.setButton2("Delete it", useOldListener);
        mAskOldDialog.setButton3("No", useOldListener);
        
        mAskOldDialog.show();
    }
    
    private void refreshView() {
    }

	public void processAndSendFinished(int result, int successfulSends) {
		this.dismissDialog(mCurrentDialog);
		if(result == ProcessAndSendTask.FULL_SUCCESS) {
			String label = "Form Sent to Server!";
			if(successfulSends > 1) {
				label = successfulSends + " Forms Sent to Server!";
			}
			Toast.makeText(this, label, Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(this, "Error in Sending! Will try again later.", Toast.LENGTH_LONG).show();
		}
		refreshView();
	}
	

    private void createPreferencesMenu() {
        Intent i = new Intent(this, CommCarePreferences.class);
        startActivity(i);
    }

	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_PREFERENCES, 0, "Settings").setIcon(
                android.R.drawable.ic_menu_preferences);
        menu.add(0, MENU_UPDATE, 0, "Update CommCare").setIcon(
        		android.R.drawable.ic_menu_upload);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_PREFERENCES:
                createPreferencesMenu();
                return true;
            case MENU_UPDATE:
            	CommCareApplication._().upgrade();
    			platform.getSession().clearState();
    			platform.logout();
            	Intent i = new Intent(getApplicationContext(), LoginActivity.class);
            	startActivityForResult(i,LOGIN_USER);
            	return true;
        }
        return super.onOptionsItemSelected(item);
    }

}