package org.commcare.activities;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.GridView;

import org.commcare.CommCareApplication;
import org.commcare.adapters.GridMenuAdapter;
import org.commcare.adapters.MenuAdapter;
import org.commcare.dalvik.R;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.Menu;
import org.commcare.suite.model.MenuDisplayable;
import org.commcare.util.CommCarePlatform;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.IOException;

/**
 * Handles the alternative Grid appearance for Module and Form navigation
 * 
 * @author wspride
 */
@ManagedUi(R.layout.grid_menu_layout)
public class MenuGrid extends SaveSessionCommCareActivity implements OnItemClickListener, OnItemLongClickListener {
    private MenuAdapter adapter;
    
    @UiElement(R.id.grid_menu_grid)
    private GridView grid;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
        
        String menuId = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);
        
       if (menuId == null) {
           menuId = Menu.ROOT_MENU_ID;
       }
       
       adapter = new GridMenuAdapter(this, platform,menuId);
       adapter.showAnyLoadErrors(this);
       refreshView();
       
       grid.setOnItemClickListener(this);
       grid.setOnItemLongClickListener(this);
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
        grid.setAdapter(adapter);
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
        Intent i = new Intent(getIntent());
        i.putExtra(SessionFrame.STATE_COMMAND_ID, commandId);
        setResult(RESULT_OK, i);

        finish();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view,
            int position, long id) {
        MenuDisplayable value = (MenuDisplayable)parent.getAdapter().getItem(position);
        String audioURI = value.getAudioURI();
        String audioFilename = "";
        
        MediaPlayer mp = new MediaPlayer();
        
        if(audioURI != null && !audioURI.equals("")) {
            try {
                audioFilename = ReferenceManager._().DeriveReference(audioURI).getLocalURI();
                
                mp.setDataSource(audioFilename);
                mp.prepare();
                mp.start();
                
            } catch (IOException | IllegalStateException
                    | InvalidReferenceException e) {
                e.printStackTrace();
            }
        }
        
        return false;
    }

    @Override
    protected boolean onBackwardSwipe() {
        onBackPressed();
        return true;
    }

}
