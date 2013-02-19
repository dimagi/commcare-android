/**
 * 
 */
package org.commcare.android.tasks.templates;

/**
 * @author ctsims
 *
 */
public interface CommCareTaskConnector<R> {
	public <A, B, C> void connectTask(CommCareTask<A,B,C,R> task);
	
	public void startBlockingForTask(int id);
	
	public void stopBlockingForTask(int id);
	
	public void taskCancelled(int id);

	public R getReceiver();
}
