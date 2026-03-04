package org.commcare.models.encryption;

import com.google.firebase.perf.metrics.Trace;

import org.commcare.google.services.analytics.CCPerfMonitoring;
import org.commcare.util.LogTypes;
import org.commcare.utils.EncryptionKeyAndTransform;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * Methods for dealing with encrypted input/output.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class EncryptionIO {

    public static void encryptFile(String sourceFilePath, String destPath, SecretKeySpec symmetricKey) throws IOException {
        Trace trace = CCPerfMonitoring.INSTANCE.startTracing(CCPerfMonitoring.TRACE_FILE_ENCRYPTION_TIME);

        OutputStream os;
        FileInputStream is;
        os = createFileOutputStream(destPath, symmetricKey);
        is = new FileInputStream(sourceFilePath);
        int fileSize = is.available();
        StreamsUtil.writeFromInputToOutputNew(is, os);

        CCPerfMonitoring.INSTANCE.stopFileEncryptionTracing(trace, fileSize, sourceFilePath);
    }

    public static OutputStream createFileOutputStreamWithKeystore(
            String filePath,
            EncryptionKeyAndTransform encryptionKeyAndTransform
    ) throws FileNotFoundException {
        return createFileOutputStream(
                filePath,
                encryptionKeyAndTransform.getKey(),
                encryptionKeyAndTransform.getTransformation(),
                true
        );
    }

    public static OutputStream createFileOutputStream(
            String filename,
            Key symmetricKey
    ) throws FileNotFoundException {
        return createFileOutputStream(filename, symmetricKey, null, false);
    }

    private static OutputStream createFileOutputStream(String filename,
                                                      Key symmetricKey,
                                                      String transformation,
                                                      boolean isKeyFromAndroidKeyStore
    ) throws FileNotFoundException {
        final File path = new File(filename);
        FileOutputStream fos = new FileOutputStream(path);
        if (symmetricKey == null) {
            return fos;
        } else {
            try {
                Cipher cipher = Cipher.getInstance(Objects.requireNonNullElse(transformation, "AES"));
                cipher.init(Cipher.ENCRYPT_MODE, symmetricKey);
                byte[] iv;
                if (isKeyFromAndroidKeyStore) {
                    iv = cipher.getIV();
                    fos.write(iv.length);
                    fos.write(iv);
                }
                return new BufferedOutputStream(new CipherOutputStream(fos, cipher));

                //All of these exceptions imply a bad platform and should be irrecoverable (Don't ever
                //write out data if the key isn't good, or the crypto isn't available)
            } catch (InvalidKeyException e) {
                e.printStackTrace();
                Logger.log(LogTypes.TYPE_ERROR_CRYPTO, "Invalid key: " + e.getMessage());
                throw new RuntimeException(e.getMessage());
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                Logger.log(LogTypes.TYPE_ERROR_CRYPTO, "Unavailable Crypto algorithm: " + e.getMessage());
                throw new RuntimeException(e.getMessage());
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
                Logger.log(LogTypes.TYPE_ERROR_CRYPTO, "Bad Padding: " + e.getMessage());
                throw new RuntimeException(e.getMessage());
            } catch (IOException e) {
                Logger.log(LogTypes.TYPE_ERROR_CRYPTO, "Writing IV failed with message: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    public static InputStream getFileInputStream(String filepath,
                                                 SecretKeySpec symetricKey) throws FileNotFoundException {
        final File file = new File(filepath);
        InputStream is;
        try {
            is = new FileInputStream(file);
            if (symetricKey != null) {
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, symetricKey);
                is = new BufferedInputStream(new CipherInputStream(is, cipher));
            }

            //CTS - Removed a lot of weird checks  here. file size < max int? We're shoving this
            //form into a _Byte array_, I don't think there's a lot of concern than 2GB of data
            //are gonna sneak by.

            return is;

            //CTS - Removed the byte array length check here. Plenty of
            //files are smaller than their contents (padded encryption data, etc),
            //so you can't actually know that's correct. We should be relying on the
            //methods we use to read data to make sure it's all coming out.
        } catch (InvalidKeyException | NoSuchPaddingException
                | NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
