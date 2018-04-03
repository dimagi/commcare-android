package org.commcare.logging;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.javarosa.core.io.StreamsUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class DataChangeLogger {

    private static final String PRIMARY_LOG_FILE_NAME = "CommCare Data Change Logs.txt";
    private static final String LOG_FILE_PATH = "";
    private static final String TAG = DataChangeLogger.class.getSimpleName();

    private static File sLogFile;

    // Singleton
    private static DataChangeLogger sInstance;

    private DataChangeLogger(Context context) {
        sLogFile = initLogFile(context);
    }

    public static void init(Context context) {
        if (sInstance == null) {
            sInstance = new DataChangeLogger(context);
        }
    }

    public static void log(String message) {
        if (sLogFile != null && sLogFile.exists()) {
            try {
                FileOutputStream outputStream = new FileOutputStream(sLogFile, true);
                outputStream.write(message.getBytes());
                outputStream.flush();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private File initLogFile(Context context) {
        if (!isExternalStorageWritable()) {
            Log.e(TAG, "External Storage unavialable to write logs");
            return null;
        }
        File file = new File(context.getExternalFilesDir(null), LOG_FILE_PATH + PRIMARY_LOG_FILE_NAME);
        return file;
    }

    public static String getLogs() {
        if (sLogFile != null && sLogFile.exists()) {
            try {
                FileInputStream inputStream = new FileInputStream(sLogFile);
                return  new String(StreamsUtil.inputStreamToByteArray(inputStream));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /* Checks if external storage is available for read and write */
    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

}
