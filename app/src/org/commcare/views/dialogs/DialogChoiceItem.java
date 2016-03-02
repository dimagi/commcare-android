package org.commcare.views.dialogs;

import android.view.View;

/**
 * Created by amstone326 on 10/30/15.
 */
public class DialogChoiceItem {

    public final String text;
    public final int iconResId;
    public final View.OnClickListener listener;

    public static final boolean ICON_TO_LEFT = true;
    public static final boolean ICON_ON_TOP = false;

    public DialogChoiceItem(String text, int iconResId, View.OnClickListener listener) {
        this.text = text;
        this.iconResId = iconResId;
        this.listener = listener;
    }
}

