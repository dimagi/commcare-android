package org.commcare.dalvik.activities;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
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
 * @author Forest Tong, Phillip Mates
 */
@TargetApi(11)
public class EntityMapActivity extends CommCareActivity implements OnMapReadyCallback,
        GoogleMap.OnInfoWindowClickListener {
    private static final String TAG = EntityMapActivity.class.getSimpleName();

    private EvaluationContext entityEvaluationContext;
    private CommCareSession session;
    Vector<Entity<TreeReference>> entities;

    Vector<Pair<Entity<TreeReference>, LatLng>> entityLocations;
    HashMap<Marker, TreeReference> markerReferences = new HashMap<>();

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
            //We might not have a geopoint at all. Don't even trip
        }
        return location;
//
//        boolean cached = false;
//        try {
//            GeocodeCacheModel record = geoCache.getRecordForValue(GeocodeCacheModel.META_LOCATION, address);
//            cached = true;
//            if(record.dataExists()){
//                gp = record.getGeoPoint();
//            }
//        } catch(NoSuchElementException nsee) {
//            //no record!
//        }
//
//        //If we don't have a geopoint, let's try to find our address
//        if (!cached && location != null) {
//            try {
//                List<Address> addresses = mGeoCoder.getFromLocationName(address, 3, boundHints[0], boundHints[1], boundHints[2], boundHints[3]);
//                for(Address a : addresses) {
//                    if(a.hasLatitude() && a.hasLongitude()) {
//                        int lat = (int) (a.getLatitude() * 1E6);
//                        int lng = (int) (a.getLongitude() * 1E6);
//                        gp = new GeoPoint(lat, lng);
//
//                        geoCache.write(new GeocodeCacheModel(address, lat, lng));
//                        legit++;
//                        break;
//                    }
//                }
//
//                //We didn't find an address, make a miss record
//                if(gp == null) {
//                    geoCache.write(GeocodeCacheModel.NoHitRecord(address));
//                }
//            } catch (StorageFullException | IOException e1) {
//                e1.printStackTrace();
//            }
//        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        for (Pair<Entity<TreeReference>, LatLng> entityLocation: entityLocations) {
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(entityLocation.second)
                    .title(entityLocation.first.getFieldString(0)));
            markerReferences.put(marker, entityLocation.first.getElement());
        }

        map.setOnInfoWindowClickListener(this);
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
