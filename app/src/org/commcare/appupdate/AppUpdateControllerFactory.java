package org.commcare.appupdate;

import android.content.Context;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;

/**
 * A factory that creates {@link AppUpdateController} instance.
 * @author $|-|!˅@M
 */
public class AppUpdateControllerFactory {

    /**
     * @param callback {@link Runnable} to notify when there is a change in update state.
     * @param context Application context of app.
     * @return A new {@link AppUpdateController} to use for in-app updates
     */
    public static AppUpdateController create(Runnable callback, Context context) {
        // TODO: Should we check in-app update is enabled and return a demo or full operational controller?
        return new CommcareFlexibleAppUpdateManager(callback, AppUpdateManagerFactory.create(context));
    }
}
