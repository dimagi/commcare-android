package org.commcare.utils;

import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import org.commcare.dalvik.R;

public class ViewUtils {
    /**
     * Displays a SnackBar with the given message and an "OK" button.
     * The SnackBar will remain visible indefinitely until the "OK" button is pressed.
     *
     * @param view            The view to find a parent from. This view is used to create the SnackBar.
     * @param message         The message text to show in the SnackBar.
     * @param okClickListener The callback to be invoked when the "OK" button is clicked.
     */
    public static void showSnackBarWithOk(View view, String message, View.OnClickListener okClickListener) {
        Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE)
                .setAction("OK", okClickListener)
                .show();
    }
    public static void showSnackBarWithDismissAction(View view, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(view.getContext().getString(R.string.connect_register_success_button),v -> snackbar.dismiss());
        snackbar.show();
    }
}
