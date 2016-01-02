package org.commcare.android.database;

import org.javarosa.core.services.storage.Persistable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class UnencryptedFileBackedSqlStorageMock<T extends Persistable> extends UnencryptedFileBackedSqlStorage<T> {
    private static FileBackedSqlStorageMock.BlobInDBTestLogic testLogicState;
    private static int maxSize = 1000000;

    public UnencryptedFileBackedSqlStorageMock(String table,
                                               Class<? extends T> ctype,
                                               AndroidDbHelper helper,
                                               String baseDir) {
        super(table, ctype, helper, baseDir);
    }

    public static void alwaysPutInDatabase() {
        testLogicState = FileBackedSqlStorageMock.BlobInDBTestLogic.AlwaysTrue;
    }

    public static void alwaysPutInFilesystem() {
        testLogicState = FileBackedSqlStorageMock.BlobInDBTestLogic.AlwaysFalse;
    }

    public static void placeUsingSize(int newMaxSize) {
        testLogicState = FileBackedSqlStorageMock.BlobInDBTestLogic.CheckSize;
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
