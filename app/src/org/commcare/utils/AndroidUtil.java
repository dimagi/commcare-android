package org.commcare.utils;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * @author ctsims
 */
public class AndroidUtil {
    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    /**
     * Generate a value suitable for use in setId(int).
     * This value will not collide with ID values generated at build time by aapt for R.id.
     *
     * @return a generated ID value
     */
    public static int generateViewId() {
        return View.generateViewId();
    }

    /**
     * Returns an int array with the color values for the given attributes (R.attr).
     * Any unresolved colors will be represented by -1
     */
    public static int[] getThemeColorIDs(final Context context, final int[] attrs) {
        int[] colors = new int[attrs.length];
        Resources.Theme theme = context.getTheme();
        for (int i = 0; i < attrs.length; i++) {
            TypedValue typedValue = new TypedValue();
            if (theme.resolveAttribute(attrs[i], typedValue, true)) {
                colors[i] = typedValue.data;
            } else {
                colors[i] = -1;
            }
        }
        return colors;
    }

    public static void showToast(Context context, int stringResource) {
        Toast.makeText(context,
                StringUtils.getStringRobust(
                        context,
                        stringResource),
                Toast.LENGTH_LONG).show();
    }

    public static HashMap<String, String> bundleAsMap(Bundle bundle) {
        HashMap<String, String> result = new HashMap<>();
        for (String key : bundle.keySet()) {
            result.put(key, bundle.getString(key));
        }
        return result;
    }

    public static boolean isGooglePlayServicesAvailable(Context ctx) {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS;
    }



    /**
     * Attaches a WindowInsetsListener to the root view of the activity for devices running Android 15+. This
     * listener applies padding to the view based on the system window insets, including the keyboard.
     *
     * @param activity   The activity to which the listener is attached.
     * @param rootViewId The ID of the root view in the activity's layout.
     */
    @RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static void attachWindowInsetsListener(AppCompatActivity activity, int rootViewId) {
        View activityRootView = activity.findViewById(rootViewId);

        if (activityRootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(activityRootView, (view, insets) -> {
                Insets obstructions = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                                | WindowInsetsCompat.Type.ime());

                // Apply padding so content doesn't overlap with system bars or the keyboard. Edge-to-edge
                // enforcement makes adjustResize a no-op, so this is what makes room for the IME.
                view.setPadding(obstructions.left, obstructions.top, obstructions.right, obstructions.bottom);
                return insets;
            });
        }
    }
}
