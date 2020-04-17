package org.commcare.appupdate;

import android.content.Context;
import android.os.Build;

import com.google.android.play.core.appupdate.AppUpdateManagerFactory;

import org.commcare.preferences.HiddenPreferences;

import java.util.concurrent.TimeUnit;

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
        long lastCheckTime = HiddenPreferences.getLastInAppUpdateCheckTime();
        boolean isLastUpdateOverOneDay = (System.currentTimeMillis() - lastCheckTime) > TimeUnit.DAYS.toMillis(1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isLastUpdateOverOneDay) {
            // In-app updates works only with devices running Android 5.0 (API level 21) or higher
            return new CommcareFlexibleAppUpdateManager(callback, AppUpdateManagerFactory.create(context));
        } else {
            return new DummyFlexibleAppUpdateManager();
        }
    }
}
