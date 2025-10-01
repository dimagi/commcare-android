package org.commcare.views.dialogs;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.commcare.dalvik.R;
import org.commcare.preferences.LocalePreferences;

/**
 * An implementation of CommCareAlertDialog for use in any instance in which the user is being
 * given a choice between multiple options; the N choice options will be displayed in a
 * vertically-oriented list
 *
 * @author amstone
 */
public class PaneledChoiceDialog extends CommCareAlertDialog {

    private final String title;
    protected DialogChoiceItem[] choiceItems;
    private AdapterView.OnItemClickListener listClickListener;
    @Nullable
    private View.OnClickListener optionalButtonListener;
    @Nullable
    private String optionalButtonText;
    @Nullable
    private String extraInfoMessage;

    public PaneledChoiceDialog(Context context, String title) {
        this.title = title;
        isCancelable = true; // cancelable by default
    }

    protected int getLayoutFile() {
        return R.layout.choice_dialog_view;
    }

    @Override
    protected void initView(Context context) {
        super.initView(context);
        this.view = LayoutInflater.from(context).inflate(getLayoutFile(), null);
        setupListAdapter(context);
        setTitle(title);
        setUpOptionalButton();
        setUpExtraInfoPanel();
    }

    private void setUpExtraInfoPanel() {
        View extraInfoContainer = view.findViewById(R.id.extra_info_container);
        if (extraInfoContainer != null) {
            if (extraInfoMessage != null) {
                extraInfoContainer.setVisibility(View.VISIBLE);
                TextView extraInfoContent = view.findViewById(R.id.extra_info_content);
                extraInfoContent.setText(extraInfoMessage);
                final ImageButton extraInfoButton = view.findViewById(R.id.extra_info_button);
                extraInfoButton.setVisibility(View.VISIBLE);
                extraInfoButton.setOnClickListener(v -> toggleExtraInfoVisibility());
            } else {
                extraInfoContainer.setVisibility(View.GONE);
            }
        }
    }

    private void setUpOptionalButton() {
        Button optionalButton = view.findViewById(R.id.optional_button);
        if (optionalButton != null) {
            if (optionalButtonListener != null) {
                optionalButton.setText(optionalButtonText);
                optionalButton.setVisibility(View.VISIBLE);
                optionalButton.setOnClickListener(optionalButtonListener);
            } else {
                optionalButton.setVisibility(View.GONE);
            }
        }
    }

    public void setChoiceItems(DialogChoiceItem[] choiceItems) {
        this.choiceItems = choiceItems;
    }

    public void setChoiceItems(DialogChoiceItem[] choiceItems,
                               AdapterView.OnItemClickListener listClickListener) {
        setChoiceItems(choiceItems);
        this.listClickListener = listClickListener;
    }

    private ListView setupListAdapter(Context context) {
        ListView lv = view.findViewById(R.id.choices_list_view);
        if (lv != null) {
            lv.setAdapter(new ChoiceDialogAdapter(context, android.R.layout.simple_list_item_1, choiceItems));
            lv.setOnItemClickListener(listClickListener);
        }
        return lv;
    }

    private void setTitle(String title) {
        TextView tv = view.findViewById(R.id.choice_dialog_title).
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
                if (LocalePreferences.isLocaleRTL())
                    choicePanel.setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
                else
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
        optionalButtonListener = listener;
        optionalButtonText = text;
    }

    public void addCollapsibleInfoPane(String messageContent) {
        extraInfoMessage = messageContent;
    }

    private void toggleExtraInfoVisibility() {
        TextView extraInfoContent = view.findViewById(R.id.extra_info_content);
        if (extraInfoContent.getVisibility() == View.VISIBLE) {
            extraInfoContent.setVisibility(View.GONE);
        } else {
            extraInfoContent.setVisibility(View.VISIBLE);
        }
    }

}
