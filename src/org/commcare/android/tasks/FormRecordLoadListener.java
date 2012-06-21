/**
 * 
 */
package org.commcare.android.tasks;

/**
 * @author ctsims
 *
 */
public interface FormRecordLoadListener {

	void notifyPriorityLoaded(Integer first, boolean contains);
	
	void notifyLoaded();
}
