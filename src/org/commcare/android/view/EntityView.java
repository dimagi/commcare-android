/**
 * 
 */
package org.commcare.android.view;

import org.commcare.android.models.Case;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.services.storage.Persistable;

import android.content.Context;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public class EntityView extends RelativeLayout{
	
	private TextView mPrimaryTextView;

	public EntityView(Context context, CommCarePlatform platform, Persistable p) {
		super(context);
		
		String name = String.valueOf(p.getID());
		
		if(p instanceof Case) {
			name = ((Case)p).getName();
		}
		
        mPrimaryTextView = new TextView(context);
        mPrimaryTextView.setTextAppearance(context, android.R.style.TextAppearance_Large);
        mPrimaryTextView.setText(name);
        mPrimaryTextView.setPadding(20, 15, 15, 20);
        mPrimaryTextView.setId(2);
        LayoutParams l =
                new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT);
        
        addView(mPrimaryTextView, l);

	}

	public void setParams(CommCarePlatform platform, Persistable p) {
		
		String name = String.valueOf(p.getID());
		
		if(p instanceof Case) {
			name = ((Case)p).getName();
		}
		
		mPrimaryTextView.setText(name);
	}
}
