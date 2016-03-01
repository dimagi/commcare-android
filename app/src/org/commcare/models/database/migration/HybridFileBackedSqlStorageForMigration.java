package org.commcare.models.database.migration;

import android.content.ContentValues;

import org.commcare.CommCareApplication;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.HybridFileBackedSqlStorage;
import org.commcare.models.encryption.CryptUtil;
import org.commcare.modern.database.DatabaseHelper;
import org.javarosa.core.services.storage.Persistable;

/**
 * File backed storage for use before session w/ key have been setup:
 * when performing user db migrations
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class HybridFileBackedSqlStorageForMigration<T extends Persistable> extends HybridFileBackedSqlStorage<T> {
    private final byte[] fileKeySeed;

    public HybridFileBackedSqlStorageForMigration(String table,
                                                  Class<? extends T> ctype,
                                                  AndroidDbHelper helper,
                                                  String baseDir,
                                                  byte[] fileKeySeed) {
        super(table, ctype, helper, baseDir, CommCareApplication._().getCurrentApp());

        this.fileKeySeed = fileKeySeed;
    }

    @Override
    protected byte[] generateKeyAndAdd(ContentValues contentValues) {
        byte[] key = CryptUtil.generateSymmetricKey(CryptUtil.uniqueSeedFromSecureStatic(fileKeySeed)).getEncoded();
        contentValues.put(DatabaseHelper.AES_COL, key);
        return key;
    }
}
