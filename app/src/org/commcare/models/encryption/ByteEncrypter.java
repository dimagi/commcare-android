package org.commcare.models.encryption;

import org.commcare.core.encryption.CryptUtil;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

/**
 * Created by amstone326 on 4/11/16.
 */
public class ByteEncrypter {

    private ByteEncrypter() {
    }

    public static byte[] wrapByteArrayWithString(byte[] bytes, String wrappingString) {
        return (new ByteEncrypter()).wrap(bytes, wrappingString);
    }

    public byte[] wrap(byte[] bytes, String wrappingString) {
        // NOTE: implementation is exposed for overriding in test harness due
        // to java encryption limitations (JCE)
        try {
            return CryptUtil.encrypt(bytes, CryptUtil.encodingCipher(wrappingString));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            return null;
        }
    }

    public static byte[] unwrapByteArrayWithString(byte[] wrapped, String wrappingString) {
        return (new ByteEncrypter()).unwrap(wrapped, wrappingString);
    }

    public byte[] unwrap(byte[] wrapped, String wrappingString) {
        // NOTE: implementation is exposed for overriding in test harness due
        // to java encryption limitations (JCE)
        try {
            Cipher cipher = CryptUtil.decodingCipher(wrappingString);
            return CryptUtil.decrypt(wrapped, cipher);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            return null;
        }
    }

}
