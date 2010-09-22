/**
 * 
 */
package org.commcare.android.database;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;

import org.commcare.android.util.CryptUtil;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

/**
 * @author ctsims
 *
 */
public abstract class DbHelper {
	
	protected Context c;
	private Cipher encrypter;
	//private Hashtable<String, EncryptedModel> encryptedModels;
	
	public DbHelper(Context c) {
		this.c = c;
	}
	
	public DbHelper(Context c, Cipher encrypter) {
		this.c = c;
		this.encrypter = encrypter;
	}
	
	public abstract SQLiteDatabase getHandle();
	

	public String createWhere(String[] fieldNames, Object[] values) {
		String ret = "";
		for(int i = 0 ; i < fieldNames.length; ++i) {
			ret += fieldNames[i] + "='" + values[i].toString() + "'";
			if(i + 1 < fieldNames.length) {
				ret += " AND ";
			}
		}
		return ret;
	}
	
	public ContentValues getContentValues(Externalizable e) {
		boolean encrypt = e instanceof EncryptedModel;
		assert(!(encrypt) || encrypter != null);
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		OutputStream out = bos;
		
		
		if(encrypt && ((EncryptedModel)e).isBlobEncrypted()) {
			out = new CipherOutputStream(bos, encrypter);
		}
		
		try {
			e.writeExternal(new DataOutputStream(out));
			out.close();
		} catch (IOException e1) {
			e1.printStackTrace();
			throw new RuntimeException("Failed to serialize externalizable for content values");
		}
		byte[] blob = bos.toByteArray();
		
		ContentValues values = new ContentValues();
		
		if(e instanceof IMetaData) {
			IMetaData m = (IMetaData)e;
			Hashtable<String,Object> data = m.getMetaData();
			for(Enumeration<String> keys = data.keys() ; keys.hasMoreElements() ;) {
				String key = keys.nextElement();
				String value = data.get(key).toString();
				if(encrypt && ((EncryptedModel)e).isEncrypted(key)) {
					values.put(key, CryptUtil.encrypt(value.getBytes(), encrypter));
				} else {
					values.put(key, value);
				}
			}
		}
		
		values.put(DbUtil.DATA_COL,blob);
		
		return values;
	}
	
	public PrototypeFactory getPrototypeFactory() {
		return DbUtil.getPrototypeFactory(c);
	}
}
