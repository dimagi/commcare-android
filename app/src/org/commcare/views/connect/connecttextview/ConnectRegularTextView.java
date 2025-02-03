package org.commcare.views.connect.connecttextview;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;

import org.commcare.dalvik.R;
import org.javarosa.core.services.Logger;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.res.ResourcesCompat;

public class ConnectRegularTextView extends AppCompatTextView {
    private static final String TAG = ConnectRegularTextView.class.getSimpleName();

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
            Logger.log(TAG, "Failed to load Roboto Regular font");
            // Fallback to system bold font
            setTypeface(Typeface.DEFAULT);
        }
    }
}
