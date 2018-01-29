package org.commcare.android.database.app.models;


import android.net.Uri;
import android.provider.BaseColumns;

public class AppDbContract {

    // This class cannot be instantiated
    private AppDbContract() {
    }

    public static abstract class FormsColumns implements BaseColumns {

        public static final String TABLE_NAME = "forms";

        // These are the only things needed for an insert
        public static final String COL_DISPLAY_NAME = "displayName";
        public static final String COL_DESCRIPTION = "description";  // can be null
        public static final String COL_JR_FORM_ID = "jrFormId";
        public static final String COL_FORM_FILE_PATH = "formFilePath";
        public static final String COL_SUBMISSION_URI = "submissionUri"; // can be null
        public static final String COL_BASE64_RSA_PUBLIC_KEY = "base64RsaPublicKey"; // can be null

        // these are generated for you (but you can insert something else if you want)
        public static final String COL_DISPLAY_SUBTEXT = "displaySubtext";
        public static final String COL_MD5_HASH = "md5Hash";
        public static final String COL_DATE = "date";
        public static final String COL_JRCACHE_FILE_PATH = "jrcacheFilePath";
        public static final String COL_FORM_MEDIA_PATH = "formMediaPath";

        // these are null unless you enter something and aren't currently used
        public static final String COL_MODEL_VERSION = "modelVersion";
        public static final String COL_UI_VERSION = "uiVersion";

        // this is null on create, and can only be set on an update.
        public static final String COL_LANGUAGE = "language";
    }

    public static abstract class InstanceColumns implements BaseColumns {

        public static final String TABLE_NAME = "instances";

        // These are the only things needed for an insert
        public static final String DISPLAY_NAME = "displayName";
        public static final String SUBMISSION_URI = "submissionUri";
        public static final String INSTANCE_FILE_PATH = "instanceFilePath";
        public static final String JR_FORM_ID = "jrFormId";

        // these are generated for you (but you can insert something else if you want)
        public static final String STATUS = "status";
        public static final String CAN_EDIT_WHEN_COMPLETE = "canEditWhenComplete";
        public static final String LAST_STATUS_CHANGE_DATE = "date";
        public static final String DISPLAY_SUBTEXT = "displaySubtext";
    }
}
