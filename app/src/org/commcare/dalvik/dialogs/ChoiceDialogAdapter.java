package org.commcare.dalvik.dialogs;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;

import org.commcare.dalvik.R;

/**
 * Created by amstone326 on 10/30/15.
 */
class ChoiceDialogAdapter extends ArrayAdapter<DialogChoiceItem> {

    private final Context context;

    public ChoiceDialogAdapter(Context context, int defaultLayout,
                             DialogChoiceItem[] objects) {
        super(context, defaultLayout, objects);
        this.context = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Button choicePanel = (Button)convertView;
        if (choicePanel == null) {
            choicePanel = (Button)View.inflate(context, R.layout.single_dialog_choice_view, null);
        }
        DialogChoiceItem choiceBeingDisplayed = this.getItem(position);
        PaneledChoiceDialog.populateChoicePanel(context, choicePanel, choiceBeingDisplayed,
                DialogChoiceItem.ICON_TO_LEFT);
        return choicePanel;
    }

}
