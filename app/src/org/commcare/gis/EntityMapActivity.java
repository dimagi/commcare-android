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
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.text.TextWatcher;
import android.text.Editable;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
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

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * @author Forest Tong (ftong@dimagi.com)
 */
public class EntityMapActivity extends CommCareActivity implements OnMapReadyCallback,
        GoogleMap.OnInfoWindowClickListener, GoogleMap.OnCameraIdleListener {
    private static final int MAP_PADDING = 50;  // Number of pixels to pad bounding region of markers
    private static final int DEFAULT_MARKER_SIZE = 120;
    private static final float ZOOM_THRESHOLD = 2.0f;  //15 Zoom level threshold for simplified view

    private final Vector<Pair<Entity<TreeReference>, LatLng>> entityLocations = new Vector<>();
    private final HashMap<Marker, TreeReference> markerReferences = new HashMap<>();
    private final HashMap<Polygon, Pair<String, String>> polygonInfo = new HashMap<>(); // Polygon -> (title, snippet)

    private static final String KEY_SELECTED_MAP_TYPE = "entity_map_selected_map_type";
    private static final int[] MAP_TYPES = new int[]{
            GoogleMap.MAP_TYPE_NORMAL,
            GoogleMap.MAP_TYPE_SATELLITE,
            GoogleMap.MAP_TYPE_TERRAIN,
            GoogleMap.MAP_TYPE_HYBRID,
            GoogleMap.MAP_TYPE_NONE,
    };

    private GoogleMap mMap;
    private Spinner mapTypeSelector;
    private CheckBox markerCheckbox;
    private EditText repeatsInput;
    private int selectedMapTypeIndex = 0;
    private int repeatCount = 1; // Default repeat count

    // keeps track of detail field index that should be used to show a custom icon
    private int imageFieldIndex = -1;
    
    // tracks whether we're currently in simplified view mode
    private boolean isSimplifiedView = false;
    
    // temporary marker for showing polygon info window
    private Marker polygonInfoMarker = null;

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
            updateMap(true);
        });

        repeatsInput = findViewById(R.id.repeats_input);
        repeatsInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No-op
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // No-op
            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    String text = s.toString().trim();
                    if (!text.isEmpty()) {
                        int newValue = Integer.parseInt(text);
                        if (newValue > 0 && newValue != repeatCount) {
                            repeatCount = newValue;
                            // Only update map if mMap is ready
                            if (mMap != null) {
                                updateMap(false);
                            }
                        } else if (newValue <= 0) {
                            // Reset to 1 if invalid value
                            repeatCount = 1;
                            repeatsInput.setText("1");
                        }
                    }
                } catch (NumberFormatException e) {
                    // Invalid input, reset to 1
                    repeatCount = 1;
                    repeatsInput.setText("1");
                }
            }
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

        // Initialize simplified view state based on initial zoom level
        CameraPosition initialPosition = mMap.getCameraPosition();
        if (initialPosition != null) {
            isSimplifiedView = false;
        }

        mMap.setOnMapLoadedCallback(() ->
        {
            updateMap(true);

            mMap.setOnInfoWindowClickListener(this);
            mMap.setOnCameraIdleListener(this);
            mMap.setOnMapClickListener(latLng -> {
                // Close polygon info window when user clicks elsewhere on the map
                dismissPolygonInfoMarker();
            });
            setMapLocationEnabled(true);

            mMap.setOnMarkerClickListener(marker -> {
                // Close polygon info window when user clicks on a marker
                dismissPolygonInfoMarker();
                return false; // Allow default behavior (showing info window)
            });
        });
    }

    public void dismissPolygonInfoMarker() {
        if (polygonInfoMarker != null) {
            polygonInfoMarker.remove();
            polygonInfoMarker = null;
        }
    }

    private void updateMap(boolean zoomToFit) {
        // Remove temporary polygon info marker if it exists
        dismissPolygonInfoMarker();
        
        // Clear polygon info when clearing the map
        polygonInfo.clear();
        markerReferences.clear();
        
        mMap.clear();
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
//        if (entityLocations.size() > 0) {
//            boolean showCustomMapMarker = HiddenPreferences.shouldShowCustomMapMarker();
//
//            // Add markers to map and find bounding region
//            for (Pair<Entity<TreeReference>, LatLng> entityLocation : entityLocations) {
//                MarkerOptions markerOptions = new MarkerOptions()
//                        .position(entityLocation.second)
//                        .title(entityLocation.first.getFieldString(0))
//                        .snippet(entityLocation.first.getFieldString(1));
//                if (showCustomMapMarker) {
//                    markerOptions.icon(getEntityIcon(entityLocation.first));
//                }
//                if (markerCheckbox.isChecked()) {
//                    Marker marker = mMap.addMarker(markerOptions);
//                    markerReferences.put(marker, entityLocation.first.getElement());
//                }
//                builder.include(entityLocation.second);
//            }
//        }

        //Add some example DUs for testing
        //Create some sample polygons
        int repeats = repeatCount;
        int square = (int) Math.sqrt(repeats);
        float repeatJump = 0.005f;
        float boxRadius = 0.0005f;
        LatLng first = new LatLng(-13.643511, 33.939722);
        List<Integer> xOffsets = List.of(0, -1, 1, 0, -1, 1, -1, 2, 1, -2);
        List<Integer> yOffsets = List.of(0, 0, 0, 1, 1, 1, -1, 0, 2, 0);
        int alpha = MAP_TYPES[selectedMapTypeIndex] ==  GoogleMap.MAP_TYPE_HYBRID ||
                MAP_TYPES[selectedMapTypeIndex] ==  GoogleMap.MAP_TYPE_SATELLITE ?
                128 : 32;
        List<Integer> colors = List.of(
                Color.argb(alpha, 255, 255, 0),
                Color.argb(alpha, 0, 255, 0),
                Color.argb(alpha, 255, 0, 0)
        );

        mMap.setOnPolygonClickListener(
                polygon -> {
                    showPolygonInfo(polygon);
                });

        boolean addedShapes = false;
        for(int r = 0; r < repeats; r++) {
            LatLng newCenter = new LatLng(
                    first.latitude + (r%square * repeatJump),
                    first.longitude  + (r/square * repeatJump)
            );
            if (isSimplifiedView) {
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(newCenter)
                        .title("Zoom-out Area")
                        .snippet("Test");
                mMap.addMarker(markerOptions);
                addedShapes = true;
                builder.include(first);
            } else {
                for (int i = 0; i < xOffsets.size(); i++) {
                    LatLng center = new LatLng(
                            newCenter.latitude + 2 * boxRadius * yOffsets.get(i),
                            newCenter.longitude + 2 * boxRadius * xOffsets.get(i)
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

                    // Store polygon title and snippet (matching marker format)
                    String title = "Polygon " + (i + 1);
                    String snippet = "Test";
                    polygonInfo.put(poly, new Pair<>(title, snippet));

                    addedShapes = true;
                    builder.include(poly.getPoints().get(0));
                    builder.include(poly.getPoints().get(1));
                    builder.include(poly.getPoints().get(2));
                    builder.include(poly.getPoints().get(3));

                    if (markerCheckbox.isChecked()) {
                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(center)
                                .title("Center " + (i + 1))
                                .snippet("Test");
                        mMap.addMarker(markerOptions);
                        addedShapes = true;
                    }
                }
            }
        }

        if(addedShapes && zoomToFit) {
            final LatLngBounds bounds = builder.build();

            // Move camera to be include all markers
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, MAP_PADDING));
        }
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
                    updateMap(false);
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
        // Don't handle clicks on the temporary polygon info marker
        if (marker == polygonInfoMarker) {
            return;
        }
        
        Intent i = new Intent(getIntent());
        TreeReference ref = markerReferences.get(marker);
        if(ref != null) {
            SerializationUtil.serializeToIntent(i, EntityDetailActivity.CONTEXT_REFERENCE, ref);

            setResult(RESULT_OK, i);
            finish();
        }
    }

    @Override
    public boolean shouldListenToSyncComplete() {
        return true;
    }

    @Override
    public void onCameraIdle() {
        if (mMap != null) {
            CameraPosition cameraPosition = mMap.getCameraPosition();
            if (cameraPosition != null) {
                float currentZoom = cameraPosition.zoom;
                boolean shouldUseSimplifiedView = currentZoom < ZOOM_THRESHOLD;
                
                // Only trigger redraw if we've crossed the threshold
                if (shouldUseSimplifiedView != isSimplifiedView) {
                    isSimplifiedView = shouldUseSimplifiedView;
                    updateMap(false);
                }
            }
        }
    }

    /**
     * Shows an info window for a clicked polygon by creating a temporary marker at the polygon center,
     * similar to marker info windows. Centers the map on the polygon and displays the info bubble.
     */
    private void showPolygonInfo(Polygon polygon) {
        Pair<String, String> info = polygonInfo.get(polygon);
        if (info == null || mMap == null) {
            return;
        }
        
        // Remove any existing temporary marker
        dismissPolygonInfoMarker();
        
        // Calculate the center of the polygon
        List<LatLng> points = polygon.getPoints();
        if (points == null || points.isEmpty()) {
            return;
        }
        
        double sumLat = 0;
        double sumLng = 0;
        int numPoints = points.size() - 1;
        for (int i=0; i<numPoints; i++) {
            LatLng point = points.get(i);
            sumLat += point.latitude;
            sumLng += point.longitude;
        }
        LatLng center = new LatLng(sumLat / numPoints, sumLng / numPoints);
        
        // Create a temporary marker at the polygon center with the polygon's info
        String title = info.first;
        String snippet = info.second;
        polygonInfoMarker = mMap.addMarker(new MarkerOptions()
                .position(center)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .title(title)
                .snippet(snippet)
                .visible(true));
        
        // Center the map on the polygon center
        mMap.animateCamera(CameraUpdateFactory.newLatLng(center));
        
        // Show the info window
        polygonInfoMarker.showInfoWindow();
    }
}
