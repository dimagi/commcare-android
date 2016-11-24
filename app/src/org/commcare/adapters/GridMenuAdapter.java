package org.commcare.adapters;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
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

        MenuDisplayable menuDisplayable = displayableData[i];

        TextView rowText = (TextView)menuListItem.findViewById(R.id.row_txt);
        setupTextView(rowText, menuDisplayable);

        // set up the image, if available
        ImageView mIconView = (ImageView)menuListItem.findViewById(R.id.row_img);
        setupImageView(mIconView, menuDisplayable);

        setupNumericBadgeView(menuListItem, menuDisplayable);

        return menuListItem;
    }

    private void setupNumericBadgeView(View menuListItem, MenuDisplayable menuDisplayable) {
        View badgeCountView = menuListItem.findViewById(R.id.badge_count_view);
        TextView countText = (TextView)menuListItem.findViewById(R.id.badge_view_text);

        int count = menuDisplayable.getCountForNumericBadge(asw.getEvaluationContext());
        if (count == -1) {
            badgeCountView.setVisibility(View.GONE);
        } else {
            countText.setText("" + count);
            badgeCountView.setVisibility(View.VISIBLE);
        }
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
