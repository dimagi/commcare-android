package org.commcare.android.tasks;

import android.content.Context;
import android.util.Log;

import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.dalvik.activities.CommCareWiFiDirectActivity;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public abstract class FormTransferTask extends CommCareTask<String, String, Boolean, CommCareWiFiDirectActivity>{
    
    public Context c;

    private static final int SOCKET_TIMEOUT = 50000;
    
    public static final int RESULT_SUCCESS = 0;

    public static final int BULK_TRANSFER_ID = 9575922;
    
    Long[] results;
    String host;
    String filepath;
    int port;
    
    public FormTransferTask(String host, String filepath, int port){
        this.taskId = BULK_TRANSFER_ID;
        this.host = host;
        this.filepath = filepath;
        this.port = port;

        TAG = FormTransferTask.class.getSimpleName();
    }
    
    public InputStream getFormInputStream(String fPath) throws FileNotFoundException{
        Log.d(TAG, "Getting form input stream");
        InputStream is = null;
        String filepath = fPath;
        Log.d(TAG, " fileinptutstream  with filepath: " + filepath);
        is = new FileInputStream(filepath);
        return is;
        
    }

    @Override
    protected Boolean doTaskBackground(String... params) {
        
        Log.d(TAG, " in form transfer onHandle");
        
        Socket socket = new Socket();
        InputStream is;

        try {
            Log.d(TAG, "Opening client socket with host: " + host +  " port, " + port);
            socket.bind(null);
            socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

            Log.d(TAG, "Client socket - " + socket.isConnected());
            OutputStream stream = socket.getOutputStream();

            is = getFormInputStream(filepath);
            CommCareWiFiDirectActivity.copyFile(is, stream);
            is.close();
            return true;
        } catch (IOException ioe) {
            
            Log.e(TAG, ioe.getMessage());
            publishProgress("Error opening input stream: " + ioe.getMessage());
            
            return false;
        } finally {
           try {
                socket.close();
            } catch (IOException e) {
                // Give up
                e.printStackTrace();
            }
        }
    }
    
}

