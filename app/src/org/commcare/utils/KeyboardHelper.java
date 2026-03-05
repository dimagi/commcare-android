package org.commcare.utils;

import android.app.Activity;
import android.content.Context;
import android.os.IBinder;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Utility class for handling keyboard interactions.
 * Provides a method to show the keyboard on a given input field.
 *
 * @author dviggiano
 */
public class KeyboardHelper {

    /**
     * Displays the soft keyboard for the specified input view.
     * This method ensures the view gains focus before attempting to show the keyboard.
     * A slight delay is added to ensure the keyboard appears properly.
     *
     * @param activity The activity context used to retrieve the InputMethodManager.
     * @param view     The input view that should receive focus and trigger the keyboard.
     */
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

    public static void hideVirtualKeyboard(Activity activity) {
        InputMethodManager inputManager = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        View focus = activity.getCurrentFocus();
        if (focus != null) {
            inputManager.hideSoftInputFromWindow(focus.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }
}
