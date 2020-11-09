package org.commcare.commcaresupportlibrary;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

import static org.commcare.commcaresupportlibrary.Constants.FIXTURE_DB_BASE_URI;

/**
 * Created by willpride on 3/27/18.
 */

public class FixtureUtils {

    /**
     * Provides a listing of the names and IDs of all the fixtures in the system.
     content://org.commcare.dalvik.fixture/fixturedb/

     Response content
     column	required
     id	yes
     instance_id	yes

     * @param context Android Context
     * @return a Cursor over a list of fixtures
     */

    public static Cursor getFixtureList(Context context) {
        Uri tableUri = Uri.parse(FIXTURE_DB_BASE_URI);
        return context.getContentResolver().query(tableUri, null, null, null, null);
    }

    public static List<String> getFixtureIdList(Context context) {
        Cursor cursor = getFixtureList(context);
        List<String> fixtureList = new ArrayList<>();
        while (cursor.moveToNext()) {
            fixtureList.add(cursor.getString(cursor.getColumnIndex("instance_id")));
        }
        return fixtureList;
    }

    /**
     *
     Returns the raw XML for a fixture with the given instance ID.

     content://org.commcare.dalvik.fixture/fixturedb/[FIXTURE_ID]
     column	required
     id	yes
     instance_id	yes
     content	yes
     Note that the value returned is the full body of the attachment,
     which means this API is only viable for communicating with attachments that are not particularly large,
     depending on the amount of memory on the device.
     * @param context Android Context
     * @param fixtureId the ID of the fixture to retrieve
     * @return a Cursor over the fixture data
     */

    public static Cursor getFixtureData(Context context, String fixtureId) {
        Uri tableUri = Uri.parse(FIXTURE_DB_BASE_URI + fixtureId);
        return context.getContentResolver().query(tableUri, null, null, null, null);
    }

    public static String getFixtureXml(Context context, String fixtureId) {
        Cursor cursor = getFixtureData(context, fixtureId);
        if (cursor.moveToFirst()) {
            return cursor.getString(cursor.getColumnIndex("content"));
        } else {
            return null;
        }
    }
}
