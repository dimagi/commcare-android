package org.commcare.dalvik.activities;

import android.annotation.TargetApi;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.util.AndroidInstanceInitializer;
import org.commcare.android.util.SerializationUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.SessionDatum;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.data.GeoPointData;
import org.javarosa.core.model.data.UncastData;
import org.javarosa.core.model.instance.TreeReference;

import java.util.HashMap;
import java.util.Vector;

/**
 * @author ctsims
 */
@TargetApi(11)
public class EntityMapActivity extends CommCareActivity implements OnMapReadyCallback,
        GoogleMap.OnInfoWindowClickListener {
    private static final String TAG = EntityMapActivity.class.getSimpleName();
    private static final int MAP_PADDING = 50;  // Number of pixels to pad bounding region of markers

    private EvaluationContext entityEvaluationContext;
    private CommCareSession session;
    Vector<Entity<TreeReference>> entities;

    Vector<Pair<Entity<TreeReference>, LatLng>> entityLocations;
    HashMap<Marker, TreeReference> markerReferences = new HashMap<>();

    GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.entity_map_view);

        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        session = CommCareApplication._().getCurrentSession();
        SessionDatum selectDatum = session.getNeededDatum();
        Detail detail = session.getDetail(selectDatum.getShortDetail());
        NodeEntityFactory factory = new NodeEntityFactory(detail, this.getEvaluationContext());

        Vector<TreeReference> references = getEvaluationContext().expandReference(
                selectDatum.getNodeset());
        entities = new Vector<>();
        for(TreeReference ref : references) {
            entities.add(factory.getEntity(ref));
        }

        int bogusAddresses = 0;
        entityLocations = new Vector<>();
        for(Entity<TreeReference> entity : entities) {
            for(int i = 0 ; i < detail.getHeaderForms().length; ++i ){
                if("address".equals(detail.getTemplateForms()[i])) {
                    String address = entity.getFieldString(i).trim();
                    if(address != null && !"".equals(address)) {
                        LatLng location = getLatLngFromAddress(address);
                        if (location == null) {
                            bogusAddresses++;
                        } else {
                            entityLocations.add(new Pair<>(entity, location));
                        }
                    }
                }
            }
        }
        Log.d(TAG, "Loaded. " + entityLocations.size() +" addresses discovered, " + bogusAddresses + " could not be located");
    }

    private LatLng getLatLngFromAddress(String address) {
        LatLng location = null;
        try {
            GeoPointData data = new GeoPointData().cast(new UncastData(address));
            if(data != null) {
                location = new LatLng(data.getLatitude(), data.getLongitude());
            }
        } catch(Exception ex) {
        }

        // Geocaching code removed

        return location;
    }

    @Override
    public void onMapReady(final GoogleMap map) {
        mMap = map;

        // Find bounding region of markers
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (Pair<Entity<TreeReference>, LatLng> entityLocation: entityLocations) {
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(entityLocation.second)
                    .title(entityLocation.first.getFieldString(0))
                    .snippet(entityLocation.first.getFieldString(1)));
            markerReferences.put(marker, entityLocation.first.getElement());
            builder.include(entityLocation.second);
        }
        final LatLngBounds bounds = builder.build();

        // Move camera to be include all markers
        // TODO: does this work for 0 or 1 marker?
        map.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, MAP_PADDING));
            }
        });

        map.setOnInfoWindowClickListener(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.setMyLocationEnabled(true);
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

        if (mMap != null && (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            mMap.setMyLocationEnabled(false);
        }
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        Intent i = new Intent(EntityMapActivity.this.getIntent());
        TreeReference ref = markerReferences.get(marker);
        SerializationUtil.serializeToIntent(i, EntityDetailActivity.CONTEXT_REFERENCE, ref);

        setResult(RESULT_OK, i);
        EntityMapActivity.this.finish();
    }

    private EvaluationContext getEvaluationContext() {
        if(entityEvaluationContext == null) {
            entityEvaluationContext = session.getEvaluationContext(getInstanceInit());
        }
        return entityEvaluationContext;
    }

    private AndroidInstanceInitializer getInstanceInit() {
        return new AndroidInstanceInitializer(session);
    }
}
