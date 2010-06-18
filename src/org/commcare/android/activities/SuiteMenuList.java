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
import org.commcare.android.adapters.MenuListAdapter;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.util.CommCarePlatformProvider;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.Suite;
import org.commcare.util.CommCarePlatform;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;


public class SuiteMenuList extends ListActivity {
	
	private CommCarePlatform platform;
	private Menu m;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.suite_menu_layout);
        
        String menuId = getIntent().getStringExtra(GlobalConstants.MENU_ID);
        platform = CommCarePlatformProvider.unpack(getIntent().getBundleExtra(GlobalConstants.COMMCARE_PLATFORM));
        
        for(Suite s : platform.getInstalledSuites()) {
        	for(Menu m : s.getMenus()) {
        		if(m.getId().equals(menuId)) {
        			this.m = m;
        		}
        	}
        }
        
        setTitle(getString(R.string.app_name) + " > " + m.getName().evaluate());
        
        refreshView();
    }


    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
    	setListAdapter(new MenuListAdapter(this, platform, m));
    }


    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
    	Entry e = (Entry)getListAdapter().getItem(position);

        // create intent for return and store path
        Intent i = new Intent();
        i.putExtra(GlobalConstants.COMMAND_ID, e.getCommandId());
        setResult(RESULT_OK, i);

        finish();
    }

}
