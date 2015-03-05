/**
 * 
 */
package org.commcare.android.util;

/**
 * @author ctsims
 * @see org.commcare.android.logic.DetailCalloutListenerDefaultImpl
 */
public interface DetailCalloutListener {
    public void callRequested(String phoneNumber);

    public void addressRequested(String address);
    
    public void playVideo(String videoRef);
}
