/**
 * 
 */
package org.commcare.android.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.javarosa.core.util.StreamUtil;

/**
 * @author ctsims
 *
 */
public class CryptUtil {
	
	private static final String PBE_PROVIDER = "PBEWITHSHA-256AND256BITAES-CBC-BC";
	
	private static Cipher encodingCipher(String password) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, InvalidKeySpecException {
		
		KeySpec spec = new PBEKeySpec(password.toCharArray(), "SFDWFDCF".getBytes(), 10);
		SecretKeyFactory factory = SecretKeyFactory.getInstance(PBE_PROVIDER);
		SecretKey key = factory.generateSecret(spec);

		Cipher cipher = Cipher.getInstance(PBE_PROVIDER);
		cipher.init(Cipher.ENCRYPT_MODE, key);

		return cipher;
	}
	
	private static Cipher decodingCipher(String password) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException {
		
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
			StreamUtil.transfer(cis, bos);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return bos.toByteArray();
	}
	
	public static byte[] decrypt(byte[] input, Cipher cipher) throws IOException {
		
		ByteArrayInputStream bis = new ByteArrayInputStream(input);
		CipherInputStream cis = new CipherInputStream(bis, cipher);
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		StreamUtil.transfer(cis, bos);
		
		return bos.toByteArray();
	}
	
	public static byte[] wrapKey(SecretKey key, String password) {
		try{
			//SecretKeySpec spec = (SecretKeySpec)SecretKeyFactory.getInstance("AES").getKeySpec(key, javax.crypto.spec.SecretKeySpec.class);
			byte[] secretKey = key.getEncoded();
			byte[] encrypted = encrypt(secretKey, encodingCipher(password));
			return encrypted;
		}catch (InvalidKeySpecException e) {
			throw new RuntimeException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch (InvalidKeyException e) {
			throw new RuntimeException(e);
		} catch (NoSuchPaddingException e) {
			return null;
		}
	}
	
	public static byte[] unWrapKey(byte[] wrapped, String password) {
		try{
			Cipher cipher = decodingCipher(password);
			byte[] encoded = decrypt(wrapped, cipher);
			return encoded;
		} catch(IOException e) {
			throw new RuntimeException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		} catch (InvalidKeySpecException e) {
			throw new RuntimeException(e);
		} catch (InvalidKeyException e) {
			throw new RuntimeException(e);
		} catch (NoSuchPaddingException e) {
			return null;
		}
	}
}
