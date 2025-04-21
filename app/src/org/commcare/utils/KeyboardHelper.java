package org.commcare.utils;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

/**
 * Helper class (single method) for showing the keyboard on an input
 *
 * @author dviggiano
 */
public class KeyboardHelper {
    public static void showKeyboardOnInput(Activity activity, View view) {
        view.requestFocus();

        InputMethodManager inputMethodManager = (InputMethodManager)activity.getSystemService(
                Context.INPUT_METHOD_SERVICE);
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (inputMethodManager != null) {
                    inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 250);
    }
}
