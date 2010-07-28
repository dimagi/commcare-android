/**
 * 
 */
package org.commcare.android.util;

/**
 * @author ctsims
 *
 */
public interface DetailCalloutListener {
	public void callRequested(String phoneNumber);

	public void addressRequested(String address);
}
