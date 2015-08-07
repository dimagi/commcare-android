package org.commcare.android.util;

import org.commcare.android.tasks.ExceptionReportTask;

import java.lang.Thread.UncaughtExceptionHandler;

/**
 * TODO: This class is basically just for testing, it should be rewritten for 
 * completeness and usefulness.
 * 
 * @author ctsims
 */
public class CommCareExceptionHandler implements UncaughtExceptionHandler {

    UncaughtExceptionHandler parent;
    
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
