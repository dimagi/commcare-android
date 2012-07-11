/**
 * 
 */
package org.commcare.android.providers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

import org.commcare.android.util.DelayedOperation;

/**
 * @author ctsims
 *
 */
public class EncryptedFileInputStream extends FileInputStream {
	
	FileInputStream fis;
	CipherInputStream cis;
	DelayedOperation<IOException> finalize;

	public EncryptedFileInputStream(File file, Cipher cipher) throws FileNotFoundException {
		super(file);
		try {
			super.close();
		} catch (IOException e) {
			//Weird...
			e.printStackTrace();
		}
		fis = new FileInputStream(file) { 
			
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
				
		cis = new CipherInputStream(fis, cipher);
	}

	@Override
	public int available() throws IOException {
		return cis.available();
	}

	@Override
	public void close() throws IOException {
		cis.close();
	}

	@Override
	protected void finalize() throws IOException {
		IOException t = finalize.execute();
		if(t != null) {
			throw t;
		}
	}

	@Override
	public FileChannel getChannel() {
		throw new RuntimeException("No channels are available for encrypted file input streams");
	}

	@Override
	public int read() throws IOException {
		return cis.read();
	}

	@Override
	public int read(byte[] buffer, int offset, int count) throws IOException {
		return cis.read(buffer, offset, count);
	}

	@Override
	public int read(byte[] buffer) throws IOException {
		return cis.read(buffer);
	}

	@Override
	public long skip(long count) throws IOException {
		return cis.skip(count);
	}
	
	@Override
	public void mark(int readlimit) {
		cis.mark(readlimit);
	}

	@Override
	public boolean markSupported() {
		return cis.markSupported();
	}

	@Override
	public synchronized void reset() throws IOException {
		cis.reset();
	}

}
