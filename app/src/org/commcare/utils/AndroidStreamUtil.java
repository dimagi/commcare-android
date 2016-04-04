package org.commcare.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author ctsims
 */
public class AndroidStreamUtil {

    public static byte[] inputStreamToByteArray(InputStream input) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
    }

    /**
     * Write is to os and close both
     */
    public static void writeFromInputToOutput(InputStream is, OutputStream os) throws IOException {
        writeFromInputToOutput(is, os, null);
    }

    /**
     * Write is to os and close both
     */
    public static void writeFromInputToOutput(InputStream is, OutputStream os, StreamReadObserver observer) throws IOException {
        byte[] buffer = new byte[8192];
        long counter = 0;

        try {
            int count = is.read(buffer);
            while (count != -1) {
                counter += count;
                if (observer != null) {
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
        void notifyCurrentCount(long bytesRead);
    }
}
