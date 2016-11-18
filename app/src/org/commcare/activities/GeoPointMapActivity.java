package org.commcare.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.maps.GeoPoint;

import org.commcare.dalvik.R;
import org.commcare.utils.GeoUtils;
import org.commcare.views.widgets.GeoPointWidget;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Allows location to be chosen using a map instead of current gps coordinates
 */
public class GeoPointMapActivity extends Activity implements LocationListener, OnMapReadyCallback {

    private MapView mMapView;
    private GoogleMap map;
    private TextView mLocationStatus;

    private LocationManager mLocationManager;

    private GeoPoint mGeoPoint;
    private Location mLocation;

    private boolean inViewMode = false;

    private boolean mGPSOn = false;
    private boolean mNetworkOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.geopoint_layout);

        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        loadViewModeState();
        setupButtons();

        loadMapView(savedInstanceState);

        mLocationStatus = (TextView)findViewById(R.id.location_status);
        if (inViewMode) {
            findViewById(R.id.location_status).setVisibility(View.GONE);
        }

        loadProviders();
        if (!mGPSOn && !mNetworkOn) {
            Toast.makeText(getBaseContext(), getString(R.string.provider_disabled_error),
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadMapView(Bundle savedInstanceState) {
        // Gets the MapView from the XML layout and creates it
        mMapView = (MapView)findViewById(R.id.mapview);
        mMapView.onCreate(savedInstanceState);

        // Gets to GoogleMap from the MapView and does initialization stuff
        mMapView.getMapAsync(this);
    }

    private void setupButtons() {
        Button mCancelLocation = (Button)findViewById(R.id.cancel_location);
        mCancelLocation.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Button mAcceptLocation = (Button)findViewById(R.id.accept_location);
        if (!inViewMode) {
            mAcceptLocation.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    returnLocation();
                }
            });
        } else {
            mAcceptLocation.setVisibility(View.GONE);
            Button mShowLocation = ((Button)findViewById(R.id.show_location));
            mShowLocation.setVisibility(View.VISIBLE);
            mShowLocation.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    animateToPoint(mGeoPoint.getLatitudeE6(), mGeoPoint.getLongitudeE6());
                }
            });
        }
    }

    private void loadViewModeState() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            double[] location = intent.getDoubleArrayExtra(GeoPointWidget.LOCATION);
            mGeoPoint = new GeoPoint((int)(location[0] * 1E6), (int)(location[1] * 1E6));
            inViewMode = true;
        }
    }

    private void loadProviders() {
        List<String> providers = mLocationManager.getProviders(true);
        for (String provider : providers) {
            if (provider.equalsIgnoreCase(LocationManager.GPS_PROVIDER)) {
                mGPSOn = true;
            }
            if (provider.equalsIgnoreCase(LocationManager.NETWORK_PROVIDER)) {
                mNetworkOn = true;
            }
        }
    }

    private void returnLocation() {
        if (mLocation != null) {
            Intent i = new Intent();
            i.putExtra(FormEntryActivity.LOCATION_RESULT, GeoUtils.locationToString(mLocation));
            setResult(RESULT_OK, i);
        }
        finish();
    }

    private static String truncateFloat(float f) {
        return new DecimalFormat("#.##").format(f);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationManager.removeUpdates(this);
        }
    }

    @Override
    protected void onResume() {
        mMapView.onResume();

        super.onResume();

        if (mGPSOn && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }
        if (mNetworkOn && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        }
        // TODO PLM: warn user and ask for permissions if the user has disabled them
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!inViewMode) {
            mLocation = location;
            if (mLocation != null) {
                mLocationStatus.setText(getString(R.string.location_provider_accuracy,
                        mLocation.getProvider(), truncateFloat(mLocation.getAccuracy())));
                mGeoPoint =
                        new GeoPoint((int)(mLocation.getLatitude() * 1E6),
                                (int)(mLocation.getLongitude() * 1E6));

                animateToPoint(mGeoPoint.getLatitudeE6(), mGeoPoint.getLongitudeE6());

                if (mLocation.getAccuracy() <= GeoUtils.GOOD_ACCURACY) {
                    returnLocation();
                }
            }
        }
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

    @Override
    public void onMapReady(GoogleMap map) {
        this.map = map;
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.setMyLocationEnabled(true);

        MapsInitializer.initialize(this);

        if (inViewMode) {
            map.addMarker(new MarkerOptions().position(new LatLng(mGeoPoint.getLatitudeE6(), mGeoPoint.getLongitudeE6())).title("Marker"));
        } else {
            map.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
        }
    }

    private void animateToPoint(long lat, long lng) {
        // Updates the location and zoom of the MapView
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 10);
        map.animateCamera(cameraUpdate);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

}
