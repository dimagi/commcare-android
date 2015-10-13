package org.commcare.android.util;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.dalvik.activities.CrashWarningActivity;
import org.javarosa.core.util.NoLocalizedTextException;
import org.javarosa.xpath.XPathTypeMismatchException;

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

    public static final String CRASH_EXCEPTION_KEY = "crash-exception";

    public CommCareExceptionHandler(UncaughtExceptionHandler parent,
                                    Context ctx) {
        this.parent = parent;
        this.ctx = ctx.getApplicationContext();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {

        if (isUserCreatedCrash(ex)) {
            warnUserAndExit(ex);
        } else {
            ExceptionReportTask task = new ExceptionReportTask();
            task.execute(ex);

            // handle error normally (report to ACRA/play store)
            parent.uncaughtException(thread, ex);
        }
    }

    /**
     * Launch activity showing user details of the crash if it is something
     * they can fix. Sends crash to CommCare server, then exits.
     */
    private void warnUserAndExit(Throwable ex) {
        Intent i = new Intent(ctx, CrashWarningActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Bundle extras = new Bundle();
        extras.putSerializable(CRASH_EXCEPTION_KEY, ex);
        i.putExtras(extras);

        ctx.startActivity(i);
        // You must close the crashed thread in order to start a new activity.
        System.exit(0);
    }

    private boolean isUserCreatedCrash(Throwable ex) {
        return (ex instanceof NoLocalizedTextException ||
                ex instanceof XPathTypeMismatchException);
    }
}
