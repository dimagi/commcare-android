/**
 * 
 */
package org.commcare.android.database;

import android.content.ContentValues;
import android.content.Context;
import android.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.util.SessionUnavailableException;
import org.commcare.modern.database.DatabaseHelper;
import org.commcare.modern.models.EncryptedModel;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.util.HashMap;

/**
 * Essentially a wrapper around the Java-generic DatabaseHelper
 * class that allows us to use those function i Android idiomatic classes
 * (IE ResultSet -> ContentValues, Android Pair instead of generic Pair, etc.
 *
 * @author ctsims
 * @author wspride
 *
 */
public abstract class AndroidDbHelper extends DatabaseHelper {
    
    protected Context c;
    
    public AndroidDbHelper(Context c) {
        this.c = c;
    }
    
    public abstract SQLiteDatabase getHandle() throws SessionUnavailableException;

    public ContentValues getContentValues(Externalizable e){
        ContentValues ret = new ContentValues();
        HashMap<String, Object> metaFieldsAndValues = DatabaseHelper.getMetaFieldsAndValues(e);
        for(String key: metaFieldsAndValues.keySet()){
            Object obj = metaFieldsAndValues.get(key);
            if(obj instanceof String){
                ret.put(key,(String)obj);
            } else if(obj instanceof Integer){
                ret.put(key, (Integer) obj);
            } else if(obj instanceof byte[]){
                ret.put(key, (byte[]) obj);
            } else{
                System.out.println("Couldn't determine type of object: " + obj);
            }
        }

        return ret;
    }

    public Pair<String, String[]> createWhereAndroid(String[] fieldNames, Object[] values, EncryptedModel em, Persistable p){
        org.commcare.modern.util.Pair<String, String[]> mPair = DatabaseHelper.createWhere(fieldNames, values, em, p);
        Pair<String, String[]> returnPair = new Pair<String, String[]>(mPair.first, mPair.second);
        return returnPair;

    }
    
    public PrototypeFactory getPrototypeFactory() {
        return DbUtil.getPrototypeFactory(c);
    }
}
