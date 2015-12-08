package org.commcare.dalvik.dialogs;

import android.content.Context;
import android.widget.Button;

import org.commcare.dalvik.R;

/**
 * A specialized version of PaneledChoiceDialog that shows exactly 3 choice items (rather than N
 * items) in a horizontal (rather than vertical) orientation
 *
 * @author amstone
 */
public class HorizontalPaneledChoiceDialog extends PaneledChoiceDialog {

    public HorizontalPaneledChoiceDialog(Context context, String title) {
        super(context, title);
    }

    @Override
    protected int getLayoutFile() {
        return R.layout.choice_dialog_three_panel;
    }

    @Override
    public void setChoiceItems(DialogChoiceItem[] choiceItems) {
        setupThreePanelView(choiceItems);
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

}
