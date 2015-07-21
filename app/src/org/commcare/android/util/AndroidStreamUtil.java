/**
 * 
 */
package org.commcare.android.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author ctsims
 *
 */
public class AndroidStreamUtil {
    
    /**
     * Write is to os and close both
     * @param is
     * @param os
     */
    public static void writeFromInputToOutput(@NonNull InputStream is, @NonNull OutputStream os) throws IOException {
        writeFromInputToOutput(is, os, null);
    }
    
    /**
     * Write is to os and close both
     * @param is
     * @param os
     */
    public static void writeFromInputToOutput(@NonNull InputStream is, @NonNull OutputStream os, @Nullable StreamReadObserver observer) throws IOException {
        byte[] buffer = new byte[8192];
        long counter = 0;
        
        try {
            int count = is.read(buffer);
            while(count != -1) {
                counter += count;
                if(observer != null) {
                    observer.notifyCurrentCount(counter);
                }
                os.write(buffer, 0, count);
                count = is.read(buffer);
            }
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            try {
                os.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
    public interface StreamReadObserver {
        public void notifyCurrentCount(long bytesRead);
    }
}
