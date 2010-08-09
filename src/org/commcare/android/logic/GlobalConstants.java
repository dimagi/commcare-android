package org.commcare.android.logic;

public class GlobalConstants {
	
	public static final String FILE_REF_ROOT = "sdcard/";
	
	public static final String FILE_CC_ROOT = "/" + FILE_REF_ROOT + "commcare";
	public static final String FILE_CC_INSTALL = FILE_CC_ROOT + "/install";
	public static final String FILE_CC_UPGRADE = FILE_CC_ROOT + "/upgrade";
	public static final String FILE_CC_CACHE = FILE_CC_ROOT + "/cache";
	public static final String FILE_CC_INCOMPLETE = FILE_CC_ROOT + "/incomplete/";
	public static final String FILE_CC_SAVED = FILE_CC_ROOT + "/saved/";
	public static final String FILE_CC_PROCESSED = FILE_CC_ROOT + "/processed/";
	
	
	public static final String DB_LOCATION = "/sdcard/commcare/commcare.sqlite";
	public static final String CC_DB_NAME = "commcare";
	
    /**
     * Resource storage path
     */
    public static final String RESOURCE_PATH = "jr://file/commcare/resources/";
    
    /**
     * Cache storage path
     */
    public static final String CACHE_PATH = "jr://file/commcare/cache/";
    
    public static final String INSTALL_REF = "jr://file/commcare/install";
    
    public static final String COMMCARE_PLATFORM = "COMMCARE_PLATFORM";
    

    /**
     * How long to wait when opening network connection in milliseconds
     */
    public static final int CONNECTION_TIMEOUT = 30000;

    
    //All of the app state is contained in these values
    public static final String STATE_USER_KEY = "COMMCARE_USER";
    public static final String STATE_USER_LOGIN = "USER_LOGIN";
    public static final String STATE_COMMAND_ID = "COMMAND_ID";
    public static final String STATE_MENU_ID = "MENU_ID";
    public static final String STATE_CASE_ID = "CASE_ID";
    public static final String STATE_REFERRAL_ID = "REFERRAL_ID";
    public static final String STATE_FORM_XMLNS = "FORM_XMLNS";
}
