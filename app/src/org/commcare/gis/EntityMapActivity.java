package org.commcare.gis;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Pair;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.EntityDetailActivity;
import org.commcare.cases.entity.Entity;
import org.commcare.dalvik.R;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.EntityDatum;
import org.commcare.utils.SerializationUtil;
import org.commcare.views.UserfacingErrorHandling;
import org.javarosa.core.model.data.GeoPointData;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.xpath.XPathException;

import java.util.HashMap;
import java.util.Vector;

import androidx.core.content.ContextCompat;

/**
 * @author Forest Tong (ftong@dimagi.com)
 */
public class EntityMapActivity extends CommCareActivity implements OnMapReadyCallback,
        GoogleMap.OnInfoWindowClickListener {
    private static final int MAP_PADDING = 50;  // Number of pixels to pad bounding region of markers

    private final Vector<Pair<Entity<TreeReference>, LatLng>> entityLocations = new Vector<>();
    private final HashMap<Marker, TreeReference> markerReferences = new HashMap<>();

    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.entity_map_view);

        SupportMapFragment mapFragment = (SupportMapFragment)getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        try {
            addEntityData();
        } catch (XPathException xe) {
            UserfacingErrorHandling.logErrorAndShowDialog(this, xe, true);
        }
    }


    /**
     * Gets entity locations, and adds corresponding pairs to the vector entityLocations.
     */
    private void addEntityData() {

        EntityDatum selectDatum = EntityMapUtils.getNeededEntityDatum();
        if (selectDatum != null) {
            Detail detail = CommCareApplication.instance().getCurrentSession()
                    .getDetail(selectDatum.getShortDetail());
            for (Entity<TreeReference> entity : EntityMapUtils.getEntities(detail, selectDatum.getNodeset())) {
                for (int i = 0; i < detail.getHeaderForms().length; ++i) {
                    GeoPointData data = EntityMapUtils.getEntityLocation(entity, detail, i);
                    if (data != null) {
                        entityLocations.add(
                                new Pair<>(entity, new LatLng(data.getLatitude(), data.getLongitude())));
                    }
                }
            }
        }
    }

    @Override
    public void onMapReady(final GoogleMap map) {
        mMap = map;

        if (entityLocations.size() > 0) {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            // Add markers to map and find bounding region
            for (Pair<Entity<TreeReference>, LatLng> entityLocation : entityLocations) {
                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(entityLocation.second)
                        .title(entityLocation.first.getFieldString(0))
                        .snippet(entityLocation.first.getFieldString(1)));
                markerReferences.put(marker, entityLocation.first.getElement());
                builder.include(entityLocation.second);
            }
            final LatLngBounds bounds = builder.build();

            // Move camera to be include all markers
            mMap.setOnMapLoadedCallback(() -> mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, MAP_PADDING)));
        }

        mMap.setOnInfoWindowClickListener(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mMap != null && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mMap != null) {
            mMap.setOnMapLoadedCallback(null);  // Avoid memory leak in callback
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(false);
            }
        }
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        Intent i = new Intent(getIntent());
        TreeReference ref = markerReferences.get(marker);
        SerializationUtil.serializeToIntent(i, EntityDetailActivity.CONTEXT_REFERENCE, ref);

        setResult(RESULT_OK, i);
        finish();
    }
}
