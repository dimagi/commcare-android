package org.commcare.views.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

/**
 * Dialog that persists across screen orientation changes, wraps AlertDialogFactory
 *
 * @author Phillip Mates (pmates@dimagi.com)
 * @author Aliza Stone (astone@dimagi.com)
 */
public class AlertDialogFragment extends DialogFragment {

    private AlertDialogFactory factory;

    public static AlertDialogFragment fromFactory(AlertDialogFactory f) {
        AlertDialogFragment frag = new AlertDialogFragment();
        frag.setFactory(f);
        frag.setCancelable(f.isCancelable());
        return frag;
    }

    private void setFactory(AlertDialogFactory f) {
        this.factory = f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        factory.performCancel(dialog);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return factory.getDialog();
    }

    @Override
    public void onDestroyView() {
        // Ohh, you know, just a 5 year old Android bug ol' G hasn't fixed yet
        if (getDialog() != null && getRetainInstance())
            getDialog().setDismissMessage(null);
        super.onDestroyView();
    }
}