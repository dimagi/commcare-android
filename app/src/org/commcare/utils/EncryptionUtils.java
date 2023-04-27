package org.commcare.utils;

import android.content.Context;
//import android.os.Build;
//import android.security.keystore.KeyGenParameterSpec;
//import android.security.keystore.KeyProperties;

//import androidx.annotation.RequiresApi;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;

import org.commcare.util.Base64;
//import org.commcare.util.Base64DecoderException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
//import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
//import java.security.InvalidAlgorithmParameterException;
//import java.security.InvalidKeyException;
//import java.security.KeyStore;
//import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
//import java.security.UnrecoverableEntryException;
//import java.security.cert.CertificateException;
import java.util.Random;

//import javax.crypto.BadPaddingException;
//import javax.crypto.Cipher;
//import javax.crypto.IllegalBlockSizeException;
//import javax.crypto.KeyGenerator;
//import javax.crypto.NoSuchPaddingException;
//import javax.crypto.SecretKey;
//import javax.crypto.spec.IvParameterSpec;

/**
 * Utility class for encrypting submissions during the SaveToDiskTask.
 *
 * @author mitchellsundt@gmail.com
 */

public class EncryptionUtils {

    private static final int PASSPHRASE_LENGTH = 32;

//    @RequiresApi(api = Build.VERSION_CODES.M)
//    private static final String ALGORITHM = KeyProperties.KEY_ALGORITHM_AES;
//    @RequiresApi(api = Build.VERSION_CODES.M)
//    private static final String BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC;
//    @RequiresApi(api = Build.VERSION_CODES.M)
//    private static final String PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7;
//    private static KeyStore keystore = null;
//
//
//
//    private static KeyStore getKeystore() {
//        if(keystore == null) {
//            try {
//                keystore = KeyStore.getInstance("AndroidKeyStore");
//                keystore.load(null);
//            }
//            catch(KeyStoreException e) {
//
//            }
//            catch(NoSuchAlgorithmException e) {
//
//            }
//            catch(IOException e) {
//
//            }
//            catch(CertificateException e) {
//
//            }
//        }
//
//        return keystore;
//    }
//
//    private static Cipher encryptCipher = null;
//    @RequiresApi(api = Build.VERSION_CODES.M)
//    private static Cipher getEncryptCipher() {
//        if(encryptCipher == null) {
//            try {
//                String transformation = String.format("%s/%s/%s", ALGORITHM, BLOCK_MODE, PADDING);
//                encryptCipher = Cipher.getInstance(transformation);
//                encryptCipher.init(Cipher.ENCRYPT_MODE, getKey());
//            }
//            catch(NoSuchPaddingException e) {
//
//            }
//            catch(NoSuchAlgorithmException e) {
//
//            }
//            catch(InvalidKeyException e) {
//
//            }
//        }
//
//        return encryptCipher;
//    }
//
//    @RequiresApi(api = Build.VERSION_CODES.M)
//    private static Cipher getDecryptCipherForIv(byte[] iv) {
//        try {
//            String transformation = String.format("%s/%s/%s", ALGORITHM, BLOCK_MODE, PADDING);
//            Cipher decryptCipher = Cipher.getInstance(transformation);
//            decryptCipher.init(Cipher.DECRYPT_MODE, getKey(), new IvParameterSpec(iv));
//            return decryptCipher;
//        } catch (NoSuchPaddingException e) {
//
//        } catch (NoSuchAlgorithmException e) {
//
//        } catch (InvalidKeyException e) {
//
//        } catch(InvalidAlgorithmParameterException e) {
//
//        }
//
//        return null;
//    }
//
//    @RequiresApi(api = Build.VERSION_CODES.M)
//    private static SecretKey getKey() {
//        try {
//            KeyStore.Entry existingKey = getKeystore().getEntry("secret", null);
//            if (existingKey instanceof KeyStore.SecretKeyEntry) {
//                return ((KeyStore.SecretKeyEntry)existingKey).getSecretKey();
//            }
//
//            return createKey();
//        }
//        catch(UnrecoverableEntryException e) {
//
//        }
//        catch(NoSuchAlgorithmException e) {
//
//        }
//        catch(KeyStoreException e) {
//
//        }
//
//        return null;
//    }
//
//    @RequiresApi(api = Build.VERSION_CODES.M)
//    private static SecretKey createKey() {
//        try {
//            KeyGenerator generator = KeyGenerator.getInstance(ALGORITHM);
//            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder("secret", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT);
//            builder.setBlockModes(BLOCK_MODE);
//            builder.setEncryptionPaddings(PADDING);
//            builder.setUserAuthenticationRequired(false);
//            builder.setRandomizedEncryptionRequired(true);
//            generator.init(builder.build());
//
//            return generator.generateKey();
//        }
//        catch(NoSuchAlgorithmException e) {
//
//        }
//        catch(InvalidAlgorithmParameterException e) {
//
//        }
//
//        return null;
//    }

    private static byte[] generatePassphrase() {
        Random random;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                random = SecureRandom.getInstanceStrong();
            } catch (NoSuchAlgorithmException e) {
                return null;
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

    public static byte[] getConnectDBPassphrase(Context context) {
        byte[] passphrase = null;
        try {
            File file = new File(context.getFilesDir(), "connect_phrase.bin");
            EncryptedFile encrypted = (new EncryptedFile.Builder(file, context,
                    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB)).build();

            if(file.exists()) {
                InputStream inputStream = encrypted.openFileInput();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                int nextByte = inputStream.read();
                while (nextByte != -1) {
                    byteArrayOutputStream.write(nextByte);
                    nextByte = inputStream.read();
                }

                passphrase = byteArrayOutputStream.toByteArray();
            }
            else {
                passphrase = generatePassphrase();
                OutputStream outputStream = encrypted.openFileOutput();
                outputStream.write(passphrase);
                outputStream.flush();
                outputStream.close();
            }
        }
        catch(GeneralSecurityException | IOException e) {

        }

        return passphrase;
    }

//    @RequiresApi(api = Build.VERSION_CODES.M)
//    public static byte[] encrypt(byte[] bytes) {
//        try {
//            Cipher cipher = getEncryptCipher();
//            byte[] encrypted = cipher.doFinal(bytes);
//            byte[] iv = cipher.getIV();
//
//            byte[] output = new byte[encrypted.length + iv.length + 2];
//            int writeIndex = 0;
//            output[writeIndex] = (byte)iv.length;
//            writeIndex++;
//            System.arraycopy(iv, 0, output, writeIndex, iv.length);
//            writeIndex += iv.length;
//
//            output[writeIndex] = (byte)encrypted.length;
//            writeIndex++;
//            System.arraycopy(encrypted, 0, output, writeIndex, encrypted.length);
//
//            return output;
//        }
//        catch(IllegalBlockSizeException e) {
//
//        }
//        catch(BadPaddingException e) {
//
//        }
//
//        return null;
//    }
//
//    @RequiresApi(api = Build.VERSION_CODES.M)
//    public static String encryptToBase64String(String input) {
//        byte[] bytes = input.getBytes();
//        byte[] encrypted = encrypt(bytes);
//        return Base64.encode(encrypted);
//    }
//
//    @RequiresApi(api = Build.VERSION_CODES.M)
//    public static byte[] decrypt(byte[] bytes) {
//        byte[] output = null;
//        int readIndex = 0;
//        int ivLength = bytes[readIndex];
//        byte[] iv = new byte[ivLength];
//        readIndex++;
//        System.arraycopy(bytes, readIndex, iv, 0, ivLength);
//        readIndex += ivLength;
//
//        int encryptedLength = bytes[readIndex];
//        byte[] encrypted = new byte[encryptedLength];
//        readIndex++;
//        System.arraycopy(bytes, readIndex, encrypted, 0, encryptedLength);
//
//        Cipher cipher = getDecryptCipherForIv(iv);
//
//        try {
//            output = cipher.doFinal(encrypted);
//        }
//        catch(IllegalBlockSizeException e) {
//
//        }
//        catch(BadPaddingException e) {
//
//        }
//
//        return output;
//    }
//
//    @RequiresApi(api = Build.VERSION_CODES.M)
//    public static String decodeFromBase64String(String base64) {
//        try {
//            byte[] encrypted = Base64.decode(base64);
//            byte[] decrypted = decrypt(encrypted);
//            return new String(decrypted);
//        }
//        catch(Base64DecoderException e) {
//
//        }
//
//        return null;
//    }

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
;
