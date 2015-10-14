package org.commcare.dalvik.activities;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
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

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.framework.DeviceDetailFragment;
import org.commcare.android.framework.DeviceListFragment;
import org.commcare.android.framework.DeviceListFragment.DeviceActionListener;
import org.commcare.android.framework.FileServerFragment;
import org.commcare.android.framework.FileServerFragment.FileServerListener;
import org.commcare.android.framework.SessionAwareCommCareActivity;
import org.commcare.android.framework.WiFiDirectManagementFragment;
import org.commcare.android.framework.WiFiDirectManagementFragment.WifiDirectManagerListener;
import org.commcare.android.tasks.FormTransferTask;
import org.commcare.android.tasks.SendTask;
import org.commcare.android.tasks.UnzipTask;
import org.commcare.android.tasks.WipeTask;
import org.commcare.android.tasks.ZipTask;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.FileUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.AlertDialogFactory;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.dalvik.services.WiFiDirectBroadcastReceiver;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class CommCareWiFiDirectActivity extends SessionAwareCommCareActivity<CommCareWiFiDirectActivity> implements DeviceActionListener, FileServerListener, WifiDirectManagerListener {

    public static final String TAG = CommCareWiFiDirectActivity.class.getSimpleName();

    public static final String KEY_NUMBER_DUMPED ="wd_num_dumped";

    public static final int UNZIP_TASK_ID = 392582;

    public WifiP2pManager mManager;
    public Channel mChannel;
    WiFiDirectBroadcastReceiver mReceiver;

    IntentFilter mIntentFilter;

    public enum wdState{send,receive,submit}

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
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        setContentView(R.layout.wifi_direct_main);

        myStatusText = (TextView)this.findViewById(R.id.my_status_text);

        formCountText = (TextView)this.findViewById(R.id.form_count_text);

        stateStatusText = (TextView)this.findViewById(R.id.wifi_state_status);

        stateHeaderText = (TextView)this.findViewById(R.id.wifi_state_header);
        
        String baseDir = FileUtil.getDumpDirectory(this);
        
        if(baseDir == null){
            Toast.makeText(CommCareWiFiDirectActivity.this, "Wi-Fi Direct Requires an External SD Card",
                    Toast.LENGTH_LONG).show();
            this.setResult(RESULT_CANCELED);
            finish();
            
        }
        
        baseDirectory = baseDir + "/" + Localization.get("wifi.direct.base.folder");
        sourceDirectory = baseDirectory + "/source";
        sourceZipDirectory = baseDirectory + "/zipSource.zip";
        receiveDirectory = baseDirectory + "/receive";
        receiveZipDirectory = receiveDirectory + "/zipDest";
        writeDirectory = baseDirectory + "/write";
        
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

        Logger.log(TAG, "resuming wi-fi direct activity");

        final WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);

        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, fragment);
        registerReceiver(mReceiver, mIntentFilter);

        fragment.startReceiver(mManager, mChannel, mReceiver);

        updateStatusText();
    }

    /**
     * unregister the broadcast receiver
     */
    @Override
    protected void onPause() {
        super.onPause();
        Logger.log(TAG, "Pausing wi-fi direct activity");
        unregisterReceiver(mReceiver);
    }

    public void hostGroup(){

        Logger.log(TAG, "Hosting Wi-fi direct group");

        final FileServerFragment fsFragment = (FileServerFragment) getSupportFragmentManager()
                .findFragmentById(R.id.file_server_fragment);
        fsFragment.startServer(receiveZipDirectory);

        WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);

        if(!fragment.isWifiP2pEnabled()){
            Logger.log(TAG, "returning because Wi-fi direct is not available");
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
        Logger.log(TAG, "creating wi-fi group");
        WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);
        mManager.createGroup(mChannel, fragment);
    }

    public void showDialog(Activity activity, String title, String message) {
        AlertDialogFactory factory = new AlertDialogFactory(activity, title, message);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch(which) {
                    case AlertDialog.BUTTON_POSITIVE:
                        beSubmitter();
                        break;
                    case AlertDialog.BUTTON_NEUTRAL:
                        beReceiver();
                        break;
                    case AlertDialog.BUTTON_NEGATIVE:
                        beSender();
                        break;
                }
            }
        };
        factory.setNeutralButton(localize("wifi.direct.receive.forms"), listener);
        factory.setNegativeButton(localize("wifi.direct.transfer.forms"), listener);
        factory.setPositiveButton(localize("wifi.direct.submit.forms"), listener);
        factory.showDialog();
    }

    public void beSender(){

        myStatusText.setText("Entered Send Mode");

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


        Logger.log(TAG, "Device designated as sender");
        resetData();
        mState = wdState.send;
        sendButton.setVisibility(View.VISIBLE);
        submitButton.setVisibility(View.GONE);
        discoverButton.setVisibility(View.VISIBLE);
        updateStatusText();
    }

    public void beReceiver(){

        myStatusText.setText("Entered Receive Mode");

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

        Logger.log(TAG,"Device designated as receiver");
        resetData();
        hostGroup();


        mState = wdState.receive;
        sendButton.setVisibility(View.GONE);
        updateStatusText();
        discoverButton.setVisibility(View.GONE);
        submitButton.setVisibility(View.GONE);
    }

    public void beSubmitter(){

        unzipFilesHelper();

        Logger.log(TAG,"Device designated as submitter");

        myStatusText.setText("Entered Submit Mode");

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

        Logger.log(TAG, "cleaning forms after successful Wi-fi direct transfer");

        // remove Forms from CC

        WipeTask mWipeTask = new WipeTask(getApplicationContext(), this.cachedRecords){
            @Override
            protected void deliverResult(CommCareWiFiDirectActivity receiver,
                    Boolean result) {
                receiver.onCleanSuccessful();
            }

            @Override
            protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
                receiver.updateProgress(update[0], WipeTask.WIPE_TASK_ID);
                receiver.myStatusText.setText(update[0]);
            }

            @Override
            protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
                receiver.myStatusText.setText("Error wiping forms: " + e.getMessage());
                receiver.transplantStyle(myStatusText, R.layout.template_text_notification_problem);
            }
        };

        mWipeTask.connect(CommCareWiFiDirectActivity.this);
        mWipeTask.execute();

        FileUtil.deleteFileOrDir(new File(sourceDirectory));
        FileUtil.deleteFileOrDir(new File(sourceZipDirectory));

        this.cachedRecords = null;

    }

    protected void onCleanSuccessful() {
        Logger.log(TAG, "clean successful");
        updateStatusText();
    }

    public void submitFiles(){

        Logger.log(TAG, "submitting forms in Wi-fi direct activity");

        unzipFilesHelper();

        final String url = this.getString(R.string.PostURL);

        File receiveFolder = new File (writeDirectory);

        if(!receiveFolder.isDirectory() || !receiveFolder.exists()){
            myStatusText.setText(Localization.get("wifi.direct.submit.missing", new String[] {receiveFolder.getPath()}));
        }

        File[] files = receiveFolder.listFiles();

        if (files == null){
            myStatusText.setText("Phone has received no forms via Wi-fi direct for Submitting; did you mean to Send forms?");
            transplantStyle(myStatusText, R.layout.template_text_notification_problem);
            return;
        }

        final int formsOnSD = files.length;

        //if there're no forms to dump, just return
        if(formsOnSD == 0){
            myStatusText.setText(Localization.get("bulk.form.no.unsynced"));
            transplantStyle(myStatusText, R.layout.template_text_notification_problem);
            return;
        }

        SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
        SendTask<CommCareWiFiDirectActivity> mSendTask = new SendTask<CommCareWiFiDirectActivity>(getApplicationContext(),
                settings.getString("PostURL", url), receiveFolder){

            @Override
            protected void deliverResult(CommCareWiFiDirectActivity receiver, Boolean result) {
                if(result == Boolean.TRUE){
                    Intent i = new Intent(getIntent());
                    i.putExtra(KEY_NUMBER_DUMPED, formsOnSD);
                    receiver.setResult(BULK_SEND_ID, i);
                    receiver.finish();
                } else {
                    //assume that we've already set the error message, but make it look scary
                    receiver.transplantStyle(myStatusText, R.layout.template_text_notification_problem);
                }
            }

            @Override
            protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
                receiver.updateProgress(update[0], BULK_SEND_ID);
                receiver.myStatusText.setText(update[0]);
            }

            @Override
            protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
                Logger.log(TAG, "Error submitting forms in wi-fi direct");
                receiver.myStatusText.setText(Localization.get("bulk.form.error", new String[] {e.getMessage()}));
                receiver.transplantStyle(myStatusText, R.layout.template_text_notification_problem);
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

        Logger.log(TAG, "Unzipping files in Wi-fi direct");
        Log.d(TAG, "creating unzip task");

        UnzipTask<CommCareWiFiDirectActivity> mUnzipTask = new UnzipTask<CommCareWiFiDirectActivity>() {
            @Override
            protected void deliverResult( CommCareWiFiDirectActivity receiver, Integer result) {
                Log.d(TAG, "delivering unzip result");
                if(result > 0){
                    receiver.onUnzipSuccessful(result);
                    return;
                } else {
                    //assume that we've already set the error message, but make it look scary
                    receiver.transplantStyle(myStatusText, R.layout.template_text_notification_problem);
                }
            }

            @Override
            protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
                Log.d(TAG, "delivering unzip upate");
                receiver.updateProgress(update[0], CommCareTask.GENERIC_TASK_ID);
                receiver.myStatusText.setText(update[0]);
            }

            @Override
            protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
                Log.d(TAG, "unzip deliver error: " + e.getMessage());
                receiver.myStatusText.setText(Localization.get("mult.install.error", new String[] {e.getMessage()}));
                receiver.transplantStyle(myStatusText, R.layout.template_text_notification_problem);
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
        Logger.log(TAG, "Discovering Wi-fi direct peers");
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

                Logger.log(TAG, "Discovery of Wi-fi peers failed");

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
        Logger.log(TAG,"connecting to wi-fi peer");

        Log.d(TAG, "connect in activity");

        mManager.connect(mChannel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Logger.log(TAG,"Connection to peer failed");
                Toast.makeText(CommCareWiFiDirectActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void disconnect() {
        Logger.log(TAG, "disconnecting from wi-fi direct group");
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
        //  A cancel abort request by user. Disconnect i.e. removeGroup if
        //  already connected. Else, request WifiP2pManager to abort the
        //  ongoing request
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
        Logger.log(TAG, "Preparing File Transfer");

        CommCareWiFiDirectActivity.deleteIfExists(sourceZipDirectory);

        final WiFiDirectManagementFragment fragment = (WiFiDirectManagementFragment) getSupportFragmentManager()
                .findFragmentById(R.id.wifi_manager_fragment);

        if(!fragment.getDeviceConnected()){
            Logger.log(TAG, "Device not connected to Wi-Fi Direct group");
            myStatusText.setText("This devices is not connected to any Wi-Fi Direct group.");
            return;
        }

        zipFiles();
    }

    public void onZipSuccesful(FormRecord[] records){
        Logger.log(TAG, "successfully zipped files of size: " + records.length);
        Log.d(CommCareWiFiDirectActivity.TAG, "Zip successful, attempting to send");
        myStatusText.setText("Zip successful, attempting to send files...");
        this.cachedRecords = records;
        updateStatusText();
        sendFiles();
    }

    public void onZipError(){
        FileUtil.deleteFileOrDir(new File(sourceDirectory));

        Logger.log(TAG, "Error zipping files");

        Log.d(CommCareWiFiDirectActivity.TAG, "Zip unsuccesful");

    }

    public void onUnzipSuccessful(Integer result){
        Logger.log(TAG, "Successfully unzipped files");

        Toast.makeText(CommCareWiFiDirectActivity.this, "Received " + result.toString() + " Files Successfully!",
                Toast.LENGTH_SHORT).show();

        myStatusText.setText("Receive Successful!");

        if(!FileUtil.deleteFileOrDir(new File(receiveDirectory))){
            Log.d(TAG, "source zip not succesfully deleted");
        }

        updateStatusText();
    }

    public void zipFiles(){
        Logger.log(TAG, "Zipping Files");
        Log.d(CommCareWiFiDirectActivity.TAG, "Zipping Files2");
        ZipTask mZipTask = new ZipTask(this) {
            @Override
            protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
                receiver.updateProgress(update[0], taskId);
                receiver.myStatusText.setText(update[0]);
            }

            @Override
            protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
                receiver.myStatusText.setText("Error zipping files");
                receiver.transplantStyle(receiver.myStatusText, R.layout.template_text_notification_problem);
            }

            @Override
            protected void deliverResult(CommCareWiFiDirectActivity receiver, FormRecord[] result) {
                if(result != null){
                    receiver.onZipSuccesful(result);
                    return;
                } else {
                    receiver.onZipError();
                    receiver.transplantStyle(receiver.myStatusText, R.layout.template_text_notification_problem);
                    return;
                }
            }
        };
        mZipTask.connect(CommCareWiFiDirectActivity.this);
        mZipTask.execute();
    }

    public void sendFiles(){
        Logger.log(TAG, "Sending Files via Wi-fi Direct");
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
                    receiver.transplantStyle(receiver.myStatusText, R.layout.template_text_notification_problem);
                    return;
                }

            }

            @Override
            protected void deliverUpdate(CommCareWiFiDirectActivity receiver,
                    String... update) {
                receiver.updateProgress(update[0], taskId);
                receiver.myStatusText.setText(update[0]);
            }

            @Override
            protected void deliverError(CommCareWiFiDirectActivity receiver,
                    Exception e) {
                receiver.myStatusText.setText("Error sending files with exception: " + e.getMessage());
                receiver.transplantStyle(receiver.myStatusText, R.layout.template_text_notification_problem);

            }

        };

        mTransferTask.connect(CommCareWiFiDirectActivity.this);
        mTransferTask.execute();

        Log.d(CommCareWiFiDirectActivity.TAG, "Task started");
    }

    public void onSendSuccessful(){

        Logger.log(TAG, "File Send Successful");

        Toast.makeText(CommCareWiFiDirectActivity.this, "File Send Successful!",
                Toast.LENGTH_SHORT).show();

        updateStatusText();
        myStatusText.setText("Forms Tranferred Successfully!");
        this.cleanPostSend();
    }

    public void onSendFail(){
        Logger.log(TAG, "Error Sending Files");
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
        Logger.log(TAG, "File server copying file");
        Log.d(CommCareWiFiDirectActivity.TAG, "Copying file");
        if(inputStream == null){
            Log.d(CommCareWiFiDirectActivity.TAG, "Input Null");
        }
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                Log.d(CommCareWiFiDirectActivity.TAG, "Copying file : " + new String(buf));
                out.write(buf, 0, len);

            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(CommCareWiFiDirectActivity.TAG, e.toString());
            Logger.log(TAG, "Copy in File Server failed");
            return false;
        }
        Logger.log(TAG, "Copy in File Server successful");
        return true;
    }

    @Override
    public void onFormsCopied(String result) {
        Logger.log(TAG, "Copied files successfully");
        Log.d(CommCareWiFiDirectActivity.TAG, "onCopySuccess");
        this.unzipFiles(result);
    }

    @Override
    public void updatePeers() {
        Logger.log(TAG, "Wi-Fi direct peers updating");
        mManager.requestPeers(mChannel, (PeerListListener) this.getSupportFragmentManager()
                .findFragmentById(R.id.frag_list));

    }
    
    @Override
    public void updateDeviceStatus(WifiP2pDevice mDevice) {
        Logger.log(TAG, "Wi-fi direct status updating");
        DeviceListFragment fragment = (DeviceListFragment) this.getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);

        fragment.updateThisDevice(mDevice);

    }
    
    /**
     * Implementation of generateProgressDialog() for DialogController -- other methods
     * handled entirely in CommCareActivity
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        switch (taskId) {
        case ZipTask.ZIP_TASK_ID:
            title = "Zipping Forms...";
            message ="CommCare is compressing your data for transfer";
            break;
        case UnzipTask.UNZIP_TASK_ID:
            title = "Unzipping forms";
            message = "CommCare is decompressing your forms onto your SD card";
            break;
        case SendTask.BULK_SEND_ID:
            title = "Submitting Forms";
            message = "CommCare is submitting your forms to the server";
            break;
        case FormTransferTask.BULK_TRANSFER_ID:
            title = "Sending Forms";
            message = "CommCare is sending your forms to your peer";
            break;
        case FILE_SERVER_TASK_ID:
            title = "Starting Receiving Files";
            message = "CommCare is receiving files";
            break;
        case WipeTask.WIPE_TASK_ID:
            title = "Wiping forms";
            message = "Cleaning up after transfer";
            break;
        default:
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in CommCareWifiDirectActivity");
            return null;
        }
        CustomProgressDialog dialog = CustomProgressDialog.newInstance(title, message, taskId);
        return dialog;
    }
}
