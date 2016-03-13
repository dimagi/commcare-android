package org.commcare.activities.components;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import org.commcare.activities.EntitySelectActivity;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Callout;
import org.commcare.suite.model.CalloutData;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localization;

import java.io.IOException;
import java.util.Map;

public class EntitySelectCalloutSetup {
    /**
     * Updates the ImageView layout that is passed in, based on the
     * new id and source
     */
    public static void setupImageLayout(Context context, MenuItem menuItem, final String imagePath) {
        Drawable drawable = getCalloutDrawable(context, imagePath);
        menuItem.setIcon(drawable);
    }

    /**
     * Updates the ImageView layout that is passed in, based on the
     * new id and source
     */
    public static void setupImageLayout(Context context, View layout, final String imagePath) {
        ImageView iv = (ImageView)layout;
        Drawable drawable = getCalloutDrawable(context, imagePath);
        iv.setImageDrawable(drawable);
    }

    private static Drawable getCalloutDrawable(Context context, String imagePath){
        Bitmap b;
        if (!imagePath.equals("")) {
            try {
                b = BitmapFactory.decodeStream(ReferenceManager._().DeriveReference(imagePath).getStream());
                if (b == null) {
                    // Input stream could not be used to derive bitmap, so
                    // showing error-indicating image
                    return context.getResources().getDrawable(R.drawable.ic_menu_archive);
                } else {
                    return new BitmapDrawable(b);
                }
            } catch (IOException | InvalidReferenceException ex) {
                ex.printStackTrace();
                // Error loading image, default to folder button
                return context.getResources().getDrawable(R.drawable.ic_menu_archive);
            }
        } else {
            // no image passed in, draw a white background
            return context.getResources().getDrawable(R.color.white);
        }
    }

    /**
     * @return A click listener that launches QR code scanner
     */
    public static View.OnClickListener makeBarcodeClickListener(final Activity activity) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent("com.google.zxing.client.android.SCAN");
                try {
                    activity.startActivityForResult(i, EntitySelectActivity.BARCODE_FETCH);
                } catch (ActivityNotFoundException anfe) {
                    Toast.makeText(activity,
                            Localization.get("barcode.reader.missing"),
                            Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    /**
     * Build click listener from callout: set button's image, get intent action,
     * and copy extras into intent.
     *
     * @param callout contains intent action and extras, and sometimes button image
     * @return click listener that launches the callout's activity with the
     * associated callout extras
     */
    public static View.OnClickListener makeCalloutClickListener(final Activity activity, Callout callout) {
        final CalloutData calloutData = callout.getRawCalloutData();

        final Intent i = new Intent(calloutData.getActionName());
        for (Map.Entry<String, String> keyValue : calloutData.getExtras().entrySet()) {
            i.putExtra(keyValue.getKey(), keyValue.getValue());
        }
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    activity.startActivityForResult(i, EntitySelectActivity.CALLOUT);
                } catch (ActivityNotFoundException anfe) {
                    Toast.makeText(activity,
                            Localization.get("callout.missing", new String[]{i.getAction()}),
                            Toast.LENGTH_LONG).show();
                }
            }
        };
    }
}
