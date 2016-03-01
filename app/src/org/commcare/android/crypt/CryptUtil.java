package org.commcare.android.crypt;

import org.commcare.utils.AndroidStreamUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author ctsims
 */
public class CryptUtil {

    private static final String PBE_PROVIDER = "PBEWITHSHA-256AND256BITAES-CBC-BC";

    private static Cipher encodingCipher(String passwordOrPin)
            throws NoSuchAlgorithmException, InvalidKeyException,
            NoSuchPaddingException, InvalidKeySpecException {

        KeySpec spec = new PBEKeySpec(passwordOrPin.toCharArray(), "SFDWFDCF".getBytes(), 10);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBE_PROVIDER);
        SecretKey key = factory.generateSecret(spec);

        Cipher cipher = Cipher.getInstance(PBE_PROVIDER);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        return cipher;
    }

    private static Cipher decodingCipher(String password)
            throws NoSuchAlgorithmException, InvalidKeySpecException,
            NoSuchPaddingException, InvalidKeyException {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), "SFDWFDCF".getBytes(), 10);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBE_PROVIDER);
        SecretKey key = factory.generateSecret(spec);

        Cipher cipher = Cipher.getInstance(PBE_PROVIDER);
        cipher.init(Cipher.DECRYPT_MODE, key);

        return cipher;
    }

    public static byte[] encrypt(byte[] input, Cipher cipher) {
        ByteArrayInputStream bis = new ByteArrayInputStream(input);
        CipherInputStream cis = new CipherInputStream(bis, cipher);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            AndroidStreamUtil.writeFromInputToOutput(cis, bos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return bos.toByteArray();
    }

    public static byte[] decrypt(byte[] input, Cipher cipher) {
        try {
            return cipher.doFinal(input);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] wrapByteArrayWithString(byte[] bytes, String wrappingString) {
        try {
            return encrypt(bytes, encodingCipher(wrappingString));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            return null;
        }
    }

    public static byte[] unwrapByteArrayWithString(byte[] wrapped, String wrappingString) {
        try {
            Cipher cipher = decodingCipher(wrappingString);
            return decrypt(wrapped, cipher);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            return null;
        }
    }

    private static byte[] append(byte[] one, byte[] two) {
        byte[] result = new byte[one.length + two.length];
        for (int i = 0; i < result.length; ++i) {
            if (i < one.length) {
                result[i] = one[i];
            } else {
                int index = i - one.length;
                result[i] = two[index];
            }
        }
        return result;
    }

    public static byte[] uniqueSeedFromSecureStatic(byte[] secureStatic) {
        long uniqueBase = new Date().getTime();
        String baseString = Long.toHexString(uniqueBase);
        try {
            return append(baseString.getBytes(),
                    MessageDigest.getInstance("SHA-1").digest(secureStatic));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static SecretKey generateSymmetricKey(byte[] prngSeed) {
        KeyGenerator generator;
        try {
            generator = KeyGenerator.getInstance("AES");
            generator.init(256, new SecureRandom(prngSeed));
            return generator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static SecretKey generateSemiRandomKey() {
        KeyGenerator generator;
        try {
            generator = KeyGenerator.getInstance("AES");
            generator.init(256, new SecureRandom());
            return generator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Cipher getPrivateKeyCipher(byte[] privateKey)
            throws InvalidKeyException, NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        KeySpec ks = new PKCS8EncodedKeySpec(privateKey);
        RSAPrivateKey privKey = (RSAPrivateKey)keyFactory.generatePrivate(ks);

        Cipher c = Cipher.getInstance("RSA");
        c.init(Cipher.DECRYPT_MODE, privKey);
        return c;
    }

    public static Cipher getAesKeyCipher(byte[] aesKey)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException {
        return getAesKeyCipher(aesKey, Cipher.DECRYPT_MODE);
    }

    public static Cipher getAesKeyCipher(byte[] aesKey, int mode)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException {
        SecretKeySpec spec = new SecretKeySpec(aesKey, "AES");
        Cipher decrypter = Cipher.getInstance("AES");
        decrypter.init(mode, spec);
        return decrypter;
    }
}
