package org.commcare.android.logic;

public class GlobalConstants {
	
	public static final String FILE_CC_ROOT = "/commcare";
	public static final String FILE_CC_INSTALL = FILE_CC_ROOT + "/install";
	public static final String FILE_CC_UPGRADE = FILE_CC_ROOT + "/upgrade";
	public static final String FILE_CC_CACHE = FILE_CC_ROOT + "/cache";
	public static final String FILE_CC_MEDIA = FILE_CC_ROOT + "/media/";
	public static final String FILE_CC_LOGS = FILE_CC_ROOT + "/logs/";
	
	public static final String FILE_CC_FORMS = FILE_CC_ROOT + "/formdata/";
			
	//2012-10-10 - ctsims: We're going to stop moving our form data around like this, because
	//we lack a way to do it atomically. We still need to know that these exist for legacy purposes,
	//but we don't really want to use them for new code.
	public static final String FILE_CC_INCOMPLETE_OBSELETE = FILE_CC_ROOT + "/incomplete/";
	public static final String FILE_CC_SAVED_OBSELETE = FILE_CC_ROOT + "/saved/";
	public static final String FILE_CC_STORED_OBSELETE = FILE_CC_ROOT + "/stored/";
	public static final String FILE_CC_PROCESSED_OBSELETE = FILE_CC_ROOT + "/processed/";
	
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
    
    public static final String UPGRADE_REF = "jr://file/commcare/upgrade";
    

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
}
