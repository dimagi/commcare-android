package org.commcare.dalvik.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

/**
 * Dialog that persists across screen orientation changes.
 * Wraps AlertDialogFactory interface.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public abstract class AlertDialogFragment extends DialogFragment {
    public static final String TITLE_KEY = "title";
    public static final String BODY_MESSAGE_KEY = "message";
    public static final String NEGATIVE_MESSAGE_KEY = "negative";
    public static final String POSITIVE_MESSAGE_KEY = "positive";
    public static final String NEUTRAL_MESSAGE_KEY = "neutral";

    /**
     * The click listener for the dialog buttons present. Should handle each case
     */
    public abstract DialogInterface.OnClickListener getClickListener();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        String title = args.getString(TITLE_KEY);
        String message = args.getString(BODY_MESSAGE_KEY);
        AlertDialogFactory factory =
                new AlertDialogFactory(getContext(), title, message);

        DialogInterface.OnClickListener listener = getClickListener();
        if (args.containsKey(NEGATIVE_MESSAGE_KEY)) {
            factory.setNegativeButton(args.getString(NEGATIVE_MESSAGE_KEY),
                    listener);
        }
        if (args.containsKey(POSITIVE_MESSAGE_KEY)) {
            factory.setPositiveButton(args.getString(POSITIVE_MESSAGE_KEY),
                    listener);
        }
        if (args.containsKey(NEUTRAL_MESSAGE_KEY)) {
            factory.setNeutralButton(args.getString(NEUTRAL_MESSAGE_KEY),
                    listener);
        }

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
