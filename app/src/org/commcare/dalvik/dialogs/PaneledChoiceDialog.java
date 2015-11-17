package org.commcare.dalvik.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.commcare.dalvik.R;

/**
 * Created by amstone326 on 10/27/15.
 */
public class PaneledChoiceDialog {

    private View view;
    private Context context;
    private AlertDialog dialog;
    private boolean usingThreePanelView;

    public static boolean HORIZONTAL_THREE_PANEL = true;

    public PaneledChoiceDialog(Context context, String title) {
        this(context, title, false);
    }

    public PaneledChoiceDialog(Context context, String title, boolean useSpecialThreePanelView) {
        this.context = context;
        this.dialog = new AlertDialog.Builder(context).create();
        this.usingThreePanelView = useSpecialThreePanelView;
        if (usingThreePanelView) {
            this.view = LayoutInflater.from(context).inflate(R.layout.choice_dialog_three_panel, null);
        } else {
            this.view = LayoutInflater.from(context).inflate(R.layout.choice_dialog_view, null);
        }
        setTitle(title);
        dialog.setCancelable(true); // cancelable by default
    }

    public void setChoiceItems(DialogChoiceItem[] choiceItems) {
        if (usingThreePanelView) {
             setupThreePanelView(choiceItems);
        } else {
            ListView lv = (ListView)view.findViewById(R.id.choices_list_view);
            lv.setAdapter(new ChoiceDialogAdapter(context, android.R.layout.simple_list_item_1, choiceItems));
        }
    }

    private void setTitle(String title) {
        TextView tv = (TextView) view.findViewById(R.id.choice_dialog_title).
                findViewById(R.id.dialog_title_text);
        tv.setText(title);
    }

    private void setupThreePanelView(DialogChoiceItem[] choiceItems) {
        Button panel1 = (Button)view.findViewById(R.id.choice_dialog_panel_1);
        Button panel2 = (Button)view.findViewById(R.id.choice_dialog_panel_2);
        Button panel3 = (Button)view.findViewById(R.id.choice_dialog_panel_3);
        Button[] panels = new Button[]{panel1, panel2, panel3};
        for (int i = 0; i < 3; i++) {
            populateChoicePanel(context, panels[i], choiceItems[i],
                    DialogChoiceItem.ICON_ON_TOP);
        }
    }

    public static void populateChoicePanel(Context context, Button choicePanel,
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

    public void makeNotCancelable() {
        dialog.setCancelable(false);
    }

    public void addButton(String text, View.OnClickListener listener) {
        Button button = (Button)view.findViewById(R.id.optional_button);
        button.setText(text);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(listener);
    }

    public void show() {
        dialog.setView(view);
        dialog.show();
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        dialog.setOnCancelListener(listener);
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        dialog.setOnDismissListener(listener);
    }

    public void dismiss() {
        dialog.dismiss();
    }
}
