// Copyright 2011 Google Inc. All Rights Reserved.

package org.commcare.dalvik.services;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Vector;

import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.framework.DeviceDetailFragment;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.dalvik.activities.CommCareWiFiDirectActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * A service that process each file transfer request i.e Intent by opening a
 * socket connection with the WiFi Direct Group Owner and writing the file
 */
public class FormTransferService extends IntentService {
	
	public Context c;

    private static final int SOCKET_TIMEOUT = 5000;
    public static final String ACTION_SEND_FORM = "org.commcare.dalvik.services.SEND_FORM";
    public static final String ACTION_SEND_STRING = "org.commcare.dalvik.services.SEND_STRING";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
    public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";
    public static final String USERNAME = "user-name";
    
    Long[] results;

    public FormTransferService(Context c, String name) {
    	super(name);
    	this.c = c;
        Log.d(CommCareWiFiDirectActivity.TAG, " FTS being made");
    }

    public FormTransferService() {
        super("FormTransferService");
    }
    
    public InputStream getFormInputStream(Intent intent){
    	Log.d(CommCareWiFiDirectActivity.TAG, "Getting form input stream");
    	InputStream is = null;
    	String filepath = intent.getExtras().getString(EXTRAS_FILE_PATH);
    	Log.d(CommCareWiFiDirectActivity.TAG, " fileinptutstream  with filepath: " + filepath);
    	try{
    		is = new FileInputStream(filepath);
    	}
    	catch(Exception e){
    		Log.d(CommCareWiFiDirectActivity.TAG, " fileinptutstream error");
    	}
    	return is;
    	
    }
    

    /*
     * (non-Javadoc)
     * @see android.app.IntentService#onHandleIntent(android.content.Intent)
     */
    @Override
    protected void onHandleIntent(Intent intent) {
    	
    	Log.d(CommCareWiFiDirectActivity.TAG, " in form transfer onHandle");

        Context context = getApplicationContext();
        if (intent.getAction().equals(ACTION_SEND_FORM)) {
            String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
            Socket socket = new Socket();
            int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);

            try {
                Log.d(CommCareWiFiDirectActivity.TAG, "Opening client socket - ");
                socket.bind(null);
                socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

                Log.d(CommCareWiFiDirectActivity.TAG, "Client socket - " + socket.isConnected());
                OutputStream stream = socket.getOutputStream();
                ContentResolver cr = context.getContentResolver();
                
                try {
                	InputStream is = getFormInputStream(intent);
                    CommCareWiFiDirectActivity.copyFile(is, stream);
                    Log.d(CommCareWiFiDirectActivity.TAG, "Client: Data written");
                } catch (Exception e) {
                    Log.d(CommCareWiFiDirectActivity.TAG, e.toString());
                }

            } catch (IOException e) {
                Log.e(CommCareWiFiDirectActivity.TAG, e.getMessage());
            } finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            // Give up
                            e.printStackTrace();
                        }
                    }
                }
            }

        }
    }
    
}
