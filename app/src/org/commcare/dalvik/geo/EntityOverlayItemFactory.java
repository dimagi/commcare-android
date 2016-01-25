package org.commcare.dalvik.geo;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;

import org.commcare.android.models.Entity;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author ctsims
 */
public class EntityOverlayItemFactory {

    private int imageIndex = -1;
    private int bigTextIndex = -1;
    private int smallTextIndex = -1;

    private final Map<String, Drawable> drawables;
    private final HashSet<String> missingImages;

    private final Drawable defDrawable;

    public EntityOverlayItemFactory(Detail shortDetail, Drawable defDrawable) {
        DetailField[] fields = shortDetail.getFields();
        for (int i = 0; i < fields.length; ++i) {
            String form = fields[i].getTemplateForm();
            //TODO: Deal with multiple images
            if ("image".equals(form)) {
                imageIndex = i;
            }
            if ("".equals(form) && (fields[i].getTemplateWidthHint() == null || !fields[i].getTemplateWidthHint().startsWith("0"))) {
                if (bigTextIndex == -1) {
                    bigTextIndex = i;
                } else if (smallTextIndex == -1) {
                    smallTextIndex = i;
                }
            }
        }

        drawables = new HashMap<>();
        missingImages = new HashSet<>();
        this.defDrawable = defDrawable;
    }

    public EntityOverlayItem generateOverlay(GeoPoint gp, Entity<TreeReference> e) {
        Drawable custom = null;
        if (imageIndex != -1) {
            String URI = (String)e.getField(imageIndex);
            if (URI != null && URI != "") {
                if (drawables.containsKey(URI)) {
                    custom = drawables.get(URI);
                } else {
                    if (!missingImages.contains(URI)) {
                        custom = loadDrawable(URI);
                    }
                }
            }
        }
        String big = bigTextIndex == -1 ? "" : (String)e.getField(bigTextIndex);
        String small = smallTextIndex == -1 ? "" : (String)e.getField(smallTextIndex);
        return new EntityOverlayItem(gp, big, small, custom);
    }

    private Drawable loadDrawable(String URI) {
        try {
            Drawable d = Drawable.createFromStream(ReferenceManager._().DeriveReference(URI).getStream(), URI);
            if (d != null) {
                //TODO: Resize;

                Bitmap bitmap = ((BitmapDrawable)d).getBitmap();
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
