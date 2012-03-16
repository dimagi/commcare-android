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
import org.commcare.android.tasks.ProcessAndSendListener;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.AndroidCommCareSession;
import org.commcare.android.util.CommCareInstanceInitializer;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.suite.model.Profile;
import org.commcare.suite.model.SessionDatum;
import org.commcare.util.CommCareSession;
import org.javarosa.core.model.condition.EvaluationContext;
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

public class CommCareHomeActivity extends Activity implements ProcessAndSendListener {
	
	public static final int LOGIN_USER = 0;
	public static final int GET_COMMAND = 1;
	public static final int GET_CASE = 2;
	public static final int MODEL_RESULT = 4;
	public static final int INIT_APP = 8;
	public static final int GET_INCOMPLETE_FORM = 16;
	public static final int GET_REFERRAL = 32;
	
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
                boolean formsToSend = checkAndStartUnsentTask(new ProcessAndSendListener() {
                	
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
    	String username = u.getUsername(); 
    	
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		//TODO: We do this in a lot of places, we should wrap it somewhere
		if(prefs.contains("cc_user_domain")) {
			username += "@" + prefs.getString("cc_user_domain",null);
		}


    	DataPullTask pullTask = new DataPullTask(username, u.getCachedPwd(), prefs.getString("ota-restore-url",this.getString(R.string.ota_restore_url)), "", this);
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
					mProgressDialog.setMessage("Contacting server for sync...");
				} else if(progress[0] == DataPullTask.PROGRESS_AUTHED) {
					mProgressDialog.setMessage("Server contacted, downloading data.");
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
	    			
	    			formEntry(platform.getFormContentUri(r.getFormNamespace()), r);
	    			return;
	    		}
	    	case GET_COMMAND:
	    		if(resultCode == RESULT_CANCELED) {
	    			if(session.getCommand() == null) {
	    				//Needed a command, and didn't already have one. Stepping back from
	    				//an empty state, Go home!
	            		session.clearState();
	            		refreshView();
	            		return;
	    			} else {
	    				session.stepBack();
	    				break;
	    			}
	    		} else if(resultCode == RESULT_OK) {
	    			//Get our command, set it, and continue forward
	    			String command = intent.getStringExtra(CommCareSession.STATE_COMMAND_ID);
	    			session.setCommand(command);
	    			break;
	    		}
	        case GET_CASE:
	        	if(resultCode == RESULT_CANCELED) {
	        		session.stepBack();
	        		break;
	    		} else if(resultCode == RESULT_OK) {
	    			session.setDatum(session.getNeededDatum().getDataId(), intent.getStringExtra(CommCareSession.STATE_DATUM_VAL));
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
	        		Uri resultInstanceURI = intent.getData();
	                Cursor c = managedQuery(resultInstanceURI, null,null,null, null);
	                
	                if(!c.moveToFirst()) {
	                	throw new RuntimeException("Empty query for instance record!");
	                }
	        		
	        		SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
	        		
	        		//TODO: Don't assume CC stays alive?
	        		AndroidCommCareSession entrySession = platform.getSession();
	        		FormRecord current = storage.read(entrySession.getFormRecordId());
	        		
	        		//See if we were viewing an old form, in which case we don't want to change the historical record.
	        		if(current.getStatus() == FormRecord.STATUS_COMPLETE) {
	        			
	        			//TODO: replace the session rather than clearing it in-place.
		        		session.clearState();
	        			refreshView();
		        		return;
	        		}
	        		
        			String instance = c.getString(c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
	        		
	        		if(InstanceProviderAPI.STATUS_COMPLETE.equals(c.getString(c.getColumnIndex(InstanceColumns.STATUS)))) {
	        			current.updateStatus(instance, FormRecord.STATUS_COMPLETE);
	    				
	    				try {
	    					current = FormRecordCleanupTask.getUpdatedRecord(this, current, FormRecord.STATUS_COMPLETE);
	    				} catch(Exception e) {
	    					throw new RuntimeException(e);
	    				}
	    				
	    				try {
							storage.write(current);
						} catch (StorageFullException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
		        		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(CommCareApplication._());
		        		mProcess = new ProcessAndSendTask(this, settings.getString("PostURL", this.getString(R.string.PostURL)));
		        		mProcess.setListener(this);
		        		showDialog(DIALOG_PROCESS);
		        		mProcess.execute(current);
		        		refreshView();
	        		} else {
	        			current.updateStatus(instance, FormRecord.STATUS_INCOMPLETE);
	        			
	        			try {
	        				//Try to parse out information from the record if there is one
	        				if(new File(instance).exists()) {
	        					current = FormRecordCleanupTask.getUpdatedRecord(this, current, FormRecord.STATUS_INCOMPLETE);
	        				}
	    				} catch(Exception e) {
	    					//the instance might not be complete, so don't mess with anything if there's an issue.
	    				}
	        			
	        			try {
							storage.write(current);
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
	        		return;
	    		}
	    	}     		
	    	
	    	startNextFetch();
	    	
	    	super.onActivityResult(requestCode, resultCode, intent);
    	}
	    	catch (SessionUnavailableException sue) {
	    		//TODO: Cache current return, login, and try again
	    	}
    }
    
    private void startNextFetch() throws SessionUnavailableException {
    	AndroidCommCareSession session = platform.getSession();
    	String needed = session.getNeededData();
    	String[] lastPopped = session.getPoppedStep();
    	
    	if(needed == null) {
    		startFormEntry(session);
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
    		SessionDatum datum = CommCareApplication._().getCommCarePlatform().getSession().getNeededDatum();
			XPathExpression form;
			try {
				form = XPathParseTool.parseXPath(datum.getValue());
			} catch (XPathSyntaxException e) {
				//TODO: What.
				e.printStackTrace();
				throw new RuntimeException(e.getMessage());
			}
			EvaluationContext ec = CommCareApplication._().getCommCarePlatform().getSession().getEvaluationContext(new CommCareInstanceInitializer(CommCareApplication._().getCommCarePlatform()));
			if(datum.getType() == SessionDatum.DATUM_TYPE_FORM) {
				CommCareApplication._().getCommCarePlatform().getSession().setXmlns(XPathFuncExpr.toString(form.eval(ec)));
				CommCareApplication._().getCommCarePlatform().getSession().setDatum("", "awful");
			} else {
				CommCareApplication._().getCommCarePlatform().getSession().setDatum(datum.getDataId(), XPathFuncExpr.toString(form.eval(ec)));
			}
			startNextFetch();
			return;
    	}
    }
    
    private void startFormEntry(AndroidCommCareSession session) throws SessionUnavailableException{
    	//String command = session.getCommand();
		try {
			SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
			SqlIndexedStorageUtility<AndroidCommCareSession> sessionStorage = CommCareApplication._().getStorage(AndroidCommCareSession.STORAGE_KEY, AndroidCommCareSession.class);
	    	if(session.getFormRecordId() == -1) {
	    		
	    		//TODO: This is really a join situation. Need a way to outline connections between tables to enable joining
	    		
	    		//First, we need to see if this session's unique hash corresponds to any pending forms.
	    		Vector<Integer> ids = sessionStorage.getIDsForValue(AndroidCommCareSession.META_DESCRIPTOR_HASH, session.getSessionDescriptorHash());
	    		
	    		Vector<Integer> validId = new Vector<Integer>();
	    		//Filter for forms which have actually been started.
	    		for(int id : ids) {
	    			try {
	    				int recordId = Integer.valueOf(sessionStorage.getMetaDataFieldForRecord(id, AndroidCommCareSession.META_FORM_RECORD_ID));
	    				if(FormRecord.STATUS_INCOMPLETE.equals(storage.getMetaDataFieldForRecord(recordId, FormRecord.META_STATUS))) {
	    					validId.add(recordId);
	    				}
	    			} catch(NumberFormatException nfe) {
	    				//TODO: Clean up this record
	    				continue;
	    			}
	    		}
	    		
	    		if(validId.size() > 0) {
	    			createAskUseOldDialog(session, validId.firstElement());
	    			return;
	    		} 
	    		
	    		//We need to actually get a record to start filling out
				SecretKey key = CommCareApplication._().createNewSymetricKey();
				FormRecord r = new FormRecord("", FormRecord.STATUS_UNSTARTED, session.getForm(), key.getEncoded(), null, new Date(0));
				storage.write(r);
	    		session.setFormRecord(r);
	    	}
	    	
	    	sessionStorage.write(session);
	    	
	    	//there's already a form associated with this session, let's just get to it.
	    	FormRecord record = storage.read(session.getFormRecordId());
	    	formEntry(platform.getFormContentUri(record.getFormNamespace()), record);
		} catch (StorageFullException e) {
			throw new RuntimeException(e);
		}
		
//		Entry e = platform.getMenuMap().get(command);
//		String xmlns = e.getXFormNamespace();
//		String path = platform.getFormPath(xmlns);
//		
//		SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
//		
//		String entityid = FormRecord.commitSession(platform.getSession());
//		Vector<Integer> records = storage.getIDsForValues(new String[] {FormRecord.META_XMLNS, FormRecord.META_STATUS}, new Object[] {xmlns, entityid, FormRecord.STATUS_INCOMPLETE});
//		if(records.size() > 0 ) {
//			FormRecord r = storage.read(records.elementAt(0));
//			createAskUseOldDialog(path,r);
//		} else {
//			formEntry(path, null, platform.getSession());
//		}
		
	
    }
    
    private void formEntry(Uri formUri, FormRecord r) throws SessionUnavailableException{
		PreloadContentProvider.initializeSession(platform, this);
		FormLoaderTask.iif = new CommCareInstanceInitializer(platform);
		Intent i =new Intent(getApplicationContext(), org.odk.collect.android.activities.FormEntryActivity.class);
		i.setAction(Intent.ACTION_EDIT);
		i.putExtra("instancedestination", CommCareApplication._().fsPath((GlobalConstants.FILE_CC_SAVED)));
		
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
    
    
    protected boolean checkAndStartUnsentTask(ProcessAndSendListener listener) throws SessionUnavailableException {
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
    		mProcess.setListener(listener);
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
	        	
	        	Intent i = new Intent(getApplicationContext(), LoginActivity.class);
	        	startActivityForResult(i,LOGIN_USER);
	        } else if(this.getIntent().hasExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT)) {
	        	platform.getSession().setCommand(this.getIntent().getStringExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT));
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
        	Intent i = new Intent(getApplicationContext(), LoginActivity.class);
        	startActivityForResult(i,LOGIN_USER);
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
    
    private void createAskUseOldDialog(final AndroidCommCareSession currentSession, final int existingSessionRecord) {
        mAskOldDialog = new AlertDialog.Builder(this).create();
        mAskOldDialog.setTitle("Continue Form");
        mAskOldDialog.setMessage("You've got a saved copy of an incomplete form for this client. Do you want to continue filling out that form?");
        DialogInterface.OnClickListener useOldListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
            	try {
            		SqlIndexedStorageUtility<FormRecord> storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
            		SqlIndexedStorageUtility<AndroidCommCareSession> sessionStorage =  CommCareApplication._().getStorage(AndroidCommCareSession.STORAGE_KEY, AndroidCommCareSession.class);
            		
            		AndroidCommCareSession oldSession = sessionStorage.read(existingSessionRecord);
            		
            		FormRecord record = storage.read(oldSession.getFormRecordId());
	                switch (i) {
	                    case DialogInterface.BUTTON1: // yes, use old
	                    	//TODO: Replace session or something instead of just in-place editing current one
	                    	//Sessions should now be the same
	                    	currentSession.setFormRecord(record);
	                    	currentSession.setID(oldSession.getID());
	                    	sessionStorage.write(currentSession);
	                    	
	                    	formEntry(platform.getFormContentUri(currentSession.getForm()), record);
	                        break;
	                    case DialogInterface.BUTTON2: // no, and delete the old one
	                    	sessionStorage.remove(oldSession);
	                		storage.remove(record);
	                		//TODO: This should all be part of a generalized process
	                		//How do we delete the saved record here?
	                		//Find the parent folder and delete it, I guess?
	                		try {
	                			File f = new File(record.getPath());
	                			if(!f.isDirectory() && f.getParentFile() != null) {
	                				f = f.getParentFile();
	                			}
	                			FileUtil.deleteFile(f);
	                		} catch(Exception e) {
	                			e.printStackTrace();
	                		}
	                		//fallthrough to new now that old record is gone
	                    case DialogInterface.BUTTON3: // no, create new
	                    	SecretKey key = CommCareApplication._().createNewSymetricKey();
	        				FormRecord r = new FormRecord(currentSession.getForm(), "", FormRecord.STATUS_UNSTARTED, key.getEncoded(), null, new Date(0));
	        				storage.write(r);
	        				currentSession.setFormRecord(r);
	        				sessionStorage.write(currentSession);
	                    	formEntry(platform.getFormContentUri(currentSession.getForm()), r);
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

	public void processAndSendFinished(int result, int successfulSends) {
		if(currentHome != this) { System.out.println("Fixing issue with new activity");}
		currentHome.dismissDialog(mCurrentDialog);
		if(result == ProcessAndSendTask.FULL_SUCCESS) {
			String label = "Form Sent to Server!";
			if(successfulSends > 1) {
				label = successfulSends + " Forms Sent to Server!";
			}
			Toast.makeText(this, label, Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(this, "Having issues communicating with the server to send forms. Will try again later.", Toast.LENGTH_LONG).show();
		}
		refreshView();
	}
	

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
            	CommCareApplication._().upgrade();
    			platform.getSession().clearState();
    			CommCareApplication._().getSession().logout();
            	Intent i = new Intent(getApplicationContext(), LoginActivity.class);
            	startActivityForResult(i,LOGIN_USER);
            	return true;
            case MENU_CALL_LOG:
            	createCallLogActivity();
            	return true;
        }
        return super.onOptionsItemSelected(item);
    }

}