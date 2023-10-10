package org.commcare.utils;

import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.commcare.CommCareApplication;
import org.commcare.util.Base64;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.GregorianCalendar;

import javax.crypto.KeyGenerator;
import javax.security.auth.x500.X500Principal;

import static org.commcare.util.EncryptionUtils.ANDROID_KEYSTORE_PROVIDER_NAME;

/**
 * Utility class for encrypting submissions during the SaveToDiskTask.
 *
 * @author mitchellsundt@gmail.com
 */
public class EncryptionUtils {

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

    // Generates a cryptrographic key and adds it to the Android KeyStore
    public static void generateCryptographicKeyForKeyStore(String keyAlias) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyGenerator keyGenerator = KeyGenerator
                        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE_PROVIDER_NAME);
                KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build();
                keyGenerator.init(keyGenParameterSpec);
                keyGenerator.generateKey();
            }
            else {
                KeyPairGenerator keyGenerator = KeyPairGenerator
                        .getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE_PROVIDER_NAME);
                GregorianCalendar start = new GregorianCalendar();
                GregorianCalendar end = new GregorianCalendar();
                end.add(GregorianCalendar.YEAR, 1);

                KeyPairGeneratorSpec keySpec = new KeyPairGeneratorSpec.Builder(CommCareApplication.instance())
                        // Key alias to be used to retrieve it from the KeyStore
                        .setAlias(keyAlias)
                        // The subject used for the self-signed certificate of the generated pair
                        .setSubject(new X500Principal(String.format("CN=%s", keyAlias)))
                        // The serial number used for the self-signed certificate of the
                        // generated pair
                        .setSerialNumber(BigInteger.valueOf(1337))
                        // Date range of validity for the generated pair
                        .setStartDate(start.getTime())
                        .setEndDate(end.getTime())
                        .build();

                keyGenerator.initialize(keySpec);
                keyGenerator.generateKeyPair();
            }

        } catch (NoSuchAlgorithmException | NoSuchProviderException |
                 InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }
}
