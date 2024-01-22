package org.commcare.utils;

import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.commcare.CommCareApplication;
import org.commcare.util.EncryptionHelper;
import org.commcare.util.IEncryptionKeyProvider;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.GregorianCalendar;

import javax.crypto.KeyGenerator;
import javax.security.auth.x500.X500Principal;

import androidx.annotation.RequiresApi;

import static org.commcare.util.EncryptionKeyHelper.CC_KEY_ALGORITHM_RSA;
import static org.commcare.utils.GlobalConstants.KEYSTORE_NAME;

/**
 * Class for providing encryption keys backed by Android Keystore
 *
 * @author dviggiano
 */
public class EncryptionKeyProvider implements IEncryptionKeyProvider {

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String ALGORITHM = KeyProperties.KEY_ALGORITHM_AES;
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM;
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String PADDING = KeyProperties.ENCRYPTION_PADDING_NONE;

    // Generates a cryptrographic key and adds it to the Android KeyStore
    public Key generateCryptographicKeyInKeyStore(String keyAlias,
                                                  EncryptionHelper.CryptographicOperation cryptographicOperation) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyGenerator keyGenerator = KeyGenerator
                        .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME);
                KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(BLOCK_MODE)
                        .setEncryptionPaddings(PADDING)
                        .build();
                keyGenerator.init(keyGenParameterSpec);
                return keyGenerator.generateKey();
            } else {
                // Because KeyGenParameterSpec was introduced in Android SDK 23, prior versions
                // need to resource to KeyPairGenerator which only generates asymmetric keys,
                // hence the need to switch to a correspondent algorithm as well, RSA
                // TODO: Add link to StackOverflow page
                KeyPairGenerator keyGenerator = KeyPairGenerator
                        .getInstance(CC_KEY_ALGORITHM_RSA, KEYSTORE_NAME);
                GregorianCalendar start = new GregorianCalendar();
                GregorianCalendar end = new GregorianCalendar();
                end.add(GregorianCalendar.YEAR, 100);

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
                KeyPair keyPair = keyGenerator.generateKeyPair();
                if (cryptographicOperation == EncryptionHelper.CryptographicOperation.Encryption) {
                    return keyPair.getPublic();
                } else {
                    return keyPair.getPrivate();
                }
            }

        } catch (NoSuchAlgorithmException | NoSuchProviderException |
                 InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getTransformationString() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return String.format("%s/%s/%s", ALGORITHM, BLOCK_MODE, PADDING);
        } else {
            return "RSA/ECB/PKCS1Padding";
        }
    }

    @Override
    public String getKeyStoreName() {
        return "AndroidKeyStore";
    }
}
