/**
 * 
 */
package org.commcare.android.models;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Hashtable;

import org.commcare.android.database.EncryptedModel;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * @author ctsims
 *
 */
public class FormRecord implements Persistable, IMetaData, EncryptedModel {
	
	public static final String STORAGE_KEY = "FORMRECORDS";
	public static final String META_XMLNS = "XMLNS";
	public static final String META_PATH = "PATH";
	public static final String META_ENTITY_ID = "ENTITYID";
	public static final String META_STATUS = "STATUS";
	
	public static final String STATUS_UNSENT = "unsent";
	public static final String STATUS_INCOMPLETE = "incomplete";
	public static final String STATUS_COMPLETE = "complete";
	public static final String STATUS_UNSTARTED = "unstarted";
	
	private int id = -1;
	private String status;
	private String path;
	private String xmlns;
	private String entity;
	private byte[] aesKey;
	
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
	public FormRecord(String xmlns, String path, String entityId, String status, byte[] aesKey) {
		this.xmlns = xmlns;
		this.path = path;
		this.entity = entityId;
		this.status = status;
		this.aesKey = aesKey;
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
	
	public String getPath() {
		return path;
	}
	
	public byte[] getAesKey() {
		return aesKey;
	}
	
	public String getEntityId() {
		return entity;
	}
	
	public String getStatus() {
		return status;
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
		path = ExtUtil.readString(in);
		entity = ExtUtil.readString(in);
		status = ExtUtil.readString(in);
		aesKey = ExtUtil.readBytes(in);
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
	 */
	public void writeExternal(DataOutputStream out) throws IOException {
		ExtUtil.writeNumeric(out, id);
		ExtUtil.writeString(out, xmlns);
		ExtUtil.writeString(out, path);
		ExtUtil.writeString(out, entity);
		ExtUtil.writeString(out, status);
		ExtUtil.writeBytes(out, aesKey);
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
		} else if(fieldName.equals(META_PATH)) {
			return path;
		} else if(fieldName.equals(META_ENTITY_ID)) {
			return entity;
		} else if(fieldName.equals(META_STATUS)) {
			return status;
		} else {
			throw new IllegalArgumentException("No metadata field " + fieldName  + " in the form record storage system");
		}
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IMetaData#getMetaDataFields()
	 */
	public String[] getMetaDataFields() {
		return new String [] {META_XMLNS, META_PATH, META_ENTITY_ID, META_STATUS};
	}

	public boolean isEncrypted(String data) {
		return false;
	}

	public boolean isBlobEncrypted() {
		return true;
	}

}
