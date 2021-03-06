package org.commcare.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.HereFunctionHandler;
import org.javarosa.core.model.data.GeoPointData;

import java.util.Set;

/**
 * Allows evaluation contexts to support the here() XPath function,
 * which returns the current location.
 *
 * In addition, an EntitySelectActivity can register itself to be refreshed whenever
 * a new value of here() is obtained (whenever the location changes).
 *
 * No locations are requested if here() is never evaluated.
 *
 * @author Forest Tong, Phillip Mates
 */
public class AndroidHereFunctionHandler extends HereFunctionHandler implements LocationListener {

    // only trigger entity list refresh if distance has changed by this amount
    private static final int REFRESH_METER_DELTA = 30;

    private Location location;
    // last location used to render the distance properties of the entity list
    private Location lastDisplayedLocation;

    private boolean requestingLocationUpdates;
    private boolean locationGoodEnough;

    private final Context context = CommCareApplication.instance().getApplicationContext();
    private final LocationManager mLocationManager = (LocationManager)context.getSystemService(
            Context.LOCATION_SERVICE);

    public AndroidHereFunctionHandler() {
    }

    // The EntitySelectActivity must subscribe before this method is called if a fresh location is desired.
    @Override
    public Object eval(Object[] args, EvaluationContext ec) {
        alertOnEval();
        if (location == null) {
            return "";
        }
        return toGeoPointData(location).getDisplayText();
    }

    public void allowGpsUse() {
        if (!locationGoodEnough && !requestingLocationUpdates) {
            requestLocationUpdates();
        }
    }

    public void forbidGpsUse() {
        if (requestingLocationUpdates) {
            removeLocationUpdates();
        }
    }

    public void clearLocation() {
        this.locationGoodEnough = false;
    }

    @Override
    public void onLocationChanged(Location updatedLocation) {
        this.location = updatedLocation;
        Log.i("HereFunctionHandler", "location has been set to " + this.location);

        if (this.location.getAccuracy() <= GeoUtils.DEFAULT_ACCEPTABLE_ACCURACY) {
            locationGoodEnough = true;
            forbidGpsUse();
        }


        if (listener != null && shouldRefreshEntityList()) {
            lastDisplayedLocation = location;
            listener.onEvalLocationChanged();
        }
    }

    private boolean shouldRefreshEntityList() {
        boolean isDistanceDeltaSufficient = true;

        if (lastDisplayedLocation != null) {
            float distanceFromLastLocation = lastDisplayedLocation.distanceTo(location);
            isDistanceDeltaSufficient = distanceFromLastLocation > REFRESH_METER_DELTA;
        }

        return isDistanceDeltaSufficient;
    }

    public boolean locationProvidersFound() {
        return GeoUtils.evaluateProvidersWithPermissions(mLocationManager, context).size() > 0;
    }

    private void requestLocationUpdates() {
        Set<String> mProviders = GeoUtils.evaluateProvidersWithPermissions(mLocationManager, context);

        for (String provider : mProviders) {
            // Ignore the inspector warnings; the permissions are already checked in evaluateProvidersWithPermissions.
            if (location == null) {
                Location lastKnownLocation = mLocationManager.getLastKnownLocation(provider);
                if (lastKnownLocation != null) {
                    this.location = lastKnownLocation;
                    this.lastDisplayedLocation = lastKnownLocation;
                    Log.i("HereFunctionHandler", "last known location: " + this.location);
                }
            }

            // Looper is necessary because requestLocationUpdates is called inside an AsyncTask (EntityLoaderTask).
            // What values for minTime and minDistance?
            mLocationManager.requestLocationUpdates(provider, 0, 0, this, Looper.getMainLooper());
            requestingLocationUpdates = true;
        }
    }

    // Clients must call this when done using handler.
    private void removeLocationUpdates() {
        // stops the GPS. Note that this will turn off the GPS if the screen goes to sleep.
        if (ContextCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this.context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationManager.removeUpdates(this);
            requestingLocationUpdates = false;
        }
    }

    private static GeoPointData toGeoPointData(Location location) {
        return new GeoPointData(new double[]{
                location.getLatitude(),
                location.getLongitude(),
                location.getAltitude(),
                (double)location.getAccuracy()
        });
    }

    @Override
    public void onProviderDisabled(String provider) {
    }


    @Override
    public void onProviderEnabled(String provider) {
    }


    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

}
