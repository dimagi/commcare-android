/**
 * 
 */
package org.commcare.android.view;

import java.io.IOException;

import org.commcare.android.models.Entity;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.odk.collect.android.views.media.AudioButton;
import org.odk.collect.android.views.media.AudioController;
import org.odk.collect.android.views.media.MediaEntity;
import org.odk.collect.android.views.media.ViewId;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.speech.tts.TextToSpeech;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.util.Pair;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public class EntityView extends LinearLayout {
	
	private View[] views;
	private String[] forms;
	private TextToSpeech tts; 
	private String[] searchTerms;
	private Context context;
	private AudioController controller;
	private long rowId;

	/*
	 * Constructor for row/column contents
	 */
	public EntityView(Context context, Detail d, Entity e, TextToSpeech tts,
			String[] searchTerms, AudioController controller, long rowId) {
		super(context);
		this.context = context;
		this.searchTerms = searchTerms;
		this.tts = tts;
		this.setWeightSum(1);
		this.controller = controller;
		this.rowId = rowId;
		views = new View[e.getNumFields()];
		forms = d.getTemplateForms();
		float[] weights = calculateDetailWeights(d.getTemplateSizeHints());
		
		for (int i = 0; i < views.length; ++i) {
			if (weights[i] != 0) {
		        Object uniqueId = new ViewId(rowId, i, false);
		        views[i] = establishView(null, forms[i], uniqueId);
		        views[i].setId(i);
			}
		}
		setParams(e, false, rowId);
		for (int i = 0; i < views.length; i++) {
	        LayoutParams l = new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, weights[i]);
			addView(views[i], l);
		}
	}
	
	/*
	 * Constructor for row/column headers
	 */
	public EntityView(Context context, Detail d, String[] headerText) {
		super(context);
		this.context = context;
		this.setWeightSum(1);
		views = new View[headerText.length];
		float[] lengths = calculateDetailWeights(d.getHeaderSizeHints());
		String[] headerForms = d.getHeaderForms();
		
		for (int i = 0 ; i < views.length ; ++i) {
			if (lengths[i] != 0) {
		        LayoutParams l = new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, lengths[i]);
		        ViewId uniqueId = new ViewId(rowId, i, false);
		        views[i] = establishView(headerText[i], headerForms[i], uniqueId);      
		        views[i].setId(i);
		        addView(views[i], l);
			}
		}
	}
	
	/*
	 * if form = "text", then 'text' field is just normal text
	 * if form = "audio" or "image", then text is the path to the audio/image
	 */
	private View establishView(String text, String form, Object uniqueId) {
		View retVal;
		if ("image".equals(form)) {
			ImageView iv =(ImageView)View.inflate(context, R.layout.entity_item_image, null);
			retVal = iv;
        } 
		else if ("audio".equals(form)) {
    		AudioButton b = new AudioButton(context, text, uniqueId, controller);
    		retVal = b;
        } 
        else {
    		View layout = View.inflate(context, R.layout.component_audio_text, null);
    		setupTextAndTTSLayout(layout, text);
    		retVal = layout;
        }
        return retVal;
	}
	
	public void setSearchTerms(String[] terms) {
		this.searchTerms = terms;
	}
	

	public void setParams(Entity e, boolean currentlySelected, long rowId) {
		for (int i = 0; i < e.getNumFields() ; ++i) {
			String textField = e.getField(i);
			View view = views[i];
			String form = forms[i];
			
			if (view == null) { continue; }
			
			if ("audio".equals(form)) {
				ViewId uniqueId = new ViewId(rowId, i, false);
				setupAudioLayout(view, textField, uniqueId);
			}
			else if("image".equals(form)) {
				setupImageLayout(view, textField);
	        } 
			else { //text to speech
		        setupTextAndTTSLayout(view, textField);
	        }
		}
		
		if (currentlySelected) {
			this.setBackgroundResource(R.drawable.grey_bordered_box);
		} else {
			this.setBackgroundDrawable(null);
		}
	}
	 
        
    private void setupAudioLayout(View layout, String text, ViewId uniqueId) {
    	//layout will be the previous audioButton that was scrolled off
    	AudioButton b = (AudioButton)layout;
    	b.modifyButtonForNewView(uniqueId, text);
    }
	
	private void setupTextAndTTSLayout(View layout, final String text) {
		TextView tv = (TextView)layout.findViewById(R.id.component_audio_text_txt);
		tv.setVisibility(View.VISIBLE);
	    tv.setText(highlightSearches(text == null ? "" : text));
		ImageButton btn = (ImageButton)layout.findViewById(R.id.component_audio_text_btn_audio);
		btn.setFocusable(false);

		btn.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				String textToRead = text;
				tts.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null);
			}
    	});
		if (tts == null || text == null || text.equals("")) {
			btn.setVisibility(View.INVISIBLE);
			RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
			params.width = 0;
			btn.setLayoutParams(params);
		} else {
			btn.setVisibility(View.VISIBLE);
			RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
			params.width = LayoutParams.WRAP_CONTENT;
			btn.setLayoutParams(params);
		}
    }
	
	
	public void setupImageLayout(View layout, final String text) {
		ImageView iv = (ImageView) layout;
		Bitmap b;
		if (!text.equals("")) {
			try {
				b = BitmapFactory.decodeStream(ReferenceManager._().DeriveReference(text).getStream());
				if (b == null) {
					//Input stream could not be used to derive bitmap
					iv.setImageDrawable(getResources().getDrawable(R.drawable.ic_menu_archive));
				}
				else {
					iv.setImageBitmap(b);
				}
			} catch (IOException ex) {
				ex.printStackTrace();
				//Error loading image
				iv.setImageDrawable(getResources().getDrawable(R.drawable.ic_menu_archive));
			} catch (InvalidReferenceException ex) {
				ex.printStackTrace();
				//No image
				iv.setImageDrawable(getResources().getDrawable(R.drawable.ic_menu_archive));
			}
		}
		else {
			iv.setImageDrawable(getResources().getDrawable(R.drawable.ic_menu_archive));
		}
	}
    
    private Spannable highlightSearches(String input) {
    	
	    Spannable raw = new SpannableString(input);
	    String normalized = input.toLowerCase();
		
    	if (searchTerms == null) {
    		return raw;
    	}
	    
	    //Zero out the existing spans
	    BackgroundColorSpan[] spans=raw.getSpans(0,raw.length(), BackgroundColorSpan.class);
		for (BackgroundColorSpan span : spans) {
			raw.removeSpan(span);
		}
	    
	    for (String searchText : searchTerms) {
	    	if (searchText == "") { continue;}
	
		    int index = TextUtils.indexOf(normalized, searchText);
		    
		    while (index >= 0) {
		      raw.setSpan(new BackgroundColorSpan(this.getContext().getResources().getColor(R.color.search_highlight)), index, index
		          + searchText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		      index=TextUtils.indexOf(raw, searchText, index + searchText.length());
		    }
	    }
	    
	    return raw;
    }
    
    
	
	private float[] calculateDetailWeights(int[] hints) {
		float[] weights = new float[hints.length];
		int fullSize = 100;
		int sharedBetween = 0;
		for(int hint : hints) {
			if(hint != -1) {
				fullSize -= hint;
			} else {
				sharedBetween ++;
			}
		}
		
		double average = ((double)fullSize) / (double)sharedBetween;
		
		for(int i = 0; i < hints.length; ++i) {
			int hint = hints[i];
			weights[i] = hint == -1? (float)(average/100.0) :  (float)(((double)hint)/100.0);
		}

		return weights;
	}

}
