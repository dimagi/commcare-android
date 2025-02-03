package org.commcare.views.connect.connecttextview.connecttextview;

import android.content.Context;
import android.util.AttributeSet;

import org.commcare.dalvik.R;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.res.ResourcesCompat;

public class ConnectItalicTextView extends AppCompatTextView {

    public ConnectItalicTextView(Context context) {
        super(context);
        init(context);
    }

    public ConnectItalicTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ConnectItalicTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        try {
            setTypeface(ResourcesCompat.getFont(context, R.font.roboto_italic));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
