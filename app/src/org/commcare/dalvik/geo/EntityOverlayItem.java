package org.commcare.dalvik.geo;

import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.OverlayItem;

/**
 * @author ctsims
 */
public class EntityOverlayItem extends OverlayItem {
    private Drawable custom = null;
    public EntityOverlayItem(GeoPoint gp, String big, String small, Drawable custom) {
        super(gp, big, small);
        this.custom = custom;
    }
    @Override
    public Drawable getMarker(int stateBitset) {
        if(custom == null) {
            return super.getMarker(stateBitset);
        } else {
            return custom;
        }
    }
}
