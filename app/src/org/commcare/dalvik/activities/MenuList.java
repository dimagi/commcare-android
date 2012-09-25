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

import org.commcare.android.adapters.GenericMenuListAdapter;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareSession;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;


public class MenuList extends ListActivity {
	
	private CommCarePlatform platform;
	
	private ListAdapter adapter;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        platform = CommCareApplication._().getCommCarePlatform();
        setContentView(R.layout.suite_menu_layout);
        
        String menuId = getIntent().getStringExtra(CommCareSession.STATE_COMMAND_ID);
        
       if(menuId==null){
    	   menuId="root";
       }
       
       adapter = new GenericMenuListAdapter(this,platform,menuId);
       setTitle(getString(R.string.application_name));
       refreshView();
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
    	String commandId;
    	Object value = getListAdapter().getItem(position);
    	if(value instanceof Entry) {
    		commandId = ((Entry)value).getCommandId();
    	} else {
    		commandId = ((Menu)value).getId();
    	}

        // create intent for return and store path
        Intent i = new Intent();
        i.putExtra(CommCareSession.STATE_COMMAND_ID, commandId);
        setResult(RESULT_OK, i);

        finish();
    }

}
