package org.commcare.dalvik.geo;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

import org.javarosa.core.model.data.GeoPointData;

/**
 * Created by ftong on 1/13/16.
 */

public class HereLocationListener implements LocationListener {
    private HereFunctionHandler hereFunctionHandler;


    public HereLocationListener(HereFunctionHandler hereFunctionHandler) {
        this.hereFunctionHandler = hereFunctionHandler;
    }


    @Override
    public void onLocationChanged(Location location) {
        // Do we need to check the accuracy of the location?
        hereFunctionHandler.setLocation(toGeoPointData(location));
    }


    @Override
    public void onProviderDisabled(String provider) {}


    @Override
    public void onProviderEnabled(String provider) {}


    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}


    public static GeoPointData toGeoPointData(Location location) {
        return new GeoPointData(new double[]{
                location.getLatitude(),
                location.getLongitude(),
                location.getAltitude(),
                (double) location.getAccuracy()
        });
    }
}
