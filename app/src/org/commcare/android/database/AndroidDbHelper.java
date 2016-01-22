package org.commcare.android.database;

import android.content.ContentValues;
import android.content.Context;
import android.util.Log;
import android.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.util.SessionUnavailableException;
import org.commcare.modern.database.DatabaseHelper;
import org.commcare.modern.models.EncryptedModel;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Essentially a wrapper around the Java-generic DatabaseHelper
 * class that allows us to use those function i Android idiomatic classes
 * (IE ResultSet -> ContentValues, Android Pair instead of generic Pair, etc.
 *
 * @author ctsims
 * @author wspride
 */
public abstract class AndroidDbHelper extends DatabaseHelper {
    private final static String TAG = AndroidDbHelper.class.getSimpleName();
    
    protected final Context c;
    
    public AndroidDbHelper(Context c) {
        this.c = c;
    }
    
    public abstract SQLiteDatabase getHandle() throws SessionUnavailableException;

    public ContentValues getContentValues(Externalizable e){
        ContentValues contentValues = new ContentValues();
        HashMap<String, Object> metaFieldsAndValues = DatabaseHelper.getMetaFieldsAndValues(e);

        copyMetadataIntoContentValues(metaFieldsAndValues, contentValues);

        return contentValues;
    }

    public ContentValues getContentValuesWithCustomData(Externalizable e, byte[] customData){
        ContentValues contentValues = new ContentValues();
        HashMap<String, Object> metaFieldsAndValues = DatabaseHelper.getNonDataMetaEntries(e);

        copyMetadataIntoContentValues(metaFieldsAndValues, contentValues);
        contentValues.put(DATA_COL, customData);

        return contentValues;
    }

    public ContentValues getNonDataContentValues(Externalizable e){
        ContentValues contentValues = new ContentValues();
        HashMap<String, Object> metaFieldsAndValues = DatabaseHelper.getNonDataMetaEntries(e);

        copyMetadataIntoContentValues(metaFieldsAndValues, contentValues);

        return contentValues;
    }

    private void copyMetadataIntoContentValues(HashMap<String, Object> metaFieldsAndValues,
                                               ContentValues contentValues) {
        for(Map.Entry<String, Object> entry:  metaFieldsAndValues.entrySet()){
            String key = entry.getKey();
            Object obj = entry.getValue();
            if(obj instanceof String){
                contentValues.put(key,(String)obj);
            } else if(obj instanceof Integer){
                contentValues.put(key, (Integer) obj);
            } else if(obj instanceof byte[]){
                contentValues.put(key, (byte[]) obj);
            } else{
                Log.w(TAG, "Couldn't determine type of object: " + obj);
            }
        }
    }

    public Pair<String, String[]> createWhereAndroid(String[] fieldNames,
                                                     Object[] values,
                                                     EncryptedModel em,
                                                     Persistable p){
        org.commcare.modern.util.Pair<String, String[]> mPair =
                DatabaseHelper.createWhere(fieldNames, values, em, p);
        return new Pair<>(mPair.first, mPair.second);
    }
    
    public PrototypeFactory getPrototypeFactory() {
        return DbUtil.getPrototypeFactory(c);
    }
}
