package org.commcare.android.util;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.DirectDbHelper;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.CommCareUserOpenHelper;
import org.commcare.android.database.user.UserSandboxUtils;
import org.commcare.android.database.user.models.User;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.util.PropertyUtils;

import java.util.Date;

/**
 * Demo KeyRecord and User creation
 *
 * @author ctsims
 */
public class DemoUserUtil {
    public static final String DEMO_USERNAME = "demo_user";
    public static final String DEMO_PASSWORD = "demo_user";

    public static synchronized void checkOrCreateDemoUser(Context c, CommCareApp currentApp) {
        SqlStorage<UserKeyRecord> keys = currentApp.getStorage(UserKeyRecord.class);
        int demoUsers = keys.getIDsForValue(UserKeyRecord.META_USERNAME, DEMO_USERNAME).size();
        
        if (demoUsers  == 0) {
            byte[] newRandomKey = CryptUtil.generateSemiRandomKey().getEncoded();
            
            String duserHash = UserKeyRecord.generatePwdHash(DEMO_PASSWORD);
            
            //Create us a demo user sandbox
            //TODO: D'oh, looks like keys with no expiry arent' working, just make it inestimably long instead. 
            UserKeyRecord keyRecord = new UserKeyRecord(DEMO_USERNAME, duserHash,
                                                        CryptUtil.wrapKey(newRandomKey, DEMO_PASSWORD),
                                                        new Date(0), new Date(Long.MAX_VALUE - 1), PropertyUtils.genUUID().replace("-",""));
            keys.write(keyRecord);
            
            //Ok, so we have a demo user record created, but we also need a user to put in that database
            SQLiteDatabase userDatabase = null;
            try {
                userDatabase = new CommCareUserOpenHelper(CommCareApplication._(), keyRecord.getUuid()).getWritableDatabase(UserSandboxUtils.getSqlCipherEncodedKey(newRandomKey));
                
                //Now we need an arbitrary user record
                User demoUser = new User(DEMO_USERNAME, duserHash, DEMO_USERNAME, User.TYPE_DEMO);
                
                SqlStorage<User> userStorage = new SqlStorage<>(User.STORAGE_KEY, User.class, new DirectDbHelper(c, userDatabase));
                userStorage.write(demoUser);
            } finally {
                if(userDatabase != null) {
                    userDatabase.close();
                }
            }
        }
    }

}
