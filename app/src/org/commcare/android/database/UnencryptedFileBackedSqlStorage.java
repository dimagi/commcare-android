package org.commcare.android.database;

import android.content.ContentValues;

import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.Externalizable;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Sql logic for storing persistable objects. Uses the filesystem to store
 * persitables in _unencrypted_ manner; useful when objects are larger than the
 * 1mb sql row limit.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class UnencryptedFileBackedSqlStorage<T extends Persistable>
        extends FileBackedSqlStorage<T> {
    public UnencryptedFileBackedSqlStorage(String table,
                                           Class<? extends T> ctype,
                                           AndroidDbHelper helper,
                                           String baseDir) {
        super(table, ctype, helper, baseDir);
    }

    @Override
    protected InputStream getInputStreamFromFile(String filename, byte[] aesKeyBytes) {
        try {
            return new BufferedInputStream(new FileInputStream(filename));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    protected void writeExternalizableToFile(Externalizable externalizable,
                                             String filename,
                                             byte[] aesKeyBytes) throws IOException {
        DataOutputStream objectOutStream = null;
        try {
            objectOutStream =
                    new DataOutputStream(new FileOutputStream(filename));
            externalizable.writeExternal(objectOutStream);
        } finally {
            if (objectOutStream != null) {
                objectOutStream.close();
            }
        }
    }

    @Override
    protected byte[] generateKey(ContentValues contentValues) {
        return null;
    }

    @Override
    protected byte[] getEntryAESKey(int id) {
        return null;
    }
}
