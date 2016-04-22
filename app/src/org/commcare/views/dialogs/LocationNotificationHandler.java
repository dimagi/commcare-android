package org.commcare.views.dialogs;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;

import org.commcare.activities.EntitySelectActivity;
import org.commcare.utils.GeoUtils;

import java.lang.ref.WeakReference;

/**
 * Handler class for displaying alert dialog when no location providers are found.
 * Message-passing is necessary because the dialog is displayed during the course of evaluation
 * of the here() function, which occurs in a background thread (EntityLoaderTask).
 *
 * @author Forest Tong (ftong@dimagi.com)
 */
public class LocationNotificationHandler extends Handler {
    // Use a weak reference to avoid potential memory leaks
    private final WeakReference<EntitySelectActivity> mActivity;

    public LocationNotificationHandler(EntitySelectActivity activity) {
        mActivity = new WeakReference<>(activity);
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);

        final EntitySelectActivity activity = mActivity.get();
        if (activity != null) {
            DialogInterface.OnClickListener onChangeListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int i) {
                    switch (i) {
                        case DialogInterface.BUTTON_POSITIVE:
                            Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            activity.startActivity(intent);
                            EntitySelectActivity.getHereFunctionHandler().allowGpsUse();
                            break;
                        case DialogInterface.BUTTON_NEGATIVE:
                            break;
                    }
                    dialog.dismiss();
                }
            };

            GeoUtils.showNoGpsDialog(activity, onChangeListener);
        }
        // otherwise handler has outlived activity, do nothing
    }
}
