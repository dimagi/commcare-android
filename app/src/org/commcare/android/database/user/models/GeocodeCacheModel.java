/**
 *
 */
package org.commcare.android.database.user.models;

import com.google.android.maps.GeoPoint;

import org.commcare.modern.models.EncryptedModel;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * @author ctsims
 */
public class GeocodeCacheModel implements IMetaData, Persistable, EncryptedModel {

    public static final String STORAGE_KEY = "geocodecache";

    private static final String META_LAST_QUERY = "lastquery";
    public static final String META_LOCATION = "location";
    private static final String META_HIT = "hit";

    private static final String META_HIT_TRUE = "t";
    private static final String META_HIT_FALSE = "f";


    private int recordId = -1;
    private int lat = -1;
    private int lon = -1;
    private Date lastQueried;
    private String location;
    private boolean hit;

    public GeocodeCacheModel() {

    }

    public GeocodeCacheModel(String location, int lat, int lon) {
        this(location, lat, lon, new Date());
    }

    private GeocodeCacheModel(String location, int lat, int lon, Date queried) {
        hit = true;
        this.location = location;
        this.lat = lat;
        this.lon = lon;
        this.lastQueried = queried;
    }

    public static GeocodeCacheModel NoHitRecord(String val) {
        GeocodeCacheModel model = new GeocodeCacheModel();
        model.location = val;
        model.hit = false;
        model.lastQueried = new Date();
        return model;
    }


    public boolean isEncrypted(String data) {
        return !(data.equals(META_LAST_QUERY) || data.equals(META_HIT));
    }

    public boolean isBlobEncrypted() {
        return true;
    }

    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        lastQueried = ExtUtil.readDate(in);
        hit = ExtUtil.readBool(in);
        location = ExtUtil.readString(in);
        if (hit) {
            lat = ExtUtil.readInt(in);
            lon = ExtUtil.readInt(in);
        }
    }

    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeDate(out, lastQueried);
        ExtUtil.writeBool(out, hit);
        ExtUtil.writeString(out, location);
        if (hit) {
            ExtUtil.writeNumeric(out, lat);
            ExtUtil.writeNumeric(out, lon);
        }
    }

    public GeoPoint getGeoPoint() {
        return new GeoPoint(lat, lon);
    }

    public void setID(int ID) {
        recordId = ID;
    }

    public int getID() {
        return recordId;
    }

    public String[] getMetaDataFields() {
        return new String[]{META_LAST_QUERY, META_LOCATION, META_HIT};
    }

    public Object getMetaData(String fieldName) {
        if (META_LAST_QUERY.equals(fieldName)) {
            return DateUtils.formatDate(lastQueried, DateUtils.FORMAT_ISO8601);
        } else if (META_LOCATION.equals(fieldName)) {
            return location;
        } else if (META_HIT.equals(fieldName)) {
            if (hit) {
                return META_HIT_TRUE;
            } else {
                return META_HIT_FALSE;
            }
        }
        throw new IllegalArgumentException("No metadata field " + fieldName + " for Geocoder Cache Models");
    }

    /**
     * Whether this represents a location which has a geopoint, or one which failed to look up
     */
    public boolean dataExists() {
        return hit;
    }

}
