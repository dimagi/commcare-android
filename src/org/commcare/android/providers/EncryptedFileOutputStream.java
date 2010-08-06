/**
 * 
 */
package org.commcare.android.providers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;

import org.commcare.android.util.DelayedOperation;

/**
 * @author ctsims
 *
 */
public class EncryptedFileOutputStream extends FileOutputStream {

	private CipherOutputStream cos;
	private FileOutputStream fos;
	DelayedOperation<IOException> finalize;
	
	public EncryptedFileOutputStream(File file, Cipher cipher) throws FileNotFoundException {
		super(file);
		try {
			super.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
		fos = new FileOutputStream(file) {

			{
				finalize = new DelayedOperation<IOException>() {
					public IOException execute() {
						try {
							publicFinalize();
						} catch(IOException e) {
							return e;
						}
						return null;
					}
					
				};
				
			}
			public void publicFinalize() throws IOException {
				finalize();
			}
		};
		cos = new CipherOutputStream(fos, cipher);
	}

	@Override
	public void close() throws IOException {
		if(cos != null) {
			cos.close();
		}
	}

	@Override
	protected void finalize() throws IOException {
		IOException e = finalize.execute();
		if(e != null) {
			throw e;
		}
	}

	@Override
	public FileChannel getChannel() {
		throw new RuntimeException("Encrypted file output streams can't return channels");
	}

	@Override
	public void write(byte[] buffer, int offset, int count) throws IOException {
		cos.write(buffer, offset, count);
	}

	@Override
	public void write(byte[] buffer) throws IOException {
		cos.write(buffer);
	}

	@Override
	public void write(int oneByte) throws IOException {
		cos.write(oneByte);
	}
}
