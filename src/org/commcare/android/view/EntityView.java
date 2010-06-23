/**
 * 
 */
package org.commcare.android.view;

import org.commcare.android.models.Entity;
import org.commcare.suite.model.Detail;
import org.commcare.util.CommCarePlatform;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public class EntityView extends LinearLayout {
	
	private TextView[] views;

	public EntityView(Context context, CommCarePlatform platform, Detail d, Entity e) {
		super(context);

		this.setWeightSum(1);
		
		views = new TextView[e.getFields().length];
		
        LayoutParams l = new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, (float)(1.0 / views.length));
		
		for(int i = 0 ; i < views.length ; ++i) {
		
	        views[i] = new TextView(context);
	        views[i].setTextAppearance(context, android.R.style.TextAppearance_Large);
	        views[i].setPadding(20, 15, 15, 20);
	        views[i].setId(i);
	        addView(views[i], l);
		}
        
		setParams(platform, e);
	}
	
	public EntityView(Context context, CommCarePlatform platform, Detail d, String[] headerText) {
		super(context);

		this.setWeightSum(1);
		
		views = new TextView[headerText.length];
		
        LayoutParams l = new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, (float)(1.0 / views.length));
		
		for(int i = 0 ; i < views.length ; ++i) {
		
	        views[i] = new TextView(context);
	        views[i].setTextAppearance(context, android.R.style.TextAppearance_Large);
	        views[i].setPadding(20, 15, 15, 20);
	        views[i].setId(i);
	        views[i].setText(headerText[i]);
	        addView(views[i], l);
		}
	}

	public void setParams(CommCarePlatform platform, Entity e) {
		for(int i = 0; i < e.getFields().length ; ++i) {
			views[i].setText(e.getFields()[i]);
		}
	}
}
