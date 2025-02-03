package org.commcare.views.connect.connecttextview;

import android.content.Context;
import android.util.AttributeSet;

import org.commcare.dalvik.R;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.res.ResourcesCompat;

public class ConnectMediumTextView extends AppCompatTextView {

    public ConnectMediumTextView(Context context) {
        super(context);
        init(context);
    }

    public ConnectMediumTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ConnectMediumTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        try {
            setTypeface(ResourcesCompat.getFont(context, R.font.roboto_medium));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
