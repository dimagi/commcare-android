package org.commcare.android.database;

import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.storage.Persistable;

import java.io.ByteArrayOutputStream;

/**
 * Allows toggling how serialized objects are stored (database vs filesystem)
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class HybridFileBackedSqlStorageMock<T extends Persistable>
        extends HybridFileBackedSqlStorage<T> {
    private static boolean storeInFS = false;

    public HybridFileBackedSqlStorageMock(String table,
                                          Class<? extends T> ctype,
                                          AndroidDbHelper helper,
                                          String baseDir) {
        super(table, ctype, helper, baseDir, CommCareApplication._().getCurrentApp());
    }

    public static void alwaysPutInDatabase() {
        storeInFS = false;
    }

    public static void alwaysPutInFilesystem() {
        storeInFS = true;
    }

    @Override
    protected boolean blobFitsInDb(ByteArrayOutputStream bos) {
        return !storeInFS;
    }
}
