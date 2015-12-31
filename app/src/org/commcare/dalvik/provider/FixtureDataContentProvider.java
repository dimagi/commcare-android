package org.commcare.dalvik.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.commcare.android.util.AndroidInstanceInitializer;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.instance.DataInstance;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.model.xform.DataModelSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * The fixture content provider defines the interface for external applications
 * to gain read only access to the current user's sandbox. External applications require
 * explicit permissions to access the content provider, and only data in the currently
 * logged in user's sandbox is provided.
 * 
 * The FixtureDataAPI class is responsible for defining the data models and structures that
 * can be queried with the content provider.
 * 
 * Note that this content provider requires a permission to be granted for read access.
 * 
 * No write access is currently supported.
 * 
 * @author wspride
 *
 */
public class FixtureDataContentProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        //Standard dispatcher following Android best practices
        int match = FixtureDataAPI.UriMatch(uri);

        switch(match) {
        case FixtureDataAPI.MetadataColumns.LIST_INSTANCE_ID:
            return getFixtureNames();
        case FixtureDataAPI.MetadataColumns.MATCH_INSTANCE_ID:
            return getFixtureForId(uri.getLastPathSegment());
        }
        throw new IllegalArgumentException("URI: " + uri.toString() +" is not a valid content path for CommCare Case Data");
    }

    @Override
    public String getType(@NonNull Uri uri) {

        int match = FixtureDataAPI.UriMatch(uri);

        switch(match) {
        case FixtureDataAPI.MetadataColumns.MATCH_ID:
            return FixtureDataAPI.MetadataColumns.FIXTURE_ID;
        case FixtureDataAPI.MetadataColumns.MATCH_INSTANCE_ID:
            return FixtureDataAPI.MetadataColumns.USER_ID;
        }

        return null;
    }
    
    /*
     * Return a cursor over the list of all fixture IDs and names
     */

    public Cursor getFixtureNames(){

        MatrixCursor retCursor = new MatrixCursor(new String[] {FixtureDataAPI.MetadataColumns._ID, FixtureDataAPI.MetadataColumns.FIXTURE_ID});

        IStorageUtilityIndexed<FormInstance> userFixtureStorage = CommCareApplication._().getUserStorage("fixture", FormInstance.class);

        for(IStorageIterator<FormInstance> userFixtures = userFixtureStorage.iterate(); userFixtures.hasMore(); ) {
            FormInstance fi = userFixtures.nextRecord();
            String instanceId = fi.getInstanceId();
            retCursor.addRow(new Object[] {fi.getID(), instanceId});
        }

        return retCursor;

    }
    
    /*
     * Return a cursor to the fixture associated with this id
     */
    
    public Cursor getFixtureForId(String instanceId){

        MatrixCursor retCursor = new MatrixCursor(new String[] {FixtureDataAPI.MetadataColumns._ID, FixtureDataAPI.MetadataColumns.FIXTURE_ID, "content"});

        IStorageUtilityIndexed<FormInstance> userFixtureStorage = CommCareApplication._().getUserStorage("fixture", FormInstance.class);

        for(IStorageIterator<FormInstance> userFixtures = userFixtureStorage.iterate(); userFixtures.hasMore(); ) {

            try { 
                FormInstance fi = userFixtures.nextRecord();

                String currentInstanceId = fi.getInstanceId();
                
                if(instanceId.equals(currentInstanceId)){
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();

                    DataModelSerializer s = new DataModelSerializer(bos, new AndroidInstanceInitializer(null));

                    s.serialize(fi, fi.getRoot().getRef());

                    String dump = new String(bos.toByteArray());

                    retCursor.addRow(new Object[]{ fi.getID(), fi.getInstanceId(), dump});   
                }
            } 
            catch(IOException e){
                e.printStackTrace();
            }

        }

        return retCursor;

    }

    /** All of the below are invalid due to the read-only nature of the content provider. It's not 100% clear from spec how to express
     * the read-only-ness. **/

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // Case content provider is read only.
        //TODO: Throw an exception here? Read up on the spec 
        return 0;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        // Case content provider is read only.
        return 0;
    }


    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        return null;
    }
}
