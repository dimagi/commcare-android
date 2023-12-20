package org.commcare.utils;

import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.commcare.CommCareApplication;
import org.commcare.util.EncryptionKeyAndTransformation;
import org.commcare.util.EncryptionHelper;
import org.commcare.util.IEncryptionKeyProvider;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.GregorianCalendar;

import javax.crypto.KeyGenerator;
import javax.security.auth.x500.X500Principal;

import androidx.annotation.RequiresApi;

import static org.commcare.util.EncryptionHelper.CC_KEY_ALGORITHM_RSA;
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
    private static KeyStore keystoreSingleton = null;

    private static KeyStore getKeyStore() throws KeyStoreException, CertificateException,
            IOException, NoSuchAlgorithmException {
        if (keystoreSingleton == null) {
            keystoreSingleton = KeyStore.getInstance(KEYSTORE_NAME);
            keystoreSingleton.load(null);
        }
        return keystoreSingleton;
    }

    @Override
    public EncryptionKeyAndTransformation retrieveKeyFromKeyStore(String keyAlias,
                                                                  EncryptionHelper.CryptographicOperation operation)
            throws KeyStoreException, UnrecoverableEntryException, NoSuchAlgorithmException,
            CertificateException, IOException {
        Key key;
        if (getKeyStore().containsAlias(keyAlias)) {
            KeyStore.Entry keyEntry = getKeyStore().getEntry(keyAlias, null);
            if (keyEntry instanceof KeyStore.PrivateKeyEntry) {
                if (operation == EncryptionHelper.CryptographicOperation.Encryption) {
                    key = ((KeyStore.PrivateKeyEntry)keyEntry).getCertificate().getPublicKey();
                } else {
                    key = ((KeyStore.PrivateKeyEntry)keyEntry).getPrivateKey();
                }
            } else {
                key = ((KeyStore.SecretKeyEntry)keyEntry).getSecretKey();
            }
        } else {
            throw new KeyStoreException("Key not found in KeyStore");
        }
        if (key != null) {
            return new EncryptionKeyAndTransformation(key, getTransformationString(key.getAlgorithm()));
        } else {
            return null;
        }
    }

    // Generates a cryptrographic key and adds it to the Android KeyStore
    public void generateCryptographicKeyInKeyStore(String keyAlias) {
        if (isKeyStoreAvailable()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    KeyGenerator keyGenerator = KeyGenerator
                            .getInstance(getAESKeyAlgorithmRepresentation(), KEYSTORE_NAME);
                    KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(keyAlias,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(BLOCK_MODE)
                            .setEncryptionPaddings(PADDING)
                            .build();
                    keyGenerator.init(keyGenParameterSpec);
                    keyGenerator.generateKey();
                } else {
                    // Because KeyGenParameterSpec was introduced in Android SDK 23, prior versions
                    // need to resource to KeyPairGenerator which only generates asymmetric keys,
                    // hence the need to switch to a correspondent algorithm as well, RSA
                    // TODO: Add link to StackOverflow page
                    KeyPairGenerator keyGenerator = KeyPairGenerator
                            .getInstance(getRSAKeyAlgorithmRepresentation(), KEYSTORE_NAME);
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
                    keyGenerator.generateKeyPair();
                }

            } catch (NoSuchAlgorithmException | NoSuchProviderException |
                     InvalidAlgorithmParameterException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("KeyStore not available");
        }
    }

    @Override
    public boolean isKeyStoreAvailable() {
        return Security.getProvider(KEYSTORE_NAME) != null;
    }

    @Override
    public String getAESKeyAlgorithmRepresentation() {
        return ALGORITHM;
    }

    @Override
    public String getRSAKeyAlgorithmRepresentation() {
        return CC_KEY_ALGORITHM_RSA;
    }

    @Override
    public String getTransformationString(String algorithm) {
        String transformation = null;
        if (algorithm.equals(getRSAKeyAlgorithmRepresentation())) {
            transformation = "RSA/ECB/PKCS1Padding";
        } else if (algorithm.equals(getAESKeyAlgorithmRepresentation())) {
            transformation = String.format("%s/%s/%s", algorithm, BLOCK_MODE, PADDING);
        }
        // This will cause an error if null
        return transformation;
    }

}
