package org.commcare.utils;

import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.activities.CrashWarningActivity;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.recovery.measures.ExecuteRecoveryMeasuresActivity;
import org.commcare.recovery.measures.RecoveryMeasuresHelper;
import org.javarosa.core.util.NoLocalizedTextException;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Vector;

import javax.annotation.Nullable;

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
        // Always report to HQ device logs
        ForceCloseLogger.reportExceptionInBg(ex);

        if (RecoveryMeasuresHelper.recoveryMeasuresPending()) {
            startRecoveryMeasureActivity();
            CrashUtil.reportException(ex);

            // You must close the crashed thread in order to start a new activity.
            System.exit(0);
        } else if (warnUserAndExit(ex)) {
            CrashUtil.reportException(ex);

            // You must close the crashed thread in order to start a new activity.
            System.exit(0);
        } else {
            // Default error handling, which includes reporting to Crashlytics
            parent.uncaughtException(thread, ex);
        }
    }

    private void startRecoveryMeasureActivity() {
        System.out.println("Executing recovery measures for app " +
                CommCareApplication.instance().getCurrentApp().getAppRecord().getDisplayName());
        Intent i = new Intent(ctx, ExecuteRecoveryMeasuresActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ctx.startActivity(i);
    }

    /**
     * Launch activity showing user details of the crash if it is something
     * they can fix.
     */
    private boolean warnUserAndExit(Throwable ex) {
        Vector<Throwable> causes = getAllCausesForException(ex);
        Throwable localizedException = getLocalizationException(causes);
        if (localizedException != null) {
            Intent i = new Intent(ctx, CrashWarningActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra(WARNING_MESSAGE_KEY, localizedException.getMessage());
            ctx.startActivity(i);
            return true;
        }

        Throwable sessionUnavailableException = getSessionUnavailableException(causes);
        if (sessionUnavailableException != null) {
            SessionRegistrationHelper.redirectToLogin(ctx);
            return true;
        }
        return false;
    }

    private Vector<Throwable> getAllCausesForException(Throwable ex) {
        Throwable exception = new Throwable(ex);
        Vector<Throwable> causes = new Vector<>();
        while (exception != null) {
            causes.add(exception);
            exception = exception.getCause();
        }
        return causes;
    }

    @Nullable
    private static Throwable getLocalizationException(Vector<Throwable> causes) {
        for (Throwable cause : causes) {
            if (cause instanceof NoLocalizedTextException) {
                return cause;
            }
        }
        return null;
    }

    @Nullable
    private static Throwable getSessionUnavailableException(Vector<Throwable> causes) {
        for (Throwable cause : causes) {
            if (cause instanceof SessionUnavailableException) {
                return cause;
            }
        }
        return null;
    }
}
