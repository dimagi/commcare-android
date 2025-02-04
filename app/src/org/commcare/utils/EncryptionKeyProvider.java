package org.commcare.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.crypto.KeyGenerator;
import javax.security.auth.x500.X500Principal;

import androidx.annotation.RequiresApi;

/**
 * Class for providing encryption keys backed by Android Keystore
 *
 * @author dviggiano
 */
public class EncryptionKeyProvider {
    /**
     *  Key store name that store the encryptrd key
     *  this should not be renamed due to backward compatiblity
     */
    private static final String KEYSTORE_NAME = "AndroidKeyStore";
    /**
     *  Key name to get the secret value from key store
     *  this should not be renamed due to backward compatiblity
     */
    private static final String SECRET_NAME = "secret";

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String ALGORITHM = KeyProperties.KEY_ALGORITHM_AES;
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC;
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7;

    private static class KeyStoreLoader {
        static final KeyStore INSTANCE;

        static {
            try {
                INSTANCE = KeyStore.getInstance(KEYSTORE_NAME);
                INSTANCE.load(null);
            } catch (KeyStoreException | CertificateException | IOException |
                     NoSuchAlgorithmException e) {
                Logger.exception("Initiating KeyStore", e);
                throw new RuntimeException(e);
            }
        }
    }

    private static KeyStore getKeystore() {
        return KeyStoreLoader.INSTANCE;
    }

    public EncryptionKeyAndTransform getKey(Context context, boolean trueForEncrypt)
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, UnrecoverableEntryException, NoSuchProviderException {
        return getKey(context, getKeystore(), trueForEncrypt);
    }

    //Gets the SecretKey from the Android KeyStore (creates a new one the first time)
    @SuppressLint("InlinedApi") //Suppressing since we check the API version elsewhere
    private static EncryptionKeyAndTransform getKey(Context context, KeyStore keystore, boolean trueForEncrypt)
            throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableEntryException,
            InvalidAlgorithmParameterException, NoSuchProviderException {

        if (doesKeystoreContainEncryptionKey()) {
            KeyStore.Entry existingKey = keystore.getEntry(SECRET_NAME, null);
            if (existingKey instanceof KeyStore.SecretKeyEntry) {
                KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) existingKey;
                return new EncryptionKeyAndTransform(
                        entry.getSecretKey(),
                        String.format("%s/%s/%s", ALGORITHM, BLOCK_MODE, PADDING)
                );
            } else if (existingKey instanceof KeyStore.PrivateKeyEntry) {
                KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) existingKey;
                Key key = trueForEncrypt ? entry.getCertificate().getPublicKey() : entry.getPrivateKey();
                return new EncryptionKeyAndTransform(key, "RSA/ECB/PKCS1Padding");
            }
            else {
                throw new RuntimeException("Unrecognized key type retrieved from KeyStore");
            }
        } else {
            return generateKeyInKeystore(context, trueForEncrypt);
        }
    }

    private static boolean doesKeystoreContainEncryptionKey() throws KeyStoreException {
        KeyStore keystore = getKeystore();

        return keystore.containsAlias(SECRET_NAME);
    }

    private static EncryptionKeyAndTransform generateKeyInKeystore(Context context, boolean trueForEncrypt)
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME);
            KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(SECRET_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build();

            keyGenerator.init(keySpec);
            return new EncryptionKeyAndTransform(keyGenerator.generateKey(), String.format("%s/%s/%s", ALGORITHM, BLOCK_MODE, PADDING));
        } else {
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

            Key key = trueForEncrypt ? pair.getPublic() : pair.getPrivate();
            return new EncryptionKeyAndTransform(key, "RSA/ECB/PKCS1Padding");
        }
    }

}
