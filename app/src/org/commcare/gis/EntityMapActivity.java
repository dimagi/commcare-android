package org.commcare.gis;

import static org.commcare.views.EntityView.FORM_IMAGE;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.EntityDetailActivity;
import org.commcare.cases.entity.Entity;
import org.commcare.dalvik.R;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.EntityDatum;
import org.commcare.utils.MediaUtil;
import org.commcare.utils.SerializationUtil;
import org.commcare.views.UserfacingErrorHandling;
import org.javarosa.core.model.data.GeoPointData;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.xpath.XPathException;

import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import androidx.core.content.ContextCompat;

/**
 * @author Forest Tong (ftong@dimagi.com)
 */
public class EntityMapActivity extends CommCareActivity implements OnMapReadyCallback,
        GoogleMap.OnInfoWindowClickListener {
    private static final int MAP_PADDING = 50;  // Number of pixels to pad bounding region of markers
    private static final int DEFAULT_MARKER_SIZE = 120;

    private final Vector<Pair<Entity<TreeReference>, LatLng>> entityLocations = new Vector<>();
    private final HashMap<Marker, TreeReference> markerReferences = new HashMap<>();

    private static final String KEY_SELECTED_MAP_TYPE = "entity_map_selected_map_type";
    private static final int[] MAP_TYPES = new int[]{
            GoogleMap.MAP_TYPE_NORMAL,
            GoogleMap.MAP_TYPE_SATELLITE,
            GoogleMap.MAP_TYPE_TERRAIN,
            GoogleMap.MAP_TYPE_HYBRID,
    };

    private GoogleMap mMap;
    private Spinner mapTypeSelector;
    private CheckBox markerCheckbox;
    private int selectedMapTypeIndex = 0;

    // keeps track of detail field index that should be used to show a custom icon
    private int imageFieldIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            selectedMapTypeIndex = savedInstanceState.getInt(KEY_SELECTED_MAP_TYPE, 0);
        }

        setContentView(R.layout.entity_map_view);
        setupMapTypeSelector();

        SupportMapFragment mapFragment = (SupportMapFragment)getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        markerCheckbox = findViewById(R.id.marker_checkbox);
        markerCheckbox.setOnClickListener(view -> {
            updateMap();
        });

        try {
            addEntityData();
        } catch (XPathException xe) {
            new UserfacingErrorHandling<>().logErrorAndShowDialog(this, xe, true);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_MAP_TYPE, selectedMapTypeIndex);
    }


    /**
     * Gets entity locations, and adds corresponding pairs to the vector entityLocations.
     */
    private void addEntityData() {
        EntityDatum selectDatum = EntityMapUtils.getNeededEntityDatum();
        if (selectDatum != null) {
            Detail detail = CommCareApplication.instance().getCurrentSession()
                    .getDetail(selectDatum.getShortDetail());
            evalImageFieldIndex(detail);
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

    private void evalImageFieldIndex(Detail detail) {
        DetailField[] fields = detail.getFields();
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].getTemplateForm().equals(FORM_IMAGE)) {
                imageFieldIndex = i;
                break;
            }
        }
    }

    @Override
    public void onMapReady(final GoogleMap map) {
        mMap = map;
        applySelectedMapType();

        updateMap();

        mMap.setOnInfoWindowClickListener(this);
        setMapLocationEnabled(true);
    }

    private void updateMap() {
        mMap.clear();
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        if (entityLocations.size() > 0) {
            boolean showCustomMapMarker = HiddenPreferences.shouldShowCustomMapMarker();

            // Add markers to map and find bounding region
            for (Pair<Entity<TreeReference>, LatLng> entityLocation : entityLocations) {
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(entityLocation.second)
                        .title(entityLocation.first.getFieldString(0))
                        .snippet(entityLocation.first.getFieldString(1));
                if (showCustomMapMarker) {
                    markerOptions.icon(getEntityIcon(entityLocation.first));
                }
                if (markerCheckbox.isChecked()) {
                    Marker marker = mMap.addMarker(markerOptions);
                    markerReferences.put(marker, entityLocation.first.getElement());
                }
                builder.include(entityLocation.second);

                LatLng center = entityLocation.second;
                Polygon poly = mMap.addPolygon(new PolygonOptions()
                        .add(
                                new LatLng(center.latitude - 0.001, center.longitude - 0.001),
                                new LatLng(center.latitude - 0.001, center.longitude + 0.001),
                                new LatLng(center.latitude + 0.001, center.longitude + 0.001),
                                new LatLng(center.latitude + 0.001, center.longitude - 0.001)
                        )
                        .strokeColor(Color.RED)
                        .fillColor(Color.argb(50, 255, 255, 0))
                        .strokeWidth(5));
            }
        }

        //Add some example DUs for testing
        //Create some sample polygons
        float boxRadius = 0.0005f;
        LatLng first = new LatLng(-13.643511, 33.939722);
        List<Integer> xOffsets = List.of(0, -1, 1, 0, -1, 1, -1, 2, 1, -2, -2);
        List<Integer> yOffsets = List.of(0, 0, 0, 1, 1, 1, -1, 0, 2, 0, -1);
        List<Integer> colors = List.of(
                Color.argb(25, 255, 255, 0),
                Color.argb(25, 0, 255, 0),
                Color.argb(25, 255, 0, 0)
        );

        mMap.setOnPolygonClickListener(
                polygon -> {
                });

        for (int i = 0; i < xOffsets.size(); i++) {
            LatLng center = new LatLng(
                    first.latitude + 2 * boxRadius * yOffsets.get(i),
                    first.longitude + 2 * boxRadius * xOffsets.get(i)
            );
            Polygon poly = mMap.addPolygon(new PolygonOptions()
                    .add(
                            new LatLng(center.latitude - boxRadius, center.longitude - boxRadius),
                            new LatLng(center.latitude - boxRadius, center.longitude + boxRadius),
                            new LatLng(center.latitude + boxRadius, center.longitude + boxRadius),
                            new LatLng(center.latitude + boxRadius, center.longitude - boxRadius)
                    )
                    .clickable(true)
                    .strokeColor(Color.GRAY)
                    .fillColor(colors.get(i % colors.size()))
                    .strokeWidth(5));

            builder.include(center);

            if(markerCheckbox.isChecked()) {
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(center)
                        .title("Center " + (i + 1))
                        .snippet("Test");
                mMap.addMarker(markerOptions);
            }
        }

        final LatLngBounds bounds = builder.build();

        // Move camera to be include all markers
        mMap.setOnMapLoadedCallback(
                () -> mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, MAP_PADDING)));
    }

    private void setupMapTypeSelector() {
        mapTypeSelector = findViewById(R.id.map_type_selector);
        if (mapTypeSelector == null) {
            return;
        }
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.map_type_labels,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mapTypeSelector.setAdapter(adapter);
        mapTypeSelector.setSelection(selectedMapTypeIndex, false);
        mapTypeSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (selectedMapTypeIndex != position) {
                    selectedMapTypeIndex = position;
                    applySelectedMapType();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });
    }

    private void applySelectedMapType() {
        if (mMap != null && selectedMapTypeIndex >= 0 && selectedMapTypeIndex < MAP_TYPES.length) {
            mMap.setMapType(MAP_TYPES[selectedMapTypeIndex]);
        }
    }

    private BitmapDescriptor getEntityIcon(Entity<TreeReference> entity) {
        if (imageFieldIndex != -1) {
            String jrUri = String.valueOf(entity.getData()[imageFieldIndex]);
            Bitmap bitmap = MediaUtil.inflateDisplayImage(this, jrUri, DEFAULT_MARKER_SIZE, DEFAULT_MARKER_SIZE,
                    true);
            if (bitmap != null) {
                return BitmapDescriptorFactory.fromBitmap(bitmap);
            }
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        setMapLocationEnabled(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        setMapLocationEnabled(false);
    }

    private void setMapLocationEnabled(boolean enabled) {
        if (mMap != null) {
            boolean fineLocationPermission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean coarseLocationPermission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (fineLocationPermission || coarseLocationPermission) {
                mMap.setMyLocationEnabled(enabled);
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

    @Override
    public boolean shouldListenToSyncComplete() {
        return true;
    }
}
