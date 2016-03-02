package org.commcare.models.encryption;

import org.commcare.logging.AndroidLogger;
import org.javarosa.core.services.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

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
    public static OutputStream createFileOutputStream(String filename,
                                                      SecretKeySpec symetricKey)
            throws FileNotFoundException {
        final File path = new File(filename);
        FileOutputStream fos = new FileOutputStream(path);
        if (symetricKey == null) {
            return fos;
        } else {
            try {
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.ENCRYPT_MODE, symetricKey);
                return new BufferedOutputStream(new CipherOutputStream(fos, cipher));

                //All of these exceptions imply a bad platform and should be irrecoverable (Don't ever
                //write out data if the key isn't good, or the crypto isn't available)
            } catch (InvalidKeyException e) {
                e.printStackTrace();
                Logger.log(AndroidLogger.TYPE_ERROR_CRYPTO, "Invalid key: " + e.getMessage());
                throw new RuntimeException(e.getMessage());
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                Logger.log(AndroidLogger.TYPE_ERROR_CRYPTO, "Unavailable Crypto algorithm: " + e.getMessage());
                throw new RuntimeException(e.getMessage());
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
                Logger.log(AndroidLogger.TYPE_ERROR_CRYPTO, "Bad Padding: " + e.getMessage());
                throw new RuntimeException(e.getMessage());
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
