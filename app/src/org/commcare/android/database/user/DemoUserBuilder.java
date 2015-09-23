package org.commcare.android.database.user;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.DirectDbHelper;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.user.models.User;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.util.PropertyUtils;

import java.util.Date;

import javax.crypto.SecretKey;

/**
 * Demo KeyRecord and User creation
 *
 * @author ctsims
 */
public class DemoUserBuilder {
    public static final String DEMO_USERNAME = "demo_user";
    public static final String DEMO_PASSWORD = "demo_user";

    private final Context context;
    private final SqlStorage<UserKeyRecord> keyRecordDB;
    private final String username;
    private final String password;
    private String passwordHash;
    private byte[] randomKey;

    private DemoUserBuilder(Context context, CommCareApp ccApp, String username, String password) {
        this.context = context;
        this.keyRecordDB = ccApp.getStorage(UserKeyRecord.class);
        this.username = username;
        this.password = password;
    }

    public static synchronized void build(Context context, CommCareApp ccApp) {
        (new DemoUserBuilder(context, ccApp, DEMO_USERNAME, DEMO_PASSWORD)).createAndWriteKeyRecordAndUser();
    }

    public static synchronized void buildTestUser(Context context, CommCareApp ccApp, String username, String password) {
        (new DemoUserBuilder(context, ccApp, username, password)).createAndWriteKeyRecordAndUser();
    }

    private void createAndWriteKeyRecordAndUser() {
        int userCount = keyRecordDB.getIDsForValue(UserKeyRecord.META_USERNAME, username).size();

        if (userCount == 0) {
            SecretKey secretKey = CryptUtil.generateSemiRandomKey();
            if (secretKey == null) {
                throw new RuntimeException("Error setting up user's encrypted storage");
            }
            randomKey = secretKey.getEncoded();
            passwordHash = UserKeyRecord.generatePwdHash(password);

            UserKeyRecord keyRecord = writeNewKeyRecord();
            writeNewUser(keyRecord);
        }
    }

    private UserKeyRecord writeNewKeyRecord() {
        byte[] encryptedKey = CryptUtil.wrapKey(randomKey, password);

        UserKeyRecord keyRecord =
                new UserKeyRecord(username, passwordHash, encryptedKey,
                        new Date(0), new Date(Long.MAX_VALUE - 1),
                        PropertyUtils.genUUID().replace("-", ""));

        keyRecordDB.write(keyRecord);

        return keyRecord;
    }

    private void writeNewUser(UserKeyRecord keyRecord) {
        SQLiteDatabase userDatabase = null;
        try {
            userDatabase = new CommCareUserOpenHelper(CommCareApplication._(),
                    keyRecord.getUuid()).getWritableDatabase(UserSandboxUtils.getSqlCipherEncodedKey(randomKey));

            User user = new User(username, passwordHash, username, User.TYPE_DEMO);

            SqlStorage<User> userStorage =
                    new SqlStorage<>(User.STORAGE_KEY, User.class, new DirectDbHelper(context, userDatabase));
            userStorage.write(user);
        } finally {
            if (userDatabase != null) {
                userDatabase.close();
            }
        }
    }
}
