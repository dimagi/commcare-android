/**
 * 
 */
package org.commcare.android.view;

import java.io.File;
import java.io.IOException;

import org.commcare.android.models.Entity;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.speech.tts.TextToSpeech;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.media.MediaPlayer;

/**
 * @author ctsims
 *
 */
public class EntityView extends LinearLayout {
	
	private View[] views;
	private String[] forms;
	private TextToSpeech tts; 
	private MediaPlayer mp;
	private String[] searchTerms;

	public EntityView(Context context, Detail d, Entity e, TextToSpeech tts, String[] searchTerms) {
		super(context);
		this.searchTerms = searchTerms;
		this.tts = tts;
		mp = new MediaPlayer();

		this.setWeightSum(1);
		
		views = new View[e.getNumFields()];
		forms = d.getTemplateForms();
		
		float[] weights = calculateDetailWeights(d.getTemplateSizeHints());
		
		for(int i = 0 ; i < views.length ; ++i) {
			if(weights[i] != 0) {
				LayoutParams l = new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, weights[i]);
		        views[i] = getView(context, null, forms[i]);
		        views[i].setId(i);
		        addView(views[i], l);
			}
		}
        
		setParams(e, false);
	}
	
	public EntityView(Context context, Detail d, String[] headerText) {
		super(context);

		this.setWeightSum(1);
		mp = new MediaPlayer();
		views = new View[headerText.length];
		
		
		float[] lengths = calculateDetailWeights(d.getHeaderSizeHints());
		String[] headerForms = d.getHeaderForms();
		
		for(int i = 0 ; i < views.length ; ++i) {
			if(lengths[i] != 0) {
		        LayoutParams l = new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, lengths[i]);
		        /*this first call is just demarcating the space for each view in a row, and then
		         * setParams will fill in those existing views with the appropriate content, depending
		         * on the form type
		         */
		        views[i] = getView(context, headerText[i], headerForms[i]);
		        views[i].setId(i);
		        addView(views[i], l);
			}
		}
	}
	
	/*
	 * if form = "text", then 'text' field is just normal text
	 * if form = "audio" or "image", then text is the path to the audio/image
	 */
	private View getView(Context context, String text, String form) {
		View retVal;
		if ("image".equals(form)) {
        	ImageView iv =(ImageView)View.inflate(context, R.layout.entity_item_image, null);
			retVal = iv;
        	if (text != null) {
				try {
					Bitmap b = BitmapFactory.decodeStream(ReferenceManager._().DeriveReference(text).getStream());
					iv.setImageBitmap(b);
				} catch (IOException e) {
					e.printStackTrace();
					//Error loading image
				} catch (InvalidReferenceException e) {
					e.printStackTrace();
					//No image
				}
        	}
        } 
		else if ("audio".equals(form)) {
			View layout = View.inflate(context, R.layout.component_audio_text, null);
    		setupAudioLayout(layout,text);
    		retVal = layout;
        } 
        else {
    		View layout = View.inflate(context, R.layout.component_audio_text, null);
    		setupTTSLayout(layout, text);
    		retVal = layout;
        }
        return retVal;
	}
	
	public void setSearchTerms(String[] terms) {
		this.searchTerms = terms;
	}
	

	public void setParams(Entity e, boolean currentlySelected) {
		for (int i = 0; i < e.getNumFields() ; ++i) {
			String textField = e.getField(i);
			View view = views[i];
			String form = forms[i];
			
			if (view == null || textField == null) { continue; }
			
			if ("audio".equals(form)) {
				setupAudioLayout(view, textField);
			}
			else if("image".equals(form)) {
				setupImageLayout(view, textField);
	        } 
			else { //text to speech
		        setupTTSLayout(view, textField);
	        }
		}
		
		if (currentlySelected) {
			this.setBackgroundResource(R.drawable.grey_bordered_box);
		} else {
			this.setBackgroundDrawable(null);
		}
	}
        
    private void setupAudioLayout(View layout, final String text) {
    	ImageButton btn = (ImageButton)layout.findViewById(R.id.component_audio_text_btn_audio);
		btn.setFocusable(false);
		btn.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				if (mp.isPlaying()) {
					mp.pause();
				}
				else {
					try {
						mp.setDataSource(text);
						mp.prepare();
						mp.start();
					} catch (IllegalArgumentException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (SecurityException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IllegalStateException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
    	});
		
		if (text == null || text.equals("") || !new File(text).exists()) {
			btn.setVisibility(View.INVISIBLE);
			RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
			params.width= 0;
			btn.setLayoutParams(params);
		} else {
			btn.setVisibility(View.VISIBLE);
			RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
			params.width=LayoutParams.WRAP_CONTENT;
			btn.setLayoutParams(params);
		}
    }
	
	private void setupTTSLayout(View layout, final String text) {
		TextView tv = (TextView)layout.findViewById(R.id.component_audio_text_txt);
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
			params.width= 0;
			btn.setLayoutParams(params);
		} else {
			btn.setVisibility(View.VISIBLE);
			RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
			params.width=LayoutParams.WRAP_CONTENT;
			btn.setLayoutParams(params);
		}
	    tv.setText(highlightSearches(text == null ? "" : text));
    }
	
	
	public void setupImageLayout(View layout, final String text) {
		ImageView iv = (ImageView) layout;
		Bitmap b;
		try {
			if(!text.equals("")) {
				b = BitmapFactory.decodeStream(ReferenceManager._().DeriveReference(text).getStream());
				//TODO: shouldn't this check if b is null?
				iv.setImageBitmap(b);
			} else{
				iv.setImageBitmap(null);
			}
		} catch (IOException ex) {
			ex.printStackTrace();
			//Error loading image
			iv.setImageBitmap(null);
		} catch (InvalidReferenceException ex) {
			ex.printStackTrace();
			//No image
			iv.setImageBitmap(null);
		}
	}
    
    private Spannable highlightSearches(String input) {
    	
	    Spannable raw=new SpannableString(input);
	    
	    String normalized = input.toLowerCase();
		
    	if(searchTerms == null) {
    		return raw;
    	}
	    
	    //Zero out the existing spans
	    BackgroundColorSpan[] spans=raw.getSpans(0,raw.length(), BackgroundColorSpan.class);
		for (BackgroundColorSpan span : spans) {
			raw.removeSpan(span);
		}
	    
	    for(String searchText : searchTerms) {
	    	if(searchText == "") { continue;}
	
		    int index=TextUtils.indexOf(normalized, searchText);
		    
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
