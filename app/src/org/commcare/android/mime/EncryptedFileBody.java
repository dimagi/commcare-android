/**
 * 
 */
package org.commcare.android.mime;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.content.AbstractContentBody;
import org.apache.james.mime4j.message.Entity;
import org.javarosa.core.io.StreamsUtil;


/**
 * @author ctsims
 *
 */
public class EncryptedFileBody extends AbstractContentBody {
	
	Entity entity;
	File file;
	Cipher cipher;
	String contentType;
	
	public EncryptedFileBody(File file, Cipher cipher, String contentType) {
		super(contentType);
		this.file = file;
		this.cipher = cipher;
		this.contentType = contentType;
	}

	public String getFilename() {
		return file.getName();
	}

	public String getCharset() {
		return MIME.DEFAULT_CHARSET.name();
		
	}

	public long getContentLength() {
		return -1;
	}

	public String getTransferEncoding() {
		return MIME.ENC_BINARY;
	}

	@Override
	public void writeTo(OutputStream out) throws IOException {
		CipherInputStream cis = new CipherInputStream(new FileInputStream(file), cipher);
		StreamsUtil.writeFromInputToOutput(cis, out);
		cis.close();
	}

}
