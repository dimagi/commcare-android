package org.commcare.android.nsd;


import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.util.Log;
import android.view.View;

import org.commcare.android.fragments.SelectInstallModeFragment;
import org.commcare.android.net.HttpRequestGenerator;
import org.javarosa.core.io.StreamsUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages relevant hooks for communicating with devices on the local network
 * like the CommCareHub app. Provides a place for registering listeners independent
 * from the app lifecycle and translating that into actionable hooks.
 *
 * Created by ctsims on 2/19/2016.
 */
public class NsdTools {

    private static final String TAG = "NsdTools";

    private static NsdManager.DiscoveryListener mDiscoveryListener;
    private static NsdManager mNsdManager;

    private static HashMap<String, MicroNode> mAttachedMicronodes = new HashMap<>();

    private static Set<NsdServiceListener> listeners = new HashSet<NsdServiceListener>();

    public enum NsdState {
        Init,
        Discovery,
        Idle
    }

    private static NsdState state = NsdState.Init;

    private static Object NsdToolsLock = new Object();

    public static final String SERVICE_TYPE = "_http._tcp.";
    public static final String SERVICE_NAME = "commcare_micronode";

    public static void registerForNsdServices(Context context, NsdServiceListener listener) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }

        addListener(listener);

        doDiscovery(context);
    }

    public static void deRegisterForNsdServices(NsdServiceListener listener) {
        removeListener(listener);
    }

    private static void addListener(final NsdServiceListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
        if(state != NsdState.Init) {
            if(mAttachedMicronodes.size() > 0) {

                //Receivers should expect to receive these messages from not-their-main thread
                //which is managed inherently during discovery, but registration
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        listener.onMicronodeDiscovery();
                    }
                }).start();
            }
        }
    }

    public static Collection<MicroNode> getAvailableMicronodes() {
        //Make a shallow copy in case this list is modified,
        return new HashSet<>(mAttachedMicronodes.values());
    }

    private static void removeListener(NsdServiceListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private static void doDiscovery(Context context) {
        synchronized (NsdToolsLock) {
            if(mNsdManager == null) {
                mNsdManager = (NsdManager)context.getSystemService(Context.NSD_SERVICE);
            }

            if(state == NsdState.Init || state == NsdState.Idle) {
                initializeDiscoveryListener();
                state = NsdState.Discovery;
                mNsdManager.discoverServices(SERVICE_TYPE,
                        NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static void initializeDiscoveryListener() {

        // Instantiate a new DiscoveryListener
        mDiscoveryListener = new NsdManager.DiscoveryListener() {

            //  Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found!  Do something with it.
                Log.d(TAG, "Service discovery success" + service);
                if (!service.getServiceType().equals(SERVICE_TYPE)) {
                    // Service type is the string containing the protocol and
                    // transport layer for this service.
                    Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
                } else if (service.getServiceName().equals(SERVICE_NAME)) {
                    Log.d(TAG, "Found CommCare Micronode");

                    mNsdManager.resolveService(service, getResolveListener());
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(TAG, "service lost" + service);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
                state = NsdState.Idle;
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
                state = NsdState.Idle;
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }
        };
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static NsdManager.ResolveListener getResolveListener() {
        return new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Called when the resolve fails.  Use the error code to debug.
                Log.e(TAG, "Resolve failed" + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.e(TAG, "Resolve Succeeded. " + serviceInfo);
                int port = serviceInfo.getPort();
                InetAddress host = serviceInfo.getHost();

                attachMicronode(host, port);
            }
        };
    }

    private static void updateAttachedServices() {
        for(NsdServiceListener listener : listeners) {
            listener.onMicronodeDiscovery();
        }
    }
    private static void attachMicronode(InetAddress host, int port) {
        String node = "http://" + host.getHostAddress() + ":" + port;


        if(mAttachedMicronodes.containsKey(node)) {
            //already know about this one, nothing to be done
            return;
        }
        mAttachedMicronodes.put(node, new MicroNode(node));

        updateAttachedServices();
    }


}
