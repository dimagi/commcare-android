package org.commcare.android.tasks;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.dalvik.activities.CommCareWiFiDirectActivity;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public abstract class FormTransferTask extends CommCareTask<String, String, Boolean, CommCareWiFiDirectActivity>{
	
	public Context c;

    private static final int SOCKET_TIMEOUT = 50000;
    public static final String ACTION_SEND_FORM = "org.commcare.dalvik.services.SEND_FORM";
    public static final String ACTION_SEND_STRING = "org.commcare.dalvik.services.SEND_STRING";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
    public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";
    public static final String USERNAME = "user-name";

	public static final String REQUEST_RECEIVER_EXTRA = "result_receiver_extra_key";

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
    }
    
    public InputStream getFormInputStream(String fPath) throws FileNotFoundException{
    	Log.d(CommCareWiFiDirectActivity.TAG, "Getting form input stream");
    	InputStream is = null;
    	String filepath = fPath;
    	Log.d(CommCareWiFiDirectActivity.TAG, " fileinptutstream  with filepath: " + filepath);
    	is = new FileInputStream(filepath);
    	return is;
    	
    }

	@Override
	protected Boolean doTaskBackground(String... params) {
    	
    	Log.d(CommCareWiFiDirectActivity.TAG, " in form transfer onHandle");
    	
        Socket socket = new Socket();
        InputStream is;

        try {
            Log.d(CommCareWiFiDirectActivity.TAG, "Opening client socket with host: " + host +  " port, " + port);
            socket.bind(null);
            socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

            Log.d(CommCareWiFiDirectActivity.TAG, "Client socket - " + socket.isConnected());
            OutputStream stream = socket.getOutputStream();

            is = getFormInputStream(filepath);
            CommCareWiFiDirectActivity.copyFile(is, stream);
            is.close();

        } catch (IOException ioe) {
        	
            Log.e(CommCareWiFiDirectActivity.TAG, ioe.getMessage());
            publishProgress("Error opening input stream: " + ioe.getMessage());
            
        	return false;
        } finally {
            if (socket.isConnected()) {
               try {
                    socket.close();
                } catch (IOException e) {
                    // Give up
                    e.printStackTrace();
                }
            }
        }
        
        return true;
	}
    
}

