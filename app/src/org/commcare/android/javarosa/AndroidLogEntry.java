/**
 * 
 */
package org.commcare.android.javarosa;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

import org.javarosa.core.log.LogEntry;
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
public class AndroidLogEntry extends LogEntry implements Persistable, IMetaData {
	
	public static final String STORAGE_KEY = "commcarelogs";
	
	private static final String META_TYPE = "type";
	private static final String META_DATE = "date";
	
	private Date date;
	private String message;
	private String type;
	
	private int recordId = -1;

	/**
	 * Serialization only
	 */
	public AndroidLogEntry() {
		
	}
	
	public AndroidLogEntry(String type, String message, Date date) {
		this.type = type;
		this.message = message;
		this.date = date;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#readExternal(java.io.DataInputStream, org.javarosa.core.util.externalizable.PrototypeFactory)
	 */
	@Override
	public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
		recordId = ExtUtil.readInt(in);
		date = ExtUtil.readDate(in);
		type = ExtUtil.readString(in);
		message = ExtUtil.readString(in);

	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
	 */
	@Override
	public void writeExternal(DataOutputStream out) throws IOException {
		ExtUtil.writeNumeric(out, recordId);
		ExtUtil.writeDate(out, date);
		ExtUtil.writeString(out,type);
		ExtUtil.writeString(out,message);
	}

	/**
	 * @return the time
	 */
	public Date getTime() {
		return date;
	}

	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IMetaData#getMetaDataFields()
	 */
	@Override
	public String[] getMetaDataFields() {
		return new String[] {META_TYPE, META_DATE};
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.IMetaData#getMetaData(java.lang.String)
	 */
	@Override
	public Object getMetaData(String fieldName) {
		if(META_DATE.equals(fieldName)) {
			return DateUtils.formatDate(date, DateUtils.FORMAT_ISO8601);
		} else if(META_TYPE.equals(fieldName)) {
			return type;
		} 
		throw new IllegalArgumentException("No metadata field " + fieldName  + " for Log Entry Cache models");

	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.Persistable#setID(int)
	 */
	@Override
	public void setID(int ID) {
		this.recordId = ID;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.Persistable#getID()
	 */
	@Override
	public int getID() {
		return this.recordId;
	}

}
