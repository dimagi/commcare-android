/**
 * 
 */
package org.commcare.android.storage.framework;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;

import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * @author ctsims
 *
 */
public class Persisted implements Persistable, IMetaData {
	
	protected int recordId = -1; 

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#readExternal(java.io.DataInputStream, org.javarosa.core.util.externalizable.PrototypeFactory)
	 */
	@Override
	public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
		recordId = ExtUtil.readInt(in);
		try {
			for(Field f : this.getClass().getDeclaredFields()) {
				if(f.isAnnotationPresent(Persisting.class)) {
					readVal(f, this, in, pf);
				}
			}
		} catch(IllegalAccessException iae) {
			throw new DeserializationException(iae.getMessage());
		}
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
	 */
	@Override
	public void writeExternal(DataOutputStream out) throws IOException {
		ExtUtil.writeNumeric(out, recordId);
		try {
			for(Field f : this.getClass().getDeclaredFields()) {
				if(f.isAnnotationPresent(Persisting.class)) {
					writeVal(f, this, out);
				}
			}
		} catch(IllegalAccessException iae) {
			throw new RuntimeException(iae);
		}
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.Persistable#setID(int)
	 */
	@Override
	public void setID(int ID) {
		recordId = ID;
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.services.storage.Persistable#getID()
	 */
	@Override
	public int getID() {
		return recordId;
	}
	
	
	private void readVal(Field f, Object o, DataInputStream in, PrototypeFactory pf) throws DeserializationException, IOException, IllegalAccessException {		
		Persisting p = f.getAnnotation(Persisting.class);
		Class type = f.getType();
		try {
			f.setAccessible(true);
			
			if(type.equals(String.class)) {
				String read = ExtUtil.readString(in);
				f.set(o, p.nullable() ? ExtUtil.nullIfEmpty(read) : read);
				return;
			} else if(type.equals(Integer.TYPE)) {
				//Primitive Integers
				f.setInt(o, ExtUtil.readInt(in));
				return;
			} else if(type.equals(Date.class)) {
				f.set(o, ExtUtil.readDate(in));
				return;
			} else if(type.isArray()){
				
				//We only support byte arrays for now
				if(type.getComponentType().equals(Byte.TYPE)) {
					f.set(o, ExtUtil.readBytes(in));
					return;
				}
			}
		} finally {
			f.setAccessible(false);
		}
		
		//By Default
		throw new DeserializationException("Couldn't read persisted type " + f.getType().toString());
	}
	
	private void writeVal(Field f, Object o, DataOutputStream out) throws IOException, IllegalAccessException {
		try {
			Persisting p = f.getAnnotation(Persisting.class);
			Class type = f.getType();
			f.setAccessible(true);
	
			if(type.equals(String.class)) {
				String s = (String)f.get(o);
				ExtUtil.writeString(out, p.nullable() ? ExtUtil.emptyIfNull(s) : s);
				return;
			} else if(type.equals(Integer.TYPE)) {
				ExtUtil.writeNumeric(out,f.getInt(o));
				return;
			} else if(type.equals(Date.class)) {
				ExtUtil.writeDate(out, (Date)f.get(o));
				return;
			}  else if(type.isArray()){
				//We only support byte arrays for now
				if(type.getComponentType().equals(Byte.TYPE)) {
					ExtUtil.writeBytes(out,(byte[])f.get(o));
					return;
				}
			}
		} finally {
			f.setAccessible(false);
		}
		
		//By Default
		throw new RuntimeException("Couldn't write persisted type " + f.getType().toString());
	}

	@Override
	public String[] getMetaDataFields() {
		ArrayList<String> fields = new ArrayList<String>();
		for(Field f : this.getClass().getDeclaredFields()) {
			try {
			f.setAccessible(true);

			if(f.isAnnotationPresent(MetaField.class)) {
				MetaField mf = f.getAnnotation(MetaField.class);
				fields.add(mf.value());
			}
			} finally {
				f.setAccessible(false);
			}

		}
		return fields.toArray(new String[0]);
	}

	@Override
	public Object getMetaData(String fieldName) {
		try {
			for(Field f : this.getClass().getDeclaredFields()) {
				try {
				f.setAccessible(true);

				if(f.isAnnotationPresent(MetaField.class)) {
					MetaField mf = f.getAnnotation(MetaField.class);
					if(mf.value().equals(fieldName)) {
						return f.get(this);
					}
				}}
				finally {
					f.setAccessible(false);
				}
			}
		} catch(IllegalAccessException iae) {
			throw new RuntimeException(iae.getMessage());
		}
		//If we didn't find the field
		throw new IllegalArgumentException("No metadata field " + fieldName  + " in the case storage system");
	}

}
