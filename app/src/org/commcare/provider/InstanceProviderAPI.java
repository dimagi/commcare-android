package org.commcare.provider;

import android.net.Uri;
import android.provider.BaseColumns;

import org.commcare.dalvik.BuildConfig;

public final class InstanceProviderAPI {
    public static final String AUTHORITY = BuildConfig.ODK_AUTHORITY + ".instances";

    // This class cannot be instantiated
    private InstanceProviderAPI() {
    }

    // status for instances
    public static final String STATUS_INCOMPLETE = "incomplete";
    public static final String STATUS_COMPLETE = "complete";
    public static final String STATUS_SUBMITTED = "submitted";
    public static final String STATUS_SUBMISSION_FAILED = "submissionFailed";

    /**
     * Used to signal an instance insertion shouldn't be attached to the
     * session's form record, but rather create a new record to register with.
     */
    public static final String UNINDEXED_SUBMISSION = "unindexedSubmission";

    /**
     * If a user sandbox needs to be migrated, instances will be moved by
     * inserting them into the new sandbox and updating existing form records
     * to point to the newly inserted data.
     */
    public static final String SANDBOX_MIGRATION_SUBMISSION =
            "sandboxMigrationSubmission";

    public static final class InstanceColumns implements BaseColumns {
        // This class cannot be instantiated
        private InstanceColumns() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/instances");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.odk.instance";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.odk.instance";

        // These are the only things needed for an insert
        public static final String DISPLAY_NAME = "displayName";
        public static final String SUBMISSION_URI = "submissionUri";
        public static final String INSTANCE_FILE_PATH = "instanceFilePath";
        public static final String JR_FORM_ID = "jrFormId";
        //public static final String FORM_ID = "formId";

        // these are generated for you (but you can insert something else if you want)
        public static final String STATUS = "status";
        public static final String CAN_EDIT_WHEN_COMPLETE = "canEditWhenComplete";
        public static final String LAST_STATUS_CHANGE_DATE = "date";
        public static final String DISPLAY_SUBTEXT = "displaySubtext";
        //public static final String DISPLAY_SUB_SUBTEXT = "displaySubSubtext";
    }
}
