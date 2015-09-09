package org.commcare.android.util;

import org.commcare.android.tasks.ExceptionReportTask;

import java.lang.Thread.UncaughtExceptionHandler;

/**
 * @author ctsims
 */
public class CommCareExceptionHandler implements UncaughtExceptionHandler {

    private final UncaughtExceptionHandler parent;

    public CommCareExceptionHandler(UncaughtExceptionHandler parent) {
        this.parent = parent;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        ExceptionReportTask task = new ExceptionReportTask();
        task.execute(ex);
       
        parent.uncaughtException(thread, ex);
    }
}
