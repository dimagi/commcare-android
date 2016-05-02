package org.commcare.views.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

/**
 * Created by amstone326 on 5/2/16.
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
    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        isCancelable = true;
        cancelListener = listener;
    }

    @Override
    public void finalizeView() {
        dialog = builder.create();
        dialog.setCancelable(isCancelable);
        if (isCancelable) {
            dialog.setOnCancelListener(cancelListener);
        }
    }

}
