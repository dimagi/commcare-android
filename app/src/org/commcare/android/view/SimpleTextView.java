/**
 *
 */
package org.commcare.android.view;

import android.content.Context;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.commcare.suite.model.Text;
import org.commcare.util.CommCarePlatform;

/**
 * @author ctsims
 */
public class SimpleTextView extends RelativeLayout {

    private TextView mPrimaryTextView;

    public SimpleTextView(Context context, CommCarePlatform platform, Text t) {
        super(context);

        mPrimaryTextView = new TextView(context);
        mPrimaryTextView.setTextAppearance(context, android.R.style.TextAppearance_Large);
        mPrimaryTextView.setText(t.evaluate());
        mPrimaryTextView.setPadding(20, 15, 15, 20);
        mPrimaryTextView.setId(2);
        LayoutParams l =
                new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT);

        addView(mPrimaryTextView, l);

    }

    public void setParams(CommCarePlatform platform, Text t) {
        mPrimaryTextView.setText(t.evaluate());
    }
}
