package org.odk.collect.android.widgets;

import android.text.Spannable;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.TableLayout;

class WidgetUtils {
    private static final TableLayout.LayoutParams params;

    static {
        params = new TableLayout.LayoutParams();
        params.setMargins(7, 5, 7, 5);
    }

    public static void setupButton(Button btn, Spannable text, int fontSize, boolean enabled) {
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize);
        btn.setPadding(20, 20, 20, 20);
        btn.setEnabled(enabled);
        btn.setLayoutParams(WidgetUtils.params);
    }
}