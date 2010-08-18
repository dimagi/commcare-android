/**
 * 
 */
package org.commcare.android.view;

import org.commcare.android.R;
import org.commcare.android.models.Entity;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.suite.model.Detail;
import org.commcare.util.CommCarePlatform;

import android.content.Context;
import android.graphics.drawable.Drawable;
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
	private Button callout;
	
	private View addressView;
	private Button addressButton;
	private TextView addressText;
	
	private View currentView;
	
	LayoutParams pl;
	LayoutParams dl;
	
	int current = TEXT;
	private static final int TEXT = 0;
	private static final int PHONE = 1;
	private static final int ADDRESS = 2;
	
	
	DetailCalloutListener listener;

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
	    currentView = data;
	    addView(data, dl);
	    
	    pl = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, 0.4f);
	    pl.gravity = Gravity.CENTER;
	    
	    callout = (Button)Button.inflate(context, R.layout.phone_button, null);
	    callout.setInputType(InputType.TYPE_CLASS_PHONE);
	    callout.setId(3);
	    
	    addressView = (View)View.inflate(context, R.layout.address_view, null);
	    addressText = (TextView)addressView.findViewById(R.id.address_text);
	    addressButton = (Button)addressView.findViewById(R.id.address_button);
	    addressView.setId(4);
	    
		this.setWeightSum(1.0f);
	   
		setParams(platform, d, e, index);
	}
	
	public void setCallListener(final DetailCalloutListener listener) {
		this.listener = listener;
	}

	public void setParams(CommCarePlatform platform, Detail d, Entity e, int index) {
		label.setText(d.getHeaders()[index].evaluate());
		if("phone".equals(d.getTemplateForms()[index])) {
			callout.setText(e.getFields()[index]);
			if(current != PHONE) {
				callout.setOnClickListener(new OnClickListener() {

					public void onClick(View v) {
						listener.callRequested(callout.getText().toString());				
					}
					
				});
				this.addView(callout, pl);
				this.removeView(currentView);
				currentView = callout;
				current = PHONE;
			}
		} else if("address".equals(d.getTemplateForms()[index])) {
			final String address = e.getFields()[index];
			addressText.setText(address);
			if(current != ADDRESS) {
				addressButton.setOnClickListener(new OnClickListener() {

					public void onClick(View v) {
						listener.addressRequested(address);
					}
					
				});
				this.addView(addressView, pl);
				this.removeView(currentView);
				currentView = addressView;
				current = ADDRESS;
			}
		} else {
			data.setText(e.getFields()[index]);
			if(current != TEXT) {
				this.addView(data, dl);
				this.removeView(currentView);
				currentView = data;
				current = TEXT;
			}
		}
	}
}
