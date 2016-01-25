package org.commcare.android.util;

import org.commcare.suite.model.CalloutData;

/**
 * @author ctsims
 * @see org.commcare.android.logic.DetailCalloutListenerDefaultImpl
 */
public interface DetailCalloutListener {
    void callRequested(String phoneNumber);

    void addressRequested(String address);

    void playVideo(String videoRef);

    void performCallout(CalloutData callout, int id);
}
