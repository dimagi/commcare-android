/**
 * 
 */
package org.commcare.android.database.cache;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

import org.commcare.android.database.EncryptedModel;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * @author ctsims
 *
 */
public class GeocodeCacheModel implements IMetaData, Persistable, EncryptedModel {
	
	public static final String STORAGE_KEY = "geocodecache";
	
	public static final String META_LAST_QUERY = "lastquery";
	public static final String META_LOCATION = "location";
	public static final String META_HIT = "hit";
	public static final String META_CACHE_HASH = "hash";
	
	public static final String META_HIT_TRUE = "t";
	public static final String META_HIT_FALSE = "f";
	
	
	private int recordId = -1;
	int lat = -1;
	int lon = -1;
	Date lastQueried;
	String location;
	boolean hit;
	
	public GeocodeCacheModel() {
		
	}
	
	public GeocodeCacheModel(String location, int lat, int lon, Date queried) {
		hit = true;
		this.location = location;
		this.lat = lat;
		this.lon = lon;
		this.lastQueried = queried;
	}
	
	public static GeocodeCacheModel NoHitRecord() {
		GeocodeCacheModel model = new GeocodeCacheModel();
		model.hit = false;
		model.lastQueried = new Date();
		return model;
	}
	

	public boolean isEncrypted(String data) {
		if(data.equals(META_LAST_QUERY) || data.equals(META_HIT) || data.equals(META_CACHE_HASH)) {
			return false;
		}
		return true;
	}

	public boolean isBlobEncrypted() {
		return true;
	}

	public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
		lastQueried = ExtUtil.readDate(in);
		hit = ExtUtil.readBool(in);
		location = ExtUtil.readString(in);
		if(hit) {
			lat = ExtUtil.readInt(in);
			lon = ExtUtil.readInt(in);
		}
	}

	public void writeExternal(DataOutputStream out) throws IOException {
		ExtUtil.writeDate(out, lastQueried);
		ExtUtil.writeBool(out, hit);
		if(hit) {
			ExtUtil.writeString(out,location);
			ExtUtil.writeNumeric(out,lat);
			ExtUtil.writeNumeric(out,lon);
		}
	}

	public void setID(int ID) {
		recordId = ID;
	}

	public int getID() {
		return recordId;
	}

	public String[] getMetaDataFields() {
		return new String[] {META_LAST_QUERY, META_LOCATION, META_CACHE_HASH, META_HIT};
	}

	public Object getMetaData(String fieldName) {
		if(META_LAST_QUERY.equals(fieldName)) {
			return DateUtils.formatDate(lastQueried, DateUtils.FORMAT_ISO8601);
		} else if(META_LOCATION.equals(fieldName)) {
			return location;
		} else if(META_HIT.equals(fieldName)) {
			if(hit) { return META_HIT_TRUE; } else {return META_HIT_FALSE; }
		}
		throw new IllegalArgumentException("No metadata field " + fieldName  + " for Geocoder Cache Models");
	}
	
}
