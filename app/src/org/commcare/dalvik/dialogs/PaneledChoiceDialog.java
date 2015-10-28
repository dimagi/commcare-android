package org.commcare.dalvik.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.dalvik.R;

/**
 * Created by amstone326 on 10/27/15.
 */
public class PaneledChoiceDialog {

    private View view;
    private Context context;
    private ChoiceDialogType type;
    private AlertDialog dialog;

    public enum ChoiceDialogType {
        TWO_PANEL, THREE_PANEL
    }

    public PaneledChoiceDialog(Context context, ChoiceDialogType type, String title) {
        this.context = context;
        this.dialog = new AlertDialog.Builder(context).create();
        this.type = type;

        if (type == ChoiceDialogType.THREE_PANEL) {
            view = LayoutInflater.from(context).inflate(R.layout.choice_dialog_three_panel, null);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.choice_dialog_two_panel, null);
        }

        setTitle(title);
        dialog.setCancelable(true); // cancelable by default
    }

    private void setTitle(String title) {
        TextView tv = (TextView) view.findViewById(R.id.choice_dialog_title);
        tv.setText(title);
    }

    public void makeNotCancelable() {
        dialog.setCancelable(false);
    }

    public void addPanel1(String buttonText, int iconResId, View.OnClickListener listener) {
        Button panel = (Button) view.findViewById(R.id.choice_dialog_panel_1);
        panel.setText(buttonText);
        panel.setOnClickListener(listener);
        Drawable icon = context.getResources().getDrawable(iconResId);
        if (type == ChoiceDialogType.THREE_PANEL) {
            panel.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
        } else {
            panel.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        }
    }

    public void addPanel2(String buttonText, int iconResId, View.OnClickListener listener) {
        Button panel = (Button) view.findViewById(R.id.choice_dialog_panel_2);
        panel.setText(buttonText);
        panel.setOnClickListener(listener);
        Drawable icon = context.getResources().getDrawable(iconResId);
        if (type == ChoiceDialogType.THREE_PANEL) {
            panel.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
        } else {
            panel.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        }
    }

    public void addPanel3(String buttonText, int iconResId, View.OnClickListener listener) {
        if (type != ChoiceDialogType.THREE_PANEL) {
            return;
        }
        Button panel = (Button) view.findViewById(R.id.choice_dialog_panel_3);
        panel.setText(buttonText);
        panel.setOnClickListener(listener);
        Drawable icon = context.getResources().getDrawable(iconResId);
        panel.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
    }

    public void show() {
        dialog.setView(view);
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }
}
