/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.utilities;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class UrlUtils {

    public static boolean isValidUrl(String url) {

        try {
            new URL(URLDecoder.decode(url, "utf-8"));
            return true;
        } catch (MalformedURLException e) {
            return false;
        } catch (UnsupportedEncodingException e) {
            return false;
        }

    }
    
    public static String getPathFromUri(Uri uri, Context context) {
        if (uri.toString().startsWith("file")) {
            return uri.toString().substring(6);
        } else {
            // find entry in content provider
            String colString = null;
            Cursor c = null;
            try {
                c = context.getContentResolver().query(uri, null, null, null, null);
                c.moveToFirst();

                // get data path
                colString = c.getString(c.getColumnIndex("_data"));
            } finally {
                if ( c != null ) {
                    c.close();
                }
            }
            return colString;
        }
    }

}
