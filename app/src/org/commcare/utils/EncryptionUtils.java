package org.commcare.utils;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.RequiresApi;

import org.commcare.util.Base64;
import org.commcare.util.Base64DecoderException;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * Utility class for encrypting submissions during the SaveToDiskTask.
 *
 * @author mitchellsundt@gmail.com
 */

public class EncryptionUtils {

    private static final int PASSPHRASE_LENGTH = 32;
    private static final String SECRET_NAME = "secret";

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String ALGORITHM = KeyProperties.KEY_ALGORITHM_AES;
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC;
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7;
    private static KeyStore keystore = null;

    //Gets the SecretKey from the Android KeyStore (creates a new one the first time)
    private static SecretKey getKey()
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException,
            UnrecoverableEntryException, InvalidAlgorithmParameterException {
        if(keystore == null) {
            keystore = KeyStore.getInstance("AndroidKeyStore");
            keystore.load(null);
        }

        KeyStore.Entry existingKey = keystore.getEntry(SECRET_NAME, null);
        if (existingKey instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) existingKey).getSecretKey();
        }

        //Create key
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(SECRET_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT);
            builder.setBlockModes(BLOCK_MODE);
            builder.setEncryptionPaddings(PADDING);
            builder.setUserAuthenticationRequired(false);
            builder.setRandomizedEncryptionRequired(true);

            KeyGenerator generator = KeyGenerator.getInstance(ALGORITHM);
            generator.init(builder.build());
            return generator.generateKey();
        }

        return null;
    }

    //Generate a random passphrase
    public static byte[] generatePassphrase() {
        Random random;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                random = SecureRandom.getInstanceStrong();
            } catch (NoSuchAlgorithmException e) {
                random = new Random();
            }
        } else {
            random = new Random();
        }

        byte[] result = new byte[PASSPHRASE_LENGTH];

        while (true) {
            random.nextBytes(result);

            //Make sure there are no zeroes in the passphrase (bad for SQLCipher)
            boolean containsZero = false;
            for (byte b : result) {
                if (b == 0) {
                    containsZero = true;
                    break;
                }
            }

            if (!containsZero) {
                break;
            }
        }

        return result;
    }

    //Encryption backed by Android KeyStore
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static byte[] encrypt(byte[] bytes)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException,
            UnrecoverableEntryException, CertificateException, KeyStoreException, IOException {
        String transformation = String.format("%s/%s/%s", ALGORITHM, BLOCK_MODE, PADDING);
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] encrypted = cipher.doFinal(bytes);
        byte[] iv = cipher.getIV();

        byte[] output = new byte[encrypted.length + iv.length + 2];
        int writeIndex = 0;
        output[writeIndex] = (byte) iv.length;
        writeIndex++;
        System.arraycopy(iv, 0, output, writeIndex, iv.length);
        writeIndex += iv.length;

        output[writeIndex] = (byte) encrypted.length;
        writeIndex++;
        System.arraycopy(encrypted, 0, output, writeIndex, encrypted.length);

        return output;
    }

    //Decryption backed by Android KeyStore
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static byte[] decrypt(byte[] bytes)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException,
            UnrecoverableEntryException, CertificateException, KeyStoreException, IOException {
        int readIndex = 0;
        int ivLength = bytes[readIndex];
        byte[] iv = new byte[ivLength];
        readIndex++;
        System.arraycopy(bytes, readIndex, iv, 0, ivLength);
        readIndex += ivLength;

        int encryptedLength = bytes[readIndex];
        byte[] encrypted = new byte[encryptedLength];
        readIndex++;
        System.arraycopy(bytes, readIndex, encrypted, 0, encryptedLength);

        String transformation = String.format("%s/%s/%s", ALGORITHM, BLOCK_MODE, PADDING);
        Cipher cipher = Cipher.getInstance(transformation);

        cipher.init(Cipher.DECRYPT_MODE, getKey(), new IvParameterSpec(iv));

        return cipher.doFinal(encrypted);
    }

    //Encrypts a byte[] and converts to a base64 string for DB storage
    public static String encryptToBase64String(byte[] input) {
        try {
            //If Android version is too old, skip encryption
            byte[] encrypted = input;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                encrypted = encrypt(input);
            }

            return Base64.encode(encrypted);
        }
        catch(NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException |
              IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException |
              UnrecoverableEntryException | CertificateException | KeyStoreException | IOException e) {
            //TODO: What to do when this fails?
        }

        return null;
    }

    //Decrypts a base64 string (from DB storage) into a byte[]
    public static byte[] decryptFromBase64String(String base64) {
        try {
            byte[] encrypted = Base64.decode(base64);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return decrypt(encrypted);
            }

            //If Android version is too old, skip encryption
            return encrypted;
        }
        catch(Base64DecoderException | NoSuchPaddingException | NoSuchAlgorithmException |
              InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException |
              BadPaddingException | UnrecoverableEntryException | CertificateException |
              KeyStoreException | IOException e) {
            //TODO: What to do when this fails?
        }

        return null;
    }

    public static String getMD5HashAsString(String plainText) {
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
