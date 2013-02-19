/**
 * 
 */
package org.commcare.android.database.user.models;

import java.io.FileNotFoundException;
import java.util.Date;
import java.util.Hashtable;

import org.commcare.android.database.EncryptedModel;
import org.commcare.android.storage.framework.MetaField;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI.InstanceColumns;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * @author ctsims
 *
 */
@Table(FormRecord.STORAGE_KEY)
public class FormRecord extends Persisted implements EncryptedModel {
	
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
	
	@Persisting(1)
	@MetaField(META_XMLNS)
	private String xmlns;
	@Persisting(2)
	@MetaField(META_INSTANCE_URI)
	private String instanceURI;
	@Persisting(3)
	@MetaField(META_STATUS)
	private String status;
	@Persisting(4)
	private byte[] aesKey;
	@Persisting(value=5, nullable=true)
	@MetaField(META_UUID)
	private String uuid;
	@Persisting(6)
	@MetaField(META_LAST_MODIFIED)
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
		fr.recordId = this.recordId;
		return fr;
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

	public boolean isEncrypted(String data) {
		return false;
	}

	public boolean isBlobEncrypted() {
		return true;
	}
	
	String[] cached;
	private void decache() throws SessionUnavailableException {
		
	}

	public String getPath(Context context) throws FileNotFoundException {
		Uri uri = getInstanceURI();
		if(uri == null) { throw new FileNotFoundException("No form instance URI exists for formrecord " + recordId); }
		
		Cursor c = context.getContentResolver().query(uri, new String[] {InstanceColumns.INSTANCE_FILE_PATH}, null, null, null);
		if(!c.moveToFirst()) { throw new FileNotFoundException("No Instances were found at for formrecord " + recordId + " at isntance URI " + uri.toString()); }
		
		return c.getString(c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
	}
}