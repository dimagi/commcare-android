/**
 * 
 */
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
	private OverlayItem previousoverlay;

	public EntityOverlay(Context context, Drawable defaultMarker, MapView view) {
		super(boundCenterBottom(defaultMarker), view);
		this.context = context;
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
		if (previousoverlay != null) {
			if (index < maxNum) {
				overlays[index] = previousoverlay;
			} else {
				index = 0;
				full = true;
				overlays[index] = previousoverlay;
			}
			references[index] = reference;
			index++;
			populate();
		}
		this.previousoverlay = overlay;
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
}
