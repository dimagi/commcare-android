/**
 * 
 */
package org.commcare.android.db.legacy;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.AndroidTableBuilder;
import org.commcare.android.database.DbUtil;
import org.commcare.android.util.Base64;
import org.commcare.modern.models.EncryptedModel;
import org.javarosa.core.services.storage.IMetaData;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;

/**
 * @author ctsims
 *
 */
public abstract class LegacyDbHelper {
    
    protected Context c;
    private Cipher encrypter;
    //private Hashtable<String, EncryptedModel> encryptedModels;
    
    public LegacyDbHelper(Context c) {
        this.c = c;
    }
    
    public LegacyDbHelper(Context c, Cipher encrypter) {
        this.c = c;
        this.encrypter = encrypter;
    }
    
    public abstract SQLiteDatabase getHandle();
    

    public Pair<String, String[]> createWhere(String[] fieldNames, Object[] values, EncryptedModel em, Persistable p)  throws IllegalArgumentException {
        Set<String> fields = null;
        if(p instanceof IMetaData) {
            IMetaData m = (IMetaData)p;
            String[] thefields = m.getMetaDataFields();
            fields = new HashSet<String>();
            for(String s : thefields) {
                fields.add(LegacyTableBuilder.scrubName(s));
            }
        }
        
        if(em instanceof IMetaData) {
            IMetaData m = (IMetaData)em;
            String[] thefields = m.getMetaDataFields();
            fields = new HashSet<String>();
            for(String s : thefields) {
                fields.add(LegacyTableBuilder.scrubName(s));
            }
        }
        
        String ret = "";
        String[] arguments = new String[fieldNames.length];
        for(int i = 0 ; i < fieldNames.length; ++i) {
            String columnName = LegacyTableBuilder.scrubName(fieldNames[i]);
            if(fields != null) {
                if(!fields.contains(columnName)) {
                    throw new IllegalArgumentException("Model does not contain the column " + columnName + "!");
                }
            }
            ret += columnName + "=?";
            
            if(em != null && em.isEncrypted(fieldNames[i])) {
                arguments[i] = encrypt(values[i].toString());
            } else {
                arguments[i] = values[i].toString();
            }
            
            if(i + 1 < fieldNames.length) {
                ret += " AND ";
            }
        }
        return new Pair<String, String[]>(ret, arguments);
    }
    
    private String encrypt(String string) {
        byte[] encrypted = CryptUtil.encrypt(string.getBytes(), encrypter);
        return Base64.encode(encrypted);
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
            for(String key : m.getMetaDataFields()) {
                Object o = m.getMetaData(key);
                if(o == null ) { continue;}
                String value = o.toString();
                if(encrypt && ((EncryptedModel)e).isEncrypted(key)) {
                    values.put(AndroidTableBuilder.scrubName(key), encrypt(value));
                } else {
                    values.put(LegacyTableBuilder.scrubName(key), value);
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
