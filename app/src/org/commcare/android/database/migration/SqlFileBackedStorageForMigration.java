package org.commcare.android.database.migration;

import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.AndroidDbHelper;
import org.commcare.android.database.SqlFileBackedStorage;
import org.javarosa.core.services.storage.Persistable;

import javax.crypto.SecretKey;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SqlFileBackedStorageForMigration<T extends Persistable> extends SqlFileBackedStorage<T> {
    private final byte[] fileKeySeed;
    public SqlFileBackedStorageForMigration(String table,
                                Class<? extends T> ctype,
                                AndroidDbHelper helper,
                                String baseDir,
                                byte[] fileKeySeed) {

        super(table, ctype, helper, baseDir);

        this.fileKeySeed = fileKeySeed;
    }

    @Override
    protected SecretKey generateKey() {
        return CryptUtil.generateSymetricKey(CryptUtil.uniqueSeedFromSecureStatic(fileKeySeed));
    }
}
