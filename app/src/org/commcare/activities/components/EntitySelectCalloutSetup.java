package org.commcare.activities.components;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;

import org.commcare.activities.EntitySelectActivity;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Callout;
import org.commcare.suite.model.CalloutData;
import org.commcare.utils.MediaUtil;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.services.locale.Localization;

import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;

public class EntitySelectCalloutSetup {
    /**
     * Updates the ImageView layout that is passed in, based on the
     * new id and source
     */
    public static void setupImageLayout(Context context, MenuItem menuItem,
                                        final String imagePath) {
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

    private static Drawable getCalloutDrawable(Context context, String imagePath) {
        Bitmap b;
        if (!imagePath.equals("")) {
            int actionBarHeight = MediaUtil.getActionBarHeightInPixels(context);
            b = MediaUtil.inflateDisplayImage(context, imagePath, -1, actionBarHeight);
            if (b == null) {
                // Input stream could not be used to derive bitmap, so
                // showing error-indicating image
                return context.getResources().getDrawable(R.drawable.ic_menu_archive);
            } else {
                return new BitmapDrawable(b);
            }
        } else {
            // no image passed in, draw a white background
            return context.getResources().getDrawable(R.color.white);
        }
    }

    /**
     * @return A click listener that launches QR code scanner
     */
    public static View.OnClickListener makeBarcodeClickListener(final AppCompatActivity activity) {
        return v -> {
            Intent intent = new IntentIntegrator(activity).createScanIntent();
            try {
                activity.startActivityForResult(intent, EntitySelectActivity.BARCODE_FETCH);
            } catch (ActivityNotFoundException anfe) {
                Toast.makeText(activity,
                        Localization.get("barcode.reader.missing"),
                        Toast.LENGTH_LONG).show();
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
    public static View.OnClickListener makeCalloutClickListener(final AppCompatActivity activity,
                                                                Callout callout, EvaluationContext ec) {
        final Intent i = buildCalloutIntent(callout, ec);
        return v -> {
            try {
                activity.startActivityForResult(i, EntitySelectActivity.CALLOUT);
            } catch (ActivityNotFoundException anfe) {
                Toast.makeText(activity,
                        Localization.get("callout.missing", new String[]{i.getAction()}),
                        Toast.LENGTH_LONG).show();
            }
        };
    }

    public static Intent buildCalloutIntent(Callout callout, EvaluationContext ec) {
        final CalloutData calloutData = callout.evaluate(ec);
        Intent i = new Intent(calloutData.getActionName());
        for (Map.Entry<String, String> keyValue : calloutData.getExtras().entrySet()) {
            i.putExtra(keyValue.getKey(), keyValue.getValue());
        }
        if (calloutData.getType() != null && !"".equals(calloutData.getType())) {
            i.setType(calloutData.getType());
        }
        return i;
    }
}
