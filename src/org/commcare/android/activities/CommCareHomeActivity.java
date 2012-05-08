package org.commcare.android.activities;

import java.io.File;
import java.util.Date;
import java.util.Vector;

import javax.crypto.SecretKey;

import org.commcare.android.R;
import org.commcare.android.application.AndroidShortcuts;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.FormRecord;
import org.commcare.android.models.SessionStateDescriptor;
import org.commcare.android.models.User;
import org.commcare.android.odk.provider.FormsProviderAPI;
import org.commcare.android.odk.provider.InstanceProviderAPI;
import org.commcare.android.odk.provider.InstanceProviderAPI.InstanceColumns;
import org.commcare.android.preferences.CommCarePreferences;
import org.commcare.android.providers.PreloadContentProvider;
import org.commcare.android.tasks.DataPullListener;
import org.commcare.android.tasks.DataPullTask;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.android.tasks.ProcessTaskListener;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.AndroidSessionWrapper;
import org.commcare.android.util.CommCareInstanceInitializer;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.InvalidStateException;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.SessionDatum;
import org.commcare.util.CommCareSession;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.odk.collect.android.tasks.FormLoaderTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class CommCareHomeActivity extends Activity implements ProcessTaskListener {
	
	public static final int LOGIN_USER = 0;
	public static final int GET_COMMAND = 1;
	public static final int GET_CASE = 2;
	public static final int MODEL_RESULT = 4;
	public static final int INIT_APP = 8;
	public static final int GET_INCOMPLETE_FORM = 16;
	public static final int GET_REFERRAL = 32;
	public static final int UPGRADE_APP = 64;
	
	public static final int DIALOG_PROCESS = 0;
	public static final int USE_OLD_DIALOG = 1;
	public static final int DIALOG_SEND_UNSENT =2;
	public static final int DIALOG_CORRUPTED = 4;
	
	private static final int MENU_PREFERENCES = Menu.FIRST;
	private static final int MENU_UPDATE = Menu.FIRST  +1;
	private static final int MENU_CALL_LOG = Menu.FIRST  +2;
	
	View homeScreen;
	
	private AndroidCommCarePlatform platform;
	
	ProgressDialog mProgressDialog;
	AlertDialog mAskOldDialog;
	AlertDialog mAttemptFixDialog;
	private int mCurrentDialog;
	
	ProcessAndSendTask mProcess;
	
	static Activity currentHome;
	
	Button startButton;
	Button logoutButton;
	Button viewIncomplete;
	Button syncButton;
	
	Button viewOldForms;

	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentHome = this;
        setContentView(R.layout.main);
        
        TextView version = (TextView)findViewById(R.id.str_version);
        version.setText(CommCareApplication._().getCurrentVersionString());
        
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
                Intent i = new Intent(getApplicationContext(), FormRecordListActivity.class);
                i.putExtra(FormRecord.META_STATUS, FormRecord.STATUS_INCOMPLETE);
                
                startActivityForResult(i, GET_INCOMPLETE_FORM);
            }
        });
        
        logoutButton = (Button) findViewById(R.id.logout);
        logoutButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                CommCareApplication._().logout();
                
                //This will dispatch the login screen by default more cleanly
                dispatchHomeScreen();
            }
        });
        
        viewOldForms = (Button) findViewById(R.id.old);
        viewOldForms.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), FormRecordListActivity.class);
                
                startActivityForResult(i, GET_INCOMPLETE_FORM);
            }
        });
        
        syncButton  = (Button) findViewById(R.id.sync_now);
        syncButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
//            	if(true) {
//            	throw new RuntimeException("Pass to server!");
//            	}
                boolean formsToSend = checkAndStartUnsentTask(new ProcessTaskListener() {

					public void processTaskAllProcessed() {
						//Don't cancel the dialog, we need it to stay in the foreground to ensure things are set
					}
                	
                	public void processAndSendFinished(int result, int successfulSends) {
                		if(currentHome != CommCareHomeActivity.this) { System.out.println("Fixing issue with new activity");}
                		if(result == ProcessAndSendTask.FULL_SUCCESS) {
                			String label = successfulSends + " Forms Sent to Server!";
                			Toast.makeText(currentHome, label, Toast.LENGTH_LONG).show();
                			
                			//OK, all forms sent, sync time 
                			syncData();
                			
                		} else {
                			currentHome.dismissDialog(mCurrentDialog);
                			Toast.makeText(currentHome, "Having issues communicating with the server to send forms. Will try again later.", Toast.LENGTH_LONG).show();
                		}
                	}

                });
                
                if(!formsToSend) {
                	//No unsent forms, just sync
                	syncData();
                }
                
            }
        });
    }
    
    private void syncData() {
    	User u = CommCareApplication._().getSession().getLoggedInUser();
    	
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

    	DataPullTask pullTask = new DataPullTask(u.getUsername(), u.getCachedPwd(), prefs.getString("ota-restore-url",this.getString(R.string.ota_restore_url)), "", this);
    	pullTask.setPullListener(new DataPullListener() {

			public void finished(int status) {
				currentHome.dismissDialog(mCurrentDialog);
				
				//TODO: SHARES _A LOT_ with login activity. Unify into service
				switch(status) {
				case DataPullTask.AUTH_FAILED:
					Toast.makeText(currentHome, 
							"Authentication failed on server. Please log out and try to log in again with syncing", 
							Toast.LENGTH_LONG).show();
					break;
				case DataPullTask.BAD_DATA:
					Toast.makeText(currentHome, "Server provided improperly formatted data, please try again or contact your supervisor.", 
							Toast.LENGTH_LONG).show();
					break;
				case DataPullTask.DOWNLOAD_SUCCESS:
					Toast.makeText(currentHome, "Sync Success!", Toast.LENGTH_LONG).show();
					break;
				case DataPullTask.UNREACHABLE_HOST:
					Toast.makeText(currentHome, "Couldn't contact server. Please make sure an internet connection is available or try again later.", 
							Toast.LENGTH_LONG).show();
					break;
				case DataPullTask.UNKNOWN_FAILURE:
					Toast.makeText(currentHome, "Unknown failure, please try again.", Toast.LENGTH_LONG).show();
					break;
				}
				
				//TODO: What if the user info was updated?
			}

			public void progressUpdate(Integer... progress) {
				if(progress[0] == DataPullTask.PROGRESS_STARTED) {
					mProgressDialog.setMessage("Cleaning local data");
				} else if(progress[0] == DataPullTask.PROGRESS_CLEANED) {
					mProgressDialog.setMessage("Contacting server for sync...");
				} else if(progress[0] == DataPullTask.PROGRESS_AUTHED) {
					mProgressDialog.setMessage("Server contacted, downloading data.");
				}else if(progress[0] == DataPullTask.PROGRESS_RECOVERY_NEEDED) {
					mProgressDialog.setMessage("Phone and server have inconsistent data! Starting recovery...");
				} else if(progress[0] == DataPullTask.PROGRESS_RECOVERY_STARTED) {
					mProgressDialog.setMessage("Recovering local DB State. Please do not turn off the app!");
				}
			}
    		
    	});
    	//possibly already showing
    	currentHome.showDialog(DIALOG_PROCESS);
    	
    	pullTask.execute();
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
    	try {
    		
    		//TODO: We might need to load this from serialized state?
	    	AndroidSessionWrapper currentState = CommCareApplication._().getCurrentSessionWrapper();
	    	
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
	    	case UPGRADE_APP:
	    		if(resultCode == RESULT_CANCELED) {
	    			//This might actually be bad, but try to go about your business
    				refreshView();
	    			return;
	    		} else if(resultCode == RESULT_OK) {
	    			if(intent.getBooleanExtra(CommCareSetupActivity.KEY_REQUIRE_REFRESH, true)) {
	    				CommCareApplication._().getSession().logout();
	    			}
	    			dispatchHomeScreen();
    				return;
	    		}
	    		break;
	    	case LOGIN_USER:
	    		if(resultCode == RESULT_CANCELED) {
	    			//quit somehow.
	    			this.finish();
	    			return;
	    		} else if(resultCode == RESULT_OK) {
	    			if(intent.getBooleanExtra(LoginActivity.ALREADY_LOGGED_IN, false)) {
	    				//If we were already logged in just roll with it.
	    				refreshView();
	    			} else {
	    				refreshView();
	    				checkAndStartUnsentTask(this);
	    			}
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
	    			
	    			//Retrieve and load the appropriate ssd
	    			SqlIndexedStorageUtility<SessionStateDescriptor> ssdStorage = CommCareApplication._().getStorage(SessionStateDescriptor.STORAGE_KEY, SessionStateDescriptor.class);
	    			Vector<Integer> ssds = ssdStorage.getIDsForValue(SessionStateDescriptor.META_FORM_RECORD_ID, r.getID());
	    			if(ssds.size() == 1) {
	    				currentState.loadFromStateDescription(ssdStorage.read(ssds.firstElement()));
	    			} else {
	    				currentState.setFormRecordId(r.getID());
	    			}

	    			
	    			formEntry(platform.getFormContentUri(r.getFormNamespace()), r);
	    			return;
	    		}
	    	case GET_COMMAND:
	    		if(resultCode == RESULT_CANCELED) {
	    			if(currentState.getSession().getCommand() == null) {
	    				//Needed a command, and didn't already have one. Stepping back from
	    				//an empty state, Go home!
	            		currentState.reset();
	            		refreshView();
	            		return;
	    			} else {
	    				currentState.getSession().stepBack();
	    				break;
	    			}
	    		} else if(resultCode == RESULT_OK) {
	    			//Get our command, set it, and continue forward
	    			String command = intent.getStringExtra(CommCareSession.STATE_COMMAND_ID);
	    			currentState.getSession().setCommand(command);
	    			break;
	    		}
	        case GET_CASE:
	        	if(resultCode == RESULT_CANCELED) {
	        		currentState.getSession().stepBack();
	        		break;
	    		} else if(resultCode == RESULT_OK) {
	    			currentState.getSession().setDatum(currentState.getSession().getNeededDatum().getDataId(), intent.getStringExtra(CommCareSession.STATE_DATUM_VAL));
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
	        		
	        		Toast.makeText(this, "There was an unrecoverable error during form entry! If the problem persists, seek technical support", Toast.LENGTH_LONG);
	        		
	        		
	        		currentState.reset();
	    			refreshView();
	        		break;
	        	}
	        	else if(resultCode == RESULT_OK) {
	        		
	                //This is the state we were in when we _Started_ form entry
	        		FormRecord current = currentState.getFormRecord();
	        		
	        		//See if we were viewing an old form, in which case we don't want to change the historical record.
	        		if(current.getStatus() == FormRecord.STATUS_COMPLETE) {
	        			currentState.reset();
	        			refreshView();
		        		return;
	        		}
	        		
	        		Uri resultInstanceURI = intent.getData();
	        		
	        		//TODO: encapsulate this pattern somewhere?
	        		if(resultInstanceURI == null) {
		        		Toast.makeText(this, "Form entry did not provide a result", Toast.LENGTH_LONG);
		        		
		        		currentState.reset();
		    			refreshView();
		    			return;
	        		}
	        		
	        		//TODO: Feels like it would be cleaner to give the URI here, but we'd also 
	        		//need to get the wrapper some context with it
	                Cursor c = managedQuery(resultInstanceURI, null,null,null, null);
	                boolean complete = false;
	                try {
	                	complete = currentState.beginRecordTransaction(c);
	                } catch(IllegalArgumentException iae) {
	                	
	                	iae.printStackTrace();
		        		Toast.makeText(this, "There was an unrecoverable error attempting to read the form result! If the problem persists, seek technical support", Toast.LENGTH_LONG);
		        		//TODO: Fail more hardcore here? Wipe the form record and its ties?
		        		
		        		currentState.reset();
		    			refreshView();
		    			return;
	                } finally {
	                	c.close();
	                }
	                 
        			//TODO: Move this logic into the process task?
	                try {
						current = currentState.commitRecordTransaction();
					} catch (InvalidStateException e) {
						
						//Something went wrong with all of the connections which should exist. Tell
						//the user, 
						Toast.makeText(this, "An error occurred: " + e.getMessage() + " and your data could not be saved.", Toast.LENGTH_LONG);
						new FormRecordCleanupTask(this).wipeRecord(currentState);
						
						//Notify the server of this problem (since we aren't going to crash) 
						ExceptionReportTask ert = new ExceptionReportTask();
						ert.execute(e);
						
						currentState.reset();
	        			refreshView();
        				return;
					}
	        			        		 
	                //The form is either ready for processing, or not, depending on how it was saved
	        		if(complete) {
	        			//Form record should now be up to date now and stored correctly. Begin processing its content and submitting it. 
        				SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(CommCareApplication._());
        				
        				//TODO: Move this to be a system notification thing
        				mProcess = new ProcessAndSendTask(this, settings.getString("PostURL", this.getString(R.string.PostURL)));
        				mProcess.setListeners(this, CommCareApplication._().getSession());
        				
        				showDialog(DIALOG_PROCESS);
        				mProcess.execute(current);
	        			currentState.reset();
        				refreshView();
        				return;
	        		} else {
	        			//Form record is now stored. 
	        			currentState.reset();
	        			refreshView();
        				return;
	        		}
	    		} else {
	    			//Entry was cancelled.
	    			new FormRecordCleanupTask(this).wipeRecord(currentState);
	    			currentState.reset();
	    			refreshView();
	        		return;
	    		}
	    	}     		
	    	
	    	startNextFetch();
	    	
	    	super.onActivityResult(requestCode, resultCode, intent);
    	}
	    	catch (SessionUnavailableException sue) {
	    		//TODO: Cache current return, login, and try again
	    		returnToLogin();
	    	}
    }
    
    private void startNextFetch() throws SessionUnavailableException {
    	CommCareSession session = CommCareApplication._().getCurrentSession();
    	String needed = session.getNeededData();
    	String[] lastPopped = session.getPoppedStep();
    	
    	if(needed == null) {
    		startFormEntry(CommCareApplication._().getCurrentSessionWrapper());
    	}
    	else if(needed == CommCareSession.STATE_COMMAND_ID) {
 			Intent i = new Intent(getApplicationContext(), MenuList.class);
         
 			i.putExtra(CommCareSession.STATE_COMMAND_ID, session.getCommand());
 			startActivityForResult(i, GET_COMMAND);
     	}  else if(needed == CommCareSession.STATE_DATUM_VAL) {
            Intent i = new Intent(getApplicationContext(), EntitySelectActivity.class);
            
            i.putExtra(CommCareSession.STATE_COMMAND_ID, session.getCommand());
            if(lastPopped != null && CommCareSession.STATE_DATUM_VAL.equals(lastPopped[0])) {
            	i.putExtra(EntitySelectActivity.EXTRA_ENTITY_KEY, lastPopped[1]);
            }
            
            startActivityForResult(i, GET_CASE);
    	} else if(needed == CommCareSession.STATE_DATUM_COMPUTED) {
    		//compute
    		SessionDatum datum = session.getNeededDatum();
			XPathExpression form;
			try {
				form = XPathParseTool.parseXPath(datum.getValue());
			} catch (XPathSyntaxException e) {
				//TODO: What.
				e.printStackTrace();
				throw new RuntimeException(e.getMessage());
			}
			EvaluationContext ec = session.getEvaluationContext(new CommCareInstanceInitializer(session));
			if(datum.getType() == SessionDatum.DATUM_TYPE_FORM) {
				session.setXmlns(XPathFuncExpr.toString(form.eval(ec)));
				session.setDatum("", "awful");
			} else {
				session.setDatum(datum.getDataId(), XPathFuncExpr.toString(form.eval(ec)));
			}
			startNextFetch();
			return;
    	}
    }
    
    private void startFormEntry(AndroidSessionWrapper state) throws SessionUnavailableException{
		try {
			//If this is a new record (never saved before), which currently all should be 
	    	if(state.getFormRecordId() == -1) {
	    		
	    		//First, see if we've already started this form before
	    		SessionStateDescriptor existing = state.searchForDuplicates();
	    		
	    		//I'm not proud of the second clause, here. Basically, only ask if we should continue entry if the
	    		//saved state actually involved selecting some data.
	    		if(existing != null && existing.getSessionDescriptor().contains(CommCareSession.STATE_DATUM_VAL)) {
	    			createAskUseOldDialog(state, existing);
	    			return;
	    		}
	    		
	    		//Otherwise, generate a stub record and commit it
	    		state.commitStub();
	    	} else {
	    		Logger.log("form-entry", "Somehow ended up starting form entry with old state?");
	    	}
	    	
	    	//We should now have a valid record for our state. Time to get to form entry.
	    	FormRecord record = state.getFormRecord();
	    	formEntry(platform.getFormContentUri(record.getFormNamespace()), record);
	    	
		} catch (StorageFullException e) {
			throw new RuntimeException(e);
		}
    }
    
    private void formEntry(Uri formUri, FormRecord r) throws SessionUnavailableException{
    	//TODO: This is... just terrible. Specify where external instance data should come from
		FormLoaderTask.iif = new CommCareInstanceInitializer(CommCareApplication._().getCurrentSession());
		
		//Create our form entry activity callout
		Intent i =new Intent(getApplicationContext(), org.odk.collect.android.activities.FormEntryActivity.class);
		i.setAction(Intent.ACTION_EDIT);
		
		
		i.putExtra("instancedestination", CommCareApplication._().fsPath((GlobalConstants.FILE_CC_SAVED)));
		
		//See if there's existing form data that we want to continue entering (note, this should be stored in the form
		///record as a URI link to the instance provider in the future)
		if(r.getPath() != "") {
			//We should just be storing the index to this, not bothering to look it up with the path
			String selection = InstanceColumns.INSTANCE_FILE_PATH +"=?";
			Cursor c = this.getContentResolver().query(InstanceColumns.CONTENT_URI, new String[] {InstanceColumns._ID}, selection, new String[] {r.getPath()}, null);
			this.startManagingCursor(c);
			if(!c.moveToFirst()) {
				throw new RuntimeException("Couldn't find FormInstance for record!");
			}
			long id = c.getLong(0);
			i.setData(ContentUris.withAppendedId(InstanceColumns.CONTENT_URI, id));
		} else {
			i.setData(formUri);
		}
		
		i.putExtra("readonlyform", FormRecord.STATUS_SAVED.equals(r.getStatus()));
		
		i.putExtra("key_aes_storage", Base64.encodeToString(r.getAesKey(), Base64.DEFAULT));
		
		i.putExtra("form_content_uri", FormsProviderAPI.FormsColumns.CONTENT_URI.toString());
		i.putExtra("instance_content_uri", InstanceProviderAPI.InstanceColumns.CONTENT_URI.toString());
		
		startActivityForResult(i, MODEL_RESULT);
    }
    
    
    protected boolean checkAndStartUnsentTask(ProcessTaskListener listener) throws SessionUnavailableException {
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
    		mProcess.setListeners(this, CommCareApplication._().getSession());
    		showDialog(DIALOG_SEND_UNSENT);
    		mProcess.execute(records);
    		return true;
    	} else {
    		//Nothing.
    		return false;
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
    	try {
	        
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
	        } else if(!CommCareApplication._().getSession().isLoggedIn()) {
	        	
	//        	Intent i = new Intent(getApplicationContext(), DotsEntryActivity.class);
	//        	i.putExtra("regimen", "[1,2]");
	//       	
	//        	i.putExtra("currentdose", "['full', 'pillbox']");
	//        	i.putExtra("currentbox", "0");
	//
	//        	i.putExtra("currentdosetwo", "['empty', 'direct']");
	//        	i.putExtra("currentboxtwo", "0");
	//        	
	//        	startActivityForResult(i,LOGIN_USER);
	        	returnToLogin();
	        } else if(this.getIntent().hasExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT)) {
	        	CommCareApplication._().getCurrentSession().setCommand(this.getIntent().getStringExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT));
	        	//We were launched in shortcut mode. Get the command and load us up.
	        	startNextFetch();
	        	//Only launch shortcuts once per intent
	        	this.getIntent().removeExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT);
	        }
	        
	        //Make sure that the review button is properly enabled.
	        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
	        if(p != null && p.isFeatureActive(Profile.FEATURE_REVIEW)) {
	        	viewOldForms.setVisibility(Button.VISIBLE);
	        }
    	} catch(SessionUnavailableException sue) {
    		//TODO: See how much context we have, and go login
    		returnToLogin();
    	}
    }
    
    private void returnToLogin() {
    	Intent i = new Intent(getApplicationContext(), LoginActivity.class);
    	startActivityForResult(i,LOGIN_USER);
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
    	
    	//Test if this was a botched upgrade.
    	boolean upgradeIssue = testBotchedUpgrade();
    	if(!upgradeIssue) {
    		mAttemptFixDialog.setTitle("Storage is Corrupt :/");
    		mAttemptFixDialog.setMessage("Sorry, something really bad has happened, and the app can't start up. With your permission CommCare can try to repair itself if you have network access.");
    	} else {
    		mAttemptFixDialog.setTitle("Complete Upgrade");
    		mAttemptFixDialog.setMessage("CommCare needs to finish the upgrade by downloading the application's resources.");
    	}
        DialogInterface.OnClickListener attemptFixDialog = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
            	try {
	                switch (i) {
	                    case DialogInterface.BUTTON1: // attempt repair
	                    	CommCareApplication._().resetApplicationResources();
	                    	CommCareApplication._().cleanUpDatabaseFileLinkages();
	                    	dispatchHomeScreen();
	                        break;
	                    case DialogInterface.BUTTON2: // Shut down
	                    	CommCareHomeActivity.this.finish();
	                        break;
	                }
	            } catch(SessionUnavailableException sue) {
	            	//should be impossible to get here.
	            	throw new RuntimeException("Required session unavailable. Something is seriously wrong");
	            }
            }
        };
        mAttemptFixDialog.setCancelable(false);
        if(!upgradeIssue) {
        	mAttemptFixDialog.setButton("Attempt Fix", attemptFixDialog);
        	mAttemptFixDialog.setButton2("Shut Down", attemptFixDialog);
        } else {
        	mAttemptFixDialog.setButton("Complete Upgrade", attemptFixDialog);
        }
        
        return mAttemptFixDialog;
    }
    
    private boolean testBotchedUpgrade() {
    	//If the install folder is empty, we know that commcare wiped out our stuff.
    	File install = new File(CommCareApplication._().fsPath(GlobalConstants.FILE_CC_INSTALL));
    	File[] installed = install.listFiles();
    	if(installed == null || installed.length == 0) {
    		return true;
    	}
    	//there's another failure mode where the files somehow end up empty.
    	for(File f : installed) {
    		if(f.length() != 0) {
    			return false;
    		}
    	}
    	return true;
    }
    
    private void createAskUseOldDialog(final AndroidSessionWrapper state, final SessionStateDescriptor existing) {
        mAskOldDialog = new AlertDialog.Builder(this).create();
        mAskOldDialog.setTitle("Continue Form");
        mAskOldDialog.setMessage("You've got a saved copy of an incomplete form for this client. Do you want to continue filling out that form?");
        DialogInterface.OnClickListener useOldListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
            	try {
	                switch (i) {
	                    case DialogInterface.BUTTON1: // yes, use old
	                    	//Replace the current state from the descriptor
	                    	state.loadFromStateDescription(existing);
	                    	formEntry(platform.getFormContentUri(state.getSession().getForm()), state.getFormRecord());
	                        break;
	                    case DialogInterface.BUTTON2: // no, and delete the old one
	                    	new FormRecordCleanupTask(CommCareHomeActivity.this).wipeRecord(existing);
	                		//fallthrough to new now that old record is gone
	                    case DialogInterface.BUTTON3: // no, create new
	                    	state.commitStub();
	                    	formEntry(platform.getFormContentUri(state.getSession().getForm()), state.getFormRecord());
	                        break;
	                }
            	} catch(SessionUnavailableException sue) {
            		//TODO: From home activity, login again and return to form list if possible.
            	} catch (StorageFullException e) {
					throw new RuntimeException(e);
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

    //Process and send listeners
    
	public void processAndSendFinished(int result, int successfulSends) {
		if(currentHome != this) { System.out.println("Fixing issue with new activity");}
		if(result == ProcessAndSendTask.FULL_SUCCESS) {
			String label = "Form Sent to Server!";
			if(successfulSends > 1) {
				label = successfulSends + " Forms Sent to Server!";
			}
			Toast.makeText(this, label, Toast.LENGTH_LONG).show();
		} else if(result == ProcessAndSendTask.PROGRESS_LOGGED_OUT) {
			Toast.makeText(this, "You've been logged out of CommCare, please login again and sync", Toast.LENGTH_LONG).show();
			returnToLogin();
		} else {
			Toast.makeText(this, "Having issues communicating with the server to send forms. Will try again later.", Toast.LENGTH_LONG).show();
		}
		refreshView();
	}
	
	public void processTaskAllProcessed() {
		if(currentHome != this) { System.out.println("Fixing issue with new activity");}
		currentHome.dismissDialog(mCurrentDialog);
	}
	
	//END - Process and Send Listeners
	

    private void createPreferencesMenu() {
        Intent i = new Intent(this, CommCarePreferences.class);
        startActivity(i);
    }
    
    private void createCallLogActivity() {
        Intent i = new Intent(this, PhoneLogActivity.class);
        startActivity(i);

    }

	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_PREFERENCES, 0, "Settings").setIcon(
                android.R.drawable.ic_menu_preferences);
        menu.add(0, MENU_UPDATE, 0, "Update CommCare").setIcon(
        		android.R.drawable.ic_menu_upload);
        menu.add(0, MENU_CALL_LOG, 0, "Call Log").setIcon(
        		android.R.drawable.ic_menu_recent_history);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_PREFERENCES:
                createPreferencesMenu();
                return true;
            case MENU_UPDATE:
            	Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
            	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            	String ref = prefs.getString("default_app_server", null);
            	i.putExtra(CommCareSetupActivity.KEY_PROFILE_REF, ref);
            	i.putExtra(CommCareSetupActivity.KEY_UPGRADE_MODE, true);
            	
            	startActivityForResult(i,UPGRADE_APP);
            	return true;
            case MENU_CALL_LOG:
            	createCallLogActivity();
            	return true;
        }
        return super.onOptionsItemSelected(item);
    }
}