package org.commcare.gis;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Pair;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.firebase.perf.metrics.Trace;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.EntityDetailActivity;
import org.commcare.cases.entity.Entity;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.CCPerfMonitoring;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.EntityDatum;
import org.commcare.utils.MediaUtil;
import org.commcare.utils.SerializationUtil;
import org.commcare.utils.ViewUtils;
import org.commcare.views.UserfacingErrorHandling;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.xpath.XPathException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import static org.commcare.views.EntityView.FORM_IMAGE;

/**
 * @author Forest Tong (ftong@dimagi.com)
 */
public class EntityMapActivity extends CommCareActivity implements OnMapReadyCallback,
        GoogleMap.OnInfoWindowClickListener {
    private static final int MAP_PADDING = 50;  // Number of pixels to pad bounding region of markers
    private static final int DEFAULT_MARKER_SIZE = 120;
    private static final int BOUNDARY_POLYGON_ALPHA = 64;
    private static final int BOUNDARY_POLYGON_STROKE_WIDTH = 5;
    private static final double GEO_POINT_RADIUS_METERS = 3.0;

    private final Vector<Pair<Entity<TreeReference>, EntityMapDisplayInfo>> entityLocations = new Vector<>();
    private final HashMap<Marker, TreeReference> markerReferences = new HashMap<>();
    private final HashMap<Polygon, Pair<String, String>> polygonInfo = new HashMap<>();

    private GoogleMap mMap;

    // keeps track of detail field index that should be used to show a custom icon
    private int imageFieldIndex = -1;

    private Marker polygonInfoMarker = null;
    private Trace mapStartupTrace = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.entity_map_view);

        mapStartupTrace = CCPerfMonitoring.INSTANCE.startTracing(
                CCPerfMonitoring.TRACE_ENTITY_MAP_LOADING_TIME);

        SupportMapFragment mapFragment = (SupportMapFragment)getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        try {
            addEntityData();
        } catch (XPathException xe) {
            new UserfacingErrorHandling<>().logErrorAndShowDialog(this, xe, true);
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
            evalImageFieldIndex(detail);
            var errorEncountered = false;
            for (Entity<TreeReference> entity : EntityMapUtils.getEntities(detail, selectDatum.getNodeset())) {
                EntityMapDisplayInfo displayInfo = EntityMapUtils.getDisplayInfoForEntity(entity, detail);
                if (displayInfo != null) {
                    entityLocations.add(new Pair<>(entity, displayInfo));
                    errorEncountered |= displayInfo.getErrorEncountered();
                }
            }

            if (errorEncountered) {
                ViewUtils.showSnackBarWithNoDismissAction(findViewById(R.id.map),
                        getString(R.string.entity_map_error_message));
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
    public void onMapReady(@NonNull final GoogleMap map) {
        mMap = map;

        int numMarkers = 0;
        int numPolygons = 0;
        int numGeoPoints = 0;
        if (!entityLocations.isEmpty()) {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            boolean showCustomMapMarker = HiddenPreferences.shouldShowCustomMapMarker();

            for (Pair<Entity<TreeReference>, EntityMapDisplayInfo> displayInfoPair : entityLocations) {
                numMarkers += addMarker(builder, displayInfoPair.first, displayInfoPair.second,
                        showCustomMapMarker) ? 1 : 0;
                numPolygons += addBoundaryPolygon(builder, displayInfoPair.first,
                        displayInfoPair.second) ? 1 : 0;
                numGeoPoints += addGeoPoints(builder, displayInfoPair.second);
            }

            final LatLngBounds bounds = builder.build();

            mMap.setOnMapClickListener(latLng -> {
                dismissPolygonInfoMarker();
            });

            mMap.setOnMarkerClickListener(marker -> {
                dismissPolygonInfoMarker();
                return false;
            });

            mMap.setOnPolygonClickListener(polygon -> {
                showPolygonInfo(polygon);
            });

            final int finalMarkers = numMarkers;
            final int finalPolygons = numPolygons;
            final int finalGeoPoints = numGeoPoints;

            // Move camera to be include all markers
            mMap.setOnMapLoadedCallback(
                    () -> mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, MAP_PADDING), new GoogleMap.CancelableCallback() {
                        @Override
                        public void onCancel() {
                            finishPerformanceTrace(finalMarkers, finalPolygons, finalGeoPoints);
                        }

                        @Override
                        public void onFinish() {
                            finishPerformanceTrace(finalMarkers, finalPolygons, finalGeoPoints);
                        }
                    }));
        } else {
            finishPerformanceTrace(numMarkers, numPolygons, numGeoPoints);
        }

        mMap.setOnInfoWindowClickListener(this);
        setMapLocationEnabled(true);
    }

    private boolean addMarker(LatLngBounds.Builder builder,
                              Entity<TreeReference> entity,
                              EntityMapDisplayInfo displayInfo,
                              boolean showCustomMapMarker) {
        // Add markers to map and find bounding region
        if (displayInfo.getLocation() != null) {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(displayInfo.getLocation())
                    .title(entity.getFieldString(0))
                    .snippet(entity.getFieldString(1));
            if (showCustomMapMarker) {
                markerOptions.icon(getEntityIcon(entity));
            }

            Marker marker = mMap.addMarker(markerOptions);
            if (marker == null) {
                Logger.exception("Failed to add marker to map",
                        new Exception("Marker: " + entity.getFieldString(0)));
                return false;
            }

            markerReferences.put(marker, entity.getElement());
            builder.include(displayInfo.getLocation());

            return true;
        }

        return false;
    }

    private boolean addBoundaryPolygon(LatLngBounds.Builder builder,
                                       Entity<TreeReference> entity,
                                       EntityMapDisplayInfo displayInfo) {
        // Add boundary polygon to map
        if (displayInfo.getBoundary() != null) {
            int color = displayInfo.getBoundaryColorHex() != null ?
                    displayInfo.getBoundaryColorHex() :
                    Color.WHITE;
            int withAlpha = Color.argb(BOUNDARY_POLYGON_ALPHA,
                    Color.red(color), Color.green(color), Color.blue(color));

            PolygonOptions options = new PolygonOptions()
                    .addAll(displayInfo.getBoundary())
                    .clickable(true)
                    .strokeColor(Color.GRAY)
                    .fillColor(withAlpha)
                    .strokeWidth(BOUNDARY_POLYGON_STROKE_WIDTH);

            Polygon polygon = mMap.addPolygon(options);
            polygonInfo.put(polygon, new Pair<>(entity.getFieldString(0), entity.getFieldString(1)));

            for (LatLng coord : displayInfo.getBoundary()) {
                builder.include(coord);
            }

            return true;
        }

        return false;
    }

    private int addGeoPoints(LatLngBounds.Builder builder,
                             EntityMapDisplayInfo displayInfo) {
        // Add additional display points to map
        if (displayInfo.getPoints() != null) {
            for (int i = 0; i < displayInfo.getPoints().size(); i++) {
                LatLng coordinate = displayInfo.getPoints().get(i);
                int color = Color.WHITE;
                if (displayInfo.getPointColorsHex() != null &&
                        displayInfo.getPointColorsHex().size() > i) {
                    color = displayInfo.getPointColorsHex().get(i);
                }
                CircleOptions options = new CircleOptions()
                        .center(coordinate)
                        .radius(GEO_POINT_RADIUS_METERS)
                        .strokeColor(color)
                        .fillColor(color);

                mMap.addCircle(options);
                builder.include(coordinate);
            }

            return displayInfo.getPoints().size();
        }

        return 0;
    }

    private void finishPerformanceTrace(int numMarkers, int numPolygons, int numGeoPoints) {
        if (mapStartupTrace != null) {
            Map<String, String> perfMetrics = new HashMap<>();
            perfMetrics.put(CCPerfMonitoring.ATTR_MAP_MARKERS, Integer.toString(numMarkers));
            perfMetrics.put(CCPerfMonitoring.ATTR_MAP_POLYGONS, Integer.toString(numPolygons));
            perfMetrics.put(CCPerfMonitoring.ATTR_MAP_GEO_POINTS, Integer.toString(numGeoPoints));
            CCPerfMonitoring.INSTANCE.stopTracing(mapStartupTrace, perfMetrics);
            mapStartupTrace = null;
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

        dismissPolygonInfoMarker();

        List<LatLng> points = polygon.getPoints();
        if (points.isEmpty()) {
            return;
        }

        double sumLat = 0;
        double sumLng = 0;
        int numPoints = points.size() - 1;
        for (int i = 0; i < numPoints; i++) {
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

        mMap.animateCamera(CameraUpdateFactory.newLatLng(center));

        polygonInfoMarker.showInfoWindow();
    }

    public void dismissPolygonInfoMarker() {
        if (polygonInfoMarker != null) {
            polygonInfoMarker.remove();
            polygonInfoMarker = null;
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
    public void onInfoWindowClick(@NonNull Marker marker) {
        // Don't handle clicks on the temporary polygon info marker
        if (marker == polygonInfoMarker) {
            return;
        }

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
