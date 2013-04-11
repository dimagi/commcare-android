/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.commcare.dalvik.activities;

import org.commcare.android.adapters.IncompleteFormListAdapter;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.database.user.models.User;
import org.commcare.android.tasks.DataPullListener;
import org.commcare.android.tasks.DataPullTask;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.android.tasks.FormRecordLoadListener;
import org.commcare.android.tasks.FormRecordLoaderTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.view.IncompleteFormRecordView;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.locale.Localization;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


public class FormRecordListActivity extends Activity implements TextWatcher, FormRecordLoadListener, OnItemClickListener {
	private static final int DIALOG_PROCESS = 1;
	private ProgressDialog mProgressDialog;
	
	private static final int OPEN_RECORD = Menu.FIRST;
	private static final int DELETE_RECORD = Menu.FIRST  + 1;
	
	private static final int DOWNLOAD_FORMS = Menu.FIRST;
	
	public static final String KEY_INITIAL_RECORD_ID = "cc_initial_rec_id";
	
	private AndroidCommCarePlatform platform;
	
	private IncompleteFormListAdapter adapter;
	
	private int initialSelection = -1;
	
	private EditText searchbox;
	private LinearLayout header;
	private ImageButton barcodeButton;
	private Spinner filterSelect;
	private ListView listView;
	
	public enum FormRecordFilter {
		
		/** Processed and Pending **/ 
		SubmittedAndPending("form.record.filter.subandpending", new String[] {FormRecord.STATUS_SAVED, FormRecord.STATUS_UNSENT}),
		
		/** Submitted Only **/ 
		Submitted("form.record.filter.submitted", new String[] {FormRecord.STATUS_SAVED}),
		
		/** Pending Submission **/
		Pending("form.record.filter.pending", new String[] {FormRecord.STATUS_UNSENT}),
		
		/** Incomplete forms **/
		Incomplete("form.record.filter.incomplete", new String[] {FormRecord.STATUS_INCOMPLETE}, false);
		
		FormRecordFilter(String message, String[] statuses) {this(message, statuses, true);}
		FormRecordFilter(String message, String[] statuses, boolean visible) {this.message = message; this.statuses = statuses; this.visible = visible;}
		private final String message;
		private final String[] statuses;
		public boolean visible;
		public String getMessage() { return message;}
		public String[] getStatus() { return statuses; }
		
	}

	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
	        platform = CommCareApplication._().getCommCarePlatform();
	        setContentView(R.layout.entity_select_layout);
        	findViewById(R.id.entity_select_loading).setVisibility(View.GONE);

	        searchbox = (EditText)findViewById(R.id.searchbox);
	        header = (LinearLayout)findViewById(R.id.entity_select_header);
	        barcodeButton = (ImageButton)findViewById(R.id.barcodeButton);
	        
	        filterSelect = (Spinner)findViewById(R.id.entity_select_filter_dropdown);
	        
	        listView = (ListView)findViewById(R.id.screen_entity_select_list);
	        listView.setOnItemClickListener(this);
	        
	        header.setVisibility(View.GONE);
	        barcodeButton.setVisibility(View.GONE);
	        
	        TextView searchLabel = (TextView)findViewById(R.id.screen_entity_select_search_label);
	        searchLabel.setText(Localization.get("select.search.label"));
	        
	        searchbox.addTextChangedListener(this);
	        FormRecordLoaderTask task = new FormRecordLoaderTask(this, CommCareApplication._().getUserStorage(SessionStateDescriptor.class), platform);
	        task.setListener(this);
	
	        adapter = new IncompleteFormListAdapter(this, platform, task);
	        
	        FormRecordFilter filter = null;
	        
	        initialSelection = this.getIntent().getIntExtra(KEY_INITIAL_RECORD_ID, -1);
	        
	        if(this.getIntent().hasExtra(FormRecord.META_STATUS)) {
	        	String incomingFilter = this.getIntent().getStringExtra(FormRecord.META_STATUS);
	        	if(incomingFilter.equals(FormRecord.STATUS_INCOMPLETE)) {
	        		setTitle(getString(R.string.application_name) + " > " + Localization.get("app.workflow.incomplete.heading"));
	        		//special case, no special filtering options
	        		filter = FormRecordFilter.Incomplete;
	        	}
	        } else {
	        	setTitle(getString(R.string.application_name) + " > " + Localization.get("app.workflow.saved.heading"));
	        	
	        	filter = FormRecordFilter.SubmittedAndPending; 

	        	FormRecordFilter[] filters = FormRecordFilter.values();
	        	String[] names = new String[filters.length];
	        	for(int i = 0 ; i < filters.length; ++i ) {
	        		names[i] = Localization.get(filters[i].getMessage());
	        	}
	        	ArrayAdapter<String> spinneritems = new ArrayAdapter<String>(this, R.layout.form_filter_display, names);
	        	filterSelect.setAdapter(spinneritems);
	        	spinneritems.setDropDownViewResource(R.layout.form_filter_item);
	        	filterSelect.setOnItemSelectedListener(new OnItemSelectedListener() {
	        		
					@Override
					public void onItemSelected(AdapterView<?> arg0, View arg1, int index, long id) {
						adapter.setFormFilter(FormRecordFilter.values()[index]);
						adapter.resetRecords();
						adapter.notifyDataSetChanged();
					}

					@Override
					public void onNothingSelected(AdapterView<?> arg0) {
						// TODO Auto-generated method stub
						
					}
	        	});
	        	filterSelect.setVisibility(View.VISIBLE);
	        }
	        
	        if(filter != null) {
	        	adapter.setFormFilter(filter);
	        }
	        this.registerForContextMenu(listView);
	        refreshView();
        } catch(SessionUnavailableException sue) {
        	//TODO: session is dead, login and return
        }
    }


    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
    	disableSearch();
    	adapter.resetRecords();
    	listView.setAdapter(adapter);
    }
    
    protected void onResume() {
    	super.onResume();
    	if(adapter != null && initialSelection != -1) {
    		listView.setSelection(adapter.findRecordPosition(initialSelection));
    	}
    }
    
    protected void disableSearch() {
    	searchbox.setEnabled(false);
    }


    protected void enableSearch() {
    	searchbox.setEnabled(true);
	}


	/**
     * Stores the path of selected form and finishes.
     */
	@Override
	public void onItemClick(AdapterView<?> listView, View view, int position, long id) {
    	returnItem(position);
    }
    
    private void returnItem(int position) {
    	FormRecord value = (FormRecord)adapter.getItem(position);
    	
        // We want to actually launch an interactive form entry.
        Intent i = new Intent();
        i.putExtra("FORMRECORDS", value.getID());
        setResult(RESULT_OK, i);

        finish();
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
        IncompleteFormRecordView ifrv = (IncompleteFormRecordView)adapter.getView(info.position, null, null);
        menu.setHeaderTitle(ifrv.mPrimaryTextView.getText() + " (" + ifrv.mRightTextView.getText() + ")");
        
        menu.add(Menu.NONE, OPEN_RECORD, OPEN_RECORD, Localization.get("app.workflow.forms.open"));
        menu.add(Menu.NONE, DELETE_RECORD, DELETE_RECORD, Localization.get("app.workflow.forms.delete"));        
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	try {
	      AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
	      switch(item.getItemId()) {
	      case OPEN_RECORD:
	    	  returnItem(info.position);
	    	  return true;
	      case DELETE_RECORD:
	    	  new FormRecordCleanupTask(this, platform).wipeRecord(CommCareApplication._().getUserStorage(FormRecord.class).read((int)info.id));
	    	  listView.post(new Runnable() { public void run() {adapter.notifyDataSetInvalidated();}});
	      }
	      
	      return true;
    	} catch(SessionUnavailableException sue) {
    		//TODO: Login and try again
    		return true;
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean parent = super.onCreateOptionsMenu(menu);
        if(!FormRecordFilter.Incomplete.equals(adapter.getFilter())) {
        	SharedPreferences prefs =CommCareApplication._().getCurrentApp().getAppPreferences();
    		String source = prefs.getString("form-record-url", this.getString(R.string.form_record_url));
    		
    		//If there's nowhere to fetch forms from, we can't really go fetch them
    		if(!(source == null || source.equals(""))) {
    			menu.add(0, DOWNLOAD_FORMS, 0, Localization.get("app.workflow.forms.fetch")).setIcon(android.R.drawable.ic_menu_rotate);
    		}
	        return true;
        }
        return parent;
    }

    TextToSpeech mTts;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case DOWNLOAD_FORMS:
            	this.showDialog(DIALOG_PROCESS);
            	
        		SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
            	
            	User u = CommCareApplication._().getSession().getLoggedInUser();
            	
            	String source = prefs.getString("form-record-url", this.getString(R.string.form_record_url));
            	
            	//We should go digest auth this user on the server and see whether to pull them
				//down.
            	DataPullTask pull = new DataPullTask(u.getUsername(),u.getCachedPwd(), source, "", this);
            	pull.setPullListener(new DataPullListener() {

					public void finished(int status) {
						switch(status) {
						case DataPullTask.DOWNLOAD_SUCCESS:
							mProgressDialog.setMessage("Forms downloaded. Processing...");
							FormRecordCleanupTask task = new FormRecordCleanupTask(FormRecordListActivity.this, platform) {
	
								@Override
								protected void onPostExecute(Integer result) {
									super.onPostExecute(result);
									unlock();
									mProgressDialog.setMessage("Forms Processed.");
									FormRecordListActivity.this.dismissDialog(DIALOG_PROCESS);
									FormRecordListActivity.this.refreshView();
								}
								
								@Override
								protected void onProgressUpdate(Integer ... values) {
									if(values[0] < 0) {
										if(values[0] == FormRecordCleanupTask.STATUS_CLEANUP) {
											mProgressDialog.setMessage("Forms Processed. Cleaning up form records...");
										}
									}
									else {
										mProgressDialog.setMessage("Forms downloaded. Processing " + values[0] + " of " + values[1] +"...");
									}
								}
								
								
							};
							task.execute();
							break;
						case DataPullTask.UNKNOWN_FAILURE:
							unlock();
							Toast.makeText(FormRecordListActivity.this, "Failure retrieving or processing data, please try again later...", Toast.LENGTH_LONG).show();
							FormRecordListActivity.this.dismissDialog(DIALOG_PROCESS);
							break;
						case DataPullTask.AUTH_FAILED:
							unlock();
							Toast.makeText(FormRecordListActivity.this, "Authentication failure. Please logout and resync with the server and try again.", Toast.LENGTH_LONG).show();
							FormRecordListActivity.this.dismissDialog(DIALOG_PROCESS);
							break;
						case DataPullTask.BAD_DATA:
							unlock();
							Toast.makeText(FormRecordListActivity.this, "Bad data from server. Please talk with your supervisor.", Toast.LENGTH_LONG).show();
							FormRecordListActivity.this.dismissDialog(DIALOG_PROCESS);
							break;
						case DataPullTask.UNREACHABLE_HOST:
							unlock();
							Toast.makeText(FormRecordListActivity.this, "Couldn't contact server, please check your network connection and try again.", Toast.LENGTH_LONG).show();
							FormRecordListActivity.this.dismissDialog(DIALOG_PROCESS);
							break;
							
						}
					}

					public void progressUpdate(Integer ... progress) {
						switch(progress[0]){
						case DataPullTask.PROGRESS_AUTHED:
							mProgressDialog.setMessage("Authed with server, downloading forms" + (progress[1] == 0 ? "" : " (" +progress[1] + ")"));
							break;
						}
					}
            		
            	});
            	
            	wakelock();
            	
            	pull.execute();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    //Don't ever lose this reference
    private static WakeLock wakelock;
    
    private void wakelock() {
    	if(wakelock != null) {
    		if(wakelock.isHeld()) {
    			wakelock.release();
    		}
    	}
    	PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    	wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CommCareFormSync");
    	//TODO: We need to remove this time limit. Android has a bug that crashes
    	//if you release with a time limit
    	wakelock.acquire(1000*60*20);
    }
    
    private void unlock() {
    	if(wakelock != null) {
    		wakelock.release();
    	}
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	adapter.release();
    	//Make sure we're not holding onto the wake lock still, no matter what
    	unlock();
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
                mProgressDialog.setTitle("Fetching Old Forms");
                mProgressDialog.setMessage("Connecting to server...");
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                return mProgressDialog;
        }
        return null;
    }
    
	public void afterTextChanged(Editable s) {
		if(searchbox.getText() == s) {
			adapter.applyTextFilter(s.toString());
		}
	}


	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		//nothing
	}


	public void onTextChanged(CharSequence s, int start, int before, int count) {
		//nothing		
	}


	public void notifyPriorityLoaded(Integer record, boolean priority) {
		if(priority) {
			adapter.notifyDataSetChanged();
		}
	}


	public void notifyLoaded() {
		enableSearch();
	}
}
