/*
 * Copyright (C) 2014 Dimagi
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

import java.io.IOException;

import org.commcare.android.adapters.GridMenuAdapter;
import org.commcare.android.adapters.MenuAdapter;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.SessionFrame;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.GridView;

/**
 * Handles the alternative Grid appearance for Module and Form navigation
 * 
 * @author wspride
 *
 */

@ManagedUi(R.layout.grid_menu_layout)
public class MenuGrid extends CommCareActivity implements OnItemClickListener, OnItemLongClickListener {
    
    private CommCarePlatform platform;
    
    private MenuAdapter adapter;
    
    @UiElement(R.id.grid_menu_grid)
    private GridView grid;
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        platform = CommCareApplication._().getCommCarePlatform();
        
        String menuId = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);
        
       if(menuId==null){
           menuId="root";
       }
       
       adapter = new GridMenuAdapter(this,platform,menuId);
       refreshView();
       
       grid.setOnItemClickListener(this);
       grid.setOnItemLongClickListener(this);
    }


    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#isTopNavEnabled()
     */
    @Override
    protected boolean isTopNavEnabled() {
        return true;
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#getActivityTitle()
     */
    @Override
    public String getActivityTitle() {
        //return adapter.getMenuTitle();
        return null;
    }


    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
        grid.setAdapter(adapter);
    }


    /*
     * (non-Javadoc)
     * @see android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget.AdapterView, android.view.View, int, long)
     * 
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

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view,
            int position, long id) {
        Object value = parent.getAdapter().getItem(position);
        String audioURI = adapter.getAudioURI(value);
        String audioFilename = "";
        
        MediaPlayer mp = new MediaPlayer();
        
        if(audioURI != null && !audioURI.equals("")) {
            try {
                audioFilename = ReferenceManager._().DeriveReference(audioURI).getLocalURI();
                
                mp.setDataSource(audioFilename);
                mp.prepare();
                mp.start();
                
            } catch (InvalidReferenceException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        return false;
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onBackwardSwipe()
     */
    protected boolean onBackwardSwipe() {
        finish();
        return true;
    }
}
