package org.odk.collect.android.utilities;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class UrlUtils {

    public static String getPathFromUri(Uri uri, Context context) {
        if (uri.toString().startsWith("file")) {
            return uri.toString().substring(6);
        } else {
            // find entry in content provider
            String colString = null;
            Cursor c = null;
            try {
                c = context.getContentResolver().query(uri, null, null, null, null);
                if (c != null) {
                    c.moveToFirst();

                    // get data path
                    colString = c.getString(c.getColumnIndex("_data"));
                }
            } finally {
                if ( c != null ) {
                    c.close();
                }
            }
            return colString;
        }
    }
}
