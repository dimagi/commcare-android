package org.commcare.dalvik.activities;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Vector;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.DeviceDetailFragment;
import org.commcare.android.framework.DeviceListFragment;
import org.commcare.android.framework.DeviceListFragment.DeviceActionListener;
import org.commcare.android.framework.FileServerFragment;
import org.commcare.android.framework.FileServerFragment.FileServerListener;
import org.commcare.android.framework.WiFiDirectManagementFragment;
import org.commcare.android.framework.WiFiDirectManagementFragment.WifiDirectManagerListener;
import org.commcare.android.javarosa.AndroidLogger;
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
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
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
public class CommCareWiFiDirectActivity extends CommCareActivity<CommCareWiFiDirectActivity> implements DeviceActionListener, FileServerListener, WifiDirectManagerListener {
	
	public static final String TAG = "cc-wifidirect";

	public static final String KEY_NUMBER_DUMPED ="wd_num_dumped";
	
	public static final int UNZIP_TASK_ID = 392582;
	
	public WifiP2pManager mManager;
	public Channel mChannel;
	WiFiDirectBroadcastReceiver mReceiver;
	
	IntentFilter mIntentFilter;
	
	public enum wdState{send,receive,submit};
	
	Button discoverButton;
	Button sendButton;
	Button submitButton;
	Button changeModeButton;
	
	public static String baseDirectory;
	public static String sourceDirectory;
	public static String sourceZipDirectory;
	public static String receiveDirectory;
	public static String receiveZipDirectory;
	public static String writeDirectory;
	
	public TextView myStatusText;
	public TextView formCountText;
	public TextView stateHeaderText;
	public TextView stateStatusText;
	
	public static final int FILE_SERVER_TASK_ID = 129123;
	
	public wdState mState = wdState.send;
	
	public FormRecord[] cachedRecords;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.wifi_direct_main);
		
		myStatusText = (TextView)this.findViewById(R.id.my_status_text);
		
		formCountText = (TextView)this.findViewById(R.id.form_count_text);
		
		stateStatusText = (TextView)this.findViewById(R.id.wifi_state_status);
		
		stateHeaderText = (TextView)this.findViewById(R.id.wifi_state_header);
		
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
		
		submitButton = (Button)this.findViewById(R.id.submit_button);
		submitButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				submitFiles();
			}
			
		});
		
		changeModeButton = (Button)this.findViewById(R.id.reset_state_button);
		changeModeButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				changeState();
			}
			
		});
		
	    mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
	    mChannel = mManager.initialize(this, getMainLooper(), null);
	    mIntentFilter = new IntentFilter();
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
	    
	    showDialog(this, "Transfer, Receive, Submit?", "Do you want to transfer, receive, or submit forms?");
	}
	
	/*register the broadcast receiver */
	protected void onResume() {
	    super.onResume();
	    
	    Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "resuming wi-fi direct activity");
	    
        final WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);
		
		mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, fragment);
        registerReceiver(mReceiver, mIntentFilter);
        
        fragment.startReceiver(mManager, mChannel, mReceiver);
	    
	    updateStatusText();
	}
	/* unregister the broadcast receiver */
	@Override
	protected void onPause() {
	    super.onPause();
	    Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Pausing wi-fi direct activity");
	    unregisterReceiver(mReceiver);
	}
	
	public void hostGroup(){
		
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Hosting Wi-fi direct group");
		
        final FileServerFragment fsFragment = (FileServerFragment) getSupportFragmentManager()
                .findFragmentById(R.id.file_server_fragment);
		fsFragment.startServer(receiveZipDirectory);
		
		WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);
		
		if(!fragment.isWifiP2pEnabled()){
			Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "returning because Wi-fi direct is not available");
            Toast.makeText(CommCareWiFiDirectActivity.this, "WiFi Direct is Off - Turn it on, and press the \"Host\" button",
                    Toast.LENGTH_SHORT).show();
            //hostButton.setVisibility(View.VISIBLE);
            return;
		}

		mManager.createGroup(mChannel, fragment);
	}
	
	public void changeState(){
		showDialog(this, "Send, Receive, Submit?", "Do you want to send, receive, or submit forms?");
	}
	
	public void hostWiFiGroup(){
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "creating wi-fi group");
		WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);
		mManager.createGroup(mChannel, fragment);
	}
	
	public void showDialog(Activity activity, String title, CharSequence message) {
	    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
	    if (title != null)
	        builder.setTitle(title);
	    builder.setMessage(message);
	    builder.setNeutralButton("Receive Forms", new DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface dialog, int which) {
				beReceiver();
			}});
	    
	    builder.setNegativeButton("Send Forms", new DialogInterface.OnClickListener(){

			@Override
			public void onClick(DialogInterface dialog, int which) {
				beSender();
			}});
	    
	    builder.setPositiveButton("Submit Forms", new DialogInterface.OnClickListener() {
	        public void onClick(DialogInterface dialog, int id) {
	        	beSubmitter();
	        }});
	    
	    builder.show();
	}
	
	public void beSender(){
		
		WiFiDirectManagementFragment wifiFragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);
		
        DeviceListFragment fragmentList = (DeviceListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);
        
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_detail);
        
        FileServerFragment fsFragment = (FileServerFragment) getSupportFragmentManager()
                .findFragmentById(R.id.file_server_fragment);

		FragmentTransaction tr = getSupportFragmentManager().beginTransaction();
		
		tr.show(wifiFragment);
		tr.show(fragmentList);
		tr.show(fragmentDetails);
		tr.hide(fsFragment);
		tr.commit();
		
		wifiFragment.setIsHost(false);
		wifiFragment.resetConnectionGroup();
		
		
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Device designated as sender");
		resetData();
		mState = wdState.send;
		sendButton.setVisibility(View.VISIBLE);
		submitButton.setVisibility(View.GONE);
		discoverButton.setVisibility(View.VISIBLE);
		updateStatusText();
	}
	
	public void beReceiver(){
		
		WiFiDirectManagementFragment wifiFragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);
		
        DeviceListFragment fragmentList = (DeviceListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);
        
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_detail);
        
        FileServerFragment fsFragment = (FileServerFragment) getSupportFragmentManager()
                .findFragmentById(R.id.file_server_fragment);

		FragmentTransaction tr = getSupportFragmentManager().beginTransaction();
		
		tr.show(wifiFragment);
		tr.show(fragmentList);
		tr.show(fragmentDetails);
		tr.show(fsFragment);
		tr.commit();
		
		wifiFragment.setIsHost(true);
		wifiFragment.resetConnectionGroup();
		
		
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT,"Device designated as receiver");
		resetData();
		hostGroup();

		
		mState = wdState.receive;
		unzipFilesHelper();
		sendButton.setVisibility(View.GONE);
		updateStatusText();
		discoverButton.setVisibility(View.GONE);
		submitButton.setVisibility(View.GONE);
	}
	
	public void beSubmitter(){
		
		WiFiDirectManagementFragment wifiFragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);
		
        DeviceListFragment fragmentList = (DeviceListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);
        
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_detail);
        
        FileServerFragment fsFragment = (FileServerFragment) getSupportFragmentManager()
                .findFragmentById(R.id.file_server_fragment);

		FragmentTransaction tr = getSupportFragmentManager().beginTransaction();

		tr.hide(fsFragment);
		tr.hide(wifiFragment);
		tr.hide(fragmentList);
		tr.hide(fragmentDetails);
		tr.commit();
		
		wifiFragment.setIsHost(false);
		wifiFragment.resetConnectionGroup();
		
		mState = wdState.submit;
		
		updateStatusText();
		
		discoverButton.setVisibility(View.GONE);
		sendButton.setVisibility(View.GONE);
		submitButton.setVisibility(View.VISIBLE);
	}
	
	public void cleanPostSend(){
		
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "cleaning forms after successful Wi-fi direct transfer");
		
		// remove Forms from CC
		
		WipeTask mWipeTask = new WipeTask(getApplicationContext(), CommCareApplication._().getCurrentApp().getCommCarePlatform(), this.cachedRecords){

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
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "clean successful");
		updateStatusText();
	}

	public void submitFiles(){
		
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "submitting forms in Wi-fi direct activity");
		
		unzipFilesHelper();
		
		final String url = this.getString(R.string.PostURL);
		
		File receiveFolder = new File (writeDirectory);
		
		if(!receiveFolder.isDirectory() || !receiveFolder.exists()){
			myStatusText.setText(Localization.get("wifi.direct.submit.missing", new String[] {receiveFolder.getPath()}));
		}
		
		File[] files = receiveFolder.listFiles();
		
		if (files == null){
			myStatusText.setText("Phone has received no forms via Wi-fi direct for Submitting; did you mean to Send forms?");
			TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
			return;
		}
		
		final int formsOnSD = files.length;
				
		//if there're no forms to dump, just return
		if(formsOnSD == 0){
			myStatusText.setText(Localization.get("bulk.form.no.unsynced"));
			TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
			return;
		}
		
		SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
		SendTask<CommCareWiFiDirectActivity> mSendTask = new SendTask<CommCareWiFiDirectActivity>(getApplicationContext(), CommCareApplication._().getCurrentApp().getCommCarePlatform(), 
				settings.getString("PostURL", url), receiveFolder){
			
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
				Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Error submitting forms in wi-fi direct");
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
		
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Unzipping files in Wi-fi direct");
		Log.d(TAG, "creating unzip task");
		
		UnzipTask mUnzipTask = new UnzipTask() {

			@Override
			protected void deliverResult( CommCareWiFiDirectActivity receiver, Integer result) {
				Log.d(TAG, "delivering unzip result");
				if(result > 0){
					receiver.onUnzipSuccessful(result);
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
		
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Discovering Wi-fi direct peers");
		Log.d(TAG, "discoverPeers");
		
		WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);
		
		if(!fragment.isWifiP2pEnabled()){
            Toast.makeText(CommCareWiFiDirectActivity.this, "WiFi Direct is Off",
                    Toast.LENGTH_SHORT).show();
            return;
		}
		
        final DeviceListFragment dlFragment = (DeviceListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);
		dlFragment.onInitiateDiscovery();
        
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(CommCareWiFiDirectActivity.this, "Discovery Initiated",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
            	
            	Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Discovery of Wi-fi peers failed");
            	
            	if(reasonCode == 0){
                    Toast.makeText(CommCareWiFiDirectActivity.this, "Discovery Failed likely due to bad Wi-fi; please retry",
                            Toast.LENGTH_SHORT).show();
            	} else if(reasonCode == 2){
            		Toast.makeText(CommCareWiFiDirectActivity.this, "Discovery failed due to bad Wi-fi state; turn Wi-fi on and off, then retry",
                            Toast.LENGTH_SHORT).show();
            	} else{
            		Toast.makeText(CommCareWiFiDirectActivity.this, "Discovery failed with reason code: " + reasonCode,
                            Toast.LENGTH_SHORT).show();
            	}
            	
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

    @Override
    public void connect(WifiP2pConfig config) {
    	
    	Logger.log(AndroidLogger.TYPE_WIFI_DIRECT,"connecting to wi-fi peer");
    	
    	Log.d(TAG, "connect in activity");
    	
        mManager.connect(mChannel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
            	Logger.log(AndroidLogger.TYPE_WIFI_DIRECT,"Connection to peer failed");
                Toast.makeText(CommCareWiFiDirectActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void disconnect() {
    	Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "disconnecting from wi-fi direct group");
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
    	
    	Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Preparing File Transfer");

    	CommCareWiFiDirectActivity.deleteIfExists(sourceZipDirectory);
    	
        final WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);
    	
    	if(!fragment.getDeviceConnected()){
    		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Device not connected to Wi-Fi Direct group");
    		myStatusText.setText("This devices is not connected to any Wi-Fi Direct group.");
    		return;
    	}
    	
    	zipFiles();
    }
    
    public void onZipSuccesful(FormRecord[] records){
    	Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "successfully zipped files of size: " + records.length);
    	Log.d(CommCareWiFiDirectActivity.TAG, "Zip successful, attempting to send");
    	myStatusText.setText("Zip successful, attempting to send files...");
    	this.cachedRecords = records;
    	updateStatusText();
    	sendFiles();
    }
    
    public void onZipError(){
    	
    	FileUtil.deleteFile(new File(sourceDirectory));
    	
    	Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Error zipping files");
    	
    	Log.d(CommCareWiFiDirectActivity.TAG, "Zip unsuccesful");
    	
    }
    
    public void onUnzipSuccessful(Integer result){
    	
    	Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Successfully unzipped files");
    	
        Toast.makeText(CommCareWiFiDirectActivity.this, "Received " + result.toString() + " Files Successfully!",
                Toast.LENGTH_SHORT).show();
    	
    	myStatusText.setText("Receive Successful!");
    	
    	if(!FileUtil.deleteFile(new File(receiveDirectory))){
    		Log.d(TAG, "source zip not succesfully deleted");
    	}
    	
    	updateStatusText();
    	
    }
    
    public void zipFiles(){
    	Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Zipping Files");
    	Log.d(CommCareWiFiDirectActivity.TAG, "Zipping Files2");
			ZipTask mZipTask = new ZipTask(this, CommCareApplication._().getCurrentApp().getCommCarePlatform()){

				@Override
				protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
					receiver.updateProgress(taskId, update[0]);
					receiver.myStatusText.setText(update[0]);
				}

				@Override
				protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
					receiver.myStatusText.setText("Error zipping files");
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
    	Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Sending Files via Wi-fi Direct");
    	TextView statusText = myStatusText;
    	statusText.setText("Sending files..." );
    	Log.d(CommCareWiFiDirectActivity.TAG, "Starting form transfer task" );
    	
        final WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);
        
        String address = fragment.getHostAddress();
    	
    	FormTransferTask mTransferTask = new FormTransferTask(address,sourceZipDirectory,8988){

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
    	
    	Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "File Send Successful");
    	
        Toast.makeText(CommCareWiFiDirectActivity.this, "File Send Successful!",
                Toast.LENGTH_SHORT).show();
    	
    	updateStatusText();
    	myStatusText.setText("Forms Tranferred Successfully!");
    	this.cleanPostSend();
    }
    
    public void onSendFail(){
    	Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Error Sending Files");
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
    	
    	if(mState.equals(wdState.send)){
    		stateHeaderText.setText("You are in Transfer Form Mode");
    		formCountText.setText("Phone has " + numUnsyncedForms + " unsent forms.");
    		stateStatusText.setText("You are in Transfer Form mode. This will allow you to transfer forms from this device to another device via Wi-Fi Direct.");
    	} else if(mState.equals(wdState.receive)){
    		stateHeaderText.setText("You are in Receive Form Mode");
    		stateStatusText.setText("This will allow you to receive forms on this device from another device via Wi-Fi Direct");
    		formCountText.setText("SD Card has " + numUnsubmittedForms + " collected forms.");
    	} else{
    		stateHeaderText.setText("You are in Submit Form Mode");
    		stateStatusText.setText("This mode will allow you to submit forms to the CommCare Server if you have an internet connection.");
    		formCountText.setText("SD Card has " + numUnsubmittedForms + " unsubmitted forms.");
    	}
    	
	}
	
    public static boolean copyFile(InputStream inputStream, OutputStream out) {
    	
    	Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Copying file in Wi-Fi Direct Activity");
    	
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
            Log.d(CommCareWiFiDirectActivity.TAG, e.toString());
            return false;
        }
        return true;
    }

	@Override
	public void onFormsCopied(String result) {
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Copied files successfully");
		Log.d(CommCareWiFiDirectActivity.TAG, "onCopySuccess");
		this.unzipFiles(result);
	}

	@Override
	public void updatePeers() {
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Wi-Fi direct peers updating");
		mManager.requestPeers(mChannel, (PeerListListener) this.getSupportFragmentManager()
               .findFragmentById(R.id.frag_list));
		
	}

	@Override
	public void updateDeviceStatus(WifiP2pDevice mDevice) {
		Logger.log(AndroidLogger.TYPE_WIFI_DIRECT, "Wi-fi direct status updating");
        DeviceListFragment fragment = (DeviceListFragment) this.getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);
         
         fragment.updateThisDevice(mDevice);
		
	}

}
