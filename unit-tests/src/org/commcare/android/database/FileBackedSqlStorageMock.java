package org.commcare.android.database;

import org.javarosa.core.services.storage.Persistable;

import java.io.ByteArrayOutputStream;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class FileBackedSqlStorageMock<T extends Persistable>
        extends FileBackedSqlStorage<T> {
    protected enum BlobInDBTestLogic {AlwaysFalse, AlwaysTrue, CheckSize}
    private static BlobInDBTestLogic testLogicState;
    private static int maxSize = 1000000;

    public FileBackedSqlStorageMock(String table,
                                    Class<? extends T> ctype,
                                    AndroidDbHelper helper,
                                    String baseDir) {
        super(table, ctype, helper, baseDir);
    }

    public static void alwaysPutInDatabase() {
        testLogicState = BlobInDBTestLogic.AlwaysTrue;
    }

    public static void alwaysPutInFilesystem() {
        testLogicState = BlobInDBTestLogic.AlwaysFalse;
    }

    public static void placeUsingSize(int newMaxSize) {
        testLogicState = BlobInDBTestLogic.CheckSize;
        maxSize = newMaxSize;
    }

    @Override
    protected boolean blobFitsInDb(ByteArrayOutputStream bos) {
        switch (testLogicState) {
            case AlwaysFalse:
                return false;
            case AlwaysTrue:
                return true;
            case CheckSize:
                return bos.size() < maxSize;
        }
        return false;
    }
}
