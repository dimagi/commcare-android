package org.commcare.android.tasks;

import java.io.FileInputStream;
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

    private static final int SOCKET_TIMEOUT = 5000;
    public static final String ACTION_SEND_FORM = "org.commcare.dalvik.services.SEND_FORM";
    public static final String ACTION_SEND_STRING = "org.commcare.dalvik.services.SEND_STRING";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
    public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";
    public static final String USERNAME = "user-name";

	public static final String REQUEST_RECEIVER_EXTRA = "result_receiver_extra_key";

	public static final int RESULT_SUCCESS = 0;
    
    Long[] results;
    
    public InputStream getFormInputStream(String fPath){
    	Log.d(CommCareWiFiDirectActivity.TAG, "Getting form input stream");
    	InputStream is = null;
    	String filepath = fPath;
    	Log.d(CommCareWiFiDirectActivity.TAG, " fileinptutstream  with filepath: " + filepath);
    	try{
    		is = new FileInputStream(filepath);
    	}
    	catch(Exception e){
    		Log.d(CommCareWiFiDirectActivity.TAG, " fileinptutstream error");
    	}
    	return is;
    	
    }

	@Override
	protected Boolean doTaskBackground(String... params) {
    	
    	Log.d(CommCareWiFiDirectActivity.TAG, " in form transfer onHandle");
    	
    	String host = params[0];
    	String filepath = params[1];
    	int port = Integer.valueOf(params[2]);
    	
        //String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
        Socket socket = new Socket();
        //int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);

        try {
            Log.d(CommCareWiFiDirectActivity.TAG, "Opening client socket - ");
            socket.bind(null);
            socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

            Log.d(CommCareWiFiDirectActivity.TAG, "Client socket - " + socket.isConnected());
            OutputStream stream = socket.getOutputStream();
                
            try {
                InputStream is = getFormInputStream(filepath);
                CommCareWiFiDirectActivity.copyFile(is, stream);
                Log.d(CommCareWiFiDirectActivity.TAG, "Client: Data written");
            } catch (Exception e) {
                Log.d(CommCareWiFiDirectActivity.TAG, e.toString());
                return false;
            }

        } catch (IOException e) {
            Log.e(CommCareWiFiDirectActivity.TAG, e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                if (socket.isConnected()) {
                    try {
                        socket.close();
                        return true;
                    } catch (IOException e) {
                        // Give up
                        e.printStackTrace();
                        return false;
                    }
                }
            }
        }
        
        return true;
        
    }
    
}

