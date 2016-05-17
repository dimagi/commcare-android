package org.commcare.views.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import org.commcare.dalvik.R;

/**
 * An implementation of CommCareAlertDialog for use in any instance in which the user is being
 * given a choice between multiple options; the N choice options will be displayed in a
 * vertically-oriented list
 *
 * @author amstone
 */
public class PaneledChoiceDialog extends CommCareAlertDialog {

    protected final Context context;

    public PaneledChoiceDialog(Context context, String title) {
        this.context = context;
        this.dialog = new AlertDialog.Builder(context).create();
        this.view = LayoutInflater.from(context).inflate(getLayoutFile(), null);
        setTitle(title);
        isCancelable = true; // cancelable by default
    }

    protected int getLayoutFile() {
        return R.layout.choice_dialog_view;
    }

    public void setChoiceItems(DialogChoiceItem[] choiceItems) {
        setupListAdapter(choiceItems);
    }

    public void setChoiceItems(DialogChoiceItem[] choiceItems,
                               AdapterView.OnItemClickListener listClickListener) {
        setupListAdapter(choiceItems).setOnItemClickListener(listClickListener);
    }

    private ListView setupListAdapter(DialogChoiceItem[] choiceItems) {
        ListView lv = (ListView)view.findViewById(R.id.choices_list_view);
        lv.setAdapter(new ChoiceDialogAdapter(context, android.R.layout.simple_list_item_1, choiceItems));
        return lv;
    }

    private void setTitle(String title) {
        TextView tv = (TextView)view.findViewById(R.id.choice_dialog_title).
                findViewById(R.id.dialog_title_text);
        tv.setText(title);
    }

    public static void populateChoicePanel(Context context, Button choicePanel,
                                           DialogChoiceItem item, boolean iconToLeft) {
        choicePanel.setText(item.text);
        if (item.listener != null) {
            choicePanel.setOnClickListener(item.listener);
        } else {
            // needed to propagate clicks down to the ListView's ItemClickListener
            choicePanel.setFocusable(false);
            choicePanel.setClickable(false);
        }
        if (item.iconResId != -1) {
            Drawable icon = ContextCompat.getDrawable(context, item.iconResId);
            if (iconToLeft) {
                choicePanel.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
            } else {
                choicePanel.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
            }
        }
    }

    public void makeNotCancelable() {
        isCancelable = false;
    }

    public void addButton(String text, View.OnClickListener listener) {
        Button button = (Button)view.findViewById(R.id.optional_button);
        button.setText(text);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(listener);
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public void addCollapsibleInfoPane(String messageContent) {
        View extraInfoContainer = view.findViewById(R.id.extra_info_container);
        extraInfoContainer.setVisibility(View.VISIBLE);

        TextView extraInfoContent = (TextView)view.findViewById(R.id.extra_info_content);
        extraInfoContent.setText(messageContent);

        final ImageButton extraInfoButton = (ImageButton)view.findViewById(R.id.extra_info_button);
        extraInfoButton.setVisibility(View.VISIBLE);
        extraInfoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleExtraInfoVisibility();
            }
        });
    }

    private void toggleExtraInfoVisibility() {
        TextView extraInfoContent = (TextView)view.findViewById(R.id.extra_info_content);
        if (extraInfoContent.getVisibility() == View.VISIBLE) {
            extraInfoContent.setVisibility(View.GONE);
        } else {
            extraInfoContent.setVisibility(View.VISIBLE);
        }
    }

}
