package org.commcare.views.connect.connecttextview;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;

import org.commcare.dalvik.R;
import org.javarosa.core.services.Logger;

import java.time.format.TextStyle;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.res.ResourcesCompat;

public class ConnectBoldTextView extends AppCompatTextView {
    private static final String TAG = ConnectBoldTextView.class.getSimpleName();
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
        setTypeface(null, Typeface.BOLD);
    }
}
