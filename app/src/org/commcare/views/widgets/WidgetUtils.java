package org.commcare.views.widgets;

import android.content.Context;
import android.text.Spannable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TableLayout;

import org.commcare.dalvik.R;

class WidgetUtils {
    private static final TableLayout.LayoutParams params;

    static {
        params = new TableLayout.LayoutParams();
        params.setMargins(7, 5, 7, 5);
    }

    public static void setupButton(Button btn, Spannable text, int fontSize, boolean enabled) {
        btn.setText(text);
        int verticalPadding = (int)btn.getResources().getDimension(R.dimen.widget_button_padding);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize);
        btn.setPadding(20, verticalPadding, 20, verticalPadding);
        btn.setEnabled(enabled);
        btn.setLayoutParams(WidgetUtils.params);
    }

    public static Button setupClearButton(Context context, ViewGroup root, String text, int visibility) {
        Button clearButton = (Button) LayoutInflater.from(context).inflate(R.layout.blue_outlined_button, root, false);
        clearButton.setText(text);
        clearButton.setVisibility(visibility);
        return clearButton;
    }
}
