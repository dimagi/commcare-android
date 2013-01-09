/**
 * 
 */
package org.commcare.dalvik.geo;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.commcare.android.models.Entity;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;

/**
 * @author ctsims
 *
 */
public class EntityOverlayItemFactory {
	
	int imageIndex = -1;
	int bigTextIndex = -1;
	int smallTextIndex = -1;
	
	private Map<String, Drawable> drawables;
	private HashSet<String> missingImages;
	
	private Drawable defDrawable; 
	
	public EntityOverlayItemFactory(Detail shortDetail, Drawable defDrawable) {
		String[] forms = shortDetail.getTemplateForms();
		for(int i = 0 ; i < forms.length ; ++i) {
			//TODO: Deal with multiple images
			if("image".equals(forms[i])) {
				imageIndex = i;
			}
			if(forms[i] == null) {
				if(bigTextIndex == -1) {
					bigTextIndex = i;
				} else if(smallTextIndex == -1) {
					smallTextIndex = i;
				}
			}
		}
		
		drawables = new HashMap<String, Drawable>();
		missingImages = new HashSet<String>();
		this.defDrawable = defDrawable;
	}
	
	public EntityOverlayItem generateOverlay(GeoPoint gp, Entity<TreeReference> e) {
		Drawable custom = null;
		if(imageIndex != -1) {
			String URI = e.getField(imageIndex);
			if(URI != null && URI != "") {
				if(drawables.containsKey(URI)) {
					custom = drawables.get(URI);
				} else {
					if(!missingImages.contains(URI)) {
						custom = loadDrawable(URI);
					}
				}
			}
		}
		String big = bigTextIndex == -1 ? "" : e.getField(bigTextIndex);
		String small = smallTextIndex == -1 ? "" : e.getField(smallTextIndex);
		return new EntityOverlayItem(gp, big, small, custom);
	}
	
	private Drawable loadDrawable(String URI) {
		try {
			Drawable d = Drawable.createFromStream(ReferenceManager._().DeriveReference(URI).getStream(), URI);
			if(d != null) {
				//TODO: Resize;

				Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
				// Scale it to 50 x 50
				d = new BitmapDrawable(Bitmap.createScaledBitmap(bitmap, defDrawable.getIntrinsicWidth(), defDrawable.getIntrinsicHeight(), true));
				// Set your new, scaled drawable "d"

				
				d = EntityOverlay.bcb(d);
				drawables.put(URI, d);
				return d;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidReferenceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		missingImages.add(URI);
		return null;
	}
}
