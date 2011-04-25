/**
 * 
 */
package org.commcare.android.models;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Hashtable;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.EncryptedModel;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.util.CommCareSession;
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
	public static final String STATUS_SAVED = "saved";
	
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
	
	String[] cached;
	private void decache() throws SessionUnavailableException {
		if(AndroidCommCarePlatform.ENTITY_NONE.equals(entity)) {
			cached = new String[] { null, null, null};
		} else if(entity.startsWith("case:")) {
		    cached = new String[] { entity.substring("case:".length()), null, null};
		} else if(entity.startsWith("referral")) {
			int c = entity.indexOf(":");
			int length = Integer.parseInt(entity.substring("referral".length(), c));
			String refid = entity.substring(c+1, c + length + 1);
			String type = entity.substring(c + length+1	);
			Referral r = CommCareApplication._().getStorage(Referral.STORAGE_KEY, Referral.class).
															getRecordForValues(new String[] {Referral.REFERRAL_ID, Referral.REFERRAL_TYPE},
																			   new String[] {refid, type});
			cached = new String[] {r.getLinkedId(), r.getReferralId(), r.getType()};
		} else {
			//Pre DB26 Record
			cached = new String[] {entity, null, null };
		}
	}
	public String getCaseId() throws SessionUnavailableException {
		if(cached == null) {
			decache();
		}
		return cached[0];
	}
	
	public String getReferralId() throws SessionUnavailableException {
		if(cached == null) {
			decache();
		}
		return cached[1];
	}
	public String getReferralType() throws SessionUnavailableException {
		if(cached == null) {
			decache();
		}
		return cached[2];
	}
	
	public static String generateEntityId(CommCareSession session) {
		if(session.getReferralId() != null) {
			//referral is primary
			return "referral" + session.getReferralId().length() + ":" + session.getReferralId() + session.getReferralType();
		} else if(session.getCaseId() != null) {
			//case is primary
			return "case:"+session.getCaseId();
		} else {
			return AndroidCommCarePlatform.ENTITY_NONE;
		}
	}

}
