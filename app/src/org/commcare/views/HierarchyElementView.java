package org.commcare.views;

import android.content.Context;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.commcare.dalvik.R;
import org.odk.collect.android.logic.HierarchyElement;

public class HierarchyElementView extends RelativeLayout {
    private final TextView mPrimaryTextView;
    private final TextView mSecondaryTextView;
    private final ImageView mIcon;

    public HierarchyElementView(Context context, HierarchyElement it) {
        super(context);

        RelativeLayout layout = (RelativeLayout)inflate(context, R.layout.hierarchy_element_view, null);

        mPrimaryTextView = ((TextView)layout.findViewById(R.id.hev_primary_text));
        mSecondaryTextView = ((TextView)layout.findViewById(R.id.hev_secondary_text));
        mIcon = ((ImageView)layout.findViewById(R.id.hev_icon));

        setFromHierarchyElement(it);

        addView(layout);
    }

    public void setFromHierarchyElement(HierarchyElement hierarchyElement) {
        final int textColor = hierarchyElement.getTextColor();

        setBackgroundColor(hierarchyElement.getBgColor());

        mPrimaryTextView.setTextColor(textColor);
        mPrimaryTextView.setText(hierarchyElement.getPrimaryText());

        mSecondaryTextView.setTextColor(textColor);
        mSecondaryTextView.setText(hierarchyElement.getSecondaryText());

        mIcon.setImageDrawable(hierarchyElement.getIcon());
    }

    public void showSecondary(boolean bool) {
        if (bool) {
            mSecondaryTextView.setVisibility(VISIBLE);
            setMinimumHeight(dipToPx(64));
        } else {
            mSecondaryTextView.setVisibility(GONE);
            setMinimumHeight(dipToPx(32));
        }
    }

    private int dipToPx(int dip) {
        return (int)(dip * getResources().getDisplayMetrics().density + 0.5f);
    }
}
