package org.commcare.android.mocks;

import android.content.Context;

import org.commcare.utils.ConnectivityStatus;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.IOException;

/**
 * @author $|-|!˅@M
 */
@Implements(ConnectivityStatus.class)
public class ConnectivityStatusMock {

    @Implementation
    public void __constructor__(Context context) {
    }

    @Implementation
    public static ConnectivityStatus.NetworkState checkCaptivePortal() throws IOException {
        return ConnectivityStatus.NetworkState.CONNECTED;
    }
}
