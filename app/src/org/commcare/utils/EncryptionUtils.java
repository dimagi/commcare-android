package org.commcare.utils;

import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.RequiresApi;

import org.commcare.util.Base64;
import org.commcare.util.Base64DecoderException;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.security.auth.x500.X500Principal;

/**
 * Utility class for encrypting submissions during the SaveToDiskTask.
 *
 * @author mitchellsundt@gmail.com
 */

public class EncryptionUtils {

    private static final int PASSPHRASE_LENGTH = 32;
    private static final String KEYSTORE_NAME = "AndroidKeyStore";
    private static final String SECRET_NAME = "secret";

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String ALGORITHM = KeyProperties.KEY_ALGORITHM_AES;
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC;
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7;
    private static KeyStore keystoreSingleton = null;

    private static KeyStore getKeystore() throws KeyStoreException, CertificateException,
            IOException, NoSuchAlgorithmException {
        if (keystoreSingleton == null) {
            keystoreSingleton = KeyStore.getInstance(KEYSTORE_NAME);
            keystoreSingleton.load(null);
        }

        return keystoreSingleton;
    }

    //Gets the SecretKey from the Android KeyStore (creates a new one the first time)
    private static Key getKey(Context context, KeyStore keystore, boolean trueForEncrypt)
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException,
            UnrecoverableEntryException, InvalidAlgorithmParameterException, NoSuchProviderException {

        if(doesKeystoreContainEncryptionKey()) {
            KeyStore.Entry existingKey = keystore.getEntry(SECRET_NAME, null);
            if (existingKey instanceof KeyStore.SecretKeyEntry entry) {
                return entry.getSecretKey();
            }
            if (existingKey instanceof KeyStore.PrivateKeyEntry entry) {
                return trueForEncrypt ? entry.getCertificate().getPublicKey() : entry.getPrivateKey();
            }
            else { return null; }
        } else {
            return generateKeyInKeystore(context, trueForEncrypt);
        }
    }

    private static boolean doesKeystoreContainEncryptionKey() throws CertificateException,
            KeyStoreException, IOException, NoSuchAlgorithmException {
        KeyStore keystore = getKeystore();

        return  keystore.containsAlias(SECRET_NAME);
    }

    private static Key generateKeyInKeystore(Context context, boolean trueForEncrypt) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME);
            KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(SECRET_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build();

            keyGenerator.init(keySpec);
            return keyGenerator.generateKey();
        }
        else {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", KEYSTORE_NAME);

            GregorianCalendar start = new GregorianCalendar();
            GregorianCalendar end = new GregorianCalendar();
            end.add(Calendar.YEAR, 100);
            KeyPairGeneratorSpec keySpec = new KeyPairGeneratorSpec.Builder(context)
                    // You'll use the alias later to retrieve the key.  It's a key for the key!
                    .setAlias(SECRET_NAME)
                    // The subject used for the self-signed certificate of the generated pair
                    .setSubject(new X500Principal(String.format("CN=%s", SECRET_NAME)))
                    // The serial number used for the self-signed certificate of the
                    // generated pair.
                    .setSerialNumber(BigInteger.valueOf(1337))
                    // Date range of validity for the generated pair.
                    .setStartDate(start.getTime())
                    .setEndDate(end.getTime())
                    .build();

            generator.initialize(keySpec);
            KeyPair pair = generator.generateKeyPair();

            return trueForEncrypt ? pair.getPublic() : pair.getPrivate();
        }
    }

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

            if (!containsZero) {
                break;
            }
        }

        return result;
    }

    private static String getTransformationString() {
        String transformation;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            transformation = String.format("%s/%s/%s", ALGORITHM, BLOCK_MODE, PADDING);
        }
        else {
            transformation = "RSA/ECB/PKCS1Padding";
        }

        return transformation;
    }

    //Encryption backed by Android KeyStore
    private static byte[] encrypt(Context context, byte[] bytes)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException,
            UnrecoverableEntryException, CertificateException, KeyStoreException, IOException, NoSuchProviderException {
        String transformation = getTransformationString();
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.ENCRYPT_MODE, getKey(context, getKeystore(), true));
        byte[] encrypted = cipher.doFinal(bytes);
        byte[] iv = cipher.getIV();
        int ivLength = iv == null ? 0 : iv.length;

        byte[] output = new byte[encrypted.length + ivLength + 3];
        int writeIndex = 0;
        output[writeIndex] = (byte) ivLength;
        writeIndex++;
        if(ivLength > 0) {
            System.arraycopy(iv, 0, output, writeIndex, iv.length);
            writeIndex += iv.length;
        }

        output[writeIndex] = (byte) (encrypted.length / 256);
        writeIndex++;
        output[writeIndex] = (byte) (encrypted.length % 256);
        writeIndex++;
        System.arraycopy(encrypted, 0, output, writeIndex, encrypted.length);

        return output;
    }

    //Decryption backed by Android KeyStore
    private static byte[] decrypt(Context context, byte[] bytes)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException,
            UnrecoverableEntryException, CertificateException, KeyStoreException, IOException, NoSuchProviderException {
        int readIndex = 0;
        int ivLength = bytes[readIndex];
        readIndex++;
        //if(ivLength < 0) {
            //TODO: Early chance to catch decryption error
        //}
        byte[] iv = null;
        if(ivLength > 0) {
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

        String transformation = getTransformationString();
        Cipher cipher = Cipher.getInstance(transformation);

        cipher.init(Cipher.DECRYPT_MODE, getKey(context, getKeystore(), false), iv != null ? new IvParameterSpec(iv) : null);

        return cipher.doFinal(encrypted);
    }

    //Encrypts a byte[] and converts to a base64 string for DB storage
    public static String encryptToBase64String(Context context, byte[] input) throws
            InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            UnrecoverableEntryException, CertificateException, NoSuchAlgorithmException,
            BadPaddingException, KeyStoreException, IOException, InvalidKeyException, NoSuchProviderException {
        byte[] encrypted = encrypt(context, input);

        return Base64.encode(encrypted);
    }

    //Decrypts a base64 string (from DB storage) into a byte[]
    public static byte[] decryptFromBase64String(Context context, String base64) throws Base64DecoderException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            UnrecoverableEntryException, CertificateException, NoSuchAlgorithmException,
            BadPaddingException, KeyStoreException, IOException, InvalidKeyException, NoSuchProviderException {
        byte[] encrypted = Base64.decode(base64);

        return decrypt(context, encrypted);
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
