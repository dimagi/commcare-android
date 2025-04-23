package org.commcare.views.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.KeyEvent;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

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
    private DialogInterface.OnCancelListener cancelListener;
    private DialogInterface.OnDismissListener dismissListener;

    private CharSequence positiveButtonText;
    private DialogInterface.OnClickListener positiveButtonListener;

    protected View view;
    // false by default, can be overridden if desired
    protected boolean isCancelable;
    private boolean dismissOnBackPress = false;

    protected void finalizeView(Context context) {
        dialog.setCancelable(isCancelable);
        if (cancelListener != null) {
            dialog.setOnCancelListener(cancelListener);
        }
        if (dismissListener != null) {
            dialog.setOnDismissListener(dismissListener);
        }
        if (view != null) {
            dialog.setView(view);
        }
        if(dismissOnBackPress){
            dialog.setOnKeyListener((dialog, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dialog.dismiss();
                }
                return true;
            });
        }
    }

    public void dismissOnBackPress() {
        dismissOnBackPress = true;
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

    public void makeCancelable() {
        isCancelable = true;
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        isCancelable = true;
        cancelListener = listener;
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        dismissListener = listener;
    }

    public AlertDialog getDialog() {
        return dialog;
    }

    // IMPORTANT: Should ONLY be used to show dialogs in activities that are NOT CommCareActivities
    // (and therefore cannot use the DialogController infrastructure set up there)
    public void showNonPersistentDialog(Context context) {
        buildDialog(context);
        dialog.show();
    }

    /**
     * Builds the dialog with the context given. This must be called to create the dialog
     * in order to be able to show it on screen.
     * @param context context we want to attach to the dialog
     * @return created dialog
     */
    public Dialog buildDialog(Context context) {
        initView(context);
        if (view == null) {
            throw new IllegalStateException("Dialog view is null; cannot rebuild dialog");
        }

        // Deattach view from previous view
        if (view.getParent() != null && view.getParent() instanceof View) {
            ((android.view.ViewGroup) view.getParent()).removeView(view);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(view);
        if (positiveButtonText != null) {
            builder.setPositiveButton(positiveButtonText, positiveButtonListener);
        }
        dialog = builder.create();
        finalizeView(context);
        return dialog;
    }

    protected void initView(Context context) {
        // nothing required here, implementations can use this to init any views before building dialog
    }

    protected void setView(View view) {
        this.view = view;
    }

    protected void setPositiveButtonText(CharSequence positiveButtonText) {
        this.positiveButtonText = positiveButtonText;
    }

    protected void setPositiveButtonListener(DialogInterface.OnClickListener positiveButtonListener) {
        this.positiveButtonListener = positiveButtonListener;
    }
}
