/**
 * 
 */
package org.commcare.android.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
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
			AndroidStreamUtil.writeFromInputToOutput(cis, bos);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return bos.toByteArray();
	}
	
	public static byte[] decrypt(byte[] input, Cipher cipher) throws IOException {
		
		try {
			return cipher.doFinal(input);
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
//		
//		ByteArrayInputStream bis = new ByteArrayInputStream(input);
//		CipherInputStream cis = new CipherInputStream(bis, cipher);
//		
//		ByteArrayOutputStream bos = new ByteArrayOutputStream();
//		
//		AndroidStreamUtil.writeFromInputToOutput(new BufferedInputStream(cis), bos);
//		
//		return bos.toByteArray();
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
	
	private static byte[] append(byte[] one, byte[] two) {
		byte[] result = new byte[one.length + two.length];
		for(int i = 0; i < result.length; ++i) {
			if(i < one.length) {
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
			return append(baseString.getBytes(), MessageDigest.getInstance("SHA-1").digest(secureStatic));
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public static SecretKey generateSymetricKey(byte[] prngSeed) {
		KeyGenerator generator;
		try {
			generator = KeyGenerator.getInstance("AES");
			generator.init(256, new SecureRandom(prngSeed));
			return generator.generateKey();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
