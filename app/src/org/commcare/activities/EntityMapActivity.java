package org.commcare.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
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
import org.commcare.dalvik.R;
import org.commcare.models.Entity;
import org.commcare.models.NodeEntityFactory;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.EntityDatum;
import org.commcare.suite.model.SessionDatum;
import org.commcare.utils.AndroidInstanceInitializer;
import org.commcare.utils.SerializationUtil;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.data.GeoPointData;
import org.javarosa.core.model.data.UncastData;
import org.javarosa.core.model.instance.TreeReference;

import java.util.HashMap;
import java.util.Vector;

/**
 * @author Forest Tong (ftong@dimagi.com)
 */
public class EntityMapActivity extends CommCareActivity implements OnMapReadyCallback,
        GoogleMap.OnInfoWindowClickListener {
    private static final String TAG = EntityMapActivity.class.getSimpleName();
    private static final int MAP_PADDING = 50;  // Number of pixels to pad bounding region of markers

    private final CommCareSession session = CommCareApplication._().getCurrentSession();
    private EntityDatum selectDatum;

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
        SessionDatum datum = session.getNeededDatum();
        if (datum instanceof EntityDatum) {
            selectDatum = (EntityDatum)datum;

            Detail detail = session.getDetail(selectDatum.getShortDetail());
            addEntityLocations(detail);
            Log.d(TAG, "Loaded. " + entityLocations.size() + " addresses discovered, " + (
                    detail.getHeaderForms().length - entityLocations.size()) + " could not be located");
        }
    }

    /**
     * Gets entity locations, and adds corresponding pairs to the vector entityLocations.
     */
    private void addEntityLocations(Detail detail) {
        for (Entity<TreeReference> entity : getEntities(detail)) {
            for (int i = 0; i < detail.getHeaderForms().length; ++i) {
                if ("address".equals(detail.getTemplateForms()[i])) {
                    String address = entity.getFieldString(i).trim();
                    if (!"".equals(address)) {
                        LatLng location = getLatLngFromAddress(address);
                        if (location != null) {
                            entityLocations.add(new Pair<>(entity, location));
                        }
                    }
                }
            }
        }
    }

    private Vector<Entity<TreeReference>> getEntities(Detail detail) {
        EvaluationContext evaluationContext = session.getEvaluationContext(
                new AndroidInstanceInitializer(session));
        evaluationContext.addFunctionHandler(EntitySelectActivity.getHereFunctionHandler());

        NodeEntityFactory factory = new NodeEntityFactory(detail, evaluationContext);
        Vector<TreeReference> references = evaluationContext.expandReference(
                selectDatum.getNodeset());

        Vector<Entity<TreeReference>> entities = new Vector<>();
        for (TreeReference ref : references) {
            entities.add(factory.getEntity(ref));
        }
        return entities;
    }

    private LatLng getLatLngFromAddress(@NonNull String address) {
        LatLng location = null;
        try {
            GeoPointData data = new GeoPointData().cast(new UncastData(address));
            if (data != null) {
                location = new LatLng(data.getLatitude(), data.getLongitude());
            }
        } catch (IllegalArgumentException ignored) {
        }
        return location;
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
            mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                @Override
                public void onMapLoaded() {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, MAP_PADDING));
                }
            });
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
