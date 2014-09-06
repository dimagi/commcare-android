/**
 * 
 */
package org.commcare.dalvik.geo;

import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.OverlayItem;

/**
 * @author ctsims
 *
 */
public class EntityOverlayItem extends OverlayItem {
    Drawable custom = null;
    public EntityOverlayItem(GeoPoint gp, String big, String small, Drawable custom) {
        super(gp, big, small);
        this.custom = custom;
    }
    /* (non-Javadoc)
     * @see com.google.android.maps.OverlayItem#getMarker(int)
     */
    @Override
    public Drawable getMarker(int stateBitset) {
        if(custom == null) {
            return super.getMarker(stateBitset);
        } else {
            return custom;
        }
    }
    
    
}
