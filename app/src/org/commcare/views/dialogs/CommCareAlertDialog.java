package org.commcare.views.dialogs;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;

/**
 * Created by amstone326 on 4/30/16.
 */
public abstract class CommCareAlertDialog {

    protected AlertDialog dialog;
    protected DialogInterface.OnCancelListener cancelListener;
    protected DialogInterface.OnDismissListener dismissListener;

    protected boolean isCancelable;
    protected View view;

    public void finalizeView() {
        dialog.setCancelable(isCancelable);
        dialog.setView(view);
    }

    public void performCancel(DialogInterface dialog) {
        if (cancelListener != null) {
            cancelListener.onCancel(dialog);
        }
    }

    public void performDismiss(DialogInterface dialog) {
        if (dismissListener != null) {
            dismissListener.onDismiss(dialog);
        }
    }

    public boolean isCancelable() {
        return isCancelable;
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        isCancelable = true;
        dialog.setOnCancelListener(listener);
        cancelListener = listener;
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        dialog.setOnDismissListener(listener);
        dismissListener = listener;
    }

    public AlertDialog getDialog() {
        return dialog;
    }

    // IMPORTANT: Should ONLY be used to show dialogs in activities that are NOT CommCareActivities
    // (and therefore cannot use the DialogController infrastructure set up there)
    public void showDialog() {
        finalizeView();
        dialog.show();
    }

}