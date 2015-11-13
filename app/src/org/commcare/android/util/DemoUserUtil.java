/**
 * 
 */
package org.commcare.android.util;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.DirectAndroidDbHelper;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.CommCareUserOpenHelper;
import org.commcare.android.database.user.UserSandboxUtils;
import org.javarosa.core.model.User;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.util.PropertyUtils;

import java.util.Date;

/**
 * Placeholders for demo user stuff.
 * 
 * @author ctsims
 *
 */
public class DemoUserUtil {
    public static final String DEMO_USER = "demo_user";

    public static synchronized void checkOrCreateDemoUser(Context c, CommCareApp currentApp) {
        SqlStorage<UserKeyRecord> keys = currentApp.getStorage(UserKeyRecord.class);
        int demoUsers = keys.getIDsForValue(UserKeyRecord.META_USERNAME, DEMO_USER).size();
        
        if(demoUsers > 1) {
            //There should _not_ be more than one demo user record here, we should probably
            //delete the sandbox
        } else if(demoUsers  == 0) {
            
            byte[] newRandomKey = CryptUtil.generateSemiRandomKey().getEncoded();
            
            String duserHash = UserKeyRecord.generatePwdHash(DEMO_USER);
            
            //Create us a demo user sandbox
            //TODO: D'oh, looks like keys with no expiry arent' working, just make it inestimably long instead. 
            UserKeyRecord keyRecord = new UserKeyRecord(DEMO_USER, duserHash, 
                                                        CryptUtil.wrapKey(newRandomKey, DEMO_USER),
                                                        new Date(0), new Date(Long.MAX_VALUE - 1), PropertyUtils.genUUID().replace("-",""));
            keys.write(keyRecord);
            
            //Ok, so we have a demo user record created, but we also need a user to put in that database
            SQLiteDatabase userDatabase = null;
            try {
                userDatabase = new CommCareUserOpenHelper(CommCareApplication._(), keyRecord.getUuid()).getWritableDatabase(UserSandboxUtils.getSqlCipherEncodedKey(newRandomKey));
                
                //Now we need an arbitrary user record
                User demoUser = new User(DEMO_USER, duserHash, DEMO_USER, User.TYPE_DEMO);
                
                SqlStorage<User> userStorage = new SqlStorage<User>(User.STORAGE_KEY, User.class, new DirectAndroidDbHelper(c, userDatabase));
                userStorage.write(demoUser);
                
                //TODO: Demo fixtures?
            } finally {
                if(userDatabase != null) {
                    userDatabase.close();
                }
            }
        }
    }

}
