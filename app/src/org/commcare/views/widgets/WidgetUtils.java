package org.commcare.views.widgets;

import android.text.Spannable;
import android.util.TypedValue;
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
}