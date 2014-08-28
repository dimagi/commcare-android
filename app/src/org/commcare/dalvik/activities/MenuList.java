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
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareSession;
import org.commcare.util.SessionFrame;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;


@ManagedUi(R.layout.screen_suite_menu)
public class MenuList extends CommCareActivity implements OnItemClickListener {
    
    private CommCarePlatform platform;
    
    private GenericMenuListAdapter adapter;
    
    @UiElement(R.id.screen_suite_menu_list)
    private ListView list;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        platform = CommCareApplication._().getCommCarePlatform();
        
        String menuId = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);
        
       if(menuId==null){
           menuId="root";
       }
       
       adapter = new GenericMenuListAdapter(this,platform,menuId);
       refreshView();
       
       list.setOnItemClickListener(this);
    }


    @Override
    protected boolean isTopNavEnabled() {
        return true;
    }
    
    @Override
    public String getActivityTitle() {
        //return adapter.getMenuTitle();
        return null;
    }


    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
        list.setAdapter(adapter);
    }


    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    public void onItemClick(AdapterView listView, View view, int position, long id) {
        String commandId;
        Object value = listView.getAdapter().getItem(position);
        if(value instanceof Entry) {
            commandId = ((Entry)value).getCommandId();
        } else {
            commandId = ((Menu)value).getId();
        }

        // create intent for return and store path
        Intent i = new Intent();
        i.putExtra(SessionFrame.STATE_COMMAND_ID, commandId);
        setResult(RESULT_OK, i);

        finish();
    }

}
