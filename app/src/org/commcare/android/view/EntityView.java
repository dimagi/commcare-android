/**
 * 
 */
package org.commcare.android.view;

import java.io.IOException;

import org.commcare.android.models.Entity;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public class EntityView extends LinearLayout {
	
	private View[] views;
	private String[] forms; 

	public EntityView(Context context, Detail d, Entity e) {
		super(context);

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
		
		views = new View[headerText.length];
		
		
		float[] lengths = calculateDetailWeights(d.getHeaderSizeHints());
		String[] headerForms = d.getHeaderForms();
		
		for(int i = 0 ; i < views.length ; ++i) {
			if(lengths[i] != 0) {
		        LayoutParams l = new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, lengths[i]);
		        
		        views[i] = getView(context, headerText[i], headerForms[i]);
		        views[i].setId(i);
		        addView(views[i], l);
			}
		}
	}
	
	private View getView(Context context, String text, String form) {
		System.out.println("116: Get Entity View");
		View retVal;
        if("image".equals(form)) {
        	ImageView iv =(ImageView)View.inflate(context, R.layout.entity_item_image, null);
			retVal = iv;
        	if(text != null) {
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
        } else {
        	TextView tv =(TextView)View.inflate(context, R.layout.entity_item_text, null);
	        retVal = tv;
        	if(text != null) {
        		tv.setText(text);
        	}
        }
        return retVal;
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

	public void setParams(Entity e, boolean currentlySelected) {
		for(int i = 0; i < e.getNumFields() ; ++i) {
			//Empty (width = 0) field
			if(views[i] == null) { continue;}
			
			if(e.getField(i) == null) {
				continue;
			}
	        if("image".equals(forms[i])) {
	        	ImageView iv = (ImageView)views[i];
				Bitmap b;
				try {
					if(!e.getField(i).equals("")) {
						b = BitmapFactory.decodeStream(ReferenceManager._().DeriveReference(e.getField(i)).getStream());
						iv.setImageBitmap(b);
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
	        } else {
		        ((TextView)views[i]).setText(e.getField(i));
	        }
		}
		
		if(currentlySelected) {
			this.setBackgroundResource(R.drawable.grey_bordered_box);
		} else{
			this.setBackgroundDrawable(null);
		}
	}
}
