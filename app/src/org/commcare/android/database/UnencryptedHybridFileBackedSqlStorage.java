package org.commcare.android.database;

import android.content.ContentValues;

import org.javarosa.core.services.storage.Persistable;

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
public class UnencryptedHybridFileBackedSqlStorage<T extends Persistable>
        extends HybridFileBackedSqlStorage<T> {
    public UnencryptedHybridFileBackedSqlStorage(String table,
                                                 Class<? extends T> ctype,
                                                 AndroidDbHelper helper) {
        super(table, ctype, helper, "app_level");
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
    protected byte[] generateKeyAndAdd(ContentValues contentValues) {
        return null;
    }

    @Override
    protected DataOutputStream getOutputFileStream(String filename, byte[] aesKeyBytes) throws IOException {
        return new DataOutputStream(new FileOutputStream(filename));
    }
}
