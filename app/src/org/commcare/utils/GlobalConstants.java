package org.commcare.utils;

import org.commcare.dalvik.BuildConfig;

public class GlobalConstants {

    public static final String FILE_CC_INSTALL = "commcare/install";
    public static final String FILE_CC_UPGRADE = "commcare/upgrade/sandbox/";
    public static final String FILE_CC_CACHE = "commcare/cache";
    public static final String FILE_CC_MEDIA = "commcare/media/";
    public static final String FILE_CC_LOGS = "commcare/logs/";

    public static final String FILE_CC_ATTACHMENTS = "attachments/";

    public static final String FILE_CC_FORMS = "formdata/";

    /**
     * Root file directory for storing serialized objects for the file backed
     * sql storage layer (currently used for fixtures).
     */
    public static final String FILE_CC_DB = "file_db/";

    public static final String CC_DB_NAME = "commcare";

    /**
     * Resource storage path
     */
    public static final String RESOURCE_PATH = "jr://file/commcare/resources/";

    /**
     * Media storage path
     */
    public static final String MEDIA_REF = "jr://file/commcare/media/";

    /**
     * Cache storage path
     */
    public static final String CACHE_PATH = "jr://file/commcare/cache/";

    public static final String INSTALL_REF = "jr://file/commcare/install";

    public static final String UPGRADE_REF = "jr://file/commcare/upgrade/sandbox";

    public static final String ATTACHMENT_REF = "jr://file/attachments/";


    /**
     * How long to wait when opening network connection in milliseconds
     */
    public static final int CONNECTION_TIMEOUT = 2 * 60 * 1000;

    /**
     * How long to wait when receiving data (in milliseconds)
     */
    public static final int CONNECTION_SO_TIMEOUT = 1 * 60 * 1000;


    //All of the app state is contained in these values
    public static final String STATE_USER_KEY = "COMMCARE_USER";
    public static final String STATE_USER_LOGIN = "USER_LOGIN";

    public static final String TRUSTED_SOURCE_PUBLIC_KEY = BuildConfig.TRUSTED_SOURCE_PUBLIC_KEY;

    public static final String SMS_INSTALL_KEY_STRING = "[commcare app - do not delete]";
}
