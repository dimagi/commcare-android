/**
 * 
 */
package org.commcare.android.view;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.commcare.android.models.Entity;
import org.commcare.android.util.MediaUtil;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.speech.tts.TextToSpeech;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.media.AudioManager;
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
	private FileInputStream fis;
	private String[] searchTerms;
	private Context context;

	/*
	 * Constructor for row/column contents
	 */
	public EntityView(Context context, Detail d, Entity e, TextToSpeech tts, String[] searchTerms) {
		super(context);
		this.context = context;
		this.searchTerms = searchTerms;
		this.tts = tts;
		this.setWeightSum(1);
		views = new View[e.getNumFields()];
		forms = d.getTemplateForms();
		float[] weights = calculateDetailWeights(d.getTemplateSizeHints());
		
		for (int i = 0; i < views.length; ++i) {
			if (weights[i] != 0) {
		        views[i] = establishView(null, forms[i]);
		        views[i].setId(i);
			}
		}
		setParams(e, false);
		for (int i = 0; i < views.length; i++) {
			if (weights[i] != 0) {
				LayoutParams l = new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, weights[i]);
				addView(views[i], l);
			}
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
		        views[i] = establishView(headerText[i], headerForms[i]);      
		        views[i].setId(i);
		        addView(views[i], l);
			}
		}
	}
	
	/*
	 * if form = "text", then 'text' field is just normal text
	 * if form = "audio" or "image", then text is the path to the audio/image
	 */
	private View establishView(String text, String form) {
		View retVal;
		if ("image".equals(form)) {
			ImageView iv =(ImageView)View.inflate(context, R.layout.entity_item_image, null);
			retVal = iv;
        } 
		else if ("audio".equals(form)) {
			View layout = View.inflate(context, R.layout.component_audio_text, null);
    		retVal = layout;
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
	

	public void setParams(Entity e, boolean currentlySelected) {
		for (int i = 0; i < e.getNumFields() ; ++i) {
			String textField = e.getField(i);
			View view = views[i];
			String form = forms[i];
			
			if (view == null) { continue; }
			
			if ("audio".equals(form)) {
				setupAudioLayout(view, textField, i);
			}
			else if("image".equals(form)) {
				setupImageLayout(view, textField, i);
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
	 
        
    private void setupAudioLayout(View layout, final String text, int index) {
    	Log.i("form", "setupAudioLayout entered");
    	ImageButton btn = (ImageButton)layout.findViewById(R.id.component_audio_text_btn_audio);
    	btn.setVisibility(View.VISIBLE);
		btn.setFocusable(false);
		btn.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				if (mp.isPlaying()) {
					mp.stop();
					try {
						fis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				else {
					try {
						mp.prepare();
						mp.start();
					} catch (IllegalStateException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
    	});
		
		boolean mpFailure = true;
		if (text != null && !text.equals("")) {
			//try to initialize the media player
			try {
				mp = new MediaPlayer();
				InputStream is = ReferenceManager._().DeriveReference(text).getStream();
				fis = MediaUtil.inputStreamToFIS(is);
				mp.setDataSource(fis.getFD());
				mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
				mpFailure = false;
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		//enable/disable audio button based on media player set-up
		if (mpFailure) {
			btn.setEnabled(false);
			RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
			params.width = LayoutParams.WRAP_CONTENT;
			btn.setLayoutParams(params);
		} 
		else {
			btn.setEnabled(true);
			RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
			params.width = LayoutParams.WRAP_CONTENT;
			btn.setLayoutParams(params);
		}
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
			params.width= 0;
			btn.setLayoutParams(params);
		} else {
			btn.setVisibility(View.VISIBLE);
			RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) btn.getLayoutParams();
			params.width=LayoutParams.WRAP_CONTENT;
			btn.setLayoutParams(params);
		}
    }
	
	
	public void setupImageLayout(View layout, final String text, int index) {
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
