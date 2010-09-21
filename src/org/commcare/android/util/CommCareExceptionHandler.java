/**
 * 
 */
package org.commcare.android.util;

import java.lang.Thread.UncaughtExceptionHandler;

import org.commcare.android.tasks.ExceptionReportTask;

/**
 * TODO: This class is basically just for testing, it should be rewritten for 
 * completeness and usefulness.
 * 
 * @author ctsims
 *
 */
public class CommCareExceptionHandler implements UncaughtExceptionHandler {

	UncaughtExceptionHandler parent;
	
	public CommCareExceptionHandler(UncaughtExceptionHandler parent) {
		this.parent = parent;
	}
	/* (non-Javadoc)
	 * @see java.lang.Thread.UncaughtExceptionHandler#uncaughtException(java.lang.Thread, java.lang.Throwable)
	 */
	public void uncaughtException(Thread thread, Throwable ex) {
		ExceptionReportTask task = new ExceptionReportTask();
		task.execute(ex);
		parent.uncaughtException(thread, ex);
	}
}
