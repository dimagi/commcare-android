/**
 * 
 */
package org.commcare.android.view;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.odk.collect.android.views.media.AudioButton;
import org.odk.collect.android.views.media.ViewId;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.Entity;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.MediaUtil;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.commcare.util.CommCareSession;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.odk.collect.android.views.media.AudioController;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.LinearLayout.LayoutParams;

/**
 * @author ctsims
 *
 */
public class EntityDetailView extends FrameLayout {
	
	private TextView label;
	private TextView data;
	private TextView spacer;
	private Button callout;
	private View addressView;
	private Button addressButton;
	private TextView addressText;
	private ImageView imageView;
	private ImageButton videoButton;
	private AudioButton audioButton;
	private View valuePane;
	private View currentView;
	private AudioController controller;
	private FileInputStream fis;
	private LinearLayout detailRow;
	private LinearLayout.LayoutParams origValue;
	private LinearLayout.LayoutParams origLabel;
	private LinearLayout.LayoutParams fill;
	
	private static final String FORM_VIDEO = "video";
	private static final String FORM_AUDIO = "audio";
	private static final String FORM_PHONE = "phone";
	private static final String FORM_ADDRESS = "address";
	private static final String FORM_IMAGE = "image";

	private static final int TEXT = 0;
	private static final int PHONE = 1;
	private static final int ADDRESS = 2;
	private static final int IMAGE = 3;
	private static final int VIDEO = 4;
	private static final int AUDIO = 5;
	int current = TEXT;
	
	DetailCalloutListener listener;

	public EntityDetailView(Context context, CommCareSession session, Detail d, Entity e, int index,
			AudioController controller, int detailNumber) {
		super(context);		
	    this.controller = controller;
	    
		detailRow = (LinearLayout)View.inflate(context, R.layout.component_entity_detail_item, null);
        label = (TextView)detailRow.findViewById(R.id.detail_type_text);
        spacer = (TextView)detailRow.findViewById(R.id.entity_detail_spacer); 
	    data = (TextView)detailRow.findViewById(R.id.detail_value_text);
	    currentView = data;
	    valuePane = detailRow.findViewById(R.id.detail_value_pane);
	    videoButton = (ImageButton)detailRow.findViewById(R.id.detail_video_button);
	    
	    ViewId uniqueId = new ViewId(detailNumber, index, true);
	    audioButton = new AudioButton(context, e.getField(index), uniqueId, controller, false);
	    detailRow.addView(audioButton);
	    audioButton.setVisibility(View.GONE);
	    
	    callout = (Button)detailRow.findViewById(R.id.detail_value_phone);
	    //TODO: Still useful?
	    //callout.setInputType(InputType.TYPE_CLASS_PHONE);
	    addressView = (View)detailRow.findViewById(R.id.detail_address_view);
	    addressText = (TextView)addressView.findViewById(R.id.detail_address_text);
	    addressButton = (Button)addressView.findViewById(R.id.detail_address_button);
	    imageView = (ImageView)detailRow.findViewById(R.id.detail_value_image);
	    origLabel = (LinearLayout.LayoutParams)label.getLayoutParams();
	    origValue = (LinearLayout.LayoutParams)valuePane.getLayoutParams();

	    fill = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
	    this.addView(detailRow, FrameLayout.LayoutParams.FILL_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
	    setParams(session, d, e, index, detailNumber);
	}
	
	public void setCallListener(final DetailCalloutListener listener) {
		this.listener = listener;
	}

	public void setParams(CommCareSession session, Detail d, Entity e, int index, int detailNumber) {
		String labelText = d.getFields()[index].getHeader().evaluate();
		label.setText(labelText);
		spacer.setText(labelText);
		
		String textField = e.getField(index);
		boolean veryLong = false;
		String form = d.getTemplateForms()[index];
		if(FORM_PHONE.equals(form)) {
			callout.setText(textField);
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
		} else if(FORM_ADDRESS.equals(form)) {
			final String address = textField;
			
			addressText.setText(address);
			if(current != ADDRESS) {
				addressButton.setOnClickListener(new OnClickListener() {

					public void onClick(View v) {
						listener.addressRequested(MediaUtil.getGeoIntentURI(address));
					}
					
				});
				
				currentView.setVisibility(View.GONE);
				addressView.setVisibility(View.VISIBLE);
				currentView = addressView;
				current = ADDRESS;
			}
		} else if(FORM_IMAGE.equals(form)) {
			String imageLocation = textField;
			Bitmap b = MediaUtil.getScaledImageFromReference(imageLocation);
			
			if(b == null) {
				imageView.setImageDrawable(null);
			} else {
				
                Display display = ((WindowManager) this.getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                int screenWidth = display.getWidth();

				
				//Ok, so. We should figure out whether our image is large or small.
				if(b.getWidth() > (screenWidth / 2)) {
					veryLong = true;
				}
				
				imageView.setPadding(10, 10, 10, 10);
				imageView.setAdjustViewBounds(true);
				imageView.setImageBitmap(b);
				imageView.setId(23422634);
			}
			
			if(current != IMAGE) {
				currentView.setVisibility(View.GONE);
				imageView.setVisibility(View.VISIBLE);
				currentView = imageView;
				current = IMAGE;
			}
		} else if (FORM_AUDIO.equals(form)) {
			ViewId uniqueId = new ViewId(detailNumber, index, true);
			audioButton.modifyButtonForNewView(uniqueId, textField, true);
			if (current != AUDIO) {
				currentView.setVisibility(View.GONE);
				audioButton.setVisibility(View.VISIBLE);
				currentView = audioButton;
				current = AUDIO;
			}
		} else if(FORM_VIDEO.equals(form)) { //TODO: Why is this given a special string?
			String videoLocation = textField;
			String localLocation = null;
			try{ 
				localLocation = ReferenceManager._().DeriveReference(videoLocation).getLocalURI();
				if(localLocation.startsWith("/")) {
					//TODO: This should likely actually be happening with the getLocalURI _anyway_.
					localLocation = FileUtil.getGlobalStringUri(localLocation);
				}
			} catch(InvalidReferenceException ire) {
				Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "Couldn't understand video reference format: " + localLocation + ". Error: " + ire.getMessage());
			}
			
			final String location = localLocation;
			
			videoButton.setOnClickListener(new OnClickListener() {

				public void onClick(View v) {
					listener.playVideo(location);	
				}
				
			});
			
			if(location == null) {
				videoButton.setEnabled(false);
				Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "No local video reference available for ref: " + videoLocation);
			} else {
				videoButton.setEnabled(true);
			}
			
			if(current != VIDEO) {
				currentView.setVisibility(View.GONE);
				videoButton.setVisibility(View.VISIBLE);
				currentView = videoButton;
				current = VIDEO;
			}
		} else {
			String text = textField;
			data.setText(text);
			if(text != null && text.length() > this.getContext().getResources().getInteger(R.integer.detail_size_cutoff)) {
				veryLong = true;
			}
			if(current != TEXT) {
				currentView.setVisibility(View.GONE);
				data.setVisibility(View.VISIBLE);
				currentView = data;
				current = TEXT;
			}
		}
		
		if(veryLong) {
			
			detailRow.setOrientation(LinearLayout.VERTICAL);
			spacer.setVisibility(View.GONE);
			label.setLayoutParams(fill);
			valuePane.setLayoutParams(fill);
			
		} else {
			
			if(detailRow.getOrientation() != LinearLayout.HORIZONTAL) {
				
				detailRow.setOrientation(LinearLayout.HORIZONTAL);
				spacer.setVisibility(View.INVISIBLE);
				label.setLayoutParams(origLabel);
				valuePane.setLayoutParams(origValue);
			}
		}
	}
}
