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

package org.commcare.android.activities;

import org.commcare.android.R;
import org.commcare.android.adapters.IncompleteFormListAdapter;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.models.FormRecord;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.view.IncompleteFormRecordView;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;


public class FormRecordListActivity extends ListActivity {
	
	private static final int OPEN_RECORD = Menu.FIRST;
	private static final int DELETE_RECORD = Menu.FIRST  + 1;
	
	private AndroidCommCarePlatform platform;
	
	private IncompleteFormListAdapter adapter;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
	        platform = CommCareApplication._().getCommCarePlatform();
	        setContentView(R.layout.suite_menu_layout);
	
	        adapter = new IncompleteFormListAdapter(this, platform);
	        
	        if(this.getIntent().hasExtra(FormRecord.META_STATUS)) {
	        	String statusFilter = this.getIntent().getStringExtra(FormRecord.META_STATUS);
	        	if(statusFilter.equals(FormRecord.STATUS_INCOMPLETE)) {
	        		setTitle(getString(R.string.app_name) + " > " + "Incomplete Forms");
	        	} else {
	        		setTitle(getString(R.string.app_name) + " > " + "Saved Forms");
	        	}
	        	adapter.setFormFilter(statusFilter);
	        } else {
	        	setTitle(getString(R.string.app_name) + " > " + "Saved Forms");
	        }
	        this.registerForContextMenu(this.getListView());
	        refreshView();
        } catch(SessionUnavailableException sue) {
        	//TODO: session is dead, login and return
        }
    }


    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
    	setListAdapter(adapter);
    }


    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
    	returnItem(position);
    }
    
    private void returnItem(int position) {
    	FormRecord value = (FormRecord)getListAdapter().getItem(position);

        // create intent for return and store path
        Intent i = new Intent();
        i.putExtra(FormRecord.STORAGE_KEY, value.getID());
        setResult(RESULT_OK, i);

        finish();
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
        IncompleteFormRecordView ifrv = (IncompleteFormRecordView)adapter.getView(info.position, null, null);
        menu.setHeaderTitle(ifrv.mPrimaryTextView.getText() + " (" + ifrv.mRightTextView.getText() + ")");
        
        menu.add(Menu.NONE, OPEN_RECORD, OPEN_RECORD, "Open");
        menu.add(Menu.NONE, DELETE_RECORD, DELETE_RECORD, "Delete Entry");
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
	    	  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class).remove((int)info.id);
	    	  this.getListView().post(new Runnable() { public void run() {adapter.notifyDataSetChanged();}});
	      }
	      
	      return true;
    	} catch(SessionUnavailableException sue) {
    		//TODO: Login and try again
    		return true;
    	}
    }

}
