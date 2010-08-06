/**
 * 
 */
package org.commcare.android.providers;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.crypto.Cipher;

import org.commcare.android.application.CommCareApplication;

import android.content.res.AssetFileDescriptor;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;

/**
 * @author ctsims
 *
 */
public class CommCareFileDescriptor extends AssetFileDescriptor {
	
	FileInputStream in;
	FileInputStream out;
	File file;
	Cipher encrypter;
	Cipher decrypter;
	
	
	public CommCareFileDescriptor(File file, Cipher encrypter, Cipher decrypter) throws FileNotFoundException {
		super(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_WRITE), 0, UNKNOWN_LENGTH);
		this.file = file;
	}
	
	public CommCareFileDescriptor(String filePath, Cipher encrypter, Cipher decrypter) throws FileNotFoundException {
		super(ParcelFileDescriptor.open(new File(filePath),ParcelFileDescriptor.MODE_READ_WRITE), 0, UNKNOWN_LENGTH);
	}


	public void close() throws IOException {
		if(in != null) {
			in.close();
		} 
		
		if(out != null) {
			out.close();
		}
	}

	public FileInputStream createInputStream() throws IOException {
		return new EncryptedFileInputStream(file, decrypter);
	}

	public FileOutputStream createOutputStream() throws IOException {
		return new EncryptedFileOutputStream(file, encrypter);
	}

	public int describeContents() {
		return 0;
	}

	public long getDeclaredLength() {
		return UNKNOWN_LENGTH;
	}

	public FileDescriptor getFileDescriptor() {
		// TODO Auto-generated method stub
		return super.getFileDescriptor();
	}

	public long getLength() {
		return UNKNOWN_LENGTH;
	}

	public ParcelFileDescriptor getParcelFileDescriptor() {
		// TODO Auto-generated method stub
		return super.getParcelFileDescriptor();
	}

	public long getStartOffset() {
		return 0;
	}

	public String toString() {
		// TODO Auto-generated method stub
		return super.toString();
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeString(file.getAbsolutePath());
	}
	
    public static final Parcelable.Creator<AssetFileDescriptor> CREATOR = new Parcelable.Creator<AssetFileDescriptor>() {
    	public CommCareFileDescriptor createFromParcel(Parcel in) {
    		System.out.println("Creating CommCarefileDescriptor");
    		String path = in.readString();
    		try {
    			return new CommCareFileDescriptor(path, CommCareApplication._().getEncrypter(), CommCareApplication._().getDecrypter());
    		} catch(FileNotFoundException fe) {
    			return null;
    		}
    	}

    	public CommCareFileDescriptor[] newArray(int size) {
    		return new CommCareFileDescriptor[size];
    	}
    };

}
