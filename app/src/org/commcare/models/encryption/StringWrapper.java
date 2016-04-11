package org.commcare.models.encryption;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

/**
 * Created by amstone326 on 4/11/16.
 */
public class StringWrapper {

    public byte[] wrapByteArrayWithString(byte[] bytes, String wrappingString) {
        try {
            return CryptUtil.encrypt(bytes, CryptUtil.encodingCipher(wrappingString));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            return null;
        }
    }

    public byte[] unwrapByteArrayWithString(byte[] wrapped, String wrappingString) {
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
