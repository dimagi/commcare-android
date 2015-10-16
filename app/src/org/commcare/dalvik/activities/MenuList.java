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

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

import org.commcare.android.adapters.MenuAdapter;
import org.commcare.android.framework.BreadcrumbBarFragment;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.SessionAwareCommCareActivity;
import org.commcare.android.framework.UiElement;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.util.CommCarePlatform;


@ManagedUi(R.layout.screen_suite_menu)
public class MenuList extends SessionAwareCommCareActivity implements OnItemClickListener {
    
    private CommCarePlatform platform;
    
    private MenuAdapter adapter;
    
    @UiElement(R.id.screen_suite_menu_list)
    private ListView list;

    // removed the UiElement annotation here because it was causing a crash @ loadFields() in CommCareActivity
    private TextView header;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        platform = CommCareApplication._().getCommCarePlatform();
        String menuId = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);
        
       if(menuId==null){
           menuId=Menu.ROOT_MENU_ID;
       }

       if(header == null) {
           header = (TextView) getLayoutInflater().inflate(R.layout.menu_list_header, null);
       }
       header.setText(BreadcrumbBarFragment.getBestTitle(this));
       // header must not be clickable
       list.addHeaderView(header, null, false);
       
       adapter = new MenuAdapter(this,platform,menuId);
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
        // if value is null, probably it means that we clicked on the header view, so we just ignore it
        if(value == null) {
            if(BuildConfig.DEBUG) {
                Log.d("MenuList", "Null value on position " + position);
            }
            return;
        }
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

    protected boolean onBackwardSwipe() {
        onBackPressed();
        return true;
    }
}
