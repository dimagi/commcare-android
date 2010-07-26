/**
 * 
 */
package org.commcare.android.view;

import org.commcare.android.R;
import org.commcare.android.models.Entity;
import org.commcare.android.util.DetailButtonListener;
import org.commcare.suite.model.Detail;
import org.commcare.util.CommCarePlatform;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public class EntityDetailView extends LinearLayout {
	
	private TextView label;
	private TextView data;
	private Button phone;
	
	LayoutParams pl;
	LayoutParams dl;
	
	boolean showData = true;

	public EntityDetailView(Context context, CommCarePlatform platform, Detail d, Entity e, int index) {
		super(context);
		
		this.setOrientation(HORIZONTAL);
		
        LayoutParams l = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 0.6f);
        
        label = new TextView(context);
        label.setTextAppearance(context, android.R.style.TextAppearance_Medium);
        label.setPadding(20, 15, 15, 20);
        label.setId(1);
	    addView(label, l);
	    
	    dl = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 0.4f);
	    data = new TextView(context);
	    data.setTextAppearance(context, android.R.style.TextAppearance_Medium);
	    data.setPadding(0, 15, 15, 20);
	    data.setId(2);
	    addView(data, dl);
	    
	    pl = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 0.4f);
	    pl.gravity = Gravity.CENTER;
	    phone = (Button)Button.inflate(context, R.layout.phone_button, null);
	    phone.setInputType(InputType.TYPE_CLASS_PHONE);
	    phone.setId(3);
	    
		this.setWeightSum(1.0f);
	   
		setParams(platform, d, e, index);
	}
	
	public void setCallListener(final DetailButtonListener listener) {
		phone.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				listener.callRequested(phone.getText().toString());				
			}
			
		});
	}

	public void setParams(CommCarePlatform platform, Detail d, Entity e, int index) {
		label.setText(d.getHeaders()[index].evaluate());
		if("phone".equals(d.getTemplateForms()[index])) {
			phone.setText(e.getFields()[index]);
			if(showData) {
				this.addView(phone, pl);
				this.removeView(data);
				showData = !showData;
			}
		} else {
			data.setText(e.getFields()[index]);
			if(!showData) {
				this.addView(data, dl);
				this.removeView(phone);
				showData = !showData;
			}
		}
	}
}
