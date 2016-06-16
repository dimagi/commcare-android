package org.commcare.views.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

/**
 * An implementation of CommCareAlertDialog for which the dialog's entire view is created custom
 *
 * @author amstone
 */
public class CustomViewAlertDialog extends CommCareAlertDialog {

    private AlertDialog.Builder builder;

    public CustomViewAlertDialog(Context context, View view) {
        this.builder = new AlertDialog.Builder(context);
        builder.setView(view);
    }

    public void setPositiveButton(CharSequence displayText, final DialogInterface.OnClickListener buttonListener) {
        builder.setPositiveButton(displayText, buttonListener);
    }

    @Override
    public void finalizeView() {
        dialog = builder.create();
        super.finalizeView();
    }

}
