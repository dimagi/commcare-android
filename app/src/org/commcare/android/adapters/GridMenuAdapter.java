package org.commcare.android.adapters;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.commcare.suite.model.MenuDisplayable;
import org.commcare.util.CommCarePlatform;

/**
 * Overrides MenuAdapter to provide a different tile (MenuGridEntryView)
 * instead of the normal row view
 *
 * @author wspride
 */

public class GridMenuAdapter extends MenuAdapter {

    public GridMenuAdapter(Context context, CommCarePlatform platform,
                           String menuID) {
        super(context, platform, menuID);
    }

    @Override
    public View getView(int i, View v, ViewGroup vg) {

        // inflate view
        View menuListItem = v;

        if (menuListItem == null) {
            // inflate it and do not attach to parent, or we will get the 'addView not supported' exception
            menuListItem = LayoutInflater.from(context).inflate(R.layout.menu_grid_item, vg, false);
        }

        MenuDisplayable mObject = displayableData[i];

        TextView rowText = (TextView) menuListItem.findViewById(R.id.row_txt);
        setupTextView(rowText, mObject);

        // set up the image, if available
        ImageView mIconView = (ImageView) menuListItem.findViewById(R.id.row_img);
        setupImageView(mIconView, mObject);

        return menuListItem;

    }
}
