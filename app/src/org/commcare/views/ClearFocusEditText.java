package org.commcare.views;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;

import androidx.annotation.RequiresApi;

/**
 * @author $|-|!˅@M
 */
public class ClearFocusEditText extends EditText {

    /**
     * A boolean value to keep track of whether we explicitly want this editText to remove focus.
     */
    boolean shouldRemoveFocus = false;

    public ClearFocusEditText(Context context) {
        super(context);
    }

    public ClearFocusEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ClearFocusEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ClearFocusEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void acceptFocus() {
        shouldRemoveFocus = false;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            /*
            this.clearFocus() works in Android 10, and it should've worked in all the android versions.
            But in Android 6 it doesn't, clearFocus() internally propagates the clearFocus call up the parent hierarchy.
            And when it does so, the framework tries to give focus to the first focusable view from the top automatically.
             */
            shouldRemoveFocus = true;
            this.clearFocus();
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean isFocused() {
        if (shouldRemoveFocus) {
            return false;
        }
        return super.isFocused();
    }
}
