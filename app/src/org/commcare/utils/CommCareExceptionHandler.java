package org.commcare.utils;

import android.content.Context;
import android.content.Intent;

import org.commcare.activities.CrashWarningActivity;
import org.commcare.tasks.ExceptionReporting;
import org.javarosa.core.util.NoLocalizedTextException;

import java.lang.Thread.UncaughtExceptionHandler;

/**
 * Report unrecoverable exception to servers and shows user crash message
 * for certain exceptions.  If the user is shown the crash message, they
 * are not given the option to report a problem.
 *
 * @author ctsims
 */
public class CommCareExceptionHandler implements UncaughtExceptionHandler {
    private final UncaughtExceptionHandler parent;
    private final Context ctx;

    public static final String WARNING_MESSAGE_KEY = "warning-message";

    public CommCareExceptionHandler(UncaughtExceptionHandler parent,
                                    Context ctx) {
        this.parent = parent;
        this.ctx = ctx.getApplicationContext();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        ExceptionReporting.reportExceptionInBg(ex);

        if (warnUserAndExit(ex)) {
            // You must close the crashed thread in order to start a new activity.
            System.exit(0);
        } else {
            // handle error normally (report to ACRA/play store)
            parent.uncaughtException(thread, ex);
        }
    }

    /**
     * Launch activity showing user details of the crash if it is something
     * they can fix.
     */
    private boolean warnUserAndExit(Throwable ex) {
        if (ex instanceof NoLocalizedTextException) {
            Intent i = new Intent(ctx, CrashWarningActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra(WARNING_MESSAGE_KEY, ex.getMessage());
            ctx.startActivity(i);
            return true;
        }
        return false;
    }
}
