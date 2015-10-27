package org.odk.collect.android.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
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

        setBackgroundColor(it.getBgColor());

        mPrimaryTextView = ((TextView)layout.findViewById(R.id.hev_primary_text));
        mPrimaryTextView.setText(it.getPrimaryText());
        mPrimaryTextView.setTextColor(it.getTextColor());
        mSecondaryTextView = ((TextView)layout.findViewById(R.id.hev_secondary_text));
        mSecondaryTextView.setText(it.getSecondaryText());
        mSecondaryTextView.setTextColor(it.getTextColor());
        mIcon = ((ImageView)layout.findViewById(R.id.hev_icon));
        mIcon.setImageDrawable(it.getIcon());

        addView(layout);
    }


    public void setPrimaryText(String text, int color) {
        mPrimaryTextView.setTextColor(color);
        mPrimaryTextView.setText(text);
    }

    public void setSecondaryText(String text, int color) {
        mSecondaryTextView.setTextColor(color);
        mSecondaryTextView.setText(text);
    }

    public void setIcon(Drawable icon) {
        mIcon.setImageDrawable(icon);
    }

    public void setColor(int color) {
        setBackgroundColor(color);
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
