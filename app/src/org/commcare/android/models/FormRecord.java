/**
 * 
 */
package org.commcare.android.models;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Hashtable;

import org.commcare.android.database.EncryptedModel;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI.InstanceColumns;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * @author ctsims
 *
 */
public class FormRecord implements Persistable, IMetaData, EncryptedModel {
	
	public static final String STORAGE_KEY = "FORMRECORDS";
	public static final String META_INSTANCE_URI = "INSTANCE_URI";
	public static final String META_STATUS = "STATUS";
	public static final String META_UUID = "UUID";
	public static final String META_XMLNS = "XMLNS";
	public static final String META_LAST_MODIFIED = "DATE_MODIFIED";
	
	public static final String STATUS_UNSENT = "unsent";
	public static final String STATUS_INCOMPLETE = "incomplete";
	public static final String STATUS_COMPLETE = "complete";
	public static final String STATUS_UNSTARTED = "unstarted";
	public static final String STATUS_SAVED = "saved";
	public static final String STATUS_UNINDEXED = "unindexed";
	
	private int id = -1;
	private String status;
	private String instanceURI;
	private String xmlns;
	private byte[] aesKey;
	
	private String uuid;
	private Date lastModified;
	
	//Placeholder
	private Hashtable<String, String> metadata = null;
	
	public FormRecord() { }
	
	/**
	 * Creates a record of a form entry with the provided data. Note that none
	 * of the parameters can be null...
	 * 
	 * @param xmlns
	 * @param path
	 * @param entityId
	 * @param status
	 */
	public FormRecord(String instanceURI, String status, String xmlns, byte[] aesKey, String uuid, Date lastModified) {
		this.instanceURI = instanceURI;
		this.status = status;
		this.xmlns = xmlns;
		this.aesKey = aesKey;
		
		this.uuid = uuid;
		this.lastModified = lastModified;
		if(lastModified == null) { lastModified = new Date(); } ;
	}
	
	public FormRecord updateStatus(String instanceURI, String newStatus) {
		FormRecord fr = new FormRecord(instanceURI, newStatus, xmlns, aesKey, uuid, lastModified);
		fr.id = this.id;
		return fr;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.Persistable#getID()
	 */
	public int getID() {
		return id;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.Persistable#setID(int)
	 */
	public void setID(int ID) {
		id = ID;
	}
	
	public Uri getInstanceURI() {
		if(instanceURI == "") { return null; }
		return Uri.parse(instanceURI);
	}
	
	public byte[] getAesKey() {
		return aesKey;
	}
	
	public String getStatus() {
		return status;
	}
	
	public String getInstanceID() {
		return uuid;
	}
	
	public Date lastModified() {
		return lastModified;
	}
	
	public String getFormNamespace() {
		return xmlns;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#readExternal(java.io.DataInputStream, org.javarosa.core.util.externalizable.PrototypeFactory)
	 */
	public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
		id = (int)ExtUtil.readNumeric(in);
		xmlns = ExtUtil.readString(in);
		instanceURI = ExtUtil.readString(in);
		status = ExtUtil.readString(in);
		aesKey = ExtUtil.readBytes(in);
		uuid = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
		lastModified = ExtUtil.readDate(in);
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
	 */
	public void writeExternal(DataOutputStream out) throws IOException {
		ExtUtil.writeNumeric(out, id);
		ExtUtil.writeString(out, xmlns);
		ExtUtil.writeString(out, instanceURI);
		ExtUtil.writeString(out, status);
		ExtUtil.writeBytes(out, aesKey);
		ExtUtil.writeString(out, ExtUtil.emptyIfNull(uuid));
		ExtUtil.writeDate(out, lastModified);
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IMetaData#getMetaData()
	 */
	public Hashtable getMetaData() {
		Hashtable h = new Hashtable();
		String[] fields = getMetaDataFields();
		for (int i = 0; i < fields.length; i++) {
			String field = fields[i];
			Object value = getMetaData(field);
			if (value != null) {
				h.put(field, value);
			}
		}
		return h;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IMetaData#getMetaData(java.lang.String)
	 */
	public Object getMetaData(String fieldName) {
		if(fieldName.equals(META_XMLNS)) {
			return xmlns;
		} else if(fieldName.equals(META_INSTANCE_URI)) {
			return instanceURI;
		} else if(fieldName.equals(META_STATUS)) {
			return status;
		}  else if(fieldName.equals(META_UUID)) {
			return uuid;
		}  else if(fieldName.equals(META_LAST_MODIFIED)) {
			return lastModified;
		} else {
			throw new IllegalArgumentException("No metadata field " + fieldName  + " in the form record storage system");
		}
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IMetaData#getMetaDataFields()
	 */
	public String[] getMetaDataFields() {
		return new String [] {META_INSTANCE_URI, META_XMLNS, META_STATUS, META_UUID, META_LAST_MODIFIED};
	}

	public boolean isEncrypted(String data) {
		return false;
	}

	public boolean isBlobEncrypted() {
		return true;
	}
	
	String[] cached;
	private void decache() throws SessionUnavailableException {
		
	}

	public String getPath(Context context) {
		Uri uri = getInstanceURI();
		if(uri == null) { return null; }
		
		Cursor c = context.getContentResolver().query(uri, new String[] {InstanceColumns.INSTANCE_FILE_PATH}, null, null, null);
		if(!c.moveToFirst()) { return null; }
		
		return c.getString(c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
	}
}