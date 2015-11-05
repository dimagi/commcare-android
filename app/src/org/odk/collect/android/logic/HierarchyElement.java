package org.odk.collect.android.logic;

import android.content.Context;
import android.graphics.drawable.Drawable;

import org.commcare.dalvik.R;
import org.javarosa.core.model.FormIndex;

import java.util.ArrayList;

public class HierarchyElement {
    private String mPrimaryText = "";
    private String mSecondaryText = "";
    private Drawable mIcon;
    private HierarchyEntryType mType;
    private final FormIndex mFormIndex;
    private final ArrayList<HierarchyElement> mChildren;

    private static final int ERROR_BG_COLOR_ID= R.color.cc_error_bg_color;
    private static final int ERROR_TEXT_COLOR_ID = R.color.cc_error_text_color;
    private static final int DEFAULT_BG_COLOR_ID = R.color.white;
    private static final int DEFAULT_TEXT_COLOR_ID = R.color.cc_dark_warm_accent_text;

    private final int bgColor;
    private final int textColor;

    public HierarchyElement(Context context, String text1, String text2,
                            Drawable bullet, boolean isError,
                            HierarchyEntryType type, FormIndex f) {
        mIcon = bullet;
        mPrimaryText = text1;
        mSecondaryText = text2;
        mFormIndex = f;
        mType = type;
        mChildren = new ArrayList<>();
        if (isError) {
            bgColor = context.getResources().getColor(ERROR_BG_COLOR_ID);
            textColor = context.getResources().getColor(ERROR_TEXT_COLOR_ID);
        } else {
            bgColor = context.getResources().getColor(DEFAULT_BG_COLOR_ID);
            textColor = context.getResources().getColor(DEFAULT_TEXT_COLOR_ID);
        }
    }

    public String getPrimaryText() {
        return mPrimaryText;
    }

    public String getSecondaryText() {
        return mSecondaryText;
    }

    public void setIcon(Drawable icon) {
        mIcon = icon;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public FormIndex getFormIndex() {
        return mFormIndex;
    }

    public HierarchyEntryType getType() {
        return mType;
    }

    public void setType(HierarchyEntryType newType) {
        mType = newType;
    }

    public ArrayList<HierarchyElement> getChildren() {
        return mChildren;
    }

    public void addChild(HierarchyElement h) {
        mChildren.add(h);
    }

    public int getTextColor() {
        return textColor;
    }

    public int getBgColor() {
        return bgColor;
    }
}
