package org.commcare.utils;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.util.Base64;
import org.commcare.util.Base64DecoderException;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;

/**
 * Utility class for encryption functionality.
 * Usages include:
 * -Generating/storing/retrieving an encrypted, base64-encoded passphrase for the Connect DB
 * -Encrypting submissions during the SaveToDiskTask.
 *
 * @author mitchellsundt@gmail.com
 */

public class EncryptionUtils {

    private static final int PASSPHRASE_LENGTH = 32;

    //Generate a random passphrase
    public static byte[] generatePassphrase() {
        Random random;
        try {
            //Use SecureRandom if possible (specifying algorithm for older versions of Android)
            random = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O ?
                    SecureRandom.getInstanceStrong() : SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            //Fallback to basic Random
            random = new Random();
        }

        byte[] result = new byte[PASSPHRASE_LENGTH];
        int maxAttempts = 100; // Safety limit
        int attempts = 0;

        while (true) {
            random.nextBytes(result);

            //Make sure there are no zeroes in the passphrase
            //SQLCipher passphrases must not contain any zero byte-values
            //For more, see "Creating the Passphrase" section here:
            //https://commonsware.com/Room/pages/chap-passphrase-001.html
            boolean containsZero = false;
            for (byte b : result) {
                if (b == 0) {
                    containsZero = true;
                    break;
                }
            }

            if (!containsZero || ++attempts >= maxAttempts) {
                break;
            }
        }

            if (attempts >= maxAttempts) {
                throw new IllegalStateException("Failed to generate a passphrase without zeros after " + maxAttempts + " attempts");
        }

        return result;
    }

    public static byte[] encrypt(byte[] bytes, EncryptionKeyAndTransform keyAndTransform)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException,
            UnrecoverableEntryException, CertificateException, KeyStoreException, IOException,
            NoSuchProviderException {
        Cipher cipher = Cipher.getInstance(keyAndTransform.getTransformation());
        cipher.init(Cipher.ENCRYPT_MODE, keyAndTransform.getKey());
        byte[] encrypted = cipher.doFinal(bytes);
        byte[] iv = cipher.getIV();
        int ivLength = iv == null ? 0 : iv.length;

        byte[] output = new byte[encrypted.length + ivLength + 3];
        int writeIndex = 0;
        output[writeIndex] = (byte)ivLength;
        writeIndex++;
        if (ivLength > 0) {
            System.arraycopy(iv, 0, output, writeIndex, iv.length);
            writeIndex += iv.length;
        }

        output[writeIndex] = (byte)(encrypted.length / 256);
        writeIndex++;
        output[writeIndex] = (byte)(encrypted.length % 256);
        writeIndex++;
        System.arraycopy(encrypted, 0, output, writeIndex, encrypted.length);

        return output;
    }

    public static byte[] decrypt(byte[] bytes, EncryptionKeyAndTransform keyAndTransform)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException,
            UnrecoverableEntryException {
        int readIndex = 0;
        int ivLength = bytes[readIndex];
        readIndex++;
        if (ivLength < 0) {
            //Note: Early chance to catch decryption error
            throw new UnrecoverableKeyException("Negative IV length");
        }
        byte[] iv = null;
        if (ivLength > 0) {
            iv = new byte[ivLength];
            System.arraycopy(bytes, readIndex, iv, 0, ivLength);
            readIndex += ivLength;
        }

        int encryptedLength = bytes[readIndex] * 256;
        readIndex++;
        encryptedLength += bytes[readIndex];

        byte[] encrypted = new byte[encryptedLength];
        readIndex++;
        System.arraycopy(bytes, readIndex, encrypted, 0, encryptedLength);

        Cipher cipher = Cipher.getInstance(keyAndTransform.getTransformation());

        cipher.init(Cipher.DECRYPT_MODE, keyAndTransform.getKey(), iv != null ? new IvParameterSpec(iv) : null);

        return cipher.doFinal(encrypted);
    }

    //Encrypts a byte[] and converts to a base64 string for DB storage
    public static String encryptToBase64String(Context context, byte[] input) throws
            InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            UnrecoverableEntryException, CertificateException, NoSuchAlgorithmException,
            BadPaddingException, KeyStoreException, IOException, InvalidKeyException, NoSuchProviderException {
        byte[] encrypted = encrypt(input, CommCareApplication.instance().getEncryptionKeyProvider()
                .getKey(context, true));
        return Base64.encode(encrypted);
    }

    //Decrypts a base64 string (from DB storage) into a byte[]
    public static byte[] decryptFromBase64String(Context context, String base64) throws Base64DecoderException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            UnrecoverableEntryException, CertificateException, NoSuchAlgorithmException,
            BadPaddingException, KeyStoreException, IOException, InvalidKeyException, NoSuchProviderException {
        byte[] encrypted = Base64.decode(base64);

        return decrypt(encrypted, CommCareApplication.instance().getEncryptionKeyProvider()
                .getKey(context, false));
    }

    public static String getMd5HashAsString(String plainText) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plainText.getBytes());
            byte[] hashInBytes = md.digest();
            return Base64.encode(hashInBytes);
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
