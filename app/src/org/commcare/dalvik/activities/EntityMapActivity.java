package org.commcare.dalvik.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.OverlayItem;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.GeocodeCacheModel;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.util.CommCareInstanceInitializer;
import org.commcare.android.util.SerializationUtil;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.geo.EntityOverlay;
import org.commcare.dalvik.geo.EntityOverlayItemFactory;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.Entry;
import org.commcare.suite.model.SessionDatum;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.data.GeoPointData;
import org.javarosa.core.model.data.UncastData;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.storage.StorageFullException;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * @author ctsims
 */
public class EntityMapActivity extends MapActivity {
    private static final String TAG = EntityMapActivity.class.getSimpleName();

    MapView map;
    MyLocationOverlay mMyLocationOverlay;
    Geocoder mGeoCoder;
    LocationManager mLocationManager;
    
    CommCareSession session;
    Entry prototype;
    
    EntityOverlay mEntityOverlay;
    Vector<Entity<TreeReference>> entities;
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        String licenseKey = getLicenseKey();
        map = new MapView(this, licenseKey);
        
        this.setContentView(map);
        
        mGeoCoder = new Geocoder(this);
        
        // Get the location manager
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        // Define the criteria how to select the locatioin provider -> use
        // default
        Criteria criteria = new Criteria();
        String provider = mLocationManager.getBestProvider(criteria, false);
        Location location = mLocationManager.getLastKnownLocation(provider);

        session = CommCareApplication._().getCurrentSession();
        Vector<Entry> entries = session.getEntriesForCommand(session.getCommand());
        prototype = entries.elementAt(0);
        
        SessionDatum selectDatum = session.getNeededDatum();
        Detail detail = session.getDetail(selectDatum.getShortDetail());
        NodeEntityFactory factory = new NodeEntityFactory(detail, this.getEC());
        
        Vector<TreeReference> references = getEC().expandReference(selectDatum.getNodeset());
        
        entities = new Vector<Entity<TreeReference>>();
        for(TreeReference ref : references) {
            entities.add(factory.getEntity(ref));
        }

        Log.d(TAG, "Entities generated");
        
        map.displayZoomControls(true);
        mMyLocationOverlay = new MyLocationOverlay(this, map);
        mMyLocationOverlay.runOnFirstFix(new Runnable() { public void run() {
            map.getController().animateTo(mMyLocationOverlay.getMyLocation());
        }});
        
        double[] boundHints = new double[4];
        // Initialize the location fields
        if (location != null) {
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            
            //lLat
            boundHints[0] = lat -1;
            //lLng
            boundHints[1] = lng -1;
            
            //uLat
            boundHints[2] = lat + 1;
            
            //rLon
            boundHints[3] = lng + 1;
            
            
        } else {
            //no location
        }
        
        Drawable defaultMarker = this.getResources().getDrawable(R.drawable.marker);
        mEntityOverlay = new EntityOverlay(this, defaultMarker, map) {

            @Override
            protected void selected(TreeReference ref) {
                Intent i = new Intent(EntityMapActivity.this.getIntent());
                SerializationUtil.serializeToIntent(i, EntityDetailActivity.CONTEXT_REFERENCE, ref);
                
                setResult(RESULT_OK, i);

                EntityMapActivity.this.finish();                
            }
        };
        Log.d(TAG, "Loading addresses...");
        
        int legit = 0;
        int bogus = 0;
        
        EntityOverlayItemFactory overlayFactory = new EntityOverlayItemFactory(detail, defaultMarker);
        
        SqlStorage<GeocodeCacheModel> geoCache = CommCareApplication._().getUserStorage(GeocodeCacheModel.STORAGE_KEY, GeocodeCacheModel.class);
        
        for(Entity<TreeReference> e : entities) {
            for(int i = 0 ; i < detail.getHeaderForms().length; ++i ){
                if("address".equals(detail.getTemplateForms()[i])) {
                    String val = e.getFieldString(i).trim();
                    if(val != null && val != "") {
                        GeoPoint gp = null;
                        try {
                            GeoPointData data = new GeoPointData().cast(new UncastData(val));
                            if(data != null) {
                                int lat = (int) (data.getValue()[0] * 1E6);
                                int lng = (int) (data.getValue()[1] * 1E6);
                                gp = new GeoPoint(lat, lng);
                            }
                        } catch(Exception ex) {
                            //We might not have a geopoint at all. Don't even trip
                        }
                        
                        boolean cached = false;
                        try {
                            GeocodeCacheModel record = geoCache.getRecordForValue(GeocodeCacheModel.META_LOCATION, val);
                            cached = true;
                            if(record.dataExists()){
                                gp = record.getGeoPoint();
                            }
                        } catch(NoSuchElementException nsee) {
                            //no record!
                        }
                        
                        //If we don't have a geopoint, let's try to find our address
                        if(!cached && boundHints != null) {
                            try {
                                List<Address> addresses = mGeoCoder.getFromLocationName(val, 3, boundHints[0], boundHints[1], boundHints[2], boundHints[3]);
                                for(Address a : addresses) {
                                    if(a.hasLatitude() && a.hasLongitude()) {
                                        int lat = (int) (a.getLatitude() * 1E6);
                                        int lng = (int) (a.getLongitude() * 1E6);
                                        gp = new GeoPoint(lat, lng);
                                        
                                        geoCache.write(new GeocodeCacheModel(val, lat, lng));
                                        legit++;
                                        break;
                                    }
                                }
                                
                                //We didn't find an address, make a miss record
                                if(gp == null) {
                                    geoCache.write(GeocodeCacheModel.NoHitRecord(val));
                                }
                            } catch (StorageFullException | IOException e1) {
                                e1.printStackTrace();
                            }
                        }
                        
                        //Ok, so now we have an address or not. If we _do_ have one, let's have some fun
                        
                        if(gp != null) {
                            OverlayItem overlayItem = overlayFactory.generateOverlay(gp, e);
                            mEntityOverlay.addOverlay(overlayItem, e.getElement());
                        }
                        else { 
                            bogus++;
                        }
                    }
                }
            }
        }
        
        Log.d(TAG, "Loaded. " + legit +" addresses discovered, " + bogus + " could not be located");

        if(legit != 0 && mEntityOverlay.getCenter() != null) {
            map.getController().animateTo(mEntityOverlay.getCenter());
        } else if(location != null) {
            int lat = (int) (location.getLatitude() * 1E6);
            int lng = (int) (location.getLongitude() * 1E6);
            GeoPoint point = new GeoPoint(lat, lng);
            map.getController().animateTo(point);
        }
        
        map.getOverlays().add(mMyLocationOverlay);
        //The overlay crashes out if you try to draw it and it's empty,
        //so only add it if we found something.
        if(mEntityOverlay.size() > 0) {
            map.getOverlays().add(mEntityOverlay);
        }
        map.getController().setZoom(18);
        map.setClickable(true);
        map.setEnabled(true);
        Log.d(TAG, "Done loading");
    }
    
    private String getLicenseKey() {
        //If there's a defined debug key in the local environment, use that.
        int debugId = this.getResources().getIdentifier("maps_api_key_debug","string", this.getPackageName());
        if(debugId == 0) { 
            return BuildConfig.MAPS_API_KEY;
        }
        return this.getString(debugId);
    }

    EvaluationContext entityContext;

    private EvaluationContext getEC() {
        if(entityContext == null) {
            entityContext = session.getEvaluationContext(getInstanceInit());
        }
        return entityContext;
    }
    
    private CommCareInstanceInitializer getInstanceInit() {
        return new CommCareInstanceInitializer(session);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mMyLocationOverlay.disableCompass(); 
        mMyLocationOverlay.disableMyLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMyLocationOverlay.enableMyLocation();
        mMyLocationOverlay.enableCompass();
    }

    protected boolean isRouteDisplayed() {
        return false;
    }
}
