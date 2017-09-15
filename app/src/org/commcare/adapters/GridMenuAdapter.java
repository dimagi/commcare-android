package org.commcare.adapters;


import android.view.View;
import android.widget.ImageView;

import org.commcare.activities.CommCareActivity;
import org.commcare.dalvik.R;
import org.commcare.util.CommCarePlatform;

/**
 * Overrides MenuAdapter to provide a different tile (MenuGridEntryView)
 * instead of the normal row view
 *
 * @author wspride
 */

public class GridMenuAdapter extends MenuAdapter {

    public GridMenuAdapter(CommCareActivity context, CommCarePlatform platform,
                           String menuID) {
        super(context, platform, menuID);
    }

    @Override
    protected int getImageViewDimenResource() {
        return R.dimen.list_grid_bounding_dimension;
    }

    @Override
    protected int getListItemLayoutResource() {
        return R.layout.menu_grid_item;
    }

    @Override
    protected void setupDefaultIcon(ImageView mIconView, NavIconState iconChoice) {
        if (mIconView != null) {
            switch (iconChoice) {
                case NEXT:
                    mIconView.setImageResource(R.drawable.avatar_module_grid);
                    break;
                case JUMP:
                    mIconView.setImageResource(R.drawable.avatar_form_grid);
                    break;
                case NONE:
                    mIconView.setVisibility(View.GONE);
                    break;
            }
        }
    }
}
