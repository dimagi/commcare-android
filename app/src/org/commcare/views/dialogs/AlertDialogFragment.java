package org.commcare.views.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

/**
 * Wrapper for CommCareAlertDialogs that allows them to persist across screen orientation changes,
 * by creating a DialogFragment from a CommCareAlertDialog at the time of actually showing the
 * dialog
 *
 * @author Phillip Mates (pmates@dimagi.com)
 * @author Aliza Stone (astone@dimagi.com)
 */
public class AlertDialogFragment extends DialogFragment {

    private CommCareAlertDialog underlyingDialog;

    public static AlertDialogFragment fromCommCareAlertDialog(CommCareAlertDialog d) {
        d.finalizeView();
        AlertDialogFragment frag = new AlertDialogFragment();
        frag.setUnderlyingDialog(d);
        frag.setCancelable(d.isCancelable());
        return frag;
    }

    private void setUnderlyingDialog(CommCareAlertDialog d) {
        this.underlyingDialog = d;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        underlyingDialog.performCancel(dialog);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        underlyingDialog.performDismiss(dialog);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return underlyingDialog.getDialog();
    }

    @Override
    public void onDestroyView() {
        // Ohh, you know, just a 5 year old Android bug ol' G hasn't fixed yet
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }
}