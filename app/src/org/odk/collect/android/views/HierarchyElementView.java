/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.views;

import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.odk.collect.android.logic.HierarchyElement;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class HierarchyElementView extends RelativeLayout {

    private TextView mPrimaryTextView;
    private TextView mSecondaryTextView;
    private ImageView mIcon;


    public HierarchyElementView(Context context, HierarchyElement it) {
        super(context);

        RelativeLayout layout = (RelativeLayout) inflate(context, R.layout.hierarchy_element_view, null);

        layout.setBackgroundColor(it.getColor());

        mPrimaryTextView = ((TextView)layout.findViewById(R.id.hev_primary_text));
        mPrimaryTextView.setText(it.getPrimaryText());
        mSecondaryTextView = ((TextView)layout.findViewById(R.id.hev_secondary_text));
        mSecondaryTextView.setText(it.getSecondaryText());
        mIcon = ((ImageView)layout.findViewById(R.id.hev_icon));
        mIcon.setImageDrawable(it.getIcon());

        addView(layout);

        if (BuildConfig.DEBUG) {
            Log.i("HEVTYPE", "Type of HEV (" + hashCode() + ") is " + it.getType());
            Log.i("HEVTYPE", "Icon of HEV (" + hashCode() + ") is " + (it.getIcon() == null ? "null" : it.getIcon().toString()));
        }
    }


    public void setPrimaryText(String text) {
        mPrimaryTextView.setText(text);
    }


    public void setSecondaryText(String text) {
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
    
    public int dipToPx(int dip) {
        return (int) (dip * getResources().getDisplayMetrics().density + 0.5f);
    }

}
