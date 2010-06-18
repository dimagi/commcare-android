/**
 * 
 */
package org.commcare.android.view;

import org.commcare.suite.model.Entry;
import org.commcare.util.CommCarePlatform;

import android.content.Context;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public class EntryMenuView extends RelativeLayout {
	
	private TextView mPrimaryTextView;

	public EntryMenuView(Context context, CommCarePlatform platform, Entry e) {
		super(context);
		
        mPrimaryTextView = new TextView(context);
        mPrimaryTextView.setTextAppearance(context, android.R.style.TextAppearance_Large);
        mPrimaryTextView.setText(e.getText().evaluate());
        mPrimaryTextView.setPadding(20, 15, 15, 20);
        mPrimaryTextView.setId(2);
        LayoutParams l =
                new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT);
        
        addView(mPrimaryTextView, l);

	}

	public void setParams(CommCarePlatform platform, Entry e) {
		mPrimaryTextView.setText(e.getText().evaluate());
	}
}
