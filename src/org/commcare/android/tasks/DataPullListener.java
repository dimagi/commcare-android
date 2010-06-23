/**
 * 
 */
package org.commcare.android.tasks;

/**
 * @author ctsims
 *
 */
public interface DataPullListener {
	public void finished(int status);
	
	public void progressUpdate(int progressCode);
}
