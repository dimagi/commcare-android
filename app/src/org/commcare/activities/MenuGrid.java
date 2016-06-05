package org.commcare.activities;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.GridView;

import org.commcare.CommCareApplication;
import org.commcare.adapters.GridMenuAdapter;
import org.commcare.adapters.MenuAdapter;
import org.commcare.dalvik.R;
import org.commcare.suite.model.MenuDisplayable;
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
public class MenuGrid extends MenuBase implements OnItemLongClickListener {
    private MenuAdapter adapter;
    
    @UiElement(R.id.grid_menu_grid)
    private GridView grid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

       adapter = new GridMenuAdapter(this, CommCareApplication._().getCommCarePlatform(), menuId);
       adapter.showAnyLoadErrors(this);
       refreshView();
       
       grid.setOnItemClickListener(this);
       grid.setOnItemLongClickListener(this);
    }

    /**
     * Get form list from database and insert into view.
     */
    private void refreshView() {
        grid.setAdapter(adapter);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view,
            int position, long id) {
        MenuDisplayable value = (MenuDisplayable)parent.getAdapter().getItem(position);
        String audioURI = value.getAudioURI();
        String audioFilename;
        
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
}
