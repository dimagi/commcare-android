package org.commcare.dalvik.geo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import org.commcare.dalvik.activities.EntitySelectActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.IFunctionHandler;
import org.javarosa.core.model.data.GeoPointData;
import org.odk.collect.android.utilities.GeoUtils;

import java.util.Set;
import java.util.Vector;

/**
 * Allows evaluation contexts to support the here() XPath function,
 * which returns the current location.
 *
 * In addition, an EntitySelectActivity can register itself to be refreshed whenever
 * a new value of here() is obtained (whenever the location changes).
 *
 * No locations are requested if here() is never evaluated.
 *
 * @author Forest Tong
 */
public class HereFunctionHandler implements IFunctionHandler, LocationListener {
    public static final String HERE_NAME = "here";
    private GeoPointData location;

    private LocationManager mLocationManager;

    // This flag keeps track of whether the location manager needs to be set up and subscribed to.
    private boolean locationUpdatesRequested = false;
    // This flag keeps track of whether locationUpdatesRequested has ever been set to true
    public boolean locationUpdatesHaveBeenRequested = false;

    private final Context context = CommCareApplication._().getApplicationContext();
    // If there are more general uses for HereFunctionHandler, the type of this field can be
    // generalized to a listener interface.
    private EntitySelectActivity entitySelectActivity;

    public HereFunctionHandler() {}

    public void registerEntitySelectActivity(EntitySelectActivity entitySelectActivity) {
        this.entitySelectActivity = entitySelectActivity;
    }

    public void unregisterEntitySelectActivity() {
        this.entitySelectActivity = null;
    }

    public Object eval(Object[] args, EvaluationContext ec) {
        if (!locationUpdatesRequested) {
            requestLocationUpdates();
        }
        if (location == null) {
            return "";
        }
        return location.getDisplayText();
    }

    public void requestLocationUpdates() {
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Set<String> mProviders = GeoUtils.evaluateProviders(mLocationManager);

        for (String provider : mProviders) {
            if ((provider.equals(LocationManager.GPS_PROVIDER) && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) ||
                    (provider.equals(LocationManager.NETWORK_PROVIDER) && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                // This is non-null if the calling activity pauses.
                if (location == null) {
                    Location lastKnownLocation = mLocationManager.getLastKnownLocation(provider);
                    if (lastKnownLocation != null) {
                        this.location = toGeoPointData(lastKnownLocation);
                        Log.i("HereFunctionHandler", "last known location: " + this.location.getDisplayText());
                    }
                }

                // Looper is necessary because requestLocationUpdates is called inside an AsyncTask (EntityLoaderTask).
                // What values for minTime and minDistance?
                mLocationManager.requestLocationUpdates(provider, 0, 0, this, Looper.getMainLooper());
            }
        }

        locationUpdatesRequested = true;
        locationUpdatesHaveBeenRequested = true;
    }

    // Clients must call this when done using handler.
    public void stopLocationUpdates() {
        if (locationUpdatesRequested) {
            // stops the GPS. Note that this will turn off the GPS if the screen goes to sleep.
            if (ContextCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mLocationManager.removeUpdates(this);
            }

            locationUpdatesRequested = false;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        // Do we need to check the accuracy of the location?
        this.location = toGeoPointData(location);
        Log.i("HereFunctionHandler", "location has been set to " + this.location.getDisplayText());
        // Trigger refreshing of entity list.
        if (entitySelectActivity != null) {
            entitySelectActivity.loadEntities();
        }
    }

    public static GeoPointData toGeoPointData(Location location) {
        return new GeoPointData(new double[]{
                location.getLatitude(),
                location.getLongitude(),
                location.getAltitude(),
                (double) location.getAccuracy()
        });
    }

    @Override
    public void onProviderDisabled(String provider) {}


    @Override
    public void onProviderEnabled(String provider) {}


    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public String getName() {
        return HERE_NAME;
    }

    @Override
    public Vector getPrototypes() {
        Vector p = new Vector();
        p.addElement(new Class[0]);
        return p;
    }

    @Override
    public boolean rawArgs() {
        return false;
    }
}
