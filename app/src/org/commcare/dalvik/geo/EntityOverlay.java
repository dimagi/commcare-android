package org.commcare.dalvik.geo;

import android.graphics.drawable.Drawable;

import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;
import com.readystatesoftware.mapviewballoons.BalloonItemizedOverlay;

import org.javarosa.core.model.instance.TreeReference;

/**
 * @author ctsims
 */
public abstract class EntityOverlay extends BalloonItemizedOverlay {
    private static final int maxNum = 200;
    private final OverlayItem[] overlays = new OverlayItem[maxNum];
    private final TreeReference[] references = new TreeReference[maxNum];
    private int index = 0;
    private boolean full = false;

    public EntityOverlay(Drawable defaultMarker, MapView view) {
        super(boundCenterBottom(defaultMarker), view);
    }

    @Override
    protected OverlayItem createItem(int i) {
        return overlays[i];
    }

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
