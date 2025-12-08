package org.commcare.gis;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Pair;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

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
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.xpath.XPathException;

import java.util.HashMap;
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

    private final Vector<Pair<Entity<TreeReference>, EntityMapDisplayInfo>> entityLocations = new Vector<>();
    private final HashMap<Marker, TreeReference> markerReferences = new HashMap<>();

    private GoogleMap mMap;

    // keeps track of detail field index that should be used to show a custom icon
    private int imageFieldIndex = -1;

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
            for (Entity<TreeReference> entity : EntityMapUtils.getEntities(detail, selectDatum.getNodeset())) {
                EntityMapDisplayInfo displayInfo = EntityMapUtils.getDisplayInfoForEntity(entity, detail);
                if (displayInfo != null) {
                    entityLocations.add(new Pair<>(entity, displayInfo));
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
    public void onMapReady(@NonNull final GoogleMap map) {
        mMap = map;

        if (!entityLocations.isEmpty()) {
            boolean showCustomMapMarker = HiddenPreferences.shouldShowCustomMapMarker();
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            // Add markers to map and find bounding region
            for (Pair<Entity<TreeReference>, EntityMapDisplayInfo> displayInfoPair : entityLocations) {
                if (displayInfoPair.second.getLocation() != null) {
                    MarkerOptions markerOptions = new MarkerOptions()
                            .position(displayInfoPair.second.getLocation())
                            .title(displayInfoPair.first.getFieldString(0))
                            .snippet(displayInfoPair.first.getFieldString(1));
                    if (showCustomMapMarker) {
                        markerOptions.icon(getEntityIcon(displayInfoPair.first));
                    }

                    Marker marker = mMap.addMarker(markerOptions);
                    if(marker == null) {
                        Logger.exception("Failed to add marker to map",
                                new Exception("Marker: " + displayInfoPair.first.getFieldString(0)));
                        continue;
                    }

                    markerReferences.put(marker, displayInfoPair.first.getElement());
                    builder.include(displayInfoPair.second.getLocation());
                }
            }
            final LatLngBounds bounds = builder.build();

            // Move camera to be include all markers
            mMap.setOnMapLoadedCallback(
                    () -> mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, MAP_PADDING)));
        }

        mMap.setOnInfoWindowClickListener(this);
        setMapLocationEnabled(true);
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
