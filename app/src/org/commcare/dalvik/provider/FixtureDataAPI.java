package org.commcare.dalvik.provider;

import android.content.UriMatcher;
import android.net.Uri;
import android.provider.BaseColumns;

import org.commcare.dalvik.BuildConfig;

/**
 * This API provides the relevant interface cues for interacting with
 * the Fixture Data Content Provider.
 * 
 * @author wspride
 */
public class FixtureDataAPI {
    public static final String AUTHORITY = BuildConfig.CC_AUTHORITY + ".fixture";
    
    public static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    
    static {
        //Load the URI match patterns
        sURIMatcher.addURI(AUTHORITY, "fixturedb/*", MetadataColumns.MATCH_INSTANCE_ID);
        sURIMatcher.addURI(AUTHORITY, "fixturedb/", MetadataColumns.LIST_INSTANCE_ID);
    }
    
    /**
     * Determine which (if any) defined API tables are being referenced by the provided URI.
     * NOTE: The Match ID's are defined within the table definitions themselves.
     * 
     * @return The ID of the data model which is being referenced by the URI.
     */
    public static int UriMatch(Uri uri) {
        return sURIMatcher.match(uri);
    }
    
    
    /**
     * MetaData table for cases. Includes basic details like case type, ID, and name.
     * 
     * Can be queried for an individual case (NOTE: by case ID not by content provider ID)
     * or for all cases. 
     * 
     * Projections and Filtering are unsupported for this type
     * 
     * @author ctsims
     */
    public static final class MetadataColumns implements BaseColumns {
        // This class cannot be instantiated
        private MetadataColumns() {}
        
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/fixture_db");
        
        public static final int MATCH_ID = 1;
        public static final int MATCH_INSTANCE_ID = 2;
        public static final int LIST_INSTANCE_ID = 3;
        
        public static final String FIXTURE_ID = "instance_id";
        public static final String USER_ID = "user_id";
        
    }
}
