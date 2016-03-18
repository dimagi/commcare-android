package org.commcare.android.nsd;

/**
 * Interface for being notified about Nsd service availability from the NSDDiscoveryTools
 *
 * Created by ctsims on 2/19/2016.
 */
public interface NsdServiceListener {
    void onMicronodeDiscovery();
}
