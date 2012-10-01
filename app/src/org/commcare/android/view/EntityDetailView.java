/**
 * 
 */
package org.commcare.android.view;

import org.commcare.android.models.Entity;
import org.commcare.dalvik.R;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.suite.model.Detail;
import org.commcare.util.CommCareSession;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public class EntityDetailView extends FrameLayout {
	
	private TextView label;
	private TextView data;
	private Button callout;
	
	private View addressView;
	private Button addressButton;
	private TextView addressText;
	
	private View currentView;
	
	int current = TEXT;
	private static final int TEXT = 0;
	private static final int PHONE = 1;
	private static final int ADDRESS = 2;
	
	
	DetailCalloutListener listener;

	public EntityDetailView(Context context, CommCareSession session, Detail d, Entity e, int index) {
		super(context);
		View detailRow = View.inflate(context, R.layout.component_entity_detail_item, null);
		
        label = (TextView)detailRow.findViewById(R.id.detail_type_text);
	    
	    data = (TextView)detailRow.findViewById(R.id.detail_value_text);
	    currentView = data;
	    
	    callout = (Button)detailRow.findViewById(R.id.detail_value_phone);
	    //TODO: Still useful?
	    //callout.setInputType(InputType.TYPE_CLASS_PHONE);
	    
	    addressView = (View)detailRow.findViewById(R.id.detail_address_view);
	    addressText = (TextView)addressView.findViewById(R.id.detail_address_text);
	    addressButton = (Button)addressView.findViewById(R.id.detail_address_button);
	    
	    this.addView(detailRow, FrameLayout.LayoutParams.FILL_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
	    setParams(session, d, e, index);
	}
	
	public void setCallListener(final DetailCalloutListener listener) {
		this.listener = listener;
	}

	public void setParams(CommCareSession session, Detail d, Entity e, int index) {
		label.setText(d.getHeaders()[index].evaluate());
		if("phone".equals(d.getTemplateForms()[index])) {
			callout.setText(e.getFields()[index]);
			if(current != PHONE) {
				callout.setOnClickListener(new OnClickListener() {

					public void onClick(View v) {
						listener.callRequested(callout.getText().toString());				
					}
					
				});
				currentView.setVisibility(View.GONE);
				callout.setVisibility(View.VISIBLE);
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
				
				currentView.setVisibility(View.GONE);
				addressView.setVisibility(View.VISIBLE);
				currentView = addressView;
				current = ADDRESS;
			}
		} else {
			data.setText(e.getFields()[index]);
			if(current != TEXT) {
				currentView.setVisibility(View.GONE);
				data.setVisibility(View.VISIBLE);
				currentView = data;
				current = TEXT;
			}
		}
	}
}
