package org.commcare.views.dialogs;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;

/**
 * Framework for all alert-type dialogs used across CommCare. Any specific implementations
 * of an alert dialog in CommCare should subclass this class.
 *
 * The showing and persistence of all CommCareAlertDialogs should be managed by the implementation
 * of the DialogController interface in CommCareActivity wherever possible (e.g. wherever the
 * context of a dialog is a CommCareActivity)
 *
 */
public abstract class CommCareAlertDialog {

    protected AlertDialog dialog;
    protected DialogInterface.OnCancelListener cancelListener;
    protected DialogInterface.OnDismissListener dismissListener;
    protected View view;
    // false by default, can be overridden by calling setOnCancelListener(), or by subclass
    // definitions if desired
    protected boolean isCancelable;

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