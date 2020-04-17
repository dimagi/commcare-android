package org.commcare.appupdate;

import android.content.Context;
import android.os.Build;

import com.google.android.play.core.appupdate.AppUpdateManagerFactory;

/**
 * A factory that creates {@link AppUpdateController} instance.
 * @author $|-|!˅@M
 */
public class AppUpdateControllerFactory {

    /**
     * @param callback {@link Runnable} to notify when there is a change in update state.
     * @param context Application context of app.
     * @return A new {@link FlexibleAppUpdateController} to use for in-app updates
     */
    public static FlexibleAppUpdateController create(Runnable callback, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // In-app updates works only with devices running Android 5.0 (API level 21) or higher
            return new CommcareFlexibleAppUpdateManager(callback, AppUpdateManagerFactory.create(context));
        } else {
            return new DummyFlexibleAppUpdateManager();
        }
    }
}
