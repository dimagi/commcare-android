package org.commcare.views.connect.connecttextview;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.res.ResourcesCompat;

import org.commcare.dalvik.R;

public class ConnectBoldTextView extends AppCompatTextView {

    public ConnectBoldTextView(Context context) {
        super(context);
        init(context);
    }

    public ConnectBoldTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ConnectBoldTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        try {
            setTypeface(ResourcesCompat.getFont(context, R.font.roboto_bold));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
