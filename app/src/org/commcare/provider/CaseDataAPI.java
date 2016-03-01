package org.commcare.provider;

import android.content.UriMatcher;
import android.net.Uri;
import android.provider.BaseColumns;

import org.commcare.dalvik.BuildConfig;

/**
 * This API provides the relevant interface cues for interacting with
 * the Case Data Content Provider, along with the structure of the
 * virtual tables used by the provider.
 *
 * @author ctsims
 */
class CaseDataAPI {
    private static final String AUTHORITY = BuildConfig.CC_AUTHORITY + ".case";

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        //Load the URI match patterns
        sURIMatcher.addURI(AUTHORITY, "casedb/case", MetadataColumns.MATCH_CASES);
        sURIMatcher.addURI(AUTHORITY, "casedb/case/*", MetadataColumns.MATCH_CASE);
        sURIMatcher.addURI(AUTHORITY, "casedb/data/*", DataColumns.MATCH_DATA);
        sURIMatcher.addURI(AUTHORITY, "casedb/index/*", IndexColumns.MATCH_INDEX);
        sURIMatcher.addURI(AUTHORITY, "casedb/attachment/*", AttachmentColumns.MATCH_ATTACHMENTS);
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
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.commcare.case";
        public static final String CONTENT_TYPE_ITEM = "vnd.android.cursor.item/vnd.commcare.case";

        // This class cannot be instantiated
        private MetadataColumns() {
        }

        public static final String CASE_ID = "case_id";
        public static final String CASE_TYPE = "case_type";
        public static final String OWNER_ID = "owner_ID";
        public static final String STATUS = "status";
        public static final String CASE_NAME = "case_name";
        public static final String DATE_OPENED = "date_opened";
        public static final String LAST_MODIFIED = "last_modified";


        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/casedb/case");

        public static final int MATCH_CASES = 1;
        public static final int MATCH_CASE = 2;
    }


    /**
     * Queries the data columns associated with a case. IE: The dynamic key/value pairs for a specific
     * case. Can only be queried for a specific case using the string case id in the request URI.
     *
     * Projections and Filtering are unsupported for this type
     *
     * @author ctsims
     */
    public static final class DataColumns implements BaseColumns {

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.commcare.case.data";

        // This class cannot be instantiated
        private DataColumns() {
        }

        public static final String CASE_ID = "case_id";
        public static final String DATUM_ID = "datum_id";
        public static final String VALUE = "value";

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/casedb/data");

        public static final int MATCH_DATA = 3;
    }

    /**
     * Queries the indices associated with a case, which are named links between cases.
     *
     * Can only be queried for a specific case using the string case id in the request URI.
     *
     * Projections and Filtering are unsupported for this type
     *
     * This type is unimplemented.
     *
     * @author ctsims
     */
    public static final class IndexColumns implements BaseColumns {
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.commcare.case.index";

        // This class cannot be instantiated
        private IndexColumns() {
        }


        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/casedb/index");

        public static final int MATCH_INDEX = 4;
    }

    /**
     * Queries the attachments associated with a case, which are blobs of binary data that
     * are stored along with cases.
     *
     * Can only be queried for a specific case using the string case id in the request URI.
     *
     * Projections and Filtering are unsupported for this type
     *
     * This type is unimplemented.
     *
     * @author ctsims
     */

    public static final class AttachmentColumns implements BaseColumns {
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.commcare.case.attachment";

        // This class cannot be instantiated
        private AttachmentColumns() {
        }


        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/casedb/attachment");

        public static final int MATCH_ATTACHMENTS = 5;
    }
}
