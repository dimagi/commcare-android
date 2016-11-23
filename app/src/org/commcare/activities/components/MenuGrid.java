package org.commcare.activities.components;

import android.media.MediaPlayer;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.GridView;

import org.commcare.CommCareApplication;
import org.commcare.adapters.GridMenuAdapter;
import org.commcare.dalvik.R;
import org.commcare.suite.model.MenuDisplayable;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.IOException;

/**
 * Handles the alternative Grid appearance for Module and Form navigation
 * 
 * @author wspride
 */
public class MenuGrid extends MenuList implements OnItemLongClickListener {

    @Override
    public int getLayoutFileResource() {
        return R.layout.grid_menu_layout;
    }

    @Override
    protected void initViewAndAdapter(String menuId) {
        adapterView = (GridView)activity.findViewById(R.id.grid_menu_grid);
        adapterView.setOnItemLongClickListener(this);
        adapter = new GridMenuAdapter(activity,
                CommCareApplication.instance().getCommCarePlatform(), menuId);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view,
            int position, long id) {
        MenuDisplayable value = (MenuDisplayable)parent.getAdapter().getItem(position);
        String audioURI = value.getAudioURI();
        MediaPlayer mp = new MediaPlayer();
        String audioFilename;
        if (audioURI != null && !audioURI.equals("")) {
            try {
                audioFilename = ReferenceManager.instance().DeriveReference(audioURI).getLocalURI();
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
