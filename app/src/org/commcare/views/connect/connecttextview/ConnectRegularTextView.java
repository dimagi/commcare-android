package org.commcare.views.connect.connecttextview;

import android.content.Context;
import android.util.AttributeSet;

import org.commcare.dalvik.R;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.res.ResourcesCompat;

public class ConnectRegularTextView extends AppCompatTextView {

    public ConnectRegularTextView(Context context) {
        super(context);
        init(context);
    }

    public ConnectRegularTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ConnectRegularTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        try {
            setTypeface(ResourcesCompat.getFont(context, R.font.roboto_regular));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
