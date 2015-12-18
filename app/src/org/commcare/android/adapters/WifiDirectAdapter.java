package org.commcare.android.adapters;


import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.activities.CommCareWiFiDirectActivity;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;

/**
 * Sets up home screen buttons and gives accessors for setting their visibility and listeners
 * Created by dancluna on 3/19/15.
 */
public class WifiDirectAdapter extends SquareButtonAdapter {
    private static final String TAG = WifiDirectAdapter.class.getSimpleName();
    CommCareWiFiDirectActivity activity;

    public WifiDirectAdapter(CommCareWiFiDirectActivity activity) {
        super(activity);
        this.activity = activity;
    }

    @Override
    protected SquareButtonObject[] getButtonResources() {

        SquareButtonObject changeModeButton = new SquareButtonObject(activity, R.layout.wifi_direct_change_button) {
            @Override
            public boolean isHidden() {
                return false;
            }
        };
        SquareButtonObject transferFilesButton = new SquareButtonObject(activity, R.layout.wifi_direct_transfer_button) {
            @Override
            public boolean isHidden() {
                return false;
            }
        };
        SquareButtonObject discoverPeersButton = new SquareButtonObject(activity, R.layout.wifi_direct_discover_button) {
            @Override
            public boolean isHidden() {
                return false;
            }
        };
        SquareButtonObject submitButton = new SquareButtonObject(activity, R.layout.wifi_direct_submit_button) {
            @Override
            public boolean isHidden() {
                return true;
            }
        };

        return new SquareButtonObject[]{
                changeModeButton, transferFilesButton, discoverPeersButton, submitButton
        };
    }
}
