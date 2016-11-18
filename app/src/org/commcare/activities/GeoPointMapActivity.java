package org.commcare.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
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

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;

import org.commcare.dalvik.R;
import org.commcare.utils.GeoUtils;
import org.commcare.views.widgets.GeoPointWidget;

import java.text.DecimalFormat;
import java.util.List;

public class GeoPointMapActivity extends Activity implements LocationListener {

    private MapView mMapView;
    private TextView mLocationStatus;

    private MapController mMapController;
    private LocationManager mLocationManager;
    private Overlay mLocationOverlay;

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

        loadMapView();

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

    private void loadMapView() {
        mMapView = (MapView)findViewById(R.id.mapview);
        mMapController = mMapView.getController();

        mMapView.setBuiltInZoomControls(true);
        mMapView.setSatellite(false);
        mMapController.setZoom(16);
        mLocationOverlay = new MyLocationOverlay(this, mMapView);
        mMapView.getOverlays().add(mLocationOverlay);
        if (inViewMode) {
            Overlay mGeoPointOverlay = new Marker(mGeoPoint);
            mMapView.getOverlays().add(mGeoPointOverlay);
        }
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
                    mMapController.animateTo(mGeoPoint);
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
        ((MyLocationOverlay)mLocationOverlay).disableMyLocation();

    }

    @Override
    protected void onResume() {
        super.onResume();

        ((MyLocationOverlay)mLocationOverlay).enableMyLocation();
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

                mMapController.animateTo(mGeoPoint);

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

    class Marker extends Overlay {
        GeoPoint gp = null;

        Marker(GeoPoint gp) {
            this.gp = gp;
        }

        @Override
        public void draw(Canvas canvas, MapView mapView, boolean shadow) {
            super.draw(canvas, mapView, shadow);
            Point screenPoint = new Point();
            mMapView.getProjection().toPixels(gp, screenPoint);
            canvas.drawBitmap(BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_maps_indicator_current_position), screenPoint.x, screenPoint.y - 8,
                    null); // -8 as image is 16px high
        }
    }

}
