/**
 * 
 */
package org.commcare.android.database;

import android.content.ContentValues;
import android.content.Context;
import android.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.util.SessionUnavailableException;
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

/**
 * @author ctsims
 *
 */
public abstract class DbHelper {
    
    protected Context c;
    
    public DbHelper(Context c) {
        this.c = c;
    }
    
    public abstract SQLiteDatabase getHandle() throws SessionUnavailableException;
    

    public Pair<String, String[]> createWhere(String[] fieldNames, Object[] values, EncryptedModel em, Persistable p)  throws IllegalArgumentException {
        Set<String> fields = null;
        if(p instanceof IMetaData) {
            IMetaData m = (IMetaData)p;
            String[] thefields = m.getMetaDataFields();
            fields = new HashSet<String>();
            for(String s : thefields) {
                fields.add(TableBuilder.scrubName(s));
            }
        }
        
        if(em instanceof IMetaData) {
            IMetaData m = (IMetaData)em;
            String[] thefields = m.getMetaDataFields();
            fields = new HashSet<String>();
            for(String s : thefields) {
                fields.add(TableBuilder.scrubName(s));
            }
        }
        
        String ret = "";
        String[] arguments = new String[fieldNames.length];
        for(int i = 0 ; i < fieldNames.length; ++i) {
            String columnName = TableBuilder.scrubName(fieldNames[i]);
            if(fields != null) {
                if(!fields.contains(columnName)) {
                    throw new IllegalArgumentException("Model does not contain the column " + columnName + "!");
                }
            }
            ret += columnName + "=?";
            
            arguments[i] = values[i].toString();
            
            if(i + 1 < fieldNames.length) {
                ret += " AND ";
            }
        }
        return new Pair<String, String[]>(ret, arguments);
    }
    
    public ContentValues getContentValues(Externalizable e) throws RecordTooLargeException {
        boolean encrypt = e instanceof EncryptedModel;
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStream out = bos;
        
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
                values.put(TableBuilder.scrubName(key), value);
            }
        }

        if(blob.length > Math.pow(1024, 2)){
           throw new RecordTooLargeException(blob.length / Math.pow(1024, 2));
        }
        
        values.put(DbUtil.DATA_COL,blob);
        
        return values;
    }
    
    public PrototypeFactory getPrototypeFactory() {
        return DbUtil.getPrototypeFactory(c);
    }
}
