package org.commcare.android.framework;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareWiFiDirectActivity;
import org.javarosa.core.services.Logger;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
@SuppressLint("NewApi")
public class WiFiDirectManagementFragment extends Fragment
        implements ConnectionInfoListener, ActionListener, ChannelListener {
    private static final String TAG = WiFiDirectManagementFragment.class.getSimpleName();
    private static CommCareWiFiDirectActivity mActivity;

    private TextView mStatusText;

    private boolean isWifiP2pEnabled;
    private boolean isHost;
    private boolean isConnected;

    WifiP2pInfo info;

    public WifiP2pManager mManager;
    public Channel mChannel;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mActivity = (CommCareWiFiDirectActivity) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement fileServerListener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View mContentView = inflater.inflate(R.layout.wifi_manager, null);

        mStatusText = (TextView) mContentView.findViewById(R.id.wifi_manager_status_text);

        return mContentView;
    }

    public void setIsWifiP2pEnabled(boolean enabled) {
        setWifiP2pEnabled(enabled);
    }

    public void resetData() {
        ((WifiDirectManagerListener) this.getActivity()).resetData();
    }

    public void onPeersChanged() {
        Logger.log(TAG, "Wi-fi direct peers changed");
        mActivity.updatePeers();
    }

    public void onP2PConnectionChanged(boolean isConnected) {
        if (isConnected) {

            Logger.log(TAG, "Wifi direct P2P connection changed");

            // we are connected with the other device, request connection
            // info to find group owner IP
            Log.d(TAG, "requesting connection info activity");
            mManager.requestConnectionInfo(mChannel, this);
        } else {
            setDeviceConnected(false);
            // activity.resetData();
        }
    }

    public void onThisDeviceChanged(Intent intent) {

        setStatusText("This device's connection status changed...");

        WifiP2pDevice mDevice = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);

        int status = mDevice.status;

        if (status == WifiP2pDevice.AVAILABLE && isHost) {
            Logger.log(TAG, "Relaunching Wi-fi direct group as host");
            setStatusText("Host relaunching group...");
            mManager.createGroup(mChannel, this);
        }

        mActivity.updateDeviceStatus(mDevice);
    }

    public String getHostAddress() {
        return info.groupOwnerAddress.getHostAddress();
    }

    public void resetConnectionGroup() {
        Logger.log(TAG, "restting connection group");
        mManager.removeGroup(mChannel, this);
    }

    public void setIsHost(boolean isHost) {
        Logger.log(TAG, "setting is host: " + isHost);
        this.isHost = isHost;
        refreshStatusText();
    }

    public interface WifiDirectManagerListener {
        void resetData();

        void updatePeers();

        void updateDeviceStatus(WifiP2pDevice mDevice);
    }

    public void startReceiver(WifiP2pManager mManager, Channel mChannel) {
        Logger.log(TAG, "Starting receiver");
        this.mChannel = mChannel;
        this.mManager = mManager;
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {

        this.info = info;

        refreshStatusText();

        boolean isOwner = info.isGroupOwner;

        setDeviceConnected(info.groupFormed);

    }

    public void refreshStatusText() {

        if (info == null) {
            return;
        }

        if (info.groupFormed) {
            if (info.isGroupOwner) {
                if (isHost) {
                    setStatusText("Successfully created and hosted group");
                } else {
                    setStatusText("Group owner but not host");
                }
            } else {
                if (isHost) {
                    setStatusText("Host but not group owner");
                } else {
                    setStatusText("Successfully joined group");
                }
            }
        } else {
            if (isHost) {
                setStatusText("You are the host but didn't form a group. Restart the Wi-fi functionality.");
            } else {
                setStatusText("YWaiting to join new group...");
            }
        }
    }

    public void setDeviceConnected(boolean connected) {
        isConnected = connected;
    }

    public boolean getDeviceConnected() {
        return isConnected;
    }

    public void setStatusText(String text) {
        Log.d(TAG, text);
        mStatusText.setText(text);
    }

    public boolean isWifiP2pEnabled() {
        return isWifiP2pEnabled;
    }

    public void setWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    public void onFailure(int reason) {
        //setStatusText("Failed to create group for rason: " + reason);

    }

    @Override
    public void onSuccess() {
        //setStatusText("Successfully created group");

    }

    @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (mManager != null) {
            Toast.makeText(mActivity, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            //resetData();
            mManager.initialize(mActivity, mActivity.getMainLooper(), this);
        } else {
            Toast.makeText(mActivity,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
