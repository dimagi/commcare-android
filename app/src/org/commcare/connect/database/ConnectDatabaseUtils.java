package org.commcare.connect.database;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.util.Base64;
import org.commcare.util.Base64DecoderException;
import org.commcare.util.EncryptionUtils;
import org.commcare.utils.CrashUtil;
import org.commcare.utils.EncryptionKeyAndTransform;
import org.commcare.utils.EncryptionKeyProvider;
import org.javarosa.core.services.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Vector;

public class ConnectDatabaseUtils {
    // the value of the key should not be renamed due to backward compatibility
    private static final String SECRET_NAME = "secret";
    public static void storeConnectDbPassphrase(@NotNull Context context, byte[] passphrase, boolean isLocal) {
        try {
            if (passphrase == null || passphrase.length == 0) {
                throw new IllegalArgumentException("Passphrase must not be null or empty");
            }

            EncryptionKeyProvider encryptionKeyProvider = new EncryptionKeyProvider(context, false, SECRET_NAME);
            EncryptionKeyAndTransform keyAndTransform = encryptionKeyProvider.getKeyForEncryption();
            String encoded = EncryptionUtils.encrypt(passphrase, keyAndTransform.getKey(),
                    keyAndTransform.getTransformation(), true);

            ConnectKeyRecord record = getKeyRecord(isLocal);
            if (record == null) {
                record = new ConnectKeyRecord(encoded, isLocal);
            } else {
                record.setEncryptedPassphrase(encoded);
            }

            CommCareApplication.instance().getGlobalStorage(ConnectKeyRecord.class).write(record);
        } catch (EncryptionUtils.EncryptionException e) {
            throw new RuntimeException(e);
        }
    }

    public static ConnectKeyRecord getKeyRecord(boolean local) {
        Vector<ConnectKeyRecord> records = CommCareApplication.instance()
                .getGlobalStorage(ConnectKeyRecord.class)
                .getRecordsForValue(ConnectKeyRecord.IS_LOCAL, local);

        return records.size() > 0 ? records.firstElement() : null;
    }

    public static void storeConnectDbPassphrase(Context context, String base64EncodedPassphrase, boolean isLocal) {
        try {
            byte[] bytes = Base64.decode(base64EncodedPassphrase);
            storeConnectDbPassphrase(context, bytes, isLocal);
        } catch (Base64DecoderException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getConnectDbEncodedPassphrase(Context context, boolean isLocal) {
        try {
            byte[] passBytes = getConnectDbPassphrase(context, isLocal);
            if (passBytes != null) {
                return Base64.encode(passBytes);
            }
        } catch (Exception e) {
            Logger.exception("Getting DB passphrase", e);
        }

        return null;
    }

    public static byte[] getConnectDbPassphrase(Context context, boolean isLocal) {
        try {
            ConnectKeyRecord record = ConnectDatabaseUtils.getKeyRecord(isLocal);
            if (record == null) {
                return null;
            }

            byte[] encrypted = Base64.decode(record.getEncryptedPassphrase());

            EncryptionKeyProvider encryptionKeyProvider = new EncryptionKeyProvider(context, false,
                    SECRET_NAME);
            EncryptionKeyAndTransform keyAndTransform = encryptionKeyProvider.getKeyForDecryption();
            return EncryptionUtils.decrypt(encrypted, keyAndTransform.getKey(),
                    keyAndTransform.getTransformation(), true);
        } catch (Base64DecoderException | EncryptionUtils.EncryptionException e) {
            throw new RuntimeException(e);
        }
    }
}
