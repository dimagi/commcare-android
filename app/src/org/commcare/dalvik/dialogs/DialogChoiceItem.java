package org.commcare.dalvik.dialogs;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Button;

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

    public static void populateSingleChoicePanel(Context context, Button choicePanel,
                                                 DialogChoiceItem item, boolean iconToLeft) {
        choicePanel.setText(item.text);
        choicePanel.setOnClickListener(item.listener);
        if (item.iconResId != -1) {
            Drawable icon = context.getResources().getDrawable(item.iconResId);
            if (iconToLeft) {
                choicePanel.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
            } else {
                choicePanel.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
            }
        }
    }
}

