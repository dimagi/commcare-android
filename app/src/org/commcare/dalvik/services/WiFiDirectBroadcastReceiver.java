package org.commcare.dalvik.services;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Build;
import android.util.Log;

import org.commcare.android.framework.WiFiDirectManagementFragment;
import org.javarosa.core.services.Logger;

/**
 * A BroadcastReceiver that notifies of important wifi p2p events.
 */
@SuppressLint("NewApi")
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = WiFiDirectBroadcastReceiver.class.getSimpleName();
    private final WifiP2pManager manager;
    private Channel channel;
    private final WiFiDirectManagementFragment activity;

    /**
     * @param manager WifiP2pManager system service
     * @param channel Wifi p2p channel
     * @param activity activity associated with the receiver
     */
    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, Channel channel,
            WiFiDirectManagementFragment activity) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "in on receive ");
        String action = intent.getAction();
        
        Logger.log(TAG, "onReceive of BroadCastReceiver with action: " + action);
        
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {

            // UI update to indicate wifi p2p status.
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                Log.d(TAG, "BR enabled");
                // Wifi Direct mode is enabled
                activity.setIsWifiP2pEnabled(true);
            } else {
                Log.d(TAG, "BR not enabled");
                activity.setIsWifiP2pEnabled(false);
                activity.resetData();

            }
            Log.d(TAG, "P2P state changed - " + state);
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

            // request available peers from the wifi p2p manager. This is an
            // asynchronous call and the calling activity is notified with a
            // callback on PeerListListener.onPeersAvailable()
            if (manager != null) {
                
                activity.onPeersChanged();
                
            }
            Log.d(TAG, "P2P peers changed2");
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

            if (manager == null) {
                return;
            }

            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            
            activity.onP2PConnectionChanged(networkInfo.isConnected());

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "in last else with device: " + intent.getParcelableExtra(
                    WifiP2pManager.EXTRA_WIFI_P2P_DEVICE).toString());
            
            
            activity.onThisDeviceChanged(intent);
            

        }
    }
}
