package org.odk.collect.android.jr.extensions;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.javarosa.core.model.Action;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.Recalculate;
import org.javarosa.core.model.data.AnswerDataFactory;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapNullable;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.utilities.GeoUtils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * XForms Action extension to periodically poll a sensor and optionally save its value.
 * @author jschweers
 */
public class PollSensorAction extends Action implements LocationListener {
    private static String name = "pollsensor";
    private TreeReference target;
    
    private LocationManager mLocationManager;
    private FormDef mModel;
    private TreeReference mContextRef;

    private class ProvidersChangedHandler extends BroadcastReceiver {
        /*
         * (non-Javadoc)
         * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            Set<String> providers = GeoUtils.evaluateProviders(mLocationManager);
            requestLocationUpdates(providers);
        }
    }
    
    private class StopPollingTask extends TimerTask {
        /*
         * (non-Javadoc)
         * @see java.util.TimerTask#run()
         */
        @Override
        public void run() {
            mLocationManager.removeUpdates(PollSensorAction.this);
        }
    }
    
    public PollSensorAction() {
        super(name);
    }
    
    public PollSensorAction(TreeReference target) {
        super(name);
        this.target = target;
    }
    
    /**
     * Deal with a pollsensor action: start getting a GPS fix, and prepare to cancel after maximum amount of time.
     * @param model The FormDef that triggered the action
     * @param contextRef
     */
    public void processAction(FormDef model, TreeReference contextRef) {
        mModel = model;
        mContextRef = contextRef;
        
        // LocationManager needs to be dealt with in the main UI thread, so wrap GPS-checking logic in a Handler
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                // Start requesting GPS updates
                Context context = Collect.getStaticApplicationContext();
                mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                Set<String> providers = GeoUtils.evaluateProviders(mLocationManager);
                if (providers.isEmpty()) {
                    context.registerReceiver(
                        new ProvidersChangedHandler(), 
                        new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
                    );
                    
                    // This thread can't take action on the UI, so instead send a message that actual activities
                    // notice and then display a dialog asking user to enable location access
                    Intent noGPSIntent = new Intent(GeoUtils.ACTION_CHECK_GPS_ENABLED);
                    context.sendStickyBroadcast(noGPSIntent);
                }
                requestLocationUpdates(providers);
            }
        });
    }
    
    /**
     * Start polling for location, based on whatever providers are given, and set up a timeout after MAXIMUM_WAIT is exceeded.
     * @param providers Set of String objects that may contain LocationManager.GPS_PROVDER and/or LocationManager.NETWORK_PROVIDER
     */
    private void requestLocationUpdates(Set<String> providers) {
        if (providers.isEmpty()) {
            mLocationManager.removeUpdates(PollSensorAction.this);
            return;
        }
        
        for (String provider : providers) {
            mLocationManager.requestLocationUpdates(provider, 0, 0, PollSensorAction.this);
        }

        // Cancel polling after maximum time is exceeded
        Timer timeout = new Timer();
        timeout.schedule(new StopPollingTask(), GeoUtils.MAXIMUM_WAIT);
    }
    
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        super.readExternal(in, pf);
        target = (TreeReference)ExtUtil.read(in, new ExtWrapNullable(TreeReference.class), pf);
    }

    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
        ExtUtil.write(out, new ExtWrapNullable(target));
    }
    
    /**
     * (non-Javadoc)
     * @see android.location.LocationListener#onLocationChanged(android.location.Location)
     * 
     * If this action has a target node, update its value with the given location.
     * @param location
     */
    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            if (target != null) {
                String result = GeoUtils.locationToString(location);
                TreeReference qualifiedReference = mContextRef == null ? target : target.contextualize(mContextRef);
                EvaluationContext context = new EvaluationContext(mModel.getEvaluationContext(), qualifiedReference);
                AbstractTreeElement node = context.resolveReference(qualifiedReference);
                if(node == null) { throw new NullPointerException("Target of TreeReference " + qualifiedReference.toString(true) +" could not be resolved!"); }
                int dataType = node.getDataType();
                IAnswerData val = Recalculate.wrapData(result, dataType);
                mModel.setValue(val == null ? null: AnswerDataFactory.templateByDataType(dataType).cast(val.uncast()), qualifiedReference);
            }
            
            if (location.getAccuracy() <= GeoUtils.GOOD_ACCURACY) {
                mLocationManager.removeUpdates(this);
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see android.location.LocationListener#onProviderDisabled(java.lang.String)
     */
    @Override
    public void onProviderDisabled(String provider) { }

    /*
     * (non-Javadoc)
     * @see android.location.LocationListener#onProviderEnabled(java.lang.String)
     */
    @Override
    public void onProviderEnabled(String provider) { }

    /*
     * (non-Javadoc)
     * @see android.location.LocationListener#onStatusChanged(java.lang.String, int, android.os.Bundle)
     */
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }
}
