package org.commcare.dalvik.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.DeviceDetailFragment;
import org.commcare.android.framework.DeviceListFragment;
import org.commcare.android.framework.DeviceListFragment.DeviceActionListener;
import org.commcare.android.tasks.FormTransferTask;
import org.commcare.android.tasks.SendTask;
import org.commcare.android.tasks.UnzipTask;
import org.commcare.android.tasks.WipeTask;
import org.commcare.android.tasks.ZipTask;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.FileUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.services.WiFiDirectBroadcastReceiver;
import org.javarosa.core.services.locale.Localization;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class CommCareWiFiDirectActivity extends CommCareActivity<CommCareWiFiDirectActivity> implements ChannelListener, DeviceActionListener, ConnectionInfoListener, ActionListener {
	
	public static final String TAG = "cc-wifidirect";

	public static final String KEY_NUMBER_DUMPED ="wd_num_dumped";
	
	public static final int UNZIP_TASK_ID = 392582;
	
	WifiP2pManager mManager;
	Channel mChannel;
	BroadcastReceiver mReceiver;
	
	IntentFilter mIntentFilter;
	
	Button discoverButton;
	Button sendButton;
	Button hostButton;
	Button submitButton;
	Button resetButton;
	
	public static String baseDirectory;
	public static String sourceDirectory;
	public static String sourceZipDirectory;
	public static String receiveDirectory;
	public static String receiveZipDirectory;
	public static String writeDirectory;
	
	WifiP2pInfo info;
	
	private boolean isWifiP2pEnabled = false;
	
	public TextView ownerStatusText;
	public TextView myStatusText;
	public TextView formCountText;
	public TextView serverStatusText;
	
	public static final int FILE_SERVER_TASK_ID = 129123;
	
	public FormRecord[] cachedRecords;
	
	boolean isConnected;
	
	boolean isSender = false;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.wifi_direct_main);
		
		ownerStatusText= (TextView)this.findViewById(R.id.owner_status_text);
		
		myStatusText = (TextView)this.findViewById(R.id.my_status_text);
		
		formCountText = (TextView)this.findViewById(R.id.form_count_text);
		
		serverStatusText = (TextView)this.findViewById(R.id.server_status_text);
		
		try{
			
			ArrayList<String> externalMounts = FileUtil.getExternalMounts();
			String baseDir = externalMounts.get(0);
			
			baseDirectory = baseDir + "/" + Localization.get("wifi.direct.base.folder");
			sourceDirectory = baseDirectory + "/source";
			sourceZipDirectory = baseDirectory + "/zipSource.zip";
			receiveDirectory = baseDirectory + "/receive";
			receiveZipDirectory = receiveDirectory + "/zipDest";
			writeDirectory = baseDirectory + "/write";
			
		} catch(NullPointerException npe){
			myStatusText.setText("Can't access external SD Card");
			TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
		}
		
		discoverButton = (Button)this.findViewById(R.id.discover_button);
		discoverButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				discoverPeers();
			}
			
		});
		
		sendButton = (Button)this.findViewById(R.id.send_button);
		sendButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				prepareFileTransfer();
			}
			
		});
		
		hostButton = (Button)this.findViewById(R.id.host_button);
		hostButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				hostGroup();
			}
			
		});
		
		resetButton = (Button)this.findViewById(R.id.reset_button);
		resetButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				resetData();
			}
			
		});
		
		submitButton = (Button)this.findViewById(R.id.submit_button);
		submitButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				submitFiles();
			}
			
		});
		
	    mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
	    mChannel = mManager.initialize(this, getMainLooper(), null);
	    mIntentFilter = new IntentFilter();
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
	    
	    showDialog(this, "Send or Receive", "Do you want to send or receive forms?");
	}
	
	/*register the broadcast receiver */
	protected void onResume() {
	    super.onResume();
	    mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        registerReceiver(mReceiver, mIntentFilter);
	    updateStatusText();
	}
	/* unregister the broadcast receiver */
	@Override
	protected void onPause() {
	    super.onPause();
	    unregisterReceiver(mReceiver);
	}
	
	public void hostGroup(){
		
		if(!isWifiP2pEnabled){
            Toast.makeText(CommCareWiFiDirectActivity.this, "WiFi Direct is Off",
                    Toast.LENGTH_SHORT).show();
            return;
		}
		
		FileServerAsyncTask mFileServer = new FileServerAsyncTask(this, this);
		
		//Execute on a true multithreaded chain. We should probably replace all of our calls with this
		//but this is the big one for now.
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
			mFileServer.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		} else {
			mFileServer.execute();
		}
		mManager.createGroup(mChannel, this);
	}
	
	public void showDialog(Activity activity, String title, CharSequence message) {
	    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
	    if (title != null)
	        builder.setTitle(title);
	    builder.setMessage(message);
	    builder.setPositiveButton("Receive Forms", new DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface dialog, int which) {
				beReceiver();
			}});
	    
	    builder.setNegativeButton("Send Forms", new DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface dialog, int which) {
				beSender();
			}});
	    
	    builder.show();
	}
	
	public void beSender(){
		isSender = true;
		hostButton.setVisibility(View.GONE);
		submitButton.setVisibility(View.GONE);
		updateStatusText();
	}
	
	public void beReceiver(){
		isSender = false;
		unzipFilesHelper();
		sendButton.setVisibility(View.GONE);
		updateStatusText();
	}
	
	public void cleanPostSend(){
		
		// remove Forms from CC
		
		WipeTask mWipeTask = new WipeTask(getApplicationContext(), CommCareApplication._().getCurrentApp().getCommCarePlatform(), myStatusText, this.cachedRecords){

			@Override
			protected void deliverResult(CommCareWiFiDirectActivity receiver,
					Boolean result) {
				
				receiver.onCleanSuccessful();

			}

			@Override
			protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
				receiver.updateProgress(WipeTask.WIPE_TASK_ID, update[0]);
				receiver.myStatusText.setText(update[0]);
			}

			@Override
			protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
				receiver.myStatusText.setText("Error wiping forms: " + e.getMessage());
				receiver.TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
			}
		};
		
		mWipeTask.connect(CommCareWiFiDirectActivity.this);
		mWipeTask.execute();
		
		FileUtil.deleteFile(new File(sourceDirectory));
		FileUtil.deleteFile(new File(sourceZipDirectory));
		
		this.cachedRecords = null;
		
	}
	
	protected void onCleanSuccessful() {
		updateStatusText();
	}

	public void submitFiles(){
		
		unzipFilesHelper();
		
		final String url = this.getString(R.string.PostURL);
		
		File receiveFolder = new File (writeDirectory);
		
		if(!receiveFolder.isDirectory() || !receiveFolder.exists()){
			myStatusText.setText(Localization.get("wifi.direct.submit.missing", new String[] {receiveFolder.getPath()}));
		}
		
		File[] files = receiveFolder.listFiles();
		
		final int formsOnSD = files.length;
				
		//if there're no forms to dump, just return
		if(formsOnSD == 0){
			myStatusText.setText(Localization.get("bulk.form.no.unsynced"));
			TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
			return;
		}
		
		SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
		SendTask<CommCareWiFiDirectActivity> mSendTask = new SendTask<CommCareWiFiDirectActivity>(getApplicationContext(), CommCareApplication._().getCurrentApp().getCommCarePlatform(), 
				settings.getString("PostURL", url), myStatusText, receiveFolder){
			
			@Override
			protected void deliverResult( CommCareWiFiDirectActivity receiver, Boolean result) {
				
				if(result == Boolean.TRUE){
			        Intent i = new Intent(getIntent());
			        i.putExtra(KEY_NUMBER_DUMPED, formsOnSD);
					receiver.setResult(BULK_SEND_ID, i);
					receiver.finish();
					return;
				} else {
					//assume that we've already set the error message, but make it look scary
					receiver.TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
				}
			}

			@Override
			protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
				receiver.updateProgress(BULK_SEND_ID, update[0]);
				receiver.myStatusText.setText(update[0]);
			}

			@Override
			protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
				receiver.myStatusText.setText(Localization.get("bulk.form.error", new String[] {e.getMessage()}));
				receiver.TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
			}
		};
		mSendTask.connect(CommCareWiFiDirectActivity.this);
		mSendTask.execute();
	}
	
	public boolean unzipFilesHelper(){
		
		File receiveZipDir = new File(receiveDirectory);
		
		if(!receiveZipDir.exists() || !(receiveZipDir.isDirectory())){
			return false;
		}
		
		File[] zipDirContents = receiveZipDir.listFiles();
		
		if(zipDirContents.length < 1){
			return false;
		}
		
		myStatusText.setText("Zip file exists, unzipping...");
		
		for(int i=0; i< zipDirContents.length; i++){
			unzipFiles(zipDirContents[i].getAbsolutePath());
		}
		
		return true;
	}
	
	public void unzipFiles(String fn){
		
		Log.d(TAG, "creating unzip task");
		
		UnzipTask mUnzipTask = new UnzipTask() {

			@Override
			protected void deliverResult( CommCareWiFiDirectActivity receiver, Boolean result) {
				Log.d(TAG, "delivering unzip result");
				if(result == Boolean.TRUE){
					receiver.onUnzipSuccessful();
					return;
				} else {
					//assume that we've already set the error message, but make it look scary
					receiver.TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
				}
			}

			@Override
			protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
				Log.d(TAG, "delivering unzip upate");
				receiver.updateProgress(CommCareTask.GENERIC_TASK_ID, update[0]);
				receiver.myStatusText.setText(update[0]);
			}

			@Override
			protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
				Log.d(TAG, "unzip deliver error: " + e.getMessage());
				receiver.myStatusText.setText(Localization.get("mult.install.error", new String[] {e.getMessage()}));
				receiver.TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
			}
		};
		
		mUnzipTask.connect(CommCareWiFiDirectActivity.this);
		Log.d(TAG, "executing task with: " + fn + " , " + writeDirectory);
		mUnzipTask.execute(fn, writeDirectory);
	}
	
	/* if successful, broadcasts WIFI_P2P_Peers_CHANGED_ACTION intent with list of peers
	 * received in WiFiDirectBroadcastReceiver class
	 */
	public void discoverPeers(){
		
		Log.d(TAG, "discoverPeers");
		
		if(!isWifiP2pEnabled){
            Toast.makeText(CommCareWiFiDirectActivity.this, "WiFi Direct is Off",
                    Toast.LENGTH_SHORT).show();
            return;
		}
		
        final DeviceListFragment fragment = (DeviceListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);
		fragment.onInitiateDiscovery();
        
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(CommCareWiFiDirectActivity.this, "Discovery Initiated",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(CommCareWiFiDirectActivity.this, "Discovery Failed : " + reasonCode,
                        Toast.LENGTH_SHORT).show();
            }
        });
	}
	
    public void resetData() {
        DeviceListFragment fragmentList = (DeviceListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_detail);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
        if (fragmentDetails != null) {
            fragmentDetails.resetViews();
        }
    }

    @Override
    public void showDetails(WifiP2pDevice device) {
    	
    	Log.d(TAG, "showDetails");
    	
        DeviceDetailFragment fragment = (DeviceDetailFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.showDetails(device);
    }
    
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
        if(!isWifiP2pEnabled){
        	this.isConnected = false;
        }
    }

    @Override
    public void connect(WifiP2pConfig config) {
    	
    	Log.d(TAG, "connect in activity");
    	
        mManager.connect(mChannel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(CommCareWiFiDirectActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void disconnect() {
        final DeviceDetailFragment fragment = (DeviceDetailFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.resetViews();
        mManager.removeGroup(mChannel, new ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

            }

            @Override
            public void onSuccess() {
                fragment.getView().setVisibility(View.GONE);
            }

        });
    }

    @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (mManager != null) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            //resetData();
            mManager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void cancelDisconnect() {

        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (mManager != null) {
            final DeviceListFragment fragment = (DeviceListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.frag_list);
            if (fragment.getDevice() == null
                    || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
                    || fragment.getDevice().status == WifiP2pDevice.INVITED) {

                mManager.cancelConnect(mChannel, new ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(CommCareWiFiDirectActivity.this, "Aborting connection",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(CommCareWiFiDirectActivity.this,
                                "Connect abort request failed. Reason Code: " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

    }
    
    public static void deleteIfExists(String filePath){
    	File toDelete = new File(filePath);
    	if(toDelete.exists()){
    		toDelete.delete();
    	}
    }
    
    public void prepareFileTransfer(){
    	Log.d(CommCareWiFiDirectActivity.TAG, "Preparing File Transfer");
    	CommCareWiFiDirectActivity.deleteIfExists(sourceZipDirectory);
    	
    	if(!isConnected){
    		myStatusText.setText("This devices is not connected to any Wi-Fi Direct group.");
    		return;
    	}
    	
    	zipFiles();
    }
    
    public void onZipSuccesful(FormRecord[] records){
    	Log.d(CommCareWiFiDirectActivity.TAG, "Zup successful, attempting to send");
    	myStatusText.setText("Zip successful, attempting to send files...");
    	this.cachedRecords = records;
    	updateStatusText();
    	sendFiles();
    }
    
    public void onZipError(){
    	Log.d(CommCareWiFiDirectActivity.TAG, "Zup unsuccesful");
    	
    }
    
    public void onUnzipSuccessful(){
    	myStatusText.setText("Unzip successful");
    	
    	if(!FileUtil.deleteFile(new File(receiveDirectory))){
    		Log.d(TAG, "source zip not succesfully deleted");
    	}
    	
    	updateStatusText();
    	
    }
    
    public void zipFiles(){
    	Log.d(CommCareWiFiDirectActivity.TAG, "Zipping Files2");
			ZipTask mZipTask = new ZipTask(this, CommCareApplication._().getCurrentApp().getCommCarePlatform(), 
					myStatusText){

				@Override
				protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
					receiver.updateProgress(taskId, update[0]);
					receiver.myStatusText.setText(update[0]);
				}

				@Override
				protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
					receiver.myStatusText.setText("error zipping files");
					receiver.TransplantStyle(receiver.myStatusText, R.layout.template_text_notification_problem);
				}

				@Override
				protected void deliverResult(
						CommCareWiFiDirectActivity receiver, FormRecord[] result) {
					if(result != null){
						receiver.onZipSuccesful(result);
						return;
					} else {
						receiver.onZipError();
						receiver.TransplantStyle(receiver.myStatusText, R.layout.template_text_notification_problem);
						return;
					}
					
				}

			};
			mZipTask.connect(CommCareWiFiDirectActivity.this);
			mZipTask.execute();
    }
    
    public void sendFiles(){
    	TextView statusText = myStatusText;
    	statusText.setText("Sending files..." );
    	Log.d(CommCareWiFiDirectActivity.TAG, "Starting form transfer task" );
    	
    	FormTransferTask mTransferTask = new FormTransferTask(info.groupOwnerAddress.getHostAddress(),sourceZipDirectory,8988){

			@Override
			protected void deliverResult(CommCareWiFiDirectActivity receiver,
					Boolean result) {
				if(result == Boolean.TRUE){
					receiver.onSendSuccessful();
					return;
				} else {
					receiver.onSendFail();
					receiver.TransplantStyle(receiver.myStatusText, R.layout.template_text_notification_problem);
					return;
				}
				
			}

			@Override
			protected void deliverUpdate(CommCareWiFiDirectActivity receiver,
					String... update) {
				receiver.updateProgress(taskId, update[0]);
				receiver.myStatusText.setText(update[0]);
			}

			@Override
			protected void deliverError(CommCareWiFiDirectActivity receiver,
					Exception e) {
				receiver.myStatusText.setText("Error sending files with exception: " + e.getMessage());
				receiver.TransplantStyle(receiver.myStatusText, R.layout.template_text_notification_problem);
				
			}
    		
    	};
    	
    	mTransferTask.connect(CommCareWiFiDirectActivity.this);
    	mTransferTask.execute();
    	
        Log.d(CommCareWiFiDirectActivity.TAG, "Task started");
    }
    
    public void onSendSuccessful(){
    	updateStatusText();
    	myStatusText.setText("Forms Tranferred Successfully!");
    	this.cleanPostSend();
    }
    
    public void onSendFail(){
    	// present an error
    }
    
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
        switch (id) {
        case ZipTask.ZIP_TASK_ID:
        	    ProgressDialog mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setTitle("Zipping Forms...");
                mProgressDialog.setMessage("CommCare is compressing your data for transfer");
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                return mProgressDialog;
        case UnzipTask.UNZIP_TASK_ID:
	        	mProgressDialog = new ProgressDialog(this);
	            mProgressDialog.setTitle("Unzipping forms");
	            mProgressDialog.setMessage("CommCare is decompressing your forms onto your SD card");
	            mProgressDialog.setIndeterminate(true);
	            mProgressDialog.setCancelable(false);
	            return mProgressDialog;
        case SendTask.BULK_SEND_ID:
        	mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setTitle("Submitting Forms");
            mProgressDialog.setMessage("CommCare is submitting your forms to the server");
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            return mProgressDialog;
        case FormTransferTask.BULK_TRANSFER_ID:
        	mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setTitle("Sending Forms");
            mProgressDialog.setMessage("CommCare is sending your forms to your peer");
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            return mProgressDialog;
        case FILE_SERVER_TASK_ID:
        	mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setTitle("Starting Receiving Files");
            mProgressDialog.setMessage("CommCare is receiving files");
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            return mProgressDialog;
        case WipeTask.WIPE_TASK_ID:
        	mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setTitle("Wiping forms");
            mProgressDialog.setMessage("Cleaning up after transfer");
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            return mProgressDialog;
        }
        return null;
	}

	@Override
	public void onConnectionInfoAvailable(WifiP2pInfo info) {
    	Log.d(CommCareWiFiDirectActivity.TAG, "activity onConnectionInfoAvailable");
    	
    	this.info = info;

        setIsOwner(info.groupFormed && info.isGroupOwner);
        
        setDeviceConnected(info.groupFormed);
        
        myStatusText.setText("Connected to group");
        
	}
	
    public void setDeviceConnected(boolean isConnected) {
    	this.isConnected = isConnected;
	}

	public static class FileServerAsyncTask extends AsyncTask<Void, String, String> {

        private Context context;
        private TextView statusText;
        private CommCareWiFiDirectActivity mListener;

        /**
         * @param context
         * @param statusText
         */
        public FileServerAsyncTask(Context context, CommCareWiFiDirectActivity mListener) {
        	Log.d(CommCareWiFiDirectActivity.TAG, "new fileasync task");
            this.context = context;
            this.statusText = mListener.serverStatusText;
            this.mListener = mListener;
            
        }

        @Override
        protected String doInBackground(Void... params) {
        	try {
        		while(true) {
        			
        			//statusText.setText("Ready to accept new file transfer.");
        			
        			ServerSocket serverSocket = new ServerSocket(8988);
        			Socket client = serverSocket.accept();
        			
        			long time = System.currentTimeMillis();
        			
        			String finalFileName = receiveZipDirectory + time + ".zip";
        			
        			Log.d(CommCareWiFiDirectActivity.TAG, "server: copying files " + finalFileName);
        			
        			final File f = new File(finalFileName);

        			File dirs = new File(f.getParent());
        			if (!dirs.exists())
        				dirs.mkdirs();
        			f.createNewFile();

        			Log.d(CommCareWiFiDirectActivity.TAG, "server: copying files " + f.toString());
        			InputStream inputstream = client.getInputStream();
        			copyFile(inputstream, new FileOutputStream(f));
        			serverSocket.close();
        			publishProgress(f.getAbsolutePath());
        		} 
        	}catch (IOException e) {
        			Log.e(CommCareWiFiDirectActivity.TAG, e.getMessage());
        			//statusText.setText("File server stopped with IOException.");
        			return null;
        	}
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {
            //statusText.setText("Stopped file server.");
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a server socket");
        }
        
        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onProgressUpdate(String ... params){
        	statusText.setText("File copied - " + params[0]);
        	mListener.unzipFiles(params[0]);
        }

    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
    	Log.d(CommCareWiFiDirectActivity.TAG, "Copying file");
    	if(inputStream == null){
    		Log.d(CommCareWiFiDirectActivity.TAG, "Input Null");
    	}
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
            	Log.d(CommCareWiFiDirectActivity.TAG, "Copying file : " + buf);
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(CommCareWiFiDirectActivity.TAG, "copying error: " +  e.toString());
            return false;
        }
        Log.d(CommCareWiFiDirectActivity.TAG, "Copying file completed");
        return true;
    }

	@Override
	public void onFailure(int reason) {
		Log.d(CommCareWiFiDirectActivity.TAG, "new onFailure: " + reason);
		
	}

	@Override
	public void onSuccess() {
		Log.d(CommCareWiFiDirectActivity.TAG, "new onSuccess");
		
	}
	
	public void setIsOwner(boolean owner){
		if(owner){
			ownerStatusText.setText("This device is the owner.");
		}
		else{
			ownerStatusText.setText("This device is not the owner.");
		}
	}
	
	public void updateStatusText(){
		
    	SqlStorage<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
    	//Get all forms which are either unsent or unprocessed
    	Vector<Integer> ids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_UNSENT});
    	ids.addAll(storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_COMPLETE}));
    	
    	int numUnsyncedForms = ids.size();
    	
    	int numUnsubmittedForms = 0;
    	
    	File wDirectory = new File(writeDirectory);
    	
    	if(!wDirectory.exists() || !wDirectory.isDirectory()){
    		numUnsubmittedForms = 0;
    	}
    	else{
    		numUnsubmittedForms = wDirectory.listFiles().length;
    	}
    	
    	if(isSender){
    		formCountText.setText("Phone has " + numUnsyncedForms + " unsent forms.");
    	}else{
    		formCountText.setText("SD Card has " + numUnsubmittedForms + " unsubmitted forms.");
    	}
    	
	}

}
