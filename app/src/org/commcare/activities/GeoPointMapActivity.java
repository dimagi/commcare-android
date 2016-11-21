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
import android.util.DisplayMetrics;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.commcare.dalvik.R;
import org.commcare.utils.GeoUtils;
import org.commcare.views.widgets.GeoPointWidget;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Allows location to be chosen using a map instead of current gps coordinates
 */
public class GeoPointMapActivity extends Activity
        implements LocationListener, OnMapReadyCallback,
        GoogleMap.OnMyLocationButtonClickListener {

    private MapView mapView;
    private GoogleMap map;
    private Marker marker;
    private TextView locationText;

    private LocationManager locationManager;
    private Location location = new Location("XForm");

    private boolean inViewMode = false;
    // don't reset marker to current GPS location if we manually selected a location
    private boolean isManualSelectedLocation = false;

    private boolean isGPSOn = false;
    private boolean isNetworkOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.geopoint_layout);

        loadViewModeState();
        setupUI();
        loadMapView(savedInstanceState);
        loadProviders();
    }

    private void loadViewModeState() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            double[] location = intent.getDoubleArrayExtra(GeoPointWidget.LOCATION);
            this.location.setLatitude(location[0]);
            this.location.setLongitude(location[1]);
            inViewMode = true;
        }
    }

    private void setupUI() {
        Button cancelButton = (Button)findViewById(R.id.cancel_location);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Button acceptButton = (Button)findViewById(R.id.accept_location);
        acceptButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                returnLocation();
            }
        });

        Button showLocationButton = ((Button)findViewById(R.id.show_location));
        showLocationButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                animateToPoint(location.getLatitude(), location.getLongitude(), location.getAccuracy());
            }
        });

        locationText = (TextView)findViewById(R.id.location_status);

        if (inViewMode) {
            acceptButton.setVisibility(View.GONE);
            showLocationButton.setVisibility(View.VISIBLE);
            findViewById(R.id.location_status).setVisibility(View.GONE);
        }
    }

    private void returnLocation() {
        if (location != null) {
            Intent i = new Intent();
            i.putExtra(FormEntryActivity.LOCATION_RESULT, GeoUtils.locationToString(location));
            setResult(RESULT_OK, i);
        }
        finish();
    }

    private void loadMapView(Bundle savedInstanceState) {
        mapView = (MapView)findViewById(R.id.mapview);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(this);
    }

    private void loadProviders() {
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        List<String> providers = locationManager.getProviders(true);
        for (String provider : providers) {
            if (provider.equalsIgnoreCase(LocationManager.GPS_PROVIDER)) {
                isGPSOn = true;
            }
            if (provider.equalsIgnoreCase(LocationManager.NETWORK_PROVIDER)) {
                isNetworkOn = true;
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!inViewMode && !isManualSelectedLocation) {
            this.location = location;
            if (this.location != null) {

                drawMarker();
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.map = map;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.getUiSettings().setMyLocationButtonEnabled(true);
            map.setMyLocationEnabled(true);
            map.setOnMyLocationButtonClickListener(this);
        }

        MapsInitializer.initialize(this);

        if (inViewMode) {
            drawMarker();
        }
        setupMapListeners();
    }

    private void setupMapListeners() {
        if (!inViewMode) {
            map.setOnMapClickListener(
                    new GoogleMap.OnMapClickListener() {
                        @Override
                        public void onMapClick(LatLng point) {
                            isManualSelectedLocation = true;
                            location.setLongitude(point.longitude);
                            location.setLatitude(point.latitude);
                            location.setAccuracy(10);
                            drawMarker();
                        }
                    }
            );
        }
    }

    private void drawMarker() {
        locationText.setText(getString(R.string.location_provider_accuracy,
                truncateFloat(location.getAccuracy())));

        if (marker != null) {
            marker.remove();
        }

        marker = map.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(), location.getLongitude())).title("Selected location"));
        animateToPoint(location.getLatitude(), location.getLongitude(), location.getAccuracy());
    }

    private static String truncateFloat(float f) {
        return new DecimalFormat("#.##").format(f);
    }


    private void animateToPoint(double lat, double lng, float accuracy) {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenSize = Math.min(metrics.widthPixels, metrics.heightPixels);
        int zoomLevel = calculateZoomLevel(screenSize, accuracy);

        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), zoomLevel);
        map.animateCamera(cameraUpdate);
    }

    // via http://stackoverflow.com/a/25143326
    private static int calculateZoomLevel(int screenWidth, float accuracy) {
        // don't zoom in too much
        final int MAX_ZOOM_LEVEL = 16;

        double equatorLength = 40075004; // in meters
        double metersPerPixel = equatorLength / 256;
        int zoomLevel = 1;
        while ((metersPerPixel * (double)screenWidth) > accuracy && zoomLevel < MAX_ZOOM_LEVEL) {
            metersPerPixel /= 2;
            zoomLevel++;
        }

        return zoomLevel;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(this);
        }
    }

    @Override
    protected void onResume() {
        mapView.onResume();

        super.onResume();

        if (isGPSOn && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }
        if (isNetworkOn && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        }
        // TODO PLM: warn user and ask for permissions if the user has disabled them
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        mapView.onLowMemory();
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
    public boolean onMyLocationButtonClick() {
        isManualSelectedLocation = false;
        return false;
    }

}
