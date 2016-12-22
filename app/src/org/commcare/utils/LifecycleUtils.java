package org.commcare.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import org.commcare.activities.DispatchActivity;
import org.commcare.activities.UnrecoverableErrorActivity;

/**
 * Utils for exiting and restarting the app
 */
public class LifecycleUtils {
    public static void triggerHandledAppExit(Context c, String message, String title) {
        triggerHandledAppExit(c, message, title, true);
    }

    public static void triggerHandledAppExit(Context c, String message, String title,
                                             boolean useExtraMessage) {
        Intent i = new Intent(c, UnrecoverableErrorActivity.class);
        i.putExtra(UnrecoverableErrorActivity.EXTRA_ERROR_TITLE, title);
        i.putExtra(UnrecoverableErrorActivity.EXTRA_ERROR_MESSAGE, message);
        i.putExtra(UnrecoverableErrorActivity.EXTRA_USE_MESSAGE, useExtraMessage);

        // start a new stack and forget where we were (so we don't restart the app from there)
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        c.startActivity(i);
    }

    public static void restartCommCare(Activity originActivity, boolean systemExit) {
        restartCommCare(originActivity, DispatchActivity.class, systemExit);
    }

    public static void restartCommCare(Activity originActivity, Class c, boolean systemExit) {
        Intent intent = new Intent(originActivity, c);

        // Make sure that the new stack starts with the given class, and clear everything between.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        originActivity.moveTaskToBack(true);
        originActivity.startActivity(intent);
        originActivity.finish();

        if (systemExit) {
            System.exit(0);
        }
    }
}
