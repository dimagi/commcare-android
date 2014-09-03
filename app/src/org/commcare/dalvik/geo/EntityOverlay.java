package org.commcare.dalvik.geo;

import org.javarosa.core.model.instance.TreeReference;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;
import com.readystatesoftware.mapviewballoons.BalloonItemizedOverlay;

/**
 * @author ctsims
 *
 */
public abstract class EntityOverlay extends BalloonItemizedOverlay {
    private static int maxNum = 200;
    private OverlayItem overlays[] = new OverlayItem[maxNum];
    private TreeReference references[] = new TreeReference[maxNum];
    private int index = 0;
    private boolean full = false;
    private Context context;

    public EntityOverlay(Context context, Drawable defaultMarker, MapView view) {
        super(boundCenterBottom(defaultMarker), view);
        this.context = context;
    }

    /*
     * (non-Javadoc)
     * @see com.google.android.maps.ItemizedOverlay#createItem(int)
     */
    @Override
    protected OverlayItem createItem(int i) {
        return overlays[i];
    }

    /*
     * (non-Javadoc)
     * @see com.google.android.maps.ItemizedOverlay#size()
     */
    @Override
    public int size() {
        if (full) {
            return overlays.length;
        } else {
            return index;
        }

    }

    public void addOverlay(OverlayItem overlay, TreeReference reference) {
        if (index < maxNum) {
            overlays[index] = overlay;
        } else {
            index = 0;
            full = true;
            overlays[index] = overlay;
        }
        references[index] = reference;
        index++;
        populate();
    }
    
    /* (non-Javadoc)
     * @see com.readystatesoftware.mapviewballoons.BalloonItemizedOverlay#onBalloonTap(int, com.google.android.maps.OverlayItem)
     */
    @Override
    protected boolean onBalloonTap(int index, OverlayItem item) {
        selected(references[index]);
        return true;
    }
    
    protected abstract void selected(TreeReference ref);
    
    public static Drawable bcb(Drawable d) {
        return EntityOverlay.boundCenterBottom(d);
    }
}
