package org.commcare.views.dialogs;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

/**
 * An implementation of CommCareAlertDialog for which the dialog's entire view is created custom
 *
 * @author amstone
 */
public class CustomViewAlertDialog extends CommCareAlertDialog {

    public CustomViewAlertDialog(Context context, View view) {
        setView(view);
    }

    public void setPositiveButton(CharSequence displayText, final DialogInterface.OnClickListener buttonListener) {
        setPositiveButtonText(displayText);
        setPositiveButtonListener(buttonListener);
    }
}
