/**
 * 
 */
package org.commcare.android.javarosa;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.database.EncryptedModel;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;
import org.commcare.android.util.FileUtil;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * A small DB record for keeping track of serialized device reports which we are planning
 * on submitting. Keeps track of the location on disk, and the key we use to encrypt it.
 * 
 * Fairly similar record to what we're storing for the forms. Should possibly use that
 * one and its process
 * 
 * @author ctsims
 *
 */

@Table("log_records")
public class DeviceReportRecord extends Persisted implements EncryptedModel{

	@Persisting
	private String fileName;
	@Persisting
	private byte[] aesKey;
	
	/**
	 * Serialization Only!!!
	 */
	public DeviceReportRecord() {
		
	}
	
	public DeviceReportRecord(String fileName, byte[] aesKey) {
		this.fileName = fileName;
		this.aesKey = aesKey;
	}
	
	public static DeviceReportRecord GenerateNewRecordStub() {
		DeviceReportRecord slr = new DeviceReportRecord();
		slr.fileName = new File(CommCareApplication._().getCurrentApp().fsPath((GlobalConstants.FILE_CC_LOGS)) + FileUtil.SanitizeFileName(File.separator + DateUtils.formatDateTime(new Date(), DateUtils.FORMAT_ISO8601)) + ".xml").getAbsolutePath();
		slr.aesKey = CommCareApplication._().createNewSymetricKey().getEncoded();
		return slr;
	}

	@Override
	public boolean isEncrypted(String data) {
		return false;
	}

	@Override
	public boolean isBlobEncrypted() {
		return true;
	}

	public byte[] getKey() {
		return aesKey;
	}

	public String getFilePath() {
		return fileName;
	}

	public final OutputStream openOutputStream() throws FileNotFoundException, IOException {
		try {
			String path = getFilePath();
			File f = new File (path);
			
			FileOutputStream os = new FileOutputStream(f);
			
			SecretKeySpec spec = new SecretKeySpec(getKey(), "AES");
			Cipher encrypter = Cipher.getInstance("AES");
			encrypter.init(Cipher.ENCRYPT_MODE, spec);
			
			return new CipherOutputStream(os, encrypter);
		} catch(InvalidKeyException ike) {
			ike.printStackTrace();
			Logger.log(AndroidLogger.TYPE_ERROR_CRYPTO, "Invalid key: " + ike.getMessage());
			throw new IOException("Bad key while trying to generate output stream for device report");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			Logger.log(AndroidLogger.TYPE_ERROR_CRYPTO, "Unavailable Crypto algorithm: " + e.getMessage());
			throw new IOException("Bad key while trying to generate output stream for device report");
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
			Logger.log(AndroidLogger.TYPE_ERROR_CRYPTO, "Bad Padding: " + e.getMessage());
			throw new IOException("Bad key while trying to generate output stream for device report");
		}
	}

}
